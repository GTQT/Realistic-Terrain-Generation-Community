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
 * RWG {@code rwg/surface/SurfaceMarshFix.java} 的逐行移植（RWG 里 1 处）。
 *
 * <p>与 {@link SurfaceGrassland} 只差一处：崖壁分支多一个高度门控
 * {@code cliff && k > 64} —— 沼泽的水线附近不画崖壁，避免水面处长出石壁。
 *
 * <p>适配：崖壁判定 → {@code TerrainBase.calcCliff(x, z, noise, river)}。
 */
public class SurfaceMarshFix extends SurfaceBase {

    private final IBlockState cliffBlock1;
    private final IBlockState cliffBlock2;

    public SurfaceMarshFix(final BiomeConfig config,
                           final IBlockState top, final IBlockState filler,
                           final IBlockState cliff1, final IBlockState cliff2) {
        super(config, top, filler);

        this.cliffBlock1 = cliff1;
        this.cliffBlock2 = cliff2;
    }

    @Override
    public void paintTerrain(final ChunkPrimer primer, final int i, final int j, final int x, final int z,
                             int depth, final RTGWorld rtgWorld, final float[] noise, final float river,
                             final Biome[] base) {

        final Random rand = rtgWorld.rand();
        final float c = TerrainBase.calcCliff(x, z, noise, river);
        final boolean cliff = c > 1.4f;

        for (int k = 255; k > -1; k--) {
            final Block b = primer.getBlockState(x, k, z).getBlock();

            if (b == Blocks.AIR) {
                depth = -1;
            } else if (b == Blocks.STONE) {
                depth++;

                if (cliff && k > 64) {
                    if (depth > -1 && depth < 2) {
                        primer.setBlockState(x, k, z, rand.nextInt(3) == 0 ? cliffBlock2 : cliffBlock1);
                    } else if (depth < 10) {
                        primer.setBlockState(x, k, z, cliffBlock1);
                    }
                } else {
                    if (depth == 0 && k > 61) {
                        primer.setBlockState(x, k, z, topBlock);
                    } else if (depth < 4) {
                        primer.setBlockState(x, k, z, fillerBlock);
                    }
                }
            }
        }
    }
}
