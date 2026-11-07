package rtg.world.gen;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.BitArray;
import net.minecraft.world.World;
import net.minecraft.world.chunk.*;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import rtg.mixins.core.BlockStateContainerAccess;
import rtg.mixins.core.ChunkPrimerAccess;
import rtg.mixins.core.ChunkStorageAccess;
import rtg.mixins.core.ExtendedBlockStorageAccess;

/**
 * 用「全局 id 直写」的方式把 {@link ChunkPrimer} 拼成 {@link Chunk}，替代原版
 * {@code new Chunk(world, primer, cx, cz)}。
 *
 * <p>原版构造是 65536 次 {@code primer.getBlockState()}（每次 {@code BLOCK_STATE_IDS.getByValue}）
 * + 每次非空气 {@code ExtendedBlockStorage.set()}（再一次 {@code getBlockState} + 两次 {@code getBlock} +
 * {@code palette.idFor}），实测 0.83ms/chunk。这里走的是：primer 的 {@code char[]} 里本来就是全局 id，
 * 把目标段的 palette 换成 registry 模式（此时 {@code BitArray} 里存的就是同一个全局 id），
 * 然后直接按位打包写 long[]，顺带数出 blockRefCount / tickRefCount，全程没有一次 map 查表。
 *
 * <p>位宽不写死：1.12.2 + BOP 的 block state 总数可能超过 8192，registry palette 的实际位宽由
 * {@code BlockStateContainer#onResize} 自己算出来（本环境是 14 位），这里跟着用。
 *
 * <p>{@code -Drtg.fastChunkBuild=false} 可关掉（A/B 对比用）。
 */
public final class FastChunkAssembler {

