package rtg.api.world.surface;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkPrimer;
import rtg.api.config.BiomeConfig;
import rtg.api.world.RTGWorld;
import rtg.api.world.terrain.TerrainBase;

import java.util.Random;


/**
 * 滨海沙丘的共享地表，对应 RWG 内联在 {@code RealisticBiomeCoastDunes} 中的逻辑。
 *
 * <p>RWG 没有把它抽成 surface 类，rtgc 之前只移植了地形。这里补上地表，让
 * {@code VanillaBeach}、{@code VanillaStoneBeach}、{@code BOPGravelBeach}、
 * {@code BOPOriginBeach} 共用同一份参考实现。
 *
 * <p>方块写死为 grass/sand/sandstone/dirt/cobblestone/stone，不读取群系自己的
 * top/filler；这是 RWG 的原始行为，也让 gravel_beach / origin_beach 的地表与普通
 * 沙滩一致。悬崖阈值为 {@code 1.3}，{@code k > 68} 的高度门槛同样沿用 RWG 字面量。
 *
 * <p>管线适配只有一处：悬崖判定改用 {@code TerrainBase.calcCliff(x, z, noise, river)}
 * （RWG 的 {@code CliffCalculator.calc} 只有 3 个参数）。
 */
public class SurfaceCoastDunes extends SurfaceBase {

    /** RWG 的崖壁阈值：{@code cliff > 1.3f}（沙漠是 2.8f，沿海沙丘更低）。 */
    private static final float CLIFF_THRESHOLD = 1.3f;

    /** RWG 的字面量：该高度以上给草，以下给沙。 */
    private static final int SAND_TOP_Y = 68;

    private static final IBlockState GRASS = Blocks.GRASS.getDefaultState();
    private static final IBlockState SAND = Blocks.SAND.getDefaultState();
    private static final IBlockState SANDSTONE = Blocks.SANDSTONE.getDefaultState();
    private static final IBlockState DIRT = Blocks.DIRT.getDefaultState();
    private static final IBlockState STONE = Blocks.STONE.getDefaultState();
    private static final IBlockState COBBLESTONE = Blocks.COBBLESTONE.getDefaultState();

    /**
     * @param config 群系配置。super 的 top/filler 只是为了满足 {@link SurfaceBase} 的约定，
     *               本类自己不使用它们（与 RWG 一致：块写死）。
     */
    public SurfaceCoastDunes(final BiomeConfig config) {

        super(config, GRASS, DIRT);
    }

    @Override
    public void paintTerrain(final ChunkPrimer primer, final int i, final int j, final int x, final int z,
                             int depth, final RTGWorld rtgWorld, final float[] noise, final float river,
                             final Biome[] base) {

        final Random rand = rtgWorld.rand();
        // 管线适配：RWG 是 `CliffCalculator.calc(x, y, noise)`，rtgc 统一用 4 参（多一个 river）
        final float cliff = TerrainBase.calcCliff(x, z, noise, river);
        boolean sand = false;

        for (int k = 255; k > -1; k--) {
            final Block b = primer.getBlockState(x, k, z).getBlock();

            if (b == Blocks.AIR) {
                depth = -1;
            } else if (b == Blocks.STONE) {
                depth++;

                if (cliff > CLIFF_THRESHOLD) {
                    if (depth > -1 && depth < 2) {
                        // RWG: rand.nextInt(3) == 0 ? cobblestone : stone
                        primer.setBlockState(x, k, z, rand.nextInt(3) == 0 ? COBBLESTONE : STONE);
                    } else if (depth < 10) {
                        primer.setBlockState(x, k, z, STONE);
                    }
                } else {
                    if (depth == 0) {
                        if (k > SAND_TOP_Y) {
                            primer.setBlockState(x, k, z, GRASS);
                        } else {
                            primer.setBlockState(x, k, z, SAND);
                            sand = true;
                        }
                    } else if (depth < 5) {
                        if (sand) {
                            if (depth < 4) {
                                primer.setBlockState(x, k, z, SAND);
                            } else {
                                primer.setBlockState(x, k, z, SANDSTONE);
                            }
                        } else {
                            primer.setBlockState(x, k, z, DIRT);
                        }
                    }
                }
            }
        }
    }
}
