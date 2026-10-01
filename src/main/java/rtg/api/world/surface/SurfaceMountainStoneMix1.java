package rtg.api.world.surface;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkPrimer;

import rtg.api.config.BiomeConfig;
import rtg.api.util.BlockUtil;
import rtg.api.util.noise.SimplexNoise;
import rtg.api.world.RTGWorld;
import rtg.api.world.terrain.TerrainBase;


/**
 * 山地链的岩石混合地表 —— RWG {@code rwg/surface/SurfaceMountainStoneMix1.java} 的逐行移植。
 *
 * <p>RWG 只在 {@code RealisticBiomeMountainChain.createSurface(...)} 里用它（一份实现）：
 * <pre>
 *   new SurfaceMountainStoneMix1(top, fill, false, null, 0f, 1.5f, 60f, 65f, 1.5f, Blocks.stone, 0.20f)
 * </pre>
 *
 * <h2>两处有意的管线适配（已在此标注）</h2>
 * <ol>
 *   <li><b>悬崖判定</b>：RWG 用 {@code CliffCalculator.calc(x, y, noise)}（只看四邻高差），
 *       rtgc 用 {@code TerrainBase.calcCliff(x, z, noise, river)}（在四邻高差之上增加了
 *       "水线附近且靠近河心时不判悬崖"的处理，见其 javadoc）。
 *       本文件跟随 rtgc 的既有约定，与其余约 120 个 surface 保持一致。</li>
 *   <li><b>坐标含义</b>：{@code i}/{@code j} 是**世界坐标**（用于噪声），
 *       {@code x}/{@code z} 是**区块内局部坐标**（用于放置方块）—— 与
 *       {@code ChunkGeneratorRTG#rReplace} 的调用约定一致。</li>
 * </ol>
 *
 * <p>元数据 {@code 9} 在 1.7.10 就是青色染色粘土，故 {@code cliff == 2} 的支路用
 * {@code BlockUtil.getStateClay(EnumDyeColor.CYAN)} ——与 RWG 的原意一致，
 * 不做"看起来更合理"的替换。
 *
 * @since 1.0.12
 */
public class SurfaceMountainStoneMix1 extends SurfaceBase {

    private final boolean beach;
    private final IBlockState beachBlock;
    private final float min;
    private final float sCliff;
    private final float sHeight;
    private final float sStrength;
    private final float cCliff;
    private final IBlockState mix;
    private final float mixHeight;

    public SurfaceMountainStoneMix1(final BiomeConfig config,
                                    final IBlockState top, final IBlockState fill,
                                    final boolean genBeach, final IBlockState genBeachBlock, final float minCliff,
                                    final float stoneCliff, final float stoneHeight, final float stoneStrength,
                                    final float clayCliff, final IBlockState mixBlock, final float mixSize) {

        super(config, top, fill);

        this.beach = genBeach;
        this.beachBlock = genBeachBlock;
        this.min = minCliff;

        this.sCliff = stoneCliff;
        this.sHeight = stoneHeight;
        this.sStrength = stoneStrength;
        this.cCliff = clayCliff;

        this.mix = mixBlock;
        this.mixHeight = mixSize;
    }

    /** 便利构造：用 {@code Blocks.STONE} 作混合块。 */
    public SurfaceMountainStoneMix1(final BiomeConfig config, final Block top, final Block fill) {
        this(config, top.getDefaultState(), fill.getDefaultState(),
                false, null, 0f, 1.5f, 60f, 65f, 1.5f, Blocks.STONE.getDefaultState(), 0.20f);
    }

    @Override
    public void paintTerrain(final ChunkPrimer primer, final int i, final int j, final int x, final int z,
                             int depth, final RTGWorld rtgWorld, final float[] noise, final float river,
                             final Biome[] base) {

        final SimplexNoise simplex = rtgWorld.simplexInstance(0);
        final java.util.Random rand = rtgWorld.rand();
        final IBlockState clay = BlockUtil.getStateClay(EnumDyeColor.CYAN);

        final float c = TerrainBase.calcCliff(x, z, noise, river);
        int cliff = 0;
        boolean gravel = false;
        boolean m = false;

        for (int k = 255; k > -1; k--) {
            final IBlockState b = primer.getBlockState(x, k, z);

            if (b.getBlock() == Blocks.AIR) {
                depth = -1;
            } else if (b.getBlock() == Blocks.STONE) {
                depth++;

                if (depth == 0) {
                    if (k < 63 && beach) {
                        gravel = true;
                    }

                    final float p = simplex.noise3f(i / 8f, j / 8f, k / 8f) * 0.5f;
                    if (c > min && c > sCliff - ((k - sHeight) / sStrength) + p) {
                        cliff = 1;
                    }
                    if (c > cCliff) {
                        cliff = 2;
                    }

                    if (cliff == 1) {
                        // RWG: rand.nextInt(3) == 0 ? cobblestone : stone
                        primer.setBlockState(x, k, z, rand.nextInt(3) == 0 ? hcCobble() : hcStone());
                    } else if (cliff == 2) {
                        primer.setBlockState(x, k, z, clay);
                    } else if (k < 63) {
                        if (beach) {
                            primer.setBlockState(x, k, z, beachBlock);
                            gravel = true;
                        } else if (k < 62) {
                            primer.setBlockState(x, k, z, fillerBlock);
                        } else {
                            primer.setBlockState(x, k, z, topBlock);
                        }
                    } else if (simplex.noise2f(i / 12f, j / 12f) > mixHeight) {
                        primer.setBlockState(x, k, z, mix);
                        m = true;
                    } else {
                        primer.setBlockState(x, k, z, topBlock);
                    }
                } else if (depth < 6) {
                    if (cliff == 1) {
                        primer.setBlockState(x, k, z, hcStone());
                    } else if (cliff == 2) {
                        primer.setBlockState(x, k, z, clay);
                    } else if (gravel) {
                        primer.setBlockState(x, k, z, beachBlock);
                    } else if (m) {
                        primer.setBlockState(x, k, z, mix);
                    } else {
                        primer.setBlockState(x, k, z, fillerBlock);
                    }
                }
            }
        }
    }
}
