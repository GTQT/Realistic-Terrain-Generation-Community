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
 * 与 {@link SurfaceGrassland} 的区别只有顶层块选择：非悬崖时用
 * {@code perlin.noise2(i / width, j / width) > height} 在 {@code mixBlock} 和
 * {@code topBlock} 之间切换，让草地与砂/石自然交错。
 *
 * <p>悬崖判定同 {@link SurfaceGrassland}，走 {@code TerrainBase.calcCliff}。
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

        // RTG+ `SurfaceVanillaSavanna` / `SurfaceBOPBrushland` 的写法：mix 块可被群系配置覆盖，
        // 配置为空时回落到构造参数。此前这份配置声明了却没人读（死配置）。
        this.mixBlock = getConfigBlock(config.SURFACE_MIX_BLOCK.get(), mix);
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
