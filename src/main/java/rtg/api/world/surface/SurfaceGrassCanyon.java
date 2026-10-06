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
 * RWG {@code rwg/surface/SurfaceGrassCanyon.java} 的逐行移植（RWG 里 1 处）。
 *
 * <p>{@link SurfaceCanyon} 的简化版：只用**一个**染色粘土颜色，没有按高度取色的层理表。
 *
 * <p>适配：崖壁判定 → {@code TerrainBase.calcCliff}；元数据 {@code claycolor}
 * → {@code EnumDyeColor.byMetadata(claycolor)}。
 */
public class SurfaceGrassCanyon extends SurfaceBase {

    private final IBlockState clay;

    public SurfaceGrassCanyon(final BiomeConfig config, final IBlockState top, final IBlockState fill,
                              final byte b) {
        super(config, top, fill);
        this.clay = BlockUtil.getStateClay(EnumDyeColor.byMetadata(b & 255));
    }

    @Override
    public void paintTerrain(final ChunkPrimer primer, final int i, final int j, final int x, final int z,
                             int depth, final RTGWorld rtgWorld, final float[] noise, final float river,
                             final Biome[] base) {

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
                        primer.setBlockState(x, k, z, clay);
                    } else {
                        if (depth > 4) {
                            primer.setBlockState(x, k, z, clay);
                        } else {
                            primer.setBlockState(x, k, z, depth == 0 ? topBlock : fillerBlock);
                        }
                    }
                } else if (k > 63) {
                    primer.setBlockState(x, k, z, clay);
                }
            }
        }
    }
}
