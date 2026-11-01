package rtg.world.gen.worley;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockSand;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.MapGenBase;
import net.minecraftforge.fluids.IFluidBlock;
import rtg.api.world.biome.IRealisticBiome;

/**
 * WorleyCaves 的洞穴生成器（移植自 WorleyCaves 1.5.2，MIT License，见 THIRD-PARTY-NOTICES.md）。
 *
 * <p>算法与上游 `fluke.worleycaves.world.WorleyCaveGenerator` **逐行一致**：
 * 5×5 列 × 129 个 Y 采样点做 3D Voronoi，再在 4×4×2 的子块里做三线性插值挖洞。
 * 相对上游只做了四类改动，全部在"怎么取数据"这一层，不碰算术：
 *
 * <ol>
 *   <li><b>种子</b>：上游把噪声种子写死（`WorleyUtil.m_seed = FastNoise.m_seed = 1337`，
 *       还留着 "TODO noise should probably be seeded with world seed"），于是每个世界的洞穴
 *       形状完全一样。这里由世界种子派生。</li>
 *   <li><b>地表材质</b>：上游用 `biome.topBlock/fillerBlock` 判定破土并补顶；RTG 的地表是
 *       `SurfaceBase` 按群系画出来的，两者未必一致。这里优先取 {@code IRealisticBiome#surface()}
 *       的 top/filler（由 {@code ChunkGeneratorRTG} 把本区块的地表群系数组传进来），
 *       拿不到才回落到 MC 群系 —— 于是"石山崖壁上补出一块草"这类错位从根上消失。</li>
 *   <li><b>分配</b>：采样缓冲扁平化并按线程复用、去掉每方块一次的 `new BlockPos`、
 *       群系按 4×4 子列缓存（上游是**每挖一个方块**查一次群系，在 RTG 里那是一次
 *       `RtgBiomeLayout` 查询）。改动本身在无头基准里省 11%（0.426 → 0.379 ms/区块），
 *       且优化前后输出**逐块相同**；真机服务端整段 `CAVES` 为 0.78 ms/区块（原版 0.14 ms）。</li>
 *   <li><b>回退</b>：上游对"黑名单维度"回退成 `new MapGenCaves()`；RTG 里没有黑名单维度
 *       （世界类型只在白名单维度启用），故不需要该分支。</li>
 * </ol>
 *
 * <h2>为什么继承 MapGenBase 而不是 MapGenCaves</h2>
 * 上游直接覆写 `generate()`，压根不走 `MapGenCaves.recursiveGenerate` 的随机游走，
 * 也用不到它的 `canReplaceBlock`/`digBlock`。继承 `MapGenBase` 可以继续挂在
 * `TerrainGen.getModdedMapGen(..., CAVE)` 这条链上（第三方洞穴模组仍可优先替换我们），
 * 同时不让人误以为它和原版洞穴共享语义。
 *
 * <h2>线程安全</h2>
 * 1.12.2 的区块生成在主线程，但结构选址/第三方工具可能从别的线程触碰生成器；
 * 采样缓冲与列缓存都放在 {@link ThreadLocal} 里，避免跨线程踩数组。
 */
public class WorleyCavesGenerator extends MapGenBase {

    /** 采样数组里"本列是否有洞"的标志位下标（上游同为 129）。 */
    private static final int HAS_CAVES_FLAG = 129;
    /** 每个采样列的 Y 采样点数（索引 0..129，真实高度 = y*2）。 */
    private static final int SAMPLES_Y = 130;
    /** 扁平采样数组长度：5 列 × 130 × 5 列。 */
    private static final int SAMPLES = 5 * SAMPLES_Y * 5;

    /** 上游取地表最高点用的 6 个六边形测试点（getMaxSurfaceHeight）。 */
    private static final int[] SURFACE_TEST_X = {2, 3, 7, 9, 12, 13};
    private static final int[] SURFACE_TEST_Z = {6, 11, 2, 13, 4, 9};

    private static final IBlockState AIR = Blocks.AIR.getDefaultState();
    private static final IBlockState LAVA = Blocks.LAVA.getDefaultState();
    private static final IBlockState SANDSTONE = Blocks.SANDSTONE.getDefaultState();
    private static final IBlockState RED_SANDSTONE = Blocks.RED_SANDSTONE.getDefaultState();

