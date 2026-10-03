package rtg.api.world.surface;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkPrimer;

import rtg.api.config.BiomeConfig;
import rtg.api.util.noise.SimplexNoise;
import rtg.api.world.RTGWorld;


/**
 * RWG {@code rwg/surface/SurfaceDuneValley.java} 的逐行移植（RWG 里 5 处）。
 *
 * <p>沙丘/绿洲谷地地表：沙线高度由两个噪声决定
 * （{@code k > 90 + noise2(i/24, j/24) * 10 - h}，其中 {@code h = (noise2(i/valley, j/valley) + 0.25) * 65}），
 * 沙层向下依次是沙 → 砂岩。
 *
 * <h2>两处记录在案的适配</h2>
 * <ol>
 *   <li>RWG 写 {@code base[x * 16 + y] = RWGBiomes.baseHotDesert}。rtgc **没有**自定义群系，
 *       故改写为 {@code base[z * 16 + x] = Biomes.DESERT}（保持 RWG 的转置写法不变）。
 *       {@code base} 在 rtgc 里唯一的消费者是 {@code RealisticBiomeMountainChain}，
 *       那里会自行改写，所以这一写实际不影响结果 —— 保留只为结构同构。</li>
 *   <li>RWG 给泥土写 {@code metadata = 1}。1.7.10 与 1.12.2 的 {@code dirt} 都只有 meta 0，
 *       该写入在两边都无效果，故省略。</li>
 * </ol>
 */
public class SurfaceDuneValley extends SurfaceBase {

    private final float valley;
    private final boolean dirt;
    private final boolean mix;

    public SurfaceDuneValley(final BiomeConfig config, final IBlockState top, final IBlockState fill,
                             final float valleySize, final boolean d, final boolean m) {
        super(config, top, fill);

        this.valley = valleySize;
        this.dirt = d;
        this.mix = m;
    }

    @Override
    public void paintTerrain(final ChunkPrimer primer, final int i, final int j, final int x, final int z,
                             int depth, final RTGWorld rtgWorld, final float[] noise, final float river,
                             final Biome[] base) {

        final SimplexNoise simplex = rtgWorld.simplexInstance(0);

        final IBlockState sand = Blocks.SAND.getDefaultState();
        final IBlockState sandstone = Blocks.SANDSTONE.getDefaultState();
        final IBlockState dirtState = Blocks.DIRT.getDefaultState();

        // RWG: h = (perlin.noise2(i / valley, j / valley) + 0.25f) * 65f; h = h < 1f ? 1f : h;
        float h = (simplex.noise2f(i / valley, j / valley) + 0.25f) * 65f;
        h = h < 1f ? 1f : h;
        final float m = simplex.noise2f(i / 12f, j / 12f);
        boolean sandFlag = false;

        for (int k = 255; k > -1; k--) {
            final Block b = primer.getBlockState(x, k, z).getBlock();

            if (b == Blocks.AIR) {
                depth = -1;
            } else if (b == Blocks.STONE) {
                depth++;

                if (depth == 0) {
                    if (k > 90f + simplex.noise2f(i / 24f, j / 24f) * 10f - h || (m < -0.28f && mix)) {
                        primer.setBlockState(x, k, z, sand);
                        base[z * 16 + x] = Biomes.DESERT;
                        sandFlag = true;
                    } else if (dirt && m < 0.22f || k < 62) {
                        primer.setBlockState(x, k, z, dirtState);
                    } else {
                        primer.setBlockState(x, k, z, topBlock);
                    }
                } else if (depth < 6) {
                    if (sandFlag) {
                        primer.setBlockState(x, k, z, depth < 4 ? sand : sandstone);
                    } else {
                        primer.setBlockState(x, k, z, fillerBlock);
                    }
                }
            }
        }
    }
}
