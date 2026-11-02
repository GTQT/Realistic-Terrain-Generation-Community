package rtg.api.world.surface;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkPrimer;
import rtg.api.config.BiomeConfig;
import rtg.api.util.BlockUtil;
import rtg.api.util.noise.PerlinNoise;
import rtg.api.world.RTGWorld;
import rtg.api.world.terrain.TerrainBase;

import java.util.Random;


/**
 * RWG {@code rwg/surface/SurfaceCanyon.java} 的逐行移植（RWG 里 4 处）。
 *
 * <p>峡谷地表：用一张**按高度取色的染色粘土表**做层理。
 * 表在构造时用固定种子 {@code 2L} 的经典 Perlin 一维噪声生成（RWG 用
 * {@code NoiseSelector.createNoiseGenerator(2L)}，其默认实现即 {@code PerlinNoise}）：
 *
 * <pre>
 *   n = perlin.noise1(i / 3f) * 3f + perlin.noise1(i / 1f) * 0.3f + 1.5f;
 *   n = n &gt;= 3f ? 2.9f : n &lt; 0f ? 0f : n;
 *   claycolor[i] = { 1, 8, 0 }[(int) n];
 * </pre>
 *
 * rtgc 的 {@code PerlinNoise} 有逐字移植的 {@code noise1}，故这张表是**逐位相同**的。
 *
 * <h2>记录在案的适配</h2>
 * <ul>
 *   <li>崖壁判定 → {@code TerrainBase.calcCliff(x, z, noise, river)}。</li>
 *   <li>元数据 1 / 8 / 0 → {@code EnumDyeColor.byMetadata(...)}（1=橙、8=银、0=白），
 *       与 1.7.10 的染色粘土元数据编号一一对应。</li>
 *   <li>构造参数 {@code blockByte}（给 topBlock/fillerBlock 写元数据）在 1.12.2 下无效，
 *       其使用者都传 0，故忽略（保留形参以对齐 RWG 签名）。</li>
 * </ul>
 */
public class SurfaceCanyon extends SurfaceBase {

    private final IBlockState[] clayColor = new IBlockState[100];
    private final int grassRaise;

    public SurfaceCanyon(final BiomeConfig config, final IBlockState top, final IBlockState fill,
                         final byte blockByte, final int grassHeight) {

        super(config, top, fill);

        this.grassRaise = grassHeight;

        final int[] c = new int[] { 1, 8, 0 };
        final PerlinNoise perlin = new PerlinNoise(2L);

        for (int i = 0; i < 100; i++) {
            float n = perlin.noise1(i / 3f) * 3f + perlin.noise1(i / 1f) * 0.3f + 1.5f;
            n = n >= 3f ? 2.9f : n < 0f ? 0f : n;
            this.clayColor[i] = BlockUtil.getStateClay(EnumDyeColor.byMetadata(c[(int) n]));
        }
    }

    /** RWG {@code getClayColorForHeight}：把 y 映射到 0..99 的颜色表下标。 */
    private IBlockState clayColorForHeight(int k) {
        k -= 60;
        k = k < 0 ? 0 : k > 99 ? 99 : k;
        return this.clayColor[k];
    }

    @Override
    public void paintTerrain(final ChunkPrimer primer, final int i, final int j, final int x, final int z,
                             int depth, final RTGWorld rtgWorld, final float[] noise, final float river,
                             final Biome[] base) {

        final Random rand = rtgWorld.rand();
        /*
         * 照抄 RWG：草/土 是这套地表的**高度分层**，不是"脏"。
         *
         * 峡谷群系（canyon / canyon_forest / BOP crag）传进来的 top/filler 都是沙，
         * 但 RWG 在这几处写死 grass/dirt：
         *   k > 74+grassRaise        → 沙（高处的沙顶）
         *   k < 62                   → 土
         *   62 ≤ k < 62+grassRaise   → 顶草、下土   ← 谷底那条草带
         *   62+grassRaise ≤ k < 75+grassRaise → 草/土/沙 随机过渡
         * 把这条草带改成沙，等于把整个峡谷从 62 到 121 全变成沙，
         *  CanyonForest 与草地群系的过渡带就会在 70–110 高度出现大片"沙蛇"。
         */
        final IBlockState dirt = Blocks.DIRT.getDefaultState();
        final IBlockState grass = Blocks.GRASS.getDefaultState();

        final float c = TerrainBase.calcCliff(x, z, noise, river);
        final boolean cliff = c > 1.3f;

        for (int k = 255; k > -1; k--) {
            final Block b = primer.getBlockState(x, k, z).getBlock();

            if (b == Blocks.AIR) {
                depth = -1;
            } else if (b == Blocks.STONE) {
                depth++;

                if (depth > -1 && depth < 12) {
                    if (cliff) {
                        primer.setBlockState(x, k, z, clayColorForHeight(k));
                    } else {
                        if (depth > 4) {
                            primer.setBlockState(x, k, z, clayColorForHeight(k));
                        } else if (k > 74 + grassRaise) {
                            if (rand.nextInt(5) == 0) {
                                primer.setBlockState(x, k, z, dirt);
                            } else {
                                primer.setBlockState(x, k, z, depth == 0 ? topBlock : fillerBlock);
                            }
                        } else if (k < 62) {
                            primer.setBlockState(x, k, z, dirt);
                        } else if (k < 62 + grassRaise) {
                            primer.setBlockState(x, k, z, depth == 0 ? grass : dirt);
                        } else if (k < 75 + grassRaise) {
                            if (depth == 0) {
                                final int r = (int) ((k - (62 + grassRaise)) / 2f);
                                if (rand.nextInt(r + 1) == 0) {
                                    primer.setBlockState(x, k, z, grass);
                                } else if (rand.nextInt((int) (r / 2f) + 1) == 0) {
                                    primer.setBlockState(x, k, z, dirt);
                                } else {
                                    primer.setBlockState(x, k, z, topBlock);
                                }
                            } else {
                                primer.setBlockState(x, k, z, fillerBlock);
                            }
                        } else {
                            primer.setBlockState(x, k, z, depth == 0 ? topBlock : fillerBlock);
                        }
                    }
                } else if (k > 63) {
                    primer.setBlockState(x, k, z, clayColorForHeight(k));
                }
            }
        }
    }

}
