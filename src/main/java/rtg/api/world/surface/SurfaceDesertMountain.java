package rtg.api.world.surface;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkPrimer;

import rtg.api.config.BiomeConfig;
import rtg.api.util.BlockUtil;
import rtg.api.util.noise.SimplexNoise;
import rtg.api.world.RTGWorld;
import rtg.api.world.terrain.TerrainBase;


/**
 * RWG {@code rwg/surface/SurfaceDesertMountain.java} 的逐行移植（RWG 里 4 处）。
 *
 * <p>结构与 {@link SurfaceMountainStone} 相同（两条崖壁规则 + 三维噪声 {@code p}），
 * 但顶层/填充块是沙漠系的，且没有"k &lt; 63 时用 filler/top"之外的特殊分支。
 *
 * <p>管线适配：悬崖判定改用 {@code TerrainBase.calcCliff(x, z, noise, river)}。
 * 元数据 9（青色染色粘土）用 {@code BlockUtil.getStateClay(EnumDyeColor.CYAN)}，与 RWG 原意一致。
 */
public class SurfaceDesertMountain extends SurfaceBase {

    private final boolean beach;
    private final IBlockState beachBlock;
    private final float min;

    private final float sCliff;
    private final float sHeight;
    private final float sStrength;
    private final float cCliff;

    public SurfaceDesertMountain(final BiomeConfig config,
                                 final IBlockState top, final IBlockState fill,
                                 final boolean genBeach, final IBlockState genBeachBlock, final float minCliff) {
        this(config, top, fill, genBeach, genBeachBlock, minCliff, 1.5f, 60f, 65f, 1.5f);
    }

    public SurfaceDesertMountain(final BiomeConfig config,
                                 final IBlockState top, final IBlockState fill,
                                 final boolean genBeach, final IBlockState genBeachBlock, final float minCliff,
                                 final float stoneCliff, final float stoneHeight, final float stoneStrength,
                                 final float clayCliff) {

        super(config, top, fill);

        this.beach = genBeach;
        this.beachBlock = genBeachBlock;
        this.min = minCliff;

        this.sCliff = stoneCliff;
        this.sHeight = stoneHeight;
        this.sStrength = stoneStrength;
        this.cCliff = clayCliff;
    }

    @Override
    public void paintTerrain(final ChunkPrimer primer, final int i, final int j, final int x, final int z,
                             int depth, final RTGWorld rtgWorld, final float[] noise, final float river,
                             final Biome[] base) {

        final Random rand = rtgWorld.rand();
        final SimplexNoise simplex = rtgWorld.simplexInstance(0);
        final IBlockState clay = BlockUtil.getStateClay(EnumDyeColor.CYAN);

        final float c = TerrainBase.calcCliff(x, z, noise, river);
        int cliff = 0;
        boolean gravel = false;

        for (int k = 255; k > -1; k--) {
            final Block b = primer.getBlockState(x, k, z).getBlock();

            if (b == Blocks.AIR) {
                depth = -1;
            } else if (b == Blocks.STONE) {
                depth++;

                if (depth == 0) {
                    if (k < 63 && beach) {
                        gravel = true;
                    }

                    final float p = simplex.noise3f(i / 8f, j / 8f, k / 8f) * 0.5f;
                    if (c > min && c > sCliff - ((k - sHeight) / sStrength) + p) {
                        cliff = 1;
                    }
                    if (c > cCliff) {
                        cliff = 2;
                    }

                    if (cliff == 1) {
                        primer.setBlockState(x, k, z, rand.nextInt(3) == 0 ? hcCobble() : hcStone());
                    } else if (cliff == 2) {
                        primer.setBlockState(x, k, z, clay);
                    } else if (k < 63) {
                        if (beach) {
                            primer.setBlockState(x, k, z, beachBlock);
                            gravel = true;
                        } else if (k < 62) {
                            primer.setBlockState(x, k, z, fillerBlock);
                        } else {
                            primer.setBlockState(x, k, z, topBlock);
                        }
                    } else {
                        primer.setBlockState(x, k, z, topBlock);
                    }
                } else if (depth < 6) {
                    if (cliff == 1) {
                        primer.setBlockState(x, k, z, hcStone());
                    } else if (cliff == 2) {
                        primer.setBlockState(x, k, z, clay);
                    } else if (gravel) {
                        primer.setBlockState(x, k, z, beachBlock);
                    } else {
                        primer.setBlockState(x, k, z, fillerBlock);
                    }
                }
            }
        }
    }
}
