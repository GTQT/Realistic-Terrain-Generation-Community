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
 * RWG {@code rwg/surface/SurfaceMountainSnow.java} 的逐行移植（RWG 里 12 处）。
 *
 * <p>在 {@link SurfaceMountainStone} 的两条崖壁规则之上多了第三条：
 * {@code k > 110 + p*4 && c < iCliff + ((k - iHeight) / iStrength) + p} ⇒ **雪**
 * （cliff = 3）。这条只在高处生效，形成雪线以上的裸雪坡。
 *
 * <p>注意 RWG 在"非崖壁且高于水线"的普通分支里写的是 {@code Blocks.grass}、
 * 深度分支里写的是 {@code Blocks.dirt} / {@code Blocks.gravel}（**不是** topBlock/fillerBlock）
 * —— 这是原样保留的，不替换成"看起来更一致"的写法。
 *
 * <p>管线适配：悬崖判定改用 {@code TerrainBase.calcCliff(x, z, noise, river)}，同 {@link SurfaceGrassland}。
 */
public class SurfaceMountainSnow extends SurfaceBase {

    private final boolean beach;
    private final IBlockState beachBlock;
    private final float min;

    private final float sCliff;
    private final float sHeight;
    private final float sStrength;
    private final float iCliff;
    private final float iHeight;
    private final float iStrength;
    private final float cCliff;

    public SurfaceMountainSnow(final BiomeConfig config,
                               final IBlockState top, final IBlockState fill,
                               final boolean genBeach, final IBlockState genBeachBlock, final float minCliff) {
        this(config, top, fill, genBeach, genBeachBlock, minCliff, 1.5f, 60f, 65f, 0.3f, 100f, 50f, 1.5f);
    }

    public SurfaceMountainSnow(final BiomeConfig config,
                               final IBlockState top, final IBlockState fill,
                               final boolean genBeach, final IBlockState genBeachBlock, final float minCliff,
                               final float stoneCliff, final float stoneHeight, final float stoneStrength,
                               final float snowCliff, final float snowHeight, final float snowStrength,
                               final float clayCliff) {

        super(config, top, fill);

        this.beach = genBeach;
        this.beachBlock = genBeachBlock;
        this.min = minCliff;

        this.sCliff = stoneCliff;
        this.sHeight = stoneHeight;
        this.sStrength = stoneStrength;
        this.iCliff = snowCliff;
        this.iHeight = snowHeight;
        this.iStrength = snowStrength;
        this.cCliff = clayCliff;
    }

    @Override
    public void paintTerrain(final ChunkPrimer primer, final int i, final int j, final int x, final int z,
                             int depth, final RTGWorld rtgWorld, final float[] noise, final float river,
                             final Biome[] base) {

        final Random rand = rtgWorld.rand();
        final SimplexNoise simplex = rtgWorld.simplexInstance(0);
        final IBlockState clay = BlockUtil.getStateClay(EnumDyeColor.CYAN);
        final IBlockState snow = Blocks.SNOW.getDefaultState();
        final IBlockState grass = Blocks.GRASS.getDefaultState();
        final IBlockState dirt = Blocks.DIRT.getDefaultState();
        final IBlockState gravelBlock = Blocks.GRAVEL.getDefaultState();

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
                    if (k > 110 + (p * 4) && c < iCliff + ((k - iHeight) / iStrength) + p) {
                        cliff = 3;
                    }

                    if (cliff == 1) {
                        primer.setBlockState(x, k, z, rand.nextInt(3) == 0 ? hcCobble() : hcStone());
                    } else if (cliff == 2) {
                        primer.setBlockState(x, k, z, clay);
                    } else if (cliff == 3) {
                        primer.setBlockState(x, k, z, snow);
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
                        // RWG 此处是 Blocks.grass（不是 topBlock）
                        primer.setBlockState(x, k, z, grass);
                    }
                } else if (depth < 6) {
                    if (cliff == 1) {
                        primer.setBlockState(x, k, z, hcStone());
                    } else if (cliff == 2) {
                        primer.setBlockState(x, k, z, clay);
                    } else if (cliff == 3) {
                        primer.setBlockState(x, k, z, snow);
                    } else if (gravel) {
                        // RWG 此处是 Blocks.gravel（不是 beachBlock）
                        primer.setBlockState(x, k, z, gravelBlock);
                    } else {
                        // RWG 此处是 Blocks.dirt（不是 fillerBlock）
                        primer.setBlockState(x, k, z, dirt);
                    }
                }
            }
        }
    }
}
