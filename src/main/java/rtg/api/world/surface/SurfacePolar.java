package rtg.api.world.surface;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkPrimer;

import rtg.api.config.BiomeConfig;
import rtg.api.util.noise.SimplexNoise;
import rtg.api.world.RTGWorld;
import rtg.api.world.terrain.TerrainBase;


/**
 * RWG {@code rwg/surface/SurfacePolar.java} 的逐行移植（RWG 里 2 处）。
 *
 * <p>与其它地表最大的不同是**湖面结冰**那一条：遍历到第一个 {@code water} 时把它换成
 * {@code ice}（`water` 标志保证每列只换一次）。
 *
 * <p>河岸分支（`riverPaint`）沿用了 RWG 的两级噪声判据：
 * {@code river > 0.05f && river + noise2(i/10, j/10) * 0.1f > 0.86f} 决定是否画河岸，
 * 再以 {@code noise2(i/12, j/12) > 0.25f} 决定这一格是泥土还是石/圆石。
 *
 * <p>雪层用 {@code TerrainBase.calcSnowHeight}（RWG {@code SnowheightCalculator.calc} 的移植）。
 * 注意 RWG 的判据是 {@code k > 61 && k < 254}，此处原样保留。
 */
public class SurfacePolar extends SurfaceBase {

    public SurfacePolar(final BiomeConfig config, final IBlockState top, final IBlockState fill) {
        super(config, top, fill);
    }

    @Override
    public void paintTerrain(final ChunkPrimer primer, final int i, final int j, final int x, final int z,
                             int depth, final RTGWorld rtgWorld, final float[] noise, final float river,
                             final Biome[] base) {

        final Random rand = rtgWorld.rand();
        final SimplexNoise simplex = rtgWorld.simplexInstance(0);

        final IBlockState snow = Blocks.SNOW.getDefaultState();
        final IBlockState ice = Blocks.ICE.getDefaultState();
        final IBlockState dirt = Blocks.DIRT.getDefaultState();

        boolean water = false;
        boolean riverPaint = false;
        boolean grass = false;

        // RWG: river > 0.05f && river + (perlin.noise2(i / 10f, j / 10f) * 0.1f) > 0.86f
        if (river > 0.05f && river + (simplex.noise2f(i / 10f, j / 10f) * 0.1f) > 0.86f) {
            riverPaint = true;

            if (simplex.noise2f(i / 12f, j / 12f) > 0.25f) {
                grass = true;
            }
        }

        for (int k = 255; k > -1; k--) {
            final Block b = primer.getBlockState(x, k, z).getBlock();

            if (b == Blocks.AIR) {
                depth = -1;
            } else if (b == Blocks.STONE) {
                depth++;

                if (riverPaint) {
                    if (grass && depth < 4) {
                        primer.setBlockState(x, k, z, dirt);
                    } else if (depth == 0) {
                        primer.setBlockState(x, k, z, rand.nextInt(2) == 0
                                ? Blocks.STONE.getDefaultState() : Blocks.COBBLESTONE.getDefaultState());
                    }
                } else if (depth > -1 && depth < 9) {
                    primer.setBlockState(x, k, z, snow);
                    if (depth == 0 && k > 61 && k < 254) {
                        TerrainBase.calcSnowHeight(x, k, z, primer, noise);
                    }
                }
            } else if (!water && b == Blocks.WATER) {
                primer.setBlockState(x, k, z, ice);
                water = true;
            }
        }
    }
}
