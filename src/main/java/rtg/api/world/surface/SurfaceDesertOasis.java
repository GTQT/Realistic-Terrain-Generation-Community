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
 * RWG {@code rwg/surface/SurfaceDesertOasis.java} 的逐行移植（RWG 里 2 处）。
 *
 * <p>绿洲地表：非崖壁时顶层在"泥土"与"沙"之间由
 * {@code noise2(i/12, j/12) > -0.3 + ((k - 61) / 15)} 决定 —— 即**越高越偏向泥土**，
 * 沙线以下 3 格是沙、再往下是砂岩。
 *
 * <h2>两处记录在案的适配</h2>
 * <ol>
 *   <li>RWG 的崖壁分支写 {@code metadata = 14}（红色染色粘土）。1.12.2 的
 *       {@code stained_hardened_clay} 用 blockstate 而不是元数据，故这里改用
 *       {@code BlockUtil.getStateClay(EnumDyeColor.RED)}。</li>
 *   <li>{@code sandMetadata} 参数同理：1.7.10 的沙有 meta（0/1），1.12.2 的
 *       {@code Blocks.SAND} 是 block（沙/红沙），故该参数在 1.12.2 下无效并**被忽略**
 *       —— 保留形参只为与 RWG 构造签名一一对应。两个使用者都传 0。</li>
 * </ol>
 */
public class SurfaceDesertOasis extends SurfaceBase {

    private final IBlockState cliffBlock1;
    private final IBlockState cliffBlock2;
    private final int cliffType;

    public SurfaceDesertOasis(final BiomeConfig config,
                              final IBlockState top, final IBlockState filler,
                              final IBlockState cliff1, final IBlockState cliff2,
                              final byte metadata, final int cliff) {
        super(config, top, filler);

        this.cliffBlock1 = cliff1;
        this.cliffBlock2 = cliff2;
        // 1.12.2：沙的 meta 由 block 变体承担，surface 传 0；此处仅保留形参以对齐 RWG 签名
        this.cliffType = cliff;
    }

    @Override
    public void paintTerrain(final ChunkPrimer primer, final int i, final int j, final int x, final int z,
                             int depth, final RTGWorld rtgWorld, final float[] noise, final float river,
                             final Biome[] base) {

        final Random rand = rtgWorld.rand();
        final SimplexNoise simplex = rtgWorld.simplexInstance(0);

        final IBlockState sand = Blocks.SAND.getDefaultState();
        final IBlockState sandstone = Blocks.SANDSTONE.getDefaultState();
        final IBlockState clayRed = rtg.api.util.BlockUtil.getStateClay(net.minecraft.item.EnumDyeColor.RED);

        final float c = TerrainBase.calcCliff(x, z, noise, river);
        final boolean cliff = c > 1.3f;
        boolean dirt = false;

        for (int k = 255; k > -1; k--) {
            final Block b = primer.getBlockState(x, k, z).getBlock();

            if (b == Blocks.AIR) {
                depth = -1;
            } else if (b == Blocks.STONE) {
                depth++;

                if (cliff) {
                    if (cliffType == 1) {
                        if (depth < 6) {
                            primer.setBlockState(x, k, z, clayRed);
                        }
                    } else {
                        if (depth > -1 && depth < 2) {
                            primer.setBlockState(x, k, z, rand.nextInt(3) == 0 ? cliffBlock2 : cliffBlock1);
                        } else if (depth < 10) {
                            primer.setBlockState(x, k, z, cliffBlock1);
                        }
                    }
                } else if (depth < 6) {
                    if (depth == 0 && k > 61) {
                        if (simplex.noise2f(i / 12f, j / 12f) > -0.3f + ((k - 61f) / 15f)) {
                            dirt = true;
                            primer.setBlockState(x, k, z, topBlock);
                        } else {
                            primer.setBlockState(x, k, z, sand);
                        }
                    } else if (depth < 4) {
                        primer.setBlockState(x, k, z, dirt ? fillerBlock : sand);
                    } else if (!dirt) {
                        primer.setBlockState(x, k, z, sandstone);
                    }
                }
            }
        }
    }
}
