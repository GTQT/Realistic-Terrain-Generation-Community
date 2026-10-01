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
 * RWG {@code rwg/surface/SurfaceGrasslandMix1.java} 的逐行移植。
 *
 * <p>与 {@link SurfaceGrassland} 的差别只有一处：非悬崖时顶层方块由
 * {@code perlin.noise2(i / width, j / width) > height} 决定用 {@code mixBlock} 还是 {@code topBlock}
 * —— 即**用噪声把另一种方块混进来**。RWG 用它做"草地与砂/石的自然交错"，
 * 这正是让地表交界不生硬的那类处理。
 *
 * <p>管线适配同 {@link SurfaceGrassland}（悬崖判定改用 {@code TerrainBase.calcCliff}）。
 */
public class SurfaceGrasslandMix1 extends SurfaceBase {

    private final IBlockState mixBlock;
    private final IBlockState cliffBlock1;
    private final IBlockState cliffBlock2;
    private final float width;
    private final float height;

    public SurfaceGrasslandMix1(final BiomeConfig config,
                                final IBlockState top, final IBlockState filler, final IBlockState mix,
                                final IBlockState cliff1, final IBlockState cliff2,
                                final float mixWidth, final float mixHeight) {

        super(config, top, filler);

        this.mixBlock = mix;
        this.cliffBlock1 = cliff1;
        this.cliffBlock2 = cliff2;

        this.width = mixWidth;
        this.height = mixHeight;
    }

    @Override
    public void paintTerrain(final ChunkPrimer primer, final int i, final int j, final int x, final int z,
                             int depth, final RTGWorld rtgWorld, final float[] noise, final float river,
                             final Biome[] base) {

        final Random rand = rtgWorld.rand();
        final SimplexNoise simplex = rtgWorld.simplexInstance(0);
        final float c = TerrainBase.calcCliff(x, z, noise, river);
        final boolean cliff = c > 1.4f;

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
                        // RWG: perlin.noise2(i / width, j / width) > height
                        if (simplex.noise2f(i / width, j / width) > height) {
                            primer.setBlockState(x, k, z, mixBlock);
                        } else {
                            primer.setBlockState(x, k, z, topBlock);
                        }
                    } else if (depth < 4) {
                        primer.setBlockState(x, k, z, fillerBlock);
                    }
                }
            }
        }
    }
}
