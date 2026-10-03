package rtg.api.world.surface;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkPrimer;

import rtg.api.config.BiomeConfig;
import rtg.api.util.BlockUtil;
import rtg.api.world.RTGWorld;
import rtg.api.world.terrain.TerrainBase;


/**
 * RWG {@code rwg/surface/SurfaceRedDesert.java} 的逐行移植（RWG 里 1 处）。
 *
 * <p>RWG 的构造**不接受参数**：top/filler 固定为 {@code Blocks.sand} + 元数据 1（红沙），
 * 底部是 {@code sandstone}，崖壁是染色粘土元数据 14（红）。
 * 这里把 top/fill 折成 1.12.2 的**红沙 blockstate**，其余照抄。
 *
 * <h2>记录在案的适配</h2>
 * <ul>
 *   <li>沙的元数据 1（红沙）→ {@code Blocks.SAND} 的 {@code RED_SAND} 变体。</li>
 *   <li>底部：RWG 写 {@code Blocks.sandstone}（1.7.10 没有红砂岩方块，只有普通砂岩），
 *       故此处同样用**普通**砂岩，不替换成 1.12.2 的红砂岩 —— 保持与 RWG 一致。</li>
 *   <li>崖壁：元数据 14 → {@code BlockUtil.getStateClay(EnumDyeColor.RED)}。</li>
 * </ul>
 */
public class SurfaceRedDesert extends SurfaceBase {

    private final IBlockState bottomBlock;
    private final IBlockState cliffBlock1;

    public SurfaceRedDesert(final BiomeConfig config) {

        super(config, redSand(), redSand());

        this.bottomBlock = Blocks.SANDSTONE.getDefaultState();
        this.cliffBlock1 = BlockUtil.getStateClay(EnumDyeColor.RED);
    }

    private static IBlockState redSand() {
        return Blocks.SAND.getDefaultState()
                .withProperty(net.minecraft.block.BlockSand.VARIANT, net.minecraft.block.BlockSand.EnumType.RED_SAND);
    }

    @Override
    public void paintTerrain(final ChunkPrimer primer, final int i, final int j, final int x, final int z,
                             int depth, final RTGWorld rtgWorld, final float[] noise, final float river,
                             final Biome[] base) {

        final float c = TerrainBase.calcCliff(x, z, noise, river);
        final boolean cliff = c > 1.4f;

        for (int k = 255; k > -1; k--) {
            final Block b = primer.getBlockState(x, k, z).getBlock();

            if (b == Blocks.AIR) {
                depth = -1;
            } else if (b == Blocks.STONE) {
                depth++;

                if (cliff) {
                    if (depth < 6) {
                        primer.setBlockState(x, k, z, cliffBlock1);
                    }
                } else if (depth < 6) {
                    if (depth == 0 && k > 61) {
                        primer.setBlockState(x, k, z, topBlock);
                    } else if (depth < 4) {
                        primer.setBlockState(x, k, z, fillerBlock);
                    } else {
                        primer.setBlockState(x, k, z, bottomBlock);
                    }
                }
            }
        }
    }
}
