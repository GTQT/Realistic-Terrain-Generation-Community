package rtg.world.gen;

import net.minecraft.block.Block;
import net.minecraft.block.BlockStone;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.BitArray;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.BlockStateContainer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IBlockStatePalette;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import rtg.api.util.ChunkGenerationProfiler;
import rtg.api.util.FastOreContext;
import rtg.mixins.core.BlockStateContainerAccess;
import rtg.mixins.core.ChunkStorageAccess;

import java.util.Random;

/**
 * 原版 {@code WorldGenMinable#generate} 的快速实现，两种落块模式：
 *
 * <ul>
 *   <li><b>椭球链</b>（{@code -Drtg.oreBlobs=false}）：形状与随机数调用顺序和原版逐行一致，
 *       内容可逐格校验；只把"读/写"换成段存储直读写。</li>
 *   <li><b>球体</b>（默认）：每条矿脉只生成一个实心球，球外一格不读；球内仍然只替换天然石
 *       （与原版同一条谓词），所以泥土/沙砾/花岗岩只出现在地下石体内部，不会在地表留下补丁。
 *       形状是内容变更，但"只换石头的皮"这条与原版一致。</li>
 * </ul>
 *
 * <p>原版每条矿脉的开销几乎全在逐格状态机上：读一次要过
 * {@code Chunk#getBlockState} → {@code BlockStateContainer#get} → {@code BitArray#getAt} +
 * {@code palette.getBlockState(id)}，再查 {@code BlockStone.VARIANT} 判断天然石；写一次要过
 * {@code Chunk#setBlockState}（重读旧方块、算光照不透明度、查 tile entity、
 * 调 {@code breakBlock}/{@code onBlockAdded}、维护高度图）。这里把这些全部替换成
 * {@code BitArray} 直读写，形状计算则照旧——形状本来就不是瓶颈。
 *
 * <p>两种模式都只接管谓词为原版 {@code StonePredicate} 的矿脉；模组自定义矿石仍在原版路径，
 * 只是读/写会经过 {@link FastOreContext} 的 Chunk 直通（见 {@code WorldGenMinableMixin}）。
 */
public final class FastOreGen {

