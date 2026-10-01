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
 * RWG {@code rwg/surface/SurfaceGrassland.java} 的逐行移植（RWG 里用得最多的地表，60 处）。
 *
 * <p>唯一改动是管线适配：RWG 的 {@code CliffCalculator.calc(x, y, noise)}（只看四邻高差）
 * 换成 rtgc 的 {@code TerrainBase.calcCliff(x, z, noise, river)}（多一条"水线附近靠近河心
 * 时不判悬崖"的处理）。这是 rtgc 全库既有约定，见 {@link SurfaceMountainStoneMix1} 的说明。
 *
 * <p>坐标约定（与 {@code ChunkGeneratorRTG#replaceBiomeBlocks} 一致）：
 * {@code i}/{@code j} 是**世界坐标**（只用于噪声），{@code x}/{@code z} 是**区块内局部坐标**
 * （用于放置方块）。RWG 那边是反的（{@code x}/{@code y} 局部、{@code i}/{@code j} 世界），
 * 含义相同、名字相反，移植时已对齐。
 */
public class SurfaceGrassland extends SurfaceBase {

    private final IBlockState cliffBlock1;
    private final IBlockState cliffBlock2;

    public SurfaceGrassland(final BiomeConfig config,
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

                if (cliff) {
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
