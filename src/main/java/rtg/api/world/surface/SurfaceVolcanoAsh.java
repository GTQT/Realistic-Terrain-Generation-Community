package rtg.api.world.surface;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkPrimer;

import rtg.api.config.BiomeConfig;
import rtg.api.world.RTGWorld;


/**
 * RWG {@code rwg/surface/SurfaceVolcanoAsh.java} 的逐行移植。
 *
 * <p>用途（照抄 RWG 原注释）：均匀地涂刷火山地形，不在陡坡上替换成石头或海滩材质。
 *
 * <p>唯一适配是索引换算：RWG 用一维数组 {@code blocks[(y * 16 + x) * 256 + level]}
 * （其中 {@code x} 是区块内局部 x、{@code y} 是区块内局部 z），rtgc 的 {@code ChunkPrimer}
 * 直接按三维局部坐标索引，故换成 {@code primer.getBlockState(x, level, z)} /
 * {@code primer.setBlockState(x, level, z, state)}，一一对应，逻辑不变。
 *
 * <p>另一处必要适配：RWG 的 {@code metadata[...] = 0} 在 1.12.2 没有对应操作
 * （元数据已经包含在 {@code IBlockState} 里，由 {@code topBlock}/{@code fillerBlock}
 * 自带），故省略。
 *
 * <p>坐标约定（与 {@code ChunkGeneratorRTG#replaceBiomeBlocks} 一致）：
 * {@code i}/{@code j} 是**世界坐标**（只用于噪声），{@code x}/{@code z} 是**区块内局部坐标**
 * （用于放置方块）。RWG 那边是反的（{@code x}/{@code y} 局部、{@code i}/{@code j} 世界），
 * 含义相同、名字相反，移植时已对齐。
 *
 * <p>{@code surfaceDepth} 重载供 {@code RealisticBiomeIslandVolcano} 指定灰层厚度；
 * 默认重载照抄 RWG 传入 {@code 6}。
 */
public class SurfaceVolcanoAsh extends SurfaceBase {

    private static final int MIN_ASH_HEIGHT = 61;

    public SurfaceVolcanoAsh(final BiomeConfig config, final IBlockState ash, final IBlockState ashStone) {

        super(config, ash, ashStone);
    }

    @Override
    public void paintTerrain(final ChunkPrimer primer, final int i, final int j, final int x, final int z,
                             final int depth, final RTGWorld rtgWorld, final float[] noise, final float river,
                             final Biome[] base) {

        paintTerrain(primer, i, j, x, z, depth, rtgWorld, noise, river, base, 6);
    }

    public void paintTerrain(final ChunkPrimer primer, final int i, final int j, final int x, final int z,
                             int depth, final RTGWorld rtgWorld, final float[] noise, final float river,
                             final Biome[] base, final int surfaceDepth) {

        boolean ashSurface = false;

        for (int level = 255; level >= 0; level--) {
            final Block block = primer.getBlockState(x, level, z).getBlock();

            if (block == Blocks.AIR) {
                depth = -1;
                ashSurface = false;
            } else if (block == Blocks.STONE) {
                depth++;

                if (depth == 0) {
                    ashSurface = level >= MIN_ASH_HEIGHT;

                    if (ashSurface) {
                        primer.setBlockState(x, level, z, topBlock);
                    }
                } else if (ashSurface && depth < 6 && depth < surfaceDepth) {
                    primer.setBlockState(x, level, z, depth < 2 ? topBlock : fillerBlock);
                }
            }
        }
    }
}
