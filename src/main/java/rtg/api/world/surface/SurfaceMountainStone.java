package rtg.api.world.surface;

import java.util.Random;

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
 * RWG {@code rwg/surface/SurfaceMountainStone.java} 的逐行移植（RWG 里第二常用，28 处）。
 *
 * <p>特点：岩石悬崖的判定是**两条独立规则**：
 * <ol>
 *   <li>{@code c > min && c > sCliff - ((k - sHeight) / sStrength) + p} ⇒ 石头/圆石（cliff = 1）；</li>
 *   <li>{@code c > cCliff} ⇒ 染色粘土（cliff = 2）。</li>
 * </ol>
 * 其中 {@code p = perlin.noise3(i/8, j/8, k/8) * 0.5f} —— **三维噪声**，
 * 使崖壁在不同高度上左右摆动，不是一条垂直直线。这是 RWG 让山体边缘"不像切出来"的关键。
 *
 * <p>元数据 {@code 9} 在 1.7.10 就是青色染色粘土，故用
 * {@code BlockUtil.getStateClay(EnumDyeColor.CYAN)} —— 与 RWG 原意一致。
 *
 * <p>管线适配：悬崖判定改用 {@code TerrainBase.calcCliff(x, z, noise, river)}，同 {@link SurfaceGrassland}。
 */
public class SurfaceMountainStone extends SurfaceBase {

    private final boolean beach;
    private final IBlockState beachBlock;
    private final float min;

    private final float sCliff;
    private final float sHeight;
    private final float sStrength;
    private final float cCliff;

    public SurfaceMountainStone(final BiomeConfig config,
                                final IBlockState top, final IBlockState fill,
                                final boolean genBeach, final IBlockState genBeachBlock, final float minCliff) {
        this(config, top, fill, genBeach, genBeachBlock, minCliff, 1.5f, 60f, 65f, 1.5f);
    }

    public SurfaceMountainStone(final BiomeConfig config,
                                final IBlockState top, final IBlockState fill,
                                final boolean genBeach, final IBlockState genBeachBlock, final float minCliff,
                                final float stoneCliff, final float stoneHeight, final float stoneStrength,
                                final float clayCliff) {

        super(config, top, fill);

        this.beach = genBeach;
        this.beachBlock = genBeachBlock;
        this.min = minCliff;

        this.sCliff = stoneCliff;
        this.sHeight = stoneHeight;
        this.sStrength = stoneStrength;
        this.cCliff = clayCliff;
    }

    @Override
    public void paintTerrain(final ChunkPrimer primer, final int i, final int j, final int x, final int z,
                             int depth, final RTGWorld rtgWorld, final float[] noise, final float river,
                             final Biome[] base) {

        final Random rand = rtgWorld.rand();
        final SimplexNoise simplex = rtgWorld.simplexInstance(0);
        final IBlockState clay = BlockUtil.getStateClay(EnumDyeColor.CYAN);

        final float c = TerrainBase.calcCliff(x, z, noise, river);
        int cliff = 0;
        boolean gravel = false;

        for (int k = 255; k > -1; k--) {
            final Block b = primer.getBlockState(x, k, z).getBlock();

            if (b == Blocks.AIR) {
                depth = -1;
            } else if (b == Blocks.STONE) {
                depth++;

                if (depth == 0) {
                    if (k < 63 && beach) {
                        gravel = true;
                    }

                    // RWG: perlin.noise3(i / 8f, j / 8f, k / 8f) * 0.5f
                    final float p = simplex.noise3f(i / 8f, j / 8f, k / 8f) * 0.5f;
                    if (c > min && c > sCliff - ((k - sHeight) / sStrength) + p) {
                        cliff = 1;
                    }
                    if (c > cCliff) {
                        cliff = 2;
                    }

                    if (cliff == 1) {
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
                    } else {
                        primer.setBlockState(x, k, z, fillerBlock);
                    }
                }
            }
        }
    }
}
