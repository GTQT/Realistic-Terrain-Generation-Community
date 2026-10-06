package rtg.api.world.surface;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkPrimer;
import rtg.api.config.BiomeConfig;
import rtg.api.util.noise.SimplexNoise;
import rtg.api.world.RTGWorld;
import rtg.api.world.terrain.TerrainBase;

import java.util.Random;


/**
 * RWG {@code rwg/surface/SurfaceGrasslandMixBig.java} 的逐行移植。
 *
 * <p>与 {@link SurfaceGrasslandMix1} 的差别：混合判定用**两个尺度**的噪声之和
 * （{@code noise2(i/width, j/width) + noise2(i/smallW, j/smallW) * smallS > height}），
 * 且混合不仅改顶层、还顺带把下面 4 格换成 {@code mixFill}。
 *
 * <p>管线适配同 {@link SurfaceGrassland}。
 */
public class SurfaceGrasslandMixBig extends SurfaceBase {

    private final IBlockState mixBlockTop;
    private final IBlockState mixBlockFill;
    private final IBlockState cliffBlock1;
    private final IBlockState cliffBlock2;
    private final float width;
    private final float height;
    private final float smallW;
    private final float smallS;

    public SurfaceGrasslandMixBig(final BiomeConfig config,
                                  final IBlockState top, final IBlockState filler,
                                  final IBlockState mixTop, final IBlockState mixFill,
                                  final IBlockState cliff1, final IBlockState cliff2,
                                  final float mixWidth, final float mixHeight,
                                  final float smallWidth, final float smallStrength) {

        super(config, top, filler);

        this.mixBlockTop = mixTop;
        this.mixBlockFill = mixFill;
        this.cliffBlock1 = cliff1;
        this.cliffBlock2 = cliff2;

        this.width = mixWidth;
        this.height = mixHeight;
        this.smallW = smallWidth;
        this.smallS = smallStrength;
    }

    @Override
    public void paintTerrain(final ChunkPrimer primer, final int i, final int j, final int x, final int z,
                             int depth, final RTGWorld rtgWorld, final float[] noise, final float river,
                             final Biome[] base) {

        final Random rand = rtgWorld.rand();
        final SimplexNoise simplex = rtgWorld.simplexInstance(0);
        final float c = TerrainBase.calcCliff(x, z, noise, river);
        final boolean cliff = c > 1.4f;
        boolean mix = false;

        for (int k = 255; k > -1; k--) {
            final Block b = primer.getBlockState(x, k, z).getBlock();

            if (b == Blocks.AIR) {
                depth = -1;
            } else if (b == Blocks.STONE) {
                depth++;

                if (cliff) {
                    if (depth > -1 && depth < 2) {
                        primer.setBlockState(x, k, z, rand.nextInt(3) == 0 ? cliffBlock2 : cliffBlock1);
                    } else if (depth < 10) {
                        primer.setBlockState(x, k, z, cliffBlock1);
                    }
                } else {
                    if (depth == 0 && k > 61) {
                        // RWG: perlin.noise2(i / width, j / width) + perlin.noise2(i / smallW, j / smallW) * smallS > height
                        if (simplex.noise2f(i / width, j / width)
                                + simplex.noise2f(i / smallW, j / smallW) * smallS > height) {
                            primer.setBlockState(x, k, z, mixBlockTop);
                            mix = true;
                        } else {
                            primer.setBlockState(x, k, z, topBlock);
                        }
                    } else if (depth < 4) {
                        primer.setBlockState(x, k, z, mix ? mixBlockFill : fillerBlock);
                    }
                }
            }
        }
    }
}
