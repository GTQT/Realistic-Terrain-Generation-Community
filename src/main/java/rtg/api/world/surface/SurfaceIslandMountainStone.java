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
 * RWG {@code rwg/surface/SurfaceIslandMountainStone.java} 的逐行移植（RWG 里 2 处）。
 *
 * <p>与 {@link SurfaceMountainStone} 的差别：海滩判据是一个**高度** {@code beach}
 * （岛屿的海滩高度可配），而不是固定 63。
 *
 * <p>适配同 {@link SurfaceMountainStone}：崖壁判定 → {@code TerrainBase.calcCliff}，
 * 元数据 9 → {@code BlockUtil.getStateClay(EnumDyeColor.CYAN)}。
 */
public class SurfaceIslandMountainStone extends SurfaceBase {

    private final int beach;
    private final IBlockState beachBlock;
    private final float min;

    private final float sCliff;
    private final float sHeight;
    private final float sStrength;
    private final float cCliff;

    public SurfaceIslandMountainStone(final BiomeConfig config,
                                      final IBlockState top, final IBlockState fill,
                                      final int beachHeight, final IBlockState genBeachBlock, final float minCliff) {
        this(config, top, fill, beachHeight, genBeachBlock, minCliff, 1.5f, 60f, 65f, 1.5f);
    }

    public SurfaceIslandMountainStone(final BiomeConfig config,
                                      final IBlockState top, final IBlockState fill,
                                      final int beachHeight, final IBlockState genBeachBlock, final float minCliff,
                                      final float stoneCliff, final float stoneHeight, final float stoneStrength,
                                      final float clayCliff) {

        super(config, top, fill);

        this.beach = beachHeight;
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
                    if (k < beach) {
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
                        primer.setBlockState(x, k, z, rand.nextInt(3) == 0 ? hcCobble() : hcStone());
                    } else if (cliff == 2) {
                        primer.setBlockState(x, k, z, clay);
                    } else if (k < beach) {
                        primer.setBlockState(x, k, z, beachBlock);
                        gravel = true;
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