    private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("rtg.fastChunkBuild", "true"));
    private static final boolean VERIFY = Boolean.getBoolean("rtg.fastChunkBuildVerify");
    private static int verifyLogs;

    private static IBlockStatePalette registryPalette;
    private static boolean[] randomTickState;
    private static int bits;
    private static long mask;
    private static boolean unusable;
    private static boolean loggedFailure;
    /**
     * RoughlyEnoughIDs（本整合的硬依赖，{@code libs/RoughlyEnoughIDs-2.2.2.jar}）会把 {@link ChunkPrimer}
     * 的方块存储从 {@code char[] data} 换成 {@code int[] intData}（它取消了 4096 个方块/65536 个状态的限制）。
     * 有它就直读 int[]，没有就退回 char[]。用反射而不是 @Accessor：字段在无 REID 的环境里不存在，
     * @Accessor 会让类加载直接失败。
     */
    private static java.lang.reflect.Field primerIntDataField;
    private static boolean primerIntDataResolved;

    private FastChunkAssembler() {
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    /** 同 {@code new Chunk(world, primer, cx, cz)}，但走快路径；任何前提不满足都退回原版构造。 */
    public static Chunk build(World world, ChunkPrimer primer, int cx, int cz) {
        if (!ENABLED || unusable || !ensureInit()) {
            return new Chunk(world, primer, cx, cz);
        }

        long tShell = System.nanoTime();
        Chunk chunk = new Chunk(world, cx, cz);
        if (rtg.api.util.ChunkGenerationProfiler.isEnabled()) {
            rtg.api.util.ChunkGenerationProfiler.add(
                    rtg.api.util.ChunkGenerationProfiler.Counter.FINALIZE_SHELL_NS, System.nanoTime() - tShell);
        }
        long tFill = System.nanoTime();
        try {
            fill(chunk, primer, world.provider.hasSkyLight());
            if (rtg.api.util.ChunkGenerationProfiler.isEnabled()) {
                rtg.api.util.ChunkGenerationProfiler.add(
                        rtg.api.util.ChunkGenerationProfiler.Counter.FINALIZE_FILL_NS, System.nanoTime() - tFill);
            }
            if (VERIFY) {
                verify(chunk, primer, world, cx, cz);
            }
            return chunk;
        } catch (Throwable t) {
            unusable = true;
            if (!loggedFailure) {
                loggedFailure = true;
                rtg.api.util.Logger.warn("FastChunkAssembler 失败，回退原版区块构造: " + t);
            }
            return new Chunk(world, primer, cx, cz);
        }
    }

    private static boolean ensureInit() {
        if (registryPalette != null) {
            return true;
        }
        try {
            // 造一个模板容器，onResize 到 registry 模式后把 palette 掏出来复用。
            // registry palette 是无状态的（idFor/getState 都直接查 BLOCK_STATE_IDS），可以全局共享。
            BlockStateContainer template = new BlockStateContainer();
            template.onResize(13, Blocks.AIR.getDefaultState());
            BlockStateContainerAccess access = (BlockStateContainerAccess) template;
            bits = access.rtgc$getBits();
            if (bits <= 8 || bits > 16) {
                unusable = true;
                rtg.api.util.Logger.warn("FastChunkAssembler 不可用：registry palette 位宽异常 = " + bits);
                return false;
            }
            mask = (1L << bits) - 1L;
            registryPalette = access.rtgc$getPalette();

            int size = Block.BLOCK_STATE_IDS.size();
            boolean[] randomTick = new boolean[size];
            for (int id = 0; id < size; id++) {
                IBlockState state = Block.BLOCK_STATE_IDS.getByValue(id);
                randomTick[id] = state != null && state.getBlock().getTickRandomly();
            }
            randomTickState = randomTick;
            rtg.api.util.Logger.info("[RTG] FastChunkAssembler 启用：registryBits=" + bits
                    + " states=" + size + " randomTickStates=" + countTrue(randomTick)
                    + " primer存储=" + resolvePrimerStorageDescription());
            return true;
        } catch (Throwable t) {
            unusable = true;
            rtg.api.util.Logger.warn("FastChunkAssembler 初始化失败，回退原版区块构造: " + t);
            return false;
        }
    }

    private static String resolvePrimerStorageDescription() {
        if (primerIntDataResolved) {
            return primerIntDataField != null ? "REID int[]" : "char[]";
        }
        primerIntDataResolved = true;
        try {
            java.lang.reflect.Field field = ChunkPrimer.class.getDeclaredField("intData");
            if (field.getType() == int[].class) {
                field.setAccessible(true);
                primerIntDataField = field;
                return "REID int[]";
            }
            rtg.api.util.Logger.warn("FastChunkAssembler：intData 字段类型不是 int[]，忽略");
        } catch (NoSuchFieldException expected) {
            // 没有 REID 的原版环境，走 char[]
        } catch (Throwable t) {
            rtg.api.util.Logger.warn("FastChunkAssembler：读取 intData 字段失败，退回 char[]: " + t);
        }
        return "char[]";
    }

    private static int countTrue(boolean[] array) {
        int count = 0;
        for (boolean value : array) {
            if (value) {
                count++;
            }
        }
        return count;
    }

    /**
     * 诊断用（{@code -Drtg.fastChunkBuildVerify=true}）：用同一份 primer 再走一次原版构造，逐格比对全局 id。
     * 只在前 8 个不一致区块打日志，避免刷屏。
     */
    private static void verify(Chunk chunk, ChunkPrimer primer, World world, int cx, int cz) {
        if (verifyLogs == 0) {
            rtg.api.util.Logger.warn("[RTG] primer校验源=" + (primerIntDataField != null ? "REID int[]" : "char[]")
                    + " id(0,0,0)=" + Block.BLOCK_STATE_IDS.get(primer.getBlockState(0, 0, 0))
                    + " id(0,64,0)=" + Block.BLOCK_STATE_IDS.get(primer.getBlockState(0, 64, 0)));
        }
        Chunk reference = new Chunk(world, primer, cx, cz);
        ExtendedBlockStorage[] ours = chunk.getBlockStorageArray();
        ExtendedBlockStorage[] theirs = reference.getBlockStorageArray();
        int mismatches = 0;
        String first = null;
        for (int section = 0; section < 16; section++) {
            ExtendedBlockStorage a = ours[section];
            ExtendedBlockStorage b = theirs[section];
            if (a == null && b == null) {
                continue;
            }
            if (a == null || b == null) {
                mismatches += 4096;
                if (first == null) {
                    first = "section " + section + " null-mismatch ours=" + (a != null) + " ref=" + (b != null);
                }
                continue;
            }
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int idA = Block.BLOCK_STATE_IDS.get(a.get(x, y, z));
                        int idB = Block.BLOCK_STATE_IDS.get(b.get(x, y, z));
                        if (idA != idB) {
                            mismatches++;
                            if (first == null) {
                                first = "(" + x + "," + (section << 4 | y) + "," + z + ") ours=" + idA + " ref=" + idB;
                            }
                        }
                    }
                }
            }
        }
        if (mismatches == 0) {
            return;
        }
        if (verifyLogs < 8) {
            verifyLogs++;
            final StringBuilder pattern = new StringBuilder();
            final StringBuilder counts = new StringBuilder();
            final StringBuilder refs = new StringBuilder();
            for (int section = 0; section < 16; section++) {
                pattern.append(ours[section] != null ? 'o' : '-');
                pattern.append(theirs[section] != null ? 'r' : '-');
                int n = 0;
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        for (int y = 0; y < 16; y++) {
                            if (primer.getBlockState(x, (section << 4) | y, z).getBlock() != Blocks.AIR) {
                                n++;
                            }
                        }
                    }
                }
                counts.append(n).append(section == 15 ? "" : ",");
                refs.append(theirs[section] == null ? "-"
                        : (((ExtendedBlockStorageAccess) theirs[section]).rtgc$getBlockRefCount() + "/"
                                + (ours[section] == null ? "-"
                                        : ((ExtendedBlockStorageAccess) ours[section]).rtgc$getBlockRefCount())))
                        .append(section == 15 ? "" : ",");
            }
            rtg.api.util.Logger.warn("[RTG] FastChunkAssembler 校验不一致 chunk=(" + cx + "," + cz + ") 差异="
                    + mismatches + " 首个=" + first);
            rtg.api.util.Logger.warn("[RTG]   段模式(ours/ref)=" + pattern + " data非零=" + counts + " refBlockRefs=" + refs);
            rtg.api.util.Logger.warn("[RTG]   arrayIdentity ours=" + System.identityHashCode(ours) + " ref="
                    + System.identityHashCode(theirs) + " accessor="
                    + System.identityHashCode(((ChunkStorageAccess) chunk).rtgc$getStorageArrays()));
        }
    }

    private static final IBlockState AIR_STATE = Blocks.AIR.getDefaultState();

    /**
     * 去重用的开放寻址表（容量 512、装载上限 256）。世界生成只在服务器线程跑，这些 scratch 数组按
     * "每段清一次 512 个 int" 复用，避免每区块再分配 20KB 垃圾。
     */
    private static final int DEDUPE_CAPACITY = 512;
    private static final int DEDUPE_MASK = DEDUPE_CAPACITY - 1;
    private static final int[] DEDUPE_KEYS = new int[DEDUPE_CAPACITY];
    private static final int[] DEDUPE_VALUES = new int[DEDUPE_CAPACITY];
    private static final byte[] LOCAL_IDS = new byte[4096];
    private static final IBlockState[] LOCAL_STATES = new IBlockState[256];

    private static void fill(Chunk chunk, ChunkPrimer primer, boolean hasSkyLight) {
        ExtendedBlockStorage[] storages = ((ChunkStorageAccess) chunk).rtgc$getStorageArrays();
        int[] intData = null;
        char[] charData = null;
        if (primerIntDataField != null) {
            try {
                intData = (int[]) primerIntDataField.get(primer);
            } catch (Throwable t) {
                primerIntDataField = null;
            }
        }
        if (intData == null) {
            charData = ((ChunkPrimerAccess) primer).rtgc$getData();
        }
        final int[] dataInt = intData;
        final char[] dataChar = charData;
        final boolean[] randomTick = randomTickState;

        for (int section = 0; section < 16; section++) {
            final int yBase = section << 4;

            // ---- pass A：去重，按首次出现顺序分配段内 id（0 固定留给空气）----
            java.util.Arrays.fill(DEDUPE_KEYS, -1);
            int distinct = 1;
            int blockRefs = 0;
            int tickRefs = 0;
            boolean overflow = false;

            outer:
            for (int x = 0; x < 16; x++) {
                int xBase = (x << 12) | yBase;
                for (int z = 0; z < 16; z++) {
                    int primerBase = xBase | (z << 8);
                    int columnBase = (z << 4) | x;
                    for (int y = 0; y < 16; y++) {
                        int id = dataInt != null ? dataInt[primerBase | y] : dataChar[primerBase | y];
                        int index = columnBase | (y << 8);
                        if (id == 0) {
                            // 必须显式清零：LOCAL_IDS 是跨段复用的 scratch 数组，残留的是上一段的 id。
                            LOCAL_IDS[index] = 0;
                            continue;
                        }
                        blockRefs++;
                        if (randomTick[id]) {
                            tickRefs++;
                        }
                        int slot = (id * 0x9E3779B1) >>> 23;
                        while (true) {
                            int key = DEDUPE_KEYS[slot];
                            if (key == id) {
                                LOCAL_IDS[index] = (byte) DEDUPE_VALUES[slot];
                                break;
                            }
                            if (key == -1) {
                                if (distinct == 256) {
                                    overflow = true;
                                    break outer;
                                }
                                DEDUPE_KEYS[slot] = id;
                                DEDUPE_VALUES[slot] = distinct;
                                LOCAL_STATES[distinct] = Block.BLOCK_STATE_IDS.getByValue(id);
                                LOCAL_IDS[index] = (byte) distinct;
                                distinct++;
                                break;
                            }
                            slot = (slot + 1) & DEDUPE_MASK;
                        }
                    }
                }
            }

            if (overflow) {
                fillSectionRegistry(storages, section, dataInt, dataChar, hasSkyLight, randomTick);
                continue;
            }
            if (blockRefs == 0) {
                // 与原版一致：全空气的段保持 null，不分配 ExtendedBlockStorage。
                continue;
            }

            // ---- pass B：按 distinct 选位宽，造一个"紧凑 palette"（原版同款实现，只是跳过扩容拷贝）----
            // 注意：palette 是活的——之后装饰阶段还会往里塞新方块状态，必须把段自己的容器当 resize 回调，
            // 传 null 会在装饰越界时 NPE。
            ExtendedBlockStorage storage = new ExtendedBlockStorage(yBase, hasSkyLight);
            BlockStateContainer containerImpl = storage.getData();
            BlockStateContainerAccess container = (BlockStateContainerAccess) containerImpl;
            int width = 4;
            while ((1 << width) < distinct) {
                width++;
            }
            IBlockStatePalette palette = width == 4
                    ? new BlockStatePaletteLinear(4, containerImpl)
                    : new BlockStatePaletteHashMap(width, containerImpl);
            for (int k = 0; k < distinct; k++) {
                int assigned = palette.idFor(k == 0 ? AIR_STATE : LOCAL_STATES[k]);
                if (assigned != k) {
                    throw new IllegalStateException("紧凑 palette id 漂移: " + assigned + " != " + k);
                }
            }

            container.rtgc$setPalette(palette);
            container.rtgc$setBits(width);
            BitArray array = new BitArray(width, 4096);
            container.rtgc$setStorage(array);

            long[] packed = array.getBackingLongArray();
            long valueMask = (1L << width) - 1L;
            for (int index = 0; index < 4096; index++) {
                int value = LOCAL_IDS[index] & 0xFF;
                if (value == 0) {
                    continue;
                }
                int bitPos = index * width;
                int word = bitPos >>> 6;
                int offset = bitPos & 63;
                packed[word] = (packed[word] & ~(valueMask << offset)) | (((long) value) << offset);
                if (offset > 64 - width) {
                    int shift = 64 - offset;
                    packed[word + 1] = (packed[word + 1] & ~(valueMask >>> shift)) | (((long) value) >>> shift);
                }
            }

            ExtendedBlockStorageAccess access = (ExtendedBlockStorageAccess) storage;
            access.rtgc$setBlockRefCount(blockRefs);
            access.rtgc$setTickRefCount(tickRefs);
            storages[section] = storage;
        }
    }

    /** 一段里出现超过 256 种不同状态时的退路：全局 registry palette + 全局 id 直写。 */
    private static void fillSectionRegistry(ExtendedBlockStorage[] storages, int section, int[] dataInt,
            char[] dataChar, boolean hasSkyLight, boolean[] randomTick) {
        final int yBase = section << 4;
        final int width = bits;
        final long valueMask = mask;
        ExtendedBlockStorage storage = null;
        long[] packed = null;
        int blockRefs = 0;
        int tickRefs = 0;

        for (int x = 0; x < 16; x++) {
            int xBase = (x << 12) | yBase;
            for (int z = 0; z < 16; z++) {
                int primerBase = xBase | (z << 8);
                int columnBase = (z << 4) | x;
                for (int y = 0; y < 16; y++) {
                    int id = dataInt != null ? dataInt[primerBase | y] : dataChar[primerBase | y];
                    if (id == 0) {
                        continue;
                    }
                    if (storage == null) {
                        storage = new ExtendedBlockStorage(yBase, hasSkyLight);
                        BlockStateContainerAccess container = (BlockStateContainerAccess) storage.getData();
                        container.rtgc$setPalette(registryPalette);
                        container.rtgc$setBits(width);
                        BitArray array = new BitArray(width, 4096);
                        container.rtgc$setStorage(array);
                        packed = array.getBackingLongArray();
                    }
                    blockRefs++;
                    if (randomTick[id]) {
                        tickRefs++;
                    }
                    // 容器下标 = y << 8 | z << 4 | x（段内 y 用低 4 位）
                    int index = columnBase | (y << 8);
                    int bitPos = index * width;
                    int word = bitPos >>> 6;
                    int offset = bitPos & 63;
                    packed[word] = (packed[word] & ~(valueMask << offset)) | (((long) id) << offset);
                    if (offset > 64 - width) {
                        int shift = 64 - offset;
                        packed[word + 1] = (packed[word + 1] & ~(valueMask >>> shift)) | (((long) id) >>> shift);
                    }
                }
            }
        }

        if (storage != null) {
            ExtendedBlockStorageAccess access = (ExtendedBlockStorageAccess) storage;
            access.rtgc$setBlockRefCount(blockRefs);
            access.rtgc$setTickRefCount(tickRefs);
            storages[section] = storage;
        }
    }
}
