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
 * RWG {@code rwg/surface/SurfaceTundra.java} 的逐行移植（RWG 里 3 处）。
 *
 * <p>苔原地表：三条崖壁规则（石/圆石、染色粘土、**雪**）之外，普通分支还有一层
 * "斑块"判定 —— {@code noise2(i/50, j/50) + p * 0.6f > 0.24f} 时顶层是**泥土**，
 * 否则低于水线是砂砾、高于水线是**草**（不是 topBlock）。
 *
 * <h2>记录在案的适配</h2>
 * <ul>
 *   <li>崖壁判定 → {@code TerrainBase.calcCliff(x, z, noise, river)}。</li>
 *   <li>元数据 9（青色染色粘土）→ {@code BlockUtil.getStateClay(EnumDyeColor.CYAN)}。</li>
 *   <li>RWG 给泥土写 {@code metadata = 2}。1.12.2 的 {@code dirt} 只有 meta 0，
 *       该写入无效果，故省略。</li>
 * </ul>
 */
public class SurfaceTundra extends SurfaceBase {

    public SurfaceTundra(final BiomeConfig config, final IBlockState top, final IBlockState fill) {
        super(config, top, fill);
    }

    @Override
    public void paintTerrain(final ChunkPrimer primer, final int i, final int j, final int x, final int z,
                             int depth, final RTGWorld rtgWorld, final float[] noise, final float river,
                             final Biome[] base) {

        final Random rand = rtgWorld.rand();
        final SimplexNoise simplex = rtgWorld.simplexInstance(0);

        final IBlockState clay = BlockUtil.getStateClay(EnumDyeColor.CYAN);
        final IBlockState snow = Blocks.SNOW.getDefaultState();
        final IBlockState grass = Blocks.GRASS.getDefaultState();
        final IBlockState dirt = Blocks.DIRT.getDefaultState();
        final IBlockState gravelBlock = Blocks.GRAVEL.getDefaultState();

        final float p = simplex.noise2f(i / 8f, j / 8f) * 0.5f;
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
                    if (k < 63) {
                        gravel = true;
                    }

                    if (c > 0.45f && c > 1.5f - ((k - 60f) / 65f) + p) {
                        cliff = 1;
                    }
                    if (c > 1.5f) {
                        cliff = 2;
                    }
                    if (k > 110 + (p * 4) && c < 0.3f + ((k - 100f) / 50f) + p) {
                        cliff = 3;
                    }

                    if (cliff == 1) {
                        primer.setBlockState(x, k, z, rand.nextInt(3) == 0 ? hcCobble() : hcStone());
                    } else if (cliff == 2) {
                        primer.setBlockState(x, k, z, clay);
                    } else if (cliff == 3) {
                        primer.setBlockState(x, k, z, snow);
                    } else if (simplex.noise2f(i / 50f, j / 50f) + p * 0.6f > 0.24f) {
                        primer.setBlockState(x, k, z, dirt);
                    } else if (k < 63) {
                        primer.setBlockState(x, k, z, gravelBlock);
                        gravel = true;
                    } else {
                        primer.setBlockState(x, k, z, grass);
                    }
                } else if (depth < 6) {
                    if (cliff == 1) {
                        primer.setBlockState(x, k, z, hcStone());
                    } else if (cliff == 2) {
                        primer.setBlockState(x, k, z, clay);
                    } else if (cliff == 3) {
                        primer.setBlockState(x, k, z, snow);
                    } else if (gravel) {
                        primer.setBlockState(x, k, z, gravelBlock);
                    } else {
                        primer.setBlockState(x, k, z, dirt);
                    }
                }
            }
        }
    }
}