    private final WorleyCaveParams params;
    private final WorleyNoise worleyNoise;
    private final WorleyWarpNoise warpNoise;
    private final ThreadLocal<Scratch> scratch = ThreadLocal.withInitial(Scratch::new);

    public WorleyCavesGenerator(long worldSeed, WorleyCaveParams params) {
        this.params = params;
        // 用种子的高低 32 位分别喂两条噪声，避免两条噪声"同源同相"
        this.worleyNoise = new WorleyNoise((int) (worldSeed ^ (worldSeed >>> 32)), 0.016f);
        this.warpNoise = new WorleyWarpNoise((int) ((worldSeed >>> 32) ^ worldSeed ^ 0x5DEECE66DL), 0.05f);
    }

    public WorleyCaveParams params() {
        return this.params;
    }

    /** 通用入口（事件链/第三方调用时走这里，没有 RTG 的地表群系数组）。 */
    @Override
    public void generate(World worldIn, int x, int z, ChunkPrimer primer) {
        this.world = worldIn;
        carve(worldIn, x, z, primer, null);
    }

    /**
     * RTG 专用入口：带上本区块**逐列**的地表群系（`ChunkGeneratorRTG#jitteredBiomes`，
     * 下标 x*16+z）。有了它，破土补顶才能用 RTG 自己的地表材质，而不是 MC 群系的 `topBlock`。
     */
    public void generate(World worldIn, int x, int z, ChunkPrimer primer, IRealisticBiome[] columnBiomes) {
        this.world = worldIn;
        carve(worldIn, x, z, primer, columnBiomes);
    }

    // ==================== 主循环 ====================

