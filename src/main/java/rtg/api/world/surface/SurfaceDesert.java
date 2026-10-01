package rtg.api.world.surface;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkPrimer;

import rtg.api.config.BiomeConfig;
import rtg.api.world.RTGWorld;
import rtg.api.world.terrain.TerrainBase;


/**
 * RWG {@code rwg/surface/SurfaceDesert.java} 的逐行移植（RWG 里 1 处）。
 *
 * <p>沙漠地表：崖壁阈值比其他地表**高得多**（{@code c > 2.8f}，多数地表是 1.4f），
 * 所以沙漠只在很陡的地方才露石头；非崖壁时 4 格以下是 {@code bottomBlock}。
 *
 * <p>管线适配：悬崖判定改用 {@code TerrainBase.calcCliff(x, z, noise, river)}，同 {@link SurfaceGrassland}。
 */
public class SurfaceDesert extends SurfaceBase {

    private final IBlockState cliffBlock1;
    private final IBlockState cliffBlock2;
    private final IBlockState bottomBlock;

    public SurfaceDesert(final BiomeConfig config,
                         final IBlockState top, final IBlockState filler, final IBlockState bottom,
                         final IBlockState cliff1, final IBlockState cliff2) {

        super(config, top, filler);

        this.bottomBlock = bottom;
        this.cliffBlock1 = cliff1;
        this.cliffBlock2 = cliff2;
    }

    @Override
    public void paintTerrain(final ChunkPrimer primer, final int i, final int j, final int x, final int z,
                             int depth, final RTGWorld rtgWorld, final float[] noise, final float river,
                             final Biome[] base) {

        final Random rand = rtgWorld.rand();
        final float c = TerrainBase.calcCliff(x, z, noise, river);
        final boolean cliff = c > 2.8f;

        for (int k = 255; k > -1; k--) {
            final Block b = primer.getBlockState(x, k, z).getBlock();

            if (b == Blocks.AIR) {
                depth = -1;
            } else if (b == Blocks.STONE) {
                depth++;

                if (cliff) {
                    if (depth > -1 && depth < 2) {
                        primer.setBlockState(x, k, z, rand.nextInt(3) == 0 ? cliffBlock2 : cliffBlock1);
                    } else if (depth < 10) {
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
