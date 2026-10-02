package rtg.world.gen;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkPrimer;
import rtg.api.util.noise.RwgCellNoise;
import rtg.api.util.noise.SimplexNoise;

import java.util.Random;


/**
 * RWG {@code rwg/map/MapVolcano.java}（77 行）的逐行移植。
 *
 * <p>RWG 里唯一的调用点是 {@code rwg/biomes/realistic/ocean/RealisticBiomeIslandTropical.java:71}
 * （在它的 {@code rMapGen} 里）。<b>rtgc 侧这个接线点目前不存在</b>：{@code rtg.api.world.biome}
 * 下的 {@link rtg.api.world.biome.IRealisticBiome} / {@link rtg.api.world.biome.RealisticBiomeBase}
 * 都没有 RWG 的 {@code generateMapGen} / {@code rMapGen} 钩子（详见任务 B 的调查结论）。
 * 本类因此暂时**零调用者**，先原样落地，等钩子接上再用。
 *
 * <h2>逐行对照（差异全部是"逐列数组 → ChunkPrimer"这一类 API 适配）</h2>
 * <ul>
 *   <li>{@code Block[] blocks + byte[] metadata} → {@code ChunkPrimer primer}。
 *       1.12.2 的方块自带状态，没有独立的 metadata 数组，故 {@code metadata} 形参删除
 *       （RWG 本方法体内也从未读写过它）。</li>
 *   <li>{@code blocks[cta(x, y, z)]} → {@code primer.getBlockState(x, y, z)} /
 *       {@code primer.setBlockState(x, y, z, state)}；RWG 的索引函数 {@code cta(x, y, z)}
 *       随之删除（它只服务于那个一维数组）。</li>
 *   <li>{@code Blocks.air/obsidian/lava/stone} → {@code Blocks.AIR/OBSIDIAN/LAVA/STONE}
 *       的 {@code getDefaultState()}。RWG 比的是 {@code Block} 身份（不看 metadata），
 *       故这里比 {@code .getBlock()} —— 与 {@code SurfaceIslandMountainStone} 等既有移植同口径。</li>
 *   <li>{@code NoiseGenerator.noise2(a, b)} → {@code SimplexNoise.noise2f(a, b)}：
 *       rtgc 的 {@code PerlinNoise.noise2f} 直接转发给**未改动**的 {@code noise2}
 *       （RWG {@code rwg/util/PerlinNoise.java} 的逐行移植），公式同源。</li>
 *   <li><b>唯一无法照抄的调用</b>：{@code TerrainMath.dis2}。rtgc 侧**没有** TerrainMath
 *       （{@code rtg/} 下搜不到 {@code TerrainMath}/{@code dis2}），故把那两行原样搬成本类的私有
 *       {@link #dis2}，调用点只去掉类名前缀。若日后有第二个使用者，应提升为共享工具类。</li>
 *   <li>{@code float[] noise}：rtgc 同名的"该列地形高度"数组（{@link ChunkLandscape#noise}，
 *       索引口径同为 {@code x * 16 + z}），原样照抄。</li>
 *   <li>{@code world} / {@code mapRand} / {@code cell} 三个形参在本方法体内从未被用到
 *       （RWG 亦然，它们只是钩子签名的一部分），为保持**参数顺序**照抄而保留。</li>
 * </ul>
 *
 * <p><b>坐标口径照抄，不要"顺手修正"</b>：{@code chunkX/chunkY} 是**区块坐标**、
 * {@code baseX/baseY} 是火山的**区块坐标**中心、{@code i/j} 是**世界方块坐标**。
 */
public class MapVolcano {

    public static void build(ChunkPrimer primer, World world, Random mapRand, int baseX, int baseY,
            int chunkX, int chunkY, SimplexNoise perlin, RwgCellNoise cell, float[] noise) {
        int i, j;
        float distance, height, obsidian;
        IBlockState b;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                i = (chunkX * 16) + x;
                j = (chunkY * 16) + z;

                distance = (float) dis2(i, j, baseX * 16, baseY * 16);
                obsidian = 140f + distance + perlin.noise2f(i / 16f, j / 16f) * 15f;

                if (distance < 10 + perlin.noise2f(i / 3f, j / 3f) * 1.5f) {
                    height = perlin.noise2f(i / 5f, j / 5f) * 2f;
                    for (int y = 255; y > -1; y--) {
                        if (y > 165) {
                            if (primer.getBlockState(x, y, z).getBlock() != Blocks.AIR) {
                                primer.setBlockState(x, y, z, Blocks.AIR.getDefaultState());
                            }
                        } else if (y > obsidian && y < 156 + height) {
                            primer.setBlockState(x, y, z, Blocks.OBSIDIAN.getDefaultState());
                        } else if (y < 166) {
                            primer.setBlockState(x, y, z, Blocks.LAVA.getDefaultState());
                        } else if (y < obsidian + 1) {
                            if (primer.getBlockState(x, y, z).getBlock() == Blocks.AIR) {
                                primer.setBlockState(x, y, z, Blocks.STONE.getDefaultState());
                            } else {
                                break;
                            }
                        }
                    }
                } else {
                    height = 190f - (distance + perlin.noise2f(i / 12f, j / 12f) * 5f) * 1.7f;
                    if (height > noise[x * 16 + z]) {
                        noise[x * 16 + z] = height;
                    }

                    for (int y = 255; y > -1; y--) {
                        if (y <= height) {
                            b = primer.getBlockState(x, y, z);
                            if (b.getBlock() == Blocks.AIR) {
                                if (y > obsidian) {
                                    b = Blocks.OBSIDIAN.getDefaultState();
                                } else {
                                    b = Blocks.STONE.getDefaultState();
                                }
                            } else {
                                break;
                            }
                            primer.setBlockState(x, y, z, b);
                        }
                    }
                }
            }
        }
    }

    /**
     * RWG {@code rwg/util/TerrainMath.dis2} 的逐字副本（rtgc 没有 TerrainMath，故就近放这里）。
     */
    private static double dis2(double x1, double y1, double x2, double y2) {
        return Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
    }
}