    private void carve(World worldIn, int chunkX, int chunkZ, ChunkPrimer primer, IRealisticBiome[] columnBiomes) {

        final Scratch s = this.scratch.get();
        resolveColumns(worldIn, chunkX, chunkZ, columnBiomes, s);

        final int chunkMaxHeight = getMaxSurfaceHeight(primer);
        final float[] samples = s.samples;
        sampleNoise(chunkX, chunkZ, chunkMaxHeight + 1, samples);

        final float oneQuarter = 0.25F;
        final float oneHalf = 0.5F;
        final int maxCaveHeight = this.params.maxCaveHeight;
        final int seaLevel = worldIn.getSeaLevel();
        final int lavaDepth = this.params.lavaDepth;
        final int easeInDepth = this.params.easeInDepth;
        final float noiseCutoff = this.params.noiseCutoff;
        final float surfaceCutoff = this.params.surfaceCutoff;
        final int minCaveHeight = this.params.minCaveHeight;

        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                int depth = 0;

                // 这一列（含右/后邻列）没有任何洞时整列跳过：省掉 64 次 y 循环
                if (samples[idx(x, HAS_CAVES_FLAG, z)] == 0
                        && samples[idx(x + 1, HAS_CAVES_FLAG, z)] == 0
                        && samples[idx(x, HAS_CAVES_FLAG, z + 1)] == 0
                        && samples[idx(x + 1, HAS_CAVES_FLAG, z + 1)] == 0) {
                    continue;
                }

                final int column = x * 4 + z;
                final IBlockState columnTop = s.top[column];
                final IBlockState columnFiller = s.filler[column];
                final boolean exceptionBiome = s.exceptionBiome[column];

                for (int y = (maxCaveHeight / 2) - 1; y >= 0; y--) {
                    final float x0y0z0 = samples[idx(x, y, z)];
                    final float x0y0z1 = samples[idx(x, y, z + 1)];
                    final float x1y0z0 = samples[idx(x + 1, y, z)];
                    final float x1y0z1 = samples[idx(x + 1, y, z + 1)];
                    final float x0y1z0 = samples[idx(x, y + 1, z)];
                    final float x0y1z1 = samples[idx(x, y + 1, z + 1)];
                    final float x1y1z0 = samples[idx(x + 1, y + 1, z)];
                    final float x1y1z1 = samples[idx(x + 1, y + 1, z + 1)];

                    float noiseStepY00 = (x0y1z0 - x0y0z0) * -oneHalf;
                    float noiseStepY01 = (x0y1z1 - x0y0z1) * -oneHalf;
                    float noiseStepY10 = (x1y1z0 - x1y0z0) * -oneHalf;
                    float noiseStepY11 = (x1y1z1 - x1y0z1) * -oneHalf;

                    float noiseStartX0 = x0y0z0;
                    float noiseStartX1 = x0y0z1;
                    float noiseEndX0 = x1y0z0;
                    float noiseEndX1 = x1y0z1;

                    // 每个 y 采样间隔对应 2 个方块（上游的 suby 1..0）
                    for (int suby = 1; suby >= 0; suby--) {
                        final int localY = suby + y * 2;
                        float noiseStartZ = noiseStartX0;
                        float noiseEndZ = noiseStartX1;

                        final float noiseStepX0 = (noiseEndX0 - noiseStartX0) * oneQuarter;
                        final float noiseStepX1 = (noiseEndX1 - noiseStartX1) * oneQuarter;

                        for (int subx = 0; subx < 4; subx++) {
                            final int localX = subx + x * 4;

                            final float noiseStepZ = (noiseEndZ - noiseStartZ) * oneQuarter;
                            float noiseVal = noiseStartZ;

                            for (int subz = 0; subz < 4; subz++) {
                                final int localZ = subz + z * 4;

                                if (depth == 0) {
                                    // 还没碰到地表：只检查 4×4 子块的角点，其余方块直接跳过
                                    if (subx == 0 && subz == 0) {
                                        final IBlockState currentBlock = primer.getBlockState(localX, localY, localZ);
                                        if (canReplaceBlock(currentBlock) || isBiomeBlock(currentBlock, columnTop, columnFiller)) {
                                            depth++;
                                        }
                                    } else {
                                        continue;
                                    }
                                } else if (subx == 0 && subz == 0) {
                                    // 已经进地表，角点只负责推进深度计数
                                    depth++;
                                }

                                float adjustedNoiseCutoff = noiseCutoff;
                                if (depth < easeInDepth) {
                                    // 越靠近地表阈值越严（surfaceCutoff），到 easeInDepth 之后恢复正常
                                    adjustedNoiseCutoff = (float) clampLerp(noiseCutoff, surfaceCutoff,
                                            (easeInDepth - (float) depth) / easeInDepth);
                                }
                                // 越靠近底部阈值越严：否则会被噪声压成一片平地板
                                if (localY < (minCaveHeight + 5)) {
                                    adjustedNoiseCutoff += ((minCaveHeight + 5) - localY) * 0.05;
                                }

                                if (noiseVal > adjustedNoiseCutoff) {
                                    final IBlockState aboveBlock = primer.getBlockState(localX, localY + 1, localZ);
                                    if (!isFluidBlock(aboveBlock) || localY <= lavaDepth) {
                                        // 地表附近 / 近海平面时要额外躲开液体，避免把水抽干
                                        if ((depth < easeInDepth || localY > (seaLevel - 8)) && localY > lavaDepth) {
                                            // 注意：上游这里用裸 continue，连循环末尾的 noiseVal += noiseStepZ 一起跳过，
                                            // 于是"贴着液体"的方块会让本列的 Z 插值错位一格。行为照抄（见预研文档 §7.5）。
                                            if (localX < 15 && isFluidBlock(primer.getBlockState(localX + 1, localY, localZ))) {
                                                continue;
                                            }
                                            if (localX > 0 && isFluidBlock(primer.getBlockState(localX - 1, localY, localZ))) {
                                                continue;
                                            }
                                            if (localZ < 15 && isFluidBlock(primer.getBlockState(localX, localY, localZ + 1))) {
                                                continue;
                                            }
                                            if (localZ > 0 && isFluidBlock(primer.getBlockState(localX, localY, localZ - 1))) {
                                                continue;
                                            }
                                        }
                                        final IBlockState currentBlock = primer.getBlockState(localX, localY, localZ);
                                        final boolean foundTopBlock = isTopBlock(currentBlock, columnTop, exceptionBiome);
                                        digBlock(primer, localX, localY, localZ, foundTopBlock,
                                                currentBlock, aboveBlock, columnTop, columnFiller);
                                    }
                                }

                                noiseVal += noiseStepZ;
                            }

                            noiseStartZ += noiseStepX0;
                            noiseEndZ += noiseStepX1;
                        }

                        noiseStartX0 += noiseStepY00;
                        noiseStartX1 += noiseStepY01;
                        noiseEndX0 += noiseStepY10;
                        noiseEndX1 += noiseStepY11;
                    }
                }
            }
        }
    }

    // ==================== 采样（与上游逐行一致，只改数据布局） ====================

    /**
     * 5×5 个采样列，每列 129 个 Y 采样点。
     *
     * <p>上游把三条位移噪声写在 y 循环里（`WorleyCaveGenerator:342-344`），但它们只依赖
     * `(realX, realZ)` —— 每列重复算 129 次。这里提到列循环外：**算术不变，结果逐位相同**
     * （预研文档 §7.3 已逐块比对过 24 个区块）。
     */
    private void sampleNoise(int chunkX, int chunkZ, int maxSurfaceHeight, float[] out) {
        final int originalMaxHeight = 128;
        final int maxCaveHeight = this.params.maxCaveHeight;
        final int minCaveHeight = this.params.minCaveHeight;
        final float noiseCutoff = this.params.noiseCutoff;
        final float warpAmplifier = this.params.warpAmplifier;
        final float xzCompression = this.params.xzCompression;
        final float yCompression = this.params.yCompression;

        for (int x = 0; x < 5; x++) {
            final int realX = x * 4 + chunkX * 16;
            for (int z = 0; z < 5; z++) {
                final int realZ = z * 4 + chunkZ * 16;

                final float disX = warpNoise.noise(realX, realZ);
                final float disY = warpNoise.noise(realX, realZ + 67.0f);
                final float disZ = warpNoise.noise(realX, realZ + 149.0f);

                int columnHasCaveFlag = 0;

                // 自上而下：这样 y+1 / y+2 的"抬洞顶"补偿才能作用到已经算过的格子上
                for (int y = 128; y >= 0; y--) {
                    final float realY = y * 2;

                    if (realY > maxSurfaceHeight || realY > maxCaveHeight || realY < minCaveHeight) {
                        // 超出有效范围：给一个低于噪声下限（-1.0）的值，插值时自然不产生洞穴
                        out[idx(x, y, z)] = -1.1F;
                        continue;
                    }

                    // 越往下位移越强（上游原注释：让洞穴系统随着下降更"混乱"）
                    final float dispAmp = (float) (warpAmplifier
                            * ((originalMaxHeight - y) / (originalMaxHeight * 0.85)));

                    final float noise = worleyNoise.singleCellular3Edge(
                            realX * xzCompression + disX * dispAmp,
                            realY * yCompression + disY * dispAmp,
                            realZ * xzCompression + disZ * dispAmp);
                    out[idx(x, y, z)] = noise;

                    if (noise > noiseCutoff) {
                        columnHasCaveFlag = 1;

                        // 邻域补偿：把洞"传染"给相邻采样点，防止三线性插值时洞穴断成碎片
                        if (x > 0) {
                            out[idx(x - 1, y, z)] = (noise * 0.2f) + (out[idx(x - 1, y, z)] * 0.8f);
                        }
                        if (z > 0) {
                            out[idx(x, y, z - 1)] = (noise * 0.2f) + (out[idx(x, y, z - 1)] * 0.8f);
                        }
                        // 上方两格按比例抬升，给玩家留出"头能站进去"的高度
                        if (y < 128) {
                            final float noiseAbove = out[idx(x, y + 1, z)];
                            if (noise > noiseAbove) {
                                out[idx(x, y + 1, z)] = (noise * 0.8F) + (noiseAbove * 0.2F);
                            }
                            if (y < 127) {
                                final float noiseTwoAbove = out[idx(x, y + 2, z)];
                                if (noise > noiseTwoAbove) {
                                    out[idx(x, y + 2, z)] = (noise * 0.35F) + (noiseTwoAbove * 0.65F);
                                }
                            }
                        }
                    }
                }

                out[idx(x, HAS_CAVES_FLAG, z)] = columnHasCaveFlag;
            }
        }
    }

    private static int idx(int x, int y, int z) {
        return ((x * SAMPLES_Y) + y) * 5 + z;
    }

    // ==================== 地表高度（二分，等价于上游的递归版） ====================

    /** 取 6 个测试点的地表最高点（六边形分布），超过洞穴上限就提前返回。 */
    private int getMaxSurfaceHeight(ChunkPrimer primer) {
        final int[] testX = {2, 3, 7, 9, 12, 13};
        final int[] testZ = {6, 11, 2, 13, 4, 9};
        int max = 0;
        for (int n = 0; n < testX.length; n++) {
            final int testmax = getSurfaceHeight(primer, testX[n], testZ[n]);
            if (testmax > max) {
                max = testmax;
                if (max > this.params.maxCaveHeight) {
                    return max;
                }
            }
        }
        return max;
    }

    /**
     * 二分找"最高的一块可替换方块之上"的位置（0..255）。
     *
     * <p>上游是递归写法（`recursiveBinarySurfaceSearch`），这里改成等价的循环：
     * 同样的下界二分，同样最多 8 步，结果完全相同。
     */
    private int getSurfaceHeight(ChunkPrimer primer, int localX, int localZ) {
        int top = 255;
        int bottom = 0;
        while (top > bottom) {
            final int mid = (bottom + top) / 2;
            if (canReplaceBlock(primer.getBlockState(localX, mid, localZ))) {
                bottom = mid + 1;
            } else {
                top = mid;
            }
        }
        return top;
    }

    // ==================== 方块判定 ====================

    /**
     * 能否被洞穴挖掉。
     *
     * <p>`allowReplaceMoreBlocks`（上游默认 true）把判定放宽到 `Material.ROCK`，
     * 这样整合包里那些非原版岩石（GT 的花岗岩、地核岩等）也能被挖开；
     * 其余情况与原版 `MapGenCaves#canReplaceBlock` 的清单一致。
     */
    private boolean canReplaceBlock(IBlockState state) {
        if (this.params.allowReplaceMoreBlocks && state.getMaterial() == Material.ROCK) {
            return true;
        }
        final Block block = state.getBlock();
        return block == Blocks.STONE
                || block == Blocks.DIRT
                || block == Blocks.GRASS
                || block == Blocks.HARDENED_CLAY
                || block == Blocks.STAINED_HARDENED_CLAY
                || block == Blocks.SANDSTONE
                || block == Blocks.RED_SANDSTONE
                || block == Blocks.MYCELIUM;
    }

    /** 该列的地表 top/filler（RTG 地表优先，拿不到才用 MC 群系）。 */
    private boolean isBiomeBlock(IBlockState state, IBlockState top, IBlockState filler) {
        return (top != null && state == top) || (filler != null && state == filler);
    }

    /**
     * 是否挖到了"地表顶层"。
     *
     * <p>上游的 `isExceptionBiome` 判定（沙滩/沙漠一律按草方块算）是原版为了复刻旧版本
     * 地形 bug 而保留的特例，这里照抄，避免同一种子在 RTG 与原版的洞口感不一致。
     */
    private boolean isTopBlock(IBlockState state, IBlockState top, boolean exceptionBiome) {
        return exceptionBiome ? state.getBlock() == Blocks.GRASS : (top != null && state == top);
    }

    private boolean isFluidBlock(IBlockState state) {
        final Block block = state.getBlock();
        return block instanceof BlockLiquid || block instanceof IFluidBlock;
    }

    private static boolean isExceptionBiome(Biome biome) {
        return biome == Biomes.BEACH || biome == Biomes.DESERT;
    }

    // ==================== 挖洞 ====================

    /**
     * 挖掉一格：低于岩浆位面填岩浆，否则填空气；若挖开的是地表，则把下面一格补成该列顶层，
     * 并把"悬空沙"换成砂岩（防止洞穴破土后沙柱整根落下）。
     */
    private void digBlock(ChunkPrimer primer, int x, int y, int z, boolean foundTop,
                          IBlockState state, IBlockState up, IBlockState top, IBlockState filler) {

        if (canReplaceBlock(state)
                || (top != null && state.getBlock() == top.getBlock())
                || (filler != null && state.getBlock() == filler.getBlock())) {

            if (y <= this.params.lavaDepth) {
                primer.setBlockState(x, y, z, LAVA);
            } else {
                primer.setBlockState(x, y, z, AIR);

                if (foundTop && top != null && filler != null
                        && primer.getBlockState(x, y - 1, z).getBlock() == filler.getBlock()) {
                    primer.setBlockState(x, y - 1, z, top);
                }

                // 洞穴把沙层挖穿后，上方悬空的沙会整根落下：换成砂岩托住
                if (up.getBlock() == Blocks.SAND) {
                    primer.setBlockState(x, y + 1, z, isRedSand(up) ? RED_SANDSTONE : SANDSTONE);
                }
            }
        }
    }

    private static boolean isRedSand(IBlockState state) {
        return state.getBlock() == Blocks.SAND
                && state.getValue(BlockSand.VARIANT) == BlockSand.EnumType.RED_SAND;
    }

    // ==================== 逐列表格 ====================

    /**
     * 把本区块 16 个 4×4 子列的地表材质与"例外群系"标记填进复用数组。
     *
     * <p>上游是在**每一格**上现查群系（`world.provider.getBiomeProvider().getBiome(pos, PLAINS)`，
     * 实测 1932 次/区块）。群系是列属性，一个 4×4 子列查一次即可 —— 而且这直接决定了
     * 洞穴破土时补的是哪种方块。
     *
     * <p>`exceptionBiome`（沙滩/沙漠按草方块算）只对**回落路径**生效：RTG 地表自带 top/filler 时
     * 不需要原版那个特例，按真实地表判定反而更准（沙漠就补沙，不会补出草）。
     */
    private void resolveColumns(World worldIn, int chunkX, int chunkZ, IRealisticBiome[] columnBiomes, Scratch s) {
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                final int column = x * 4 + z;
                final int localX = x * 4;
                final int localZ = z * 4;
                final int worldX = chunkX * 16 + localX;
                final int worldZ = chunkZ * 16 + localZ;

                IRealisticBiome realistic = null;
                if (columnBiomes != null) {
                    realistic = columnBiomes[localX * 16 + localZ];
                }

                Biome biome = null;
                if (realistic == null) {
                    // 只有"没有 RTG 地表信息"的回落路径才需要查群系；复用可变坐标避免每列一个对象
                    s.biomePos.setPos(worldX, 0, worldZ);
                    biome = worldIn.getBiomeProvider().getBiome(s.biomePos, Biomes.PLAINS);
                }

                IBlockState top = null;
                IBlockState filler = null;
                if (realistic != null && realistic.surface() != null) {
                    top = realistic.surface().getTopBlock();
                    filler = realistic.surface().getFillerBlock();
                }
                if (top == null) {
                    top = biome != null ? biome.topBlock : Blocks.GRASS.getDefaultState();
                }
                if (filler == null) {
                    filler = biome != null ? biome.fillerBlock : Blocks.DIRT.getDefaultState();
                }

                s.top[column] = top;
                s.filler[column] = filler;
                s.exceptionBiome[column] = biome != null && isExceptionBiome(biome);
            }
        }
    }

    /** 按线程复用的临时数组：采样值 + 16 个子列的地表材质。 */
    private static final class Scratch {

        final float[] samples = new float[SAMPLES];
        final IBlockState[] top = new IBlockState[16];
        final IBlockState[] filler = new IBlockState[16];
        final boolean[] exceptionBiome = new boolean[16];
        final BlockPos.MutableBlockPos biomePos = new BlockPos.MutableBlockPos();
    }

    /** 对应 1.12.2 的 `MathHelper.clampedLerp`（把 t 夹到 [0,1] 后线性插值）。 */
    private static double clampLerp(double start, double end, double part) {
        if (part < 0.0D) {
            return start;
        }
        return part > 1.0D ? end : start + (end - start) * part;
    }
}