    private static final boolean ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("rtg.fastOreGen", "true"));

    /** 逐探针打印掩码不一致的日志（只用于排查内容差异，正常关闭）。 */
    private static final boolean DEBUG = Boolean.getBoolean("rtg.fastOreGenDebug");

    /** 原版 {@code WorldGenMinable.StonePredicate}（私有内部类，只能按名字认）。 */
    private static final String STONE_PREDICATE =
            "net.minecraft.world.gen.feature.WorldGenMinable$StonePredicate";

    /**
     * 球体模式：每条矿脉只放一个实心球，球内逐格判定、球外不读。
     * 判定仍是原版的"天然石"，因此泥土/沙砾/花岗岩只替换地下石体，不会盖到草地或地表。
     * 默认开启。
     */
    private static final boolean BLOBS =
            !"false".equalsIgnoreCase(System.getProperty("rtg.oreBlobs", "true"));
    /**
     * 球半径。石变种每区块 10 条、每条原版约 80 块，2.8 的球（约 115 格）在只替换天然石时
     * 落块量与原版 33 格椭球链接近（见 docs §22 的 census 对比）。
     */
    private static final float RADIUS_STONE_BLOB = 3.0F;
    /** 全局微调系数：想整体多/少一点石头球，改这一个值即可。 */
    private static final float BLOB_SCALE =
            Float.parseFloat(System.getProperty("rtg.oreBlobScale", "1.0f"));

    /** 当前缓存的区块；矿脉基本都落在同一区块里，跨区块时才重建段缓存。 */
    private static Chunk cacheChunk;
    /** 与 {@link #cacheChunk} 配套的段数组与高度图，省掉每次探针的接口强转。 */
    private static ExtendedBlockStorage[] storages;
    private static int[] heightMap;
    /** 本次矿脉里被高度图挡掉的探针数（矿脉结束时一次性并入计数器，避免逐探针 CAS）。 */
    private static long airSkips;
    /** 诊断：球体模式本次矿脉检查过的格子数、其中不是天然石的格子数。 */
    private static long blobCells;
    private static long blobNotStone;
    private static final Section[] SECTIONS = new Section[16];

    static {
        for (int i = 0; i < SECTIONS.length; i++) {
            SECTIONS[i] = new Section();
        }
    }

    private FastOreGen() {
    }

    /** 只接管原版矿石生成器：谓词是 {@code StonePredicate}、矿石方块来自原版包。 */
    public static boolean canHandle(net.minecraft.world.gen.feature.WorldGenMinable gen) {
        if (!ENABLED || gen.predicate == null) {
            return false;
        }
        if (!STONE_PREDICATE.equals(gen.predicate.getClass().getName())) {
            return false;
        }
        final Block block = gen.oreBlock.getBlock();
        // 沙砾（BlockFalling）必须走原版：直写会跳过 onBlockAdded 的 scheduleUpdate，
        // 留下悬空沙砾。它的落块量很小（每区块 8 条脉），不值得我们复现排程逻辑。
        if (block instanceof net.minecraft.block.BlockFalling) {
            return false;
        }
        return block.getClass().getName().startsWith("net.minecraft.block.");
    }

    /**
     * 与 {@code WorldGenMinable#generate} 逐行对应：随机数调用次数、椭球参数、遍历顺序全部一致，
     * 只是探针的读写走 {@link Chunk} 的段存储。
     */
    public static boolean generate(World world, Random rand, BlockPos position,
                                   IBlockState oreState, int numberOfBlocks) {
        // 球体只用于石变种（泥土/沙砾/天然花岗岩/闪长岩/安山岩）：它们占了原版探针的 92%，
        // 而且"球"与"蠕虫"在视觉上等价。真矿石保持椭球链，内容与原版逐格一致。
        if (BLOBS && isStoneVariant(oreState)) {
            return generateBlob(world, rand, position, oreState, numberOfBlocks);
        }
        ChunkGenerationProfiler.count(ChunkGenerationProfiler.Counter.ORE_FAST_VEINS);
        airSkips = 0L;

        final float f = rand.nextFloat() * (float) Math.PI;
        final double d0 = (double) ((float) (position.getX() + 8) + MathHelper.sin(f) * (float) numberOfBlocks / 8.0F);
        final double d1 = (double) ((float) (position.getX() + 8) - MathHelper.sin(f) * (float) numberOfBlocks / 8.0F);
        final double d2 = (double) ((float) (position.getZ() + 8) + MathHelper.cos(f) * (float) numberOfBlocks / 8.0F);
        final double d3 = (double) ((float) (position.getZ() + 8) - MathHelper.cos(f) * (float) numberOfBlocks / 8.0F);
        final double d4 = (double) (position.getY() + rand.nextInt(3) - 2);
        final double d5 = (double) (position.getY() + rand.nextInt(3) - 2);

        for (int i = 0; i < numberOfBlocks; ++i) {
            final float f1 = (float) i / (float) numberOfBlocks;
            final double d6 = d0 + (d1 - d0) * (double) f1;
            final double d7 = d4 + (d5 - d4) * (double) f1;
            final double d8 = d2 + (d3 - d2) * (double) f1;
            final double d9 = rand.nextDouble() * (double) numberOfBlocks / 16.0D;
            final double d10 = (double) (MathHelper.sin((float) Math.PI * f1) + 1.0F) * d9 + 1.0D;
            final double d11 = (double) (MathHelper.sin((float) Math.PI * f1) + 1.0F) * d9 + 1.0D;
            final int j = MathHelper.floor(d6 - d10 / 2.0D);
            final int k = MathHelper.floor(d7 - d11 / 2.0D);
            final int l = MathHelper.floor(d8 - d10 / 2.0D);
            final int i1 = MathHelper.floor(d6 + d10 / 2.0D);
            final int j1 = MathHelper.floor(d7 + d11 / 2.0D);
            final int k1 = MathHelper.floor(d8 + d10 / 2.0D);
            final double halfX = d10 / 2.0D;
            final double halfY = d11 / 2.0D;

            for (int l1 = j; l1 <= i1; ++l1) {
                final double d12 = ((double) l1 + 0.5D - d6) / halfX;
                final double d12sq = d12 * d12;

                if (d12sq < 1.0D) {
                    for (int i2 = k; i2 <= j1; ++i2) {
                        final double d13 = ((double) i2 + 0.5D - d7) / halfY;
                        final double d13sq = d12sq + d13 * d13;

                        if (d13sq < 1.0D) {
                            for (int j2 = l; j2 <= k1; ++j2) {
                                final double d14 = ((double) j2 + 0.5D - d8) / halfX;

                                if (d13sq + d14 * d14 < 1.0D) {
                                    tryReplace(world, l1, i2, j2, oreState, false);
                                }
                            }
                        }
                    }
                }
            }
        }

        ChunkGenerationProfiler.add(ChunkGenerationProfiler.Counter.ORE_AIR_SKIPS, airSkips);
        return true;
    }

    /** 石变种：泥土、沙砾、以及 {@code Blocks.STONE}（含天然花岗岩/闪长岩/安山岩）。 */
    private static boolean isStoneVariant(IBlockState oreState) {
        final Block block = oreState.getBlock();
        return block == Blocks.DIRT || block == Blocks.GRAVEL || block == Blocks.STONE;
    }

    private static float blobRadius() {
        return RADIUS_STONE_BLOB * BLOB_SCALE;
    }

    /**
     * 与 {@link #generate} 同一条随机数流（{@code nextFloat} + 两次 {@code nextInt(3)}
     * + {@code numberOfBlocks} 次 {@code nextDouble}，只为保持后续装饰的随机序列不变），
     * 但落块方式换成"以原版落点为中心、半径见 {@link #blobRadius()} 的实心球"。
     *
     * <p>球外一格不读；球内仍然只替换天然石，所以不会在地表或水里留下补丁。
     */
    private static boolean generateBlob(World world, Random rand, BlockPos position,
                                        IBlockState oreState, int numberOfBlocks) {
        ChunkGenerationProfiler.count(ChunkGenerationProfiler.Counter.ORE_FAST_VEINS);
        airSkips = 0L;
        blobCells = 0L;
        blobNotStone = 0L;

        final float f = rand.nextFloat() * (float) Math.PI;
        final double d4 = (double) (position.getY() + rand.nextInt(3) - 2);
        final double d5 = (double) (position.getY() + rand.nextInt(3) - 2);
        for (int i = 0; i < numberOfBlocks; ++i) {
            rand.nextDouble();
        }

        final int cx = position.getX() + 8;
        final int cy = MathHelper.floor((d4 + d5) / 2.0D);
        final int cz = position.getZ() + 8;
        // 让球心落点带一点原版的"方向感"，但幅度很小，视觉上就是一个圆包。
        final int shiftX = MathHelper.floor(MathHelper.sin(f) * (float) numberOfBlocks / 16.0F);
        final int shiftZ = MathHelper.floor(MathHelper.cos(f) * (float) numberOfBlocks / 16.0F);

        final float blobRadius = blobRadius();
        final int radius = MathHelper.ceil(blobRadius);
        final float radiusSq = blobRadius * blobRadius;
        for (int dx = -radius; dx <= radius; ++dx) {
            final float dxsq = (float) (dx * dx);
            for (int dy = -radius; dy <= radius; ++dy) {
                final float dxySq = dxsq + (float) (dy * dy);
                if (dxySq > radiusSq) {
                    continue;
                }
                for (int dz = -radius; dz <= radius; ++dz) {
                    if (dxySq + (float) (dz * dz) <= radiusSq) {
                        tryReplace(world, cx + shiftX + dx, cy + dy, cz + shiftZ + dz, oreState, true);
                    }
                }
            }
        }

        ChunkGenerationProfiler.add(ChunkGenerationProfiler.Counter.ORE_AIR_SKIPS, airSkips);
        ChunkGenerationProfiler.add(ChunkGenerationProfiler.Counter.ORE_BLOB_CELLS, blobCells);
        ChunkGenerationProfiler.add(ChunkGenerationProfiler.Counter.ORE_BLOB_NOT_STONE, blobNotStone);
        return true;
    }

    private static void tryReplace(World world, int x, int y, int z, IBlockState oreState, boolean blob) {
        if (blob) {
            blobCells++;
        }
        if (y < 0 || y > 255) {
            return;
        }

        final Chunk chunk = FastOreContext.getLoadedChunkAt(world, x, z);
        if (chunk == null) {
            // 未加载的邻居区块：保持原版语义（极少数跨区块探针）。
            final BlockPos pos = new BlockPos(x, y, z);
            if (isNaturalStone(world.getBlockState(pos))) {
                world.setBlockState(pos, oreState, 2);
            }
            return;
        }

        if (chunk != cacheChunk) {
            refreshChunk(chunk);
        }

        // 高度图之上必是空气（高度图记录的是最高不透明方块的上方一格），
        // 原版在这里照样要走一次完整的方块查表再判定失败，这里直接跳过，结果完全相同。
        if (y >= heightMap[((z & 15) << 4) | (x & 15)]) {
            airSkips++;
            return;
        }

        final ExtendedBlockStorage section = storages[y >> 4];
        if (section == null) {
            return;
        }

        final BlockStateContainer data = section.getData();
        final Section cached = section(y >> 4, data);
        BitArray storage = cached.storage;

        if (storage == null || cached.stone == null) {
            // registry palette（单段超过 256 种状态）：退回逐格查表，仍是 Chunk 直读。
            final IBlockState state = section.get(x & 15, y & 15, z & 15);
            if (isNaturalStone(state)) {
                FastOreContext.noteWrite();
                chunk.setBlockState(new BlockPos(x, y, z), oreState);
            } else if (blob) {
                blobNotStone++;
            }
            return;
        }

        final int index = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
        final int rawId = storage.getAt(index);
        if (!cached.stone[rawId]) {
            if (blob) {
                blobNotStone++;
            }
            if (DEBUG) {
                final IBlockState actual = section.get(x & 15, y & 15, z & 15);
                if (isNaturalStone(actual)) {
                    FastOreContext.noteMaskMismatch(actual + " id=" + rawId
                            + " maskLen=" + cached.stone.length
                            + " palette=" + cached.palette.getClass().getSimpleName()
                            + " bits=" + accessOf(data).rtgc$getBits());
                }
            }
            return;
        }

        int oreId = cached.oreId;
        if (cached.oreState != oreState) {
            final BlockStateContainerAccess access = (BlockStateContainerAccess) (Object) data;
            oreId = cached.palette.idFor(oreState);
            // idFor 可能触发扩容：palette/storage 会被整个换掉，缓存必须重建。
            if (access.rtgc$getPalette() != cached.palette) {
                prepare(cached, data);
                storage = cached.storage;
                if (storage == null || cached.stone == null) {
                    return;
                }
                final int recheck = storage.getAt(index);
                if (!cached.stone[recheck]) {
                    return;
                }
            } else {
                cached.storage = access.rtgc$getStorage();
                storage = cached.storage;
                // 未扩容时 palette 对象不变，但新状态可能刚占了空位（例如本次写入的闪长岩）：
                // 位图必须同步，否则后续矿脉认不出这种方块还能被替换，内容就和原版不一致了。
                if (cached.stone != null && oreId >= 0 && oreId < cached.stone.length) {
                    cached.stone[oreId] = isNaturalStone(oreState);
                }
            }
            cached.oreState = oreState;
            cached.oreId = oreId;
        }

        storage.setAt(index, oreId);
        FastOreContext.noteWrite();
        chunk.markDirty();
        // 下落方块（沙砾）在 canHandle 里已被排除，这里不需要补排程。
    }

    private static BlockStateContainerAccess accessOf(BlockStateContainer data) {
        return (BlockStateContainerAccess) (Object) data;
    }

    private static void refreshChunk(Chunk chunk) {
        cacheChunk = chunk;
        final ChunkStorageAccess access = (ChunkStorageAccess) (Object) chunk;
        storages = access.rtgc$getStorageArrays();
        heightMap = access.rtgc$getHeightMap();
        for (int i = 0; i < SECTIONS.length; i++) {
            SECTIONS[i].data = null;
            SECTIONS[i].oreState = null;
        }
    }

    private static Section section(int index, BlockStateContainer data) {
        final Section cached = SECTIONS[index];
        if (cached.data != data) {
            prepare(cached, data);
        }
        return cached;
    }

    private static void prepare(Section cached, BlockStateContainer data) {
        final BlockStateContainerAccess access = (BlockStateContainerAccess) (Object) data;
        cached.data = data;
        cached.palette = access.rtgc$getPalette();
        cached.storage = access.rtgc$getStorage();
        cached.oreState = null;
        cached.oreId = -1;

        final int bits = access.rtgc$getBits();
        if (bits > 8) {
            // registry palette：id 就是全局 state id，逐 id 扫描不现实，退回逐格查表。
            cached.stone = null;
            return;
        }

        final int size = 1 << bits;
        boolean[] mask = cached.stone;
        if (mask == null || mask.length < size) {
            mask = new boolean[size];
            cached.stone = mask;
        } else {
            java.util.Arrays.fill(mask, 0, size, false);
        }
        for (int id = 0; id < size; id++) {
            final IBlockState state = cached.palette.getBlockState(id);
            if (state != null && isNaturalStone(state)) {
                mask[id] = true;
            }
        }
    }

    /** 与 {@code WorldGenMinable.StonePredicate} 等价：{@code Blocks.STONE} 的天然变种（不含磨制变种）。 */
    private static boolean isNaturalStone(IBlockState state) {
        if (state == null || state.getBlock() != Blocks.STONE) {
            return false;
        }
        return ((BlockStone.EnumType) state.getValue(BlockStone.VARIANT)).isNatural();
    }

    private static final class Section {
        BlockStateContainer data;
        IBlockStatePalette palette;
        BitArray storage;
        boolean[] stone;
        IBlockState oreState;
        int oreId = -1;
    }
}
