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


/**
 * RWG {@code rwg/biomes/realistic/coast/RealisticBiomeCoastIce.java:58-92} 的逐行移植 ——
 * **冰海岸**的地表（雪地 + 崖壁浮冰）。
 *
 * <h2>为什么必须单独提出来（这是一处被漏掉的 rReplace）</h2>
 *
 * RWG 的 {@code RealisticBiomeCoastIce} 里有一个 `surface` 字段：
 * <pre>
 *   surface = new SurfaceGrassland(Blocks.packed_ice, Blocks.packed_ice, Blocks.packed_ice, Blocks.ice);
 * </pre>
 * 但它**同时覆写了 {@code rReplace}**（L58-92），而 {@code RealisticBiomeBase.rReplace} 才是调用
 * `surface.paintTerrain(...)` 的地方 —— 覆写之后那个 `surface` 字段**再也没有被读过**。
 * 也就是说 RWG 那片海岸实际生成的是：
 * <ul>
 *   <li>崖壁（{@code calcCliff > 1.4f}）：{@code depth < 10} 全部刷成
 *       {@code type == 0 ? packed_ice : ice}（{@code type} 由 {@code noise2(i/5, j/5)} 定）；</li>
 *   <li>其余：{@code k > 61} 处 {@code depth < 5} 刷**雪**，并在 {@code depth == 0} 时调用
 *       RWG 的 {@code SnowheightCalculator.calc}（rtgc = {@link TerrainBase#calcSnowHeight}）；
 *       再往下 {@code depth < 3} 刷**砾石**。</li>
 * </ul>
 *
 * <p>⚠ rtgc 此前照抄的是那个**死字段**（`SurfaceGrassland(packed_ice×3, ice)`）⇒ 整片冰海岸
 * 的地面是浮冰，与 RWG 的"雪地 + 砾石底、只有崖壁是冰"完全不同。本类补上真正的 rReplace。
 *
 * <p>两处管线适配（与其余共享地表一致）：崖壁判定用 4 参
 * {@code TerrainBase.calcCliff(x, z, noise, river)}（RWG 是 3 参 {@code CliffCalculator.calc}，
 * 多一条河心抑制）；写块用 {@code ChunkPrimer.setBlockState}（RWG 是 Block[]/byte[] 直写，
 * 故没有 metadata 那一路 —— snow_layer 的高度由 {@code calcSnowHeight} 直接写 state）。
 */
public class SurfaceCoastIce extends SurfaceBase {

    private static final IBlockState SNOW = Blocks.SNOW.getDefaultState();
    private static final IBlockState PACKED_ICE = Blocks.PACKED_ICE.getDefaultState();
    private static final IBlockState ICE = Blocks.ICE.getDefaultState();
    private static final IBlockState GRAVEL = Blocks.GRAVEL.getDefaultState();

    /** RWG 的崖壁阈值（{@code c > 1.4f}）。 */
    private static final float CLIFF_THRESHOLD = 1.4f;

    /**
     * @param config 群系配置。super 的 top/filler 只是为了满足 {@link SurfaceBase} 的约定，
     *               本类自己不使用它们（与 RWG 一致：块写死）。
     */
    public SurfaceCoastIce(final BiomeConfig config) {

        super(config, SNOW, GRAVEL);
    }

    @Override
    public void paintTerrain(final ChunkPrimer primer, final int i, final int j, final int x, final int z,
                             int depth, final RTGWorld rtgWorld, final float[] noise, final float river,
                             final Biome[] base) {

        final SimplexNoise simplex = rtgWorld.simplexInstance(0);

        // RWG `CliffCalculator.calc(x, y, noise)`；rtgc 统一用 4 参（多一个 river）
        final boolean cliff = TerrainBase.calcCliff(x, z, noise, river) > CLIFF_THRESHOLD;
        int type = 0;

        for (int k = 255; k > -1; k--) {
            final Block b = primer.getBlockState(x, k, z).getBlock();

            if (b == Blocks.AIR) {
                depth = -1;
            } else if (b == Blocks.STONE) {
                depth++;

                if (cliff) {
                    if (depth == 0) {
                        // RWG: perlin.noise2(i / 5f, j / 5f) > 0f ? 1 : 0
                        type = simplex.noise2f(i / 5f, j / 5f) > 0f ? 1 : 0;
                    }
                    if (depth < 10) {
                        primer.setBlockState(x, k, z, type == 0 ? PACKED_ICE : ICE);
                    }
                } else {
                    if (depth < 5 && k > 61) {
                        primer.setBlockState(x, k, z, SNOW);
                        if (depth == 0 && k > 61 && k < 254) {
                            // RWG `SnowheightCalculator.calc(x, y, k, blocks, metadata, noise)`
                            TerrainBase.calcSnowHeight(x, k, z, primer, noise);
                        }
                    } else if (depth < 3) {
                        primer.setBlockState(x, k, z, GRAVEL);
                    }
                }
            }
        }
    }
}
