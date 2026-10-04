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
 * RWG {@code rwg/biomes/realistic/coast/RealisticBiomeCoastDunes.java:66-106} 的逐行移植。
 *
 * <h2>为什么它是"共享地表"而不是某个群系的私有物</h2>
 *
 * RWG 把这份地表**内联写在群系类里**（没有提成 {@code rwg/surface/} 下的类），
 * 所以它此前**从未被移植**：rtgc 只移植了它的地形那半
 * （{@code TerrainBase.terrainCoastDunes}），地表这半留空 ——
 * 于是 4 个滨海群系各自带一份 rtgc 自造的内层地表（`VanillaBeach` / `VanillaStoneBeach` /
 * `BOPGravelBeach` / `BOPOriginBeach`，`surface-wiring-check` 报 `still-inner=4`）。
 * 本类就是把 RWG 那份逐行提出来，让那 4 个群系接上**参考实现**，而不是各自发明。
 *
 * <h2>RWG 的原文逻辑（逐行）</h2>
 *
 * <pre>
 * cliff = CliffCalculator.calc(x, y, noise)
 * cliff &gt; 1.3f   → 崖壁：depth &lt; 2 时 1/3 概率 cobblestone、其余 stone；depth &lt; 10 → stone
 * 否则 depth == 0 → k &gt; 68 ? grass : sand（并记住 sand=true）
 *      depth &lt; 5  → sand 分支：depth &lt; 4 → sand，depth == 4 → sandstone
 *                    非 sand   → dirt
 * </pre>
 *
 * <p><b>块是写死的，不取群系自己的 top/filler</b> —— RWG 原文就是写死 grass/sand/sandstone/
 * dirt/cobblestone/stone，与 {@code SurfaceGrassland} 那种"按群系传块"的做法不同。
 * 这一点**有意保留**：BOP 的 {@code gravel_beach} / {@code origin_beach} 因此在地表上
 * 与普通沙滩一致（RWG 的单一 {@code CoastDunes} 本来就不区分它们）。
 *
 * <p>{@code k > 68} 也是 RWG 的字面量，**没有**换成 {@code WaterLevel} ——
 * 与 {@code SurfaceRiverOasis} 里保留字面量高度门槛同一口径。
 *
 * <p>唯一的管线适配：悬崖判定用 {@code TerrainBase.calcCliff(x, z, noise, river)}
 * （RWG 的 {@code CliffCalculator.calc} 只有 3 参，rtgc 的地表统一走 4 参版）。
 */
public class SurfaceCoastDunes extends SurfaceBase {

    /** RWG 的崖壁阈值：{@code cliff > 1.3f}（沙漠是 2.8f，沿海沙丘更低）。 */
    private static final float CLIFF_THRESHOLD = 1.3f;

    // SAND_TOP_Y / GRASS / DIRT 原本用于 RWG 的"沙线以上长草、草下垫土"。
    // 按用户要求（带沙群系不出现泥土）已统一成沙丘剖面，这三个成员随之不再需要。

    private static final IBlockState SAND = Blocks.SAND.getDefaultState();
    private static final IBlockState SANDSTONE = Blocks.SANDSTONE.getDefaultState();
    private static final IBlockState STONE = Blocks.STONE.getDefaultState();
    private static final IBlockState COBBLESTONE = Blocks.COBBLESTONE.getDefaultState();

    /**
     * @param config 群系配置。super 的 top/filler 只是为了满足 {@link SurfaceBase} 的约定，
     *               本类自己不使用它们（与 RWG 一致：块写死）。
     */
    public SurfaceCoastDunes(final BiomeConfig config) {

        super(config, SAND, SANDSTONE);
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
                        /*
                         * rtgc 有意偏离 RWG（用户要求：带沙的群系不许出现泥土）。
                         * RWG 原文是 `k > 68 ? grass : sand`（沙线以上长草），配套下面是泥土。
                         * 这里统一成**沙丘剖面**：沙线以上也是沙，下面沙→砂岩，
                         * 于是这些海滩（vanilla beach/stone_beach、BOP gravel/white/origin beach）
                         * 不再出现草与泥土。
                         */
                        primer.setBlockState(x, k, z, SAND);
                        sand = true;
                    } else if (depth < 5) {
                        if (depth < 4) {
                            primer.setBlockState(x, k, z, SAND);
                        } else {
                            primer.setBlockState(x, k, z, SANDSTONE);
                        }
                    }
                }
            }
        }
    }
}
