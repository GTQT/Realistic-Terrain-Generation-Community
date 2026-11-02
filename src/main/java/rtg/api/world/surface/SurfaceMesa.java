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
 * RWG {@code rwg/surface/SurfaceMesa.java} 的逐行移植（RWG 里 1 处）。
 *
 * <p>台地地表。与 {@link SurfaceCanyon} 用**同一张**按高度取色的粘土表
 * （同一个固定种子 {@code 2L} 的经典 Perlin 一维噪声，同一组颜色 {1, 8, 0}），
 * 只是高度门控不同（这里是 69 / 77 / 78，峡谷是 62 / 74 / 75）。
 *
 * <p>适配同 {@link SurfaceCanyon}：崖壁判定 → {@code TerrainBase.calcCliff}；
 * 元数据 → {@code EnumDyeColor.byMetadata}；{@code blockByte} 在 1.12.2 无效故忽略。
 */
public class SurfaceMesa extends SurfaceBase {

    private final IBlockState[] clayColor = new IBlockState[100];

    public SurfaceMesa(final BiomeConfig config, final IBlockState top, final IBlockState fill, final byte b) {

        super(config, top, fill);

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
         * 照抄 RWG：台地/恶地的"土色夹层"就是 RWG 原文的 dirt，不是 bug。
         * 之前按"沙质群系不许出现泥土"把它换成沙，会抹掉恶地最有辨识度的层理；
         * 与 RWG 对齐后这几处恢复 dirt，陶瓦（clayColorForHeight）层次保持不变。
         */
        final IBlockState dirt = Blocks.DIRT.getDefaultState();

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
                        } else if (k > 77) {
                            if (rand.nextInt(5) == 0) {
                                primer.setBlockState(x, k, z, dirt);
                            } else {
                                primer.setBlockState(x, k, z, depth == 0 ? topBlock : fillerBlock);
                            }
                        } else if (k < 69) {
                            primer.setBlockState(x, k, z, dirt);
                        } else if (k < 78) {
                            if (depth == 0) {
                                if (k < 72 && rand.nextInt(k - 69 + 1) == 0) {
                                    primer.setBlockState(x, k, z, dirt);
                                } else if (rand.nextInt(5) == 0) {
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
