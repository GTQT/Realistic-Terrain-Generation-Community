package rtg.api.world.surface;

import net.minecraft.block.state.IBlockState;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkPrimer;

import rtg.api.config.BiomeConfig;
import rtg.api.world.RTGWorld;


/**
 * RWG {@code rwg/surface/SurfaceMountainPolar.java} 的逐行移植。
 *
 * <p><b>它在 RWG 里的 {@code paintTerrain} 是空的</b> —— 不做任何地表替换，
 * 地形填出来的石头原样保留。这里**照抄这个空实现**，不"顺手补全"，
 * 因为"不动地表"本身就是它的语义（极地山体的裸石外观）。
 */
public class SurfaceMountainPolar extends SurfaceBase {

    public SurfaceMountainPolar(final BiomeConfig config,
                                final IBlockState top, final IBlockState fill,
                                final boolean genBeach, final IBlockState genBeachBlock, final float minCliff) {
        super(config, top, fill);
    }

    @Override
    public void paintTerrain(final ChunkPrimer primer, final int i, final int j, final int x, final int z,
                             int depth, final RTGWorld rtgWorld, final float[] noise, final float river,
                             final Biome[] base) {
        // RWG 原文即空实现：极地山体不做地表替换。
    }
}
