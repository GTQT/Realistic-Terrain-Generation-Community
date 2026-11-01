package rtg.world.biome;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.feature.WorldGenLakes;
import net.minecraft.world.gen.feature.WorldGenPumpkin;
import net.minecraft.world.gen.feature.WorldGenerator;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoRtgDecorator;
import rtg.api.world.gen.feature.*;

import java.util.Arrays;
import java.util.Collections;


/**
 * RWG 雪原/苔原族：
 * <ul>
 *   <li>{@link #addTundraPlains} ← {@code land/RealisticBiomeTundraPlains}</li>
 *   <li>{@link #addSnowHills} ← {@code land/RealisticBiomeSnowHills}</li>
 *   <li>{@link #addSnowRivers} ← {@code land/RealisticBiomeSnowRivers}</li>
 * </ul>
 *
 * <p>SnowHills 与 SnowRivers 的 {@code rDecorate} 只差一处：树密度噪声的常数
 * （{@code *5 + 0.5} vs {@code *5 - 0.5}），其余逐行相同，故共用一个实现。
 */
public final class RtgSnowDecos {

    private static final java.util.Collection<Block> BLOB_GROUND = Collections.unmodifiableCollection(
            Arrays.asList(Blocks.GRASS, Blocks.DIRT, Blocks.STONE, Blocks.GRAVEL, Blocks.SAND));

    private RtgSnowDecos() {}

    /** {@code RealisticBiomeTundraPlains.rDecorate}，strength = 1。 */
    public static void addTundraPlains(final RealisticBiomeBase biome) {

        biome.addDeco(new DecoRtgDecorator((world, rand, chunkX, chunkZ, river, rtgWorld) -> {

            if (rand.nextInt(15) == 0) {
                final int i2 = chunkX + rand.nextInt(16) + 8;
                final int i8 = chunkZ + rand.nextInt(16) + 8;
                final int l4 = world.getHeight(i2, i8);
                if (l4 > 63 && l4 < 105) {
                    new WorldGenLakes(Blocks.WATER).generate(world, rand, new BlockPos(i2, l4, i8));
                }
            }

            for (int l = 0; l < 3; l++) {
                final int i1 = chunkX + rand.nextInt(16) + 8;
                final int j1 = chunkZ + rand.nextInt(16) + 8;
                final int k1 = world.getHeight(i1, j1);
                if (k1 < 85 && (k1 < 64 || rand.nextInt(5) == 0)) {
                    new WorldGenBlob(Blocks.COBBLESTONE.getDefaultState(), 0, BLOB_GROUND, false)
                            .generate(world, rand, new BlockPos(i1, k1, j1));
                }
            }

            if (rand.nextInt(2) == 0) {
                final int j6 = chunkX + rand.nextInt(16) + 8;
                final int k10 = chunkZ + rand.nextInt(16) + 8;
                final int z52 = world.getHeight(j6, k10);
                final WorldGenerator worldgenerator = rand.nextInt(7) == 0
                        ? new WorldGenSmallSpruce(rand.nextInt(2))
                        : new WorldGenSmallPine(2 + rand.nextInt(3), 4 + rand.nextInt(4), rand.nextInt(2));
                worldgenerator.generate(world, rand, new BlockPos(j6, z52, k10));
            }

            {
                final int i1 = chunkX + rand.nextInt(16) + 8;
                final int j1 = chunkZ + rand.nextInt(16) + 8;
                final int k1 = world.getHeight(i1, j1);
                if (k1 < 90) {
                    new WorldGenRtgShrub(rand.nextInt(4) + 1, rand.nextInt(2), rand.nextInt(2))
                            .generate(world, rand, new BlockPos(i1, k1, j1));
                }
            }

            if (rand.nextInt(25) == 0) {
                final int j16 = chunkX + rand.nextInt(16) + 8;
                final int j18 = rand.nextInt(100);
                final int j21 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenPumpkin().generate(world, rand, new BlockPos(j16, j18, j21));
            }

            for (int f23 = 0; f23 < 2; f23++) {
                final int j15 = chunkX + rand.nextInt(16) + 8;
                final int j17 = rand.nextInt(128);
                final int j20 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenRtgFlowers(new int[] { 0 }).generate(world, rand, new BlockPos(j15, j17, j20));
            }

            for (int l14 = 0; l14 < 4; l14++) {
                final int l19 = chunkX + rand.nextInt(16) + 8;
                final int k22 = rand.nextInt(128);
                final int j24 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenRtgGrass(WorldGenRtgGrass.tallGrass())
                        .generate(world, rand, new BlockPos(l19, k22, j24));
            }
        }));
    }

    /** {@code RealisticBiomeSnowHills.rDecorate}（噪声常数 {@code +0.5}），strength = 1。 */
    public static void addSnowHills(final RealisticBiomeBase biome) {
        addSnowBiome(biome, 0.5f);
    }

    /** {@code RealisticBiomeSnowRivers.rDecorate}（噪声常数 {@code -0.5}），strength = 1。 */
    public static void addSnowRivers(final RealisticBiomeBase biome) {
        addSnowBiome(biome, -0.5f);
    }

    private static void addSnowBiome(final RealisticBiomeBase biome, final float treeNoiseAddend) {

        biome.addDeco(new DecoRtgDecorator((world, rand, chunkX, chunkZ, river, rtgWorld) -> {

            if (rand.nextInt(15) == 0) {
                final int i2 = chunkX + rand.nextInt(16) + 8;
                final int i8 = chunkZ + rand.nextInt(16) + 8;
                final int l4 = world.getHeight(i2, i8);
                if (l4 > 63 && l4 < 105) {
                    new WorldGenLakes(Blocks.WATER).generate(world, rand, new BlockPos(i2, l4, i8));
                }
            }

            for (int l = 0; l < 6; l++) {
                final int i1 = chunkX + rand.nextInt(16) + 8;
                final int j1 = chunkZ + rand.nextInt(16) + 8;
                final int k1 = world.getHeight(i1, j1);
                if (k1 < 95 && (k1 < 64 || rand.nextInt(15) == 0)) {
                    new WorldGenBlob(Blocks.MOSSY_COBBLESTONE.getDefaultState(), 0, BLOB_GROUND, false)
                            .generate(world, rand, new BlockPos(i1, k1, j1));
                }
            }

            if (rand.nextInt(25) == 0) {
                final int j16 = chunkX + rand.nextInt(16) + 8;
                final int j18 = rand.nextInt(128);
                final int j21 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenPumpkin().generate(world, rand, new BlockPos(j16, j18, j21));
            }

            for (int f23 = 0; f23 < 2; f23++) {
                final int j15 = chunkX + rand.nextInt(16) + 8;
                final int j17 = rand.nextInt(128);
                final int j20 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenRtgFlowers(new int[] { 9, 0, 3 }).generate(world, rand, new BlockPos(j15, j17, j20));
            }

            for (int l14 = 0; l14 < 3; l14++) {
                final int l19 = chunkX + rand.nextInt(16) + 8;
                final int k22 = rand.nextInt(128);
                final int j24 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenRtgGrass(WorldGenRtgGrass.tallGrass())
                        .generate(world, rand, new BlockPos(l19, k22, j24));
            }

            final float l = rtgWorld.simplexInstance(0).noise2f(chunkX / 100f, chunkZ / 100f) * 5f + treeNoiseAddend;
            for (int b1 = 0; b1 < l * 2f; b1++) {
                final int j6 = chunkX + rand.nextInt(16) + 8;
                final int k10 = chunkZ + rand.nextInt(16) + 8;
                final int z52 = world.getHeight(j6, k10);

                if (z52 < 75) {
                    final WorldGenerator worldgenerator = rand.nextInt(8) != 0
                            ? new WorldGenSmallSpruce(1 + rand.nextInt(2))
                            : new WorldGenSmallPine(1 + rand.nextInt(3), 2 + rand.nextInt(4), 0);
                    worldgenerator.generate(world, rand, new BlockPos(j6, z52, k10));
                } else if (z52 < 110) {
                    final WorldGenerator worldgenerator = rand.nextInt(4) != 0
                            ? new WorldGenSmallSpruce(rand.nextInt(2))
                            : new WorldGenSmallPine(2 + rand.nextInt(2), 4 + rand.nextInt(5), 0);
                    worldgenerator.generate(world, rand, new BlockPos(j6, z52, k10));
                }
            }

            if (l > -0.4f) {
                for (int b = 0; b < 2; b++) {
                    final int i1 = chunkX + rand.nextInt(16) + 8;
                    final int j1 = chunkZ + rand.nextInt(16) + 8;
                    final int k1 = world.getHeight(i1, j1);
                    if (k1 < 110) {
                        final int size = rand.nextInt(10) == 0 ? rand.nextInt(5) + 4 : rand.nextInt(4) + 1;
                        new WorldGenRtgShrub(size, rand.nextInt(2), rand.nextInt(2))
                                .generate(world, rand, new BlockPos(i1, k1, j1));
                    }
                }
            }
        }));
    }

    /** {@code land/RealisticBiomePolar.rDecorate}，strength = 1。 */
    public static void addPolar(final RealisticBiomeBase biome) {

        biome.addDeco(new DecoRtgDecorator((world, rand, chunkX, chunkZ, river, rtgWorld) -> {

            if (river > 0.86f) {
                for (int j = 0; j < 5; j++) {
                    final int i1 = chunkX + rand.nextInt(16) + 8;
                    final int j1 = chunkZ + rand.nextInt(16) + 8;
                    final int k1 = world.getHeight(i1, j1);
                    if (k1 < 64) {
                        new WorldGenBlob(Blocks.PACKED_ICE.getDefaultState(), 0, BLOB_GROUND, false)
                                .generate(world, rand, new BlockPos(i1, k1, j1));
                    }
                }

                if (rand.nextInt(2) == 0) {
                    final int j6 = chunkX + rand.nextInt(16) + 8;
                    final int k10 = chunkZ + rand.nextInt(16) + 8;
                    final int z52 = world.getHeight(j6, k10);
                    new WorldGenSmallSpruce(rand.nextInt(2)).generate(world, rand, new BlockPos(j6, z52, k10));
                }
            }
        }));
    }

    /** {@code land/RealisticBiomeSnowLakes.rDecorate}，strength = 1。 */
    public static void addSnowLakes(final RealisticBiomeBase biome) {

        biome.addDeco(new DecoRtgDecorator((world, rand, chunkX, chunkZ, river, rtgWorld) -> {

            if (rand.nextInt(15) == 0) {
                final int i2 = chunkX + rand.nextInt(16) + 8;
                final int i8 = chunkZ + rand.nextInt(16) + 8;
                final int l4 = world.getHeight(i2, i8);
                if (l4 > 63 && l4 < 105) {
                    new WorldGenLakes(Blocks.WATER).generate(world, rand, new BlockPos(i2, l4, i8));
                }
            }

            for (int l = 0; l < 6; l++) {
                final int i1 = chunkX + rand.nextInt(16) + 8;
                final int j1 = chunkZ + rand.nextInt(16) + 8;
                final int k1 = world.getHeight(i1, j1);
                if (k1 < 95 && (k1 < 64 || rand.nextInt(15) == 0)) {
                    new WorldGenBlob(Blocks.MOSSY_COBBLESTONE.getDefaultState(), 0, BLOB_GROUND, false)
                            .generate(world, rand, new BlockPos(i1, k1, j1));
                }
            }

            if (rand.nextInt(25) == 0) {
                final int j16 = chunkX + rand.nextInt(16) + 8;
                final int j18 = rand.nextInt(128);
                final int j21 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenPumpkin().generate(world, rand, new BlockPos(j16, j18, j21));
            }

            for (int f23 = 0; f23 < 2; f23++) {
                final int j15 = chunkX + rand.nextInt(16) + 8;
                final int j17 = rand.nextInt(128);
                final int j20 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenRtgFlowers(new int[] { 9, 0, 3 }).generate(world, rand, new BlockPos(j15, j17, j20));
            }

            for (int l14 = 0; l14 < 3; l14++) {
                final int l19 = chunkX + rand.nextInt(16) + 8;
                final int k22 = rand.nextInt(128);
                final int j24 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenRtgGrass(WorldGenRtgGrass.tallGrass())
                        .generate(world, rand, new BlockPos(l19, k22, j24));
            }

            if (rand.nextInt(4) == 0) {
                final int j6 = chunkX + rand.nextInt(16) + 8;
                final int k10 = chunkZ + rand.nextInt(16) + 8;
                final int z52 = world.getHeight(j6, k10);

                if (z52 < 120) {
                    final WorldGenerator worldgenerator = rand.nextInt(3) != 0
                            ? new WorldGenSmallPine(1 + rand.nextInt(3), 3 + rand.nextInt(5), 0)
                            : new WorldGenSmallSpruce(rand.nextInt(2));
                    worldgenerator.generate(world, rand, new BlockPos(j6, z52, k10));
                }
            }

            if (rand.nextInt(2) == 0) {
                final int i1 = chunkX + rand.nextInt(16) + 8;
                final int j1 = chunkZ + rand.nextInt(16) + 8;
                final int k1 = world.getHeight(i1, j1);
                if (k1 < 110) {
                    new WorldGenRtgShrub(rand.nextInt(4) + 1, rand.nextInt(2), rand.nextInt(2))
                            .generate(world, rand, new BlockPos(i1, k1, j1));
                }
            }
        }));
    }

    /** {@code land/RealisticBiomeTundraHills.rDecorate}，strength = 1。 */
    public static void addTundraHills(final RealisticBiomeBase biome) {

        biome.addDeco(new DecoRtgDecorator((world, rand, chunkX, chunkZ, river, rtgWorld) -> {

            if (rand.nextInt(15) == 0) {
                final int i2 = chunkX + rand.nextInt(16) + 8;
                final int i8 = chunkZ + rand.nextInt(16) + 8;
                final int l4 = world.getHeight(i2, i8);
                if (l4 > 63 && l4 < 105) {
                    new WorldGenLakes(Blocks.WATER).generate(world, rand, new BlockPos(i2, l4, i8));
                }
            }

            {
                final int i1 = chunkX + rand.nextInt(16) + 8;
                final int j1 = chunkZ + rand.nextInt(16) + 8;
                final int k1 = world.getHeight(i1, j1);
                if (k1 < 95) {
                    new WorldGenBlob(Blocks.COBBLESTONE.getDefaultState(), 0, BLOB_GROUND, false)
                            .generate(world, rand, new BlockPos(i1, k1, j1));
                }
            }

            if (river > 0.75f) {
                for (int b1 = 0; b1 < 6; b1++) {
                    final int j6 = chunkX + rand.nextInt(16) + 8;
                    final int k10 = chunkZ + rand.nextInt(16) + 8;
                    final int z52 = world.getHeight(j6, k10);
                    final WorldGenerator worldgenerator = rand.nextInt(3) != 0
                            ? new WorldGenSmallPine(1 + rand.nextInt(3), 3 + rand.nextInt(5), 0)
                            : new WorldGenSmallSpruce(rand.nextInt(2));
                    worldgenerator.generate(world, rand, new BlockPos(j6, z52, k10));
                }

                for (int f24 = 0; f24 < 4; f24++) {
                    final int i1 = chunkX + rand.nextInt(16) + 8;
                    final int j1 = chunkZ + rand.nextInt(16) + 8;
                    final int k1 = world.getHeight(i1, j1);
                    if (k1 < 110) {
                        new WorldGenRtgShrub(rand.nextInt(4) + 1, rand.nextInt(2), rand.nextInt(2))
                                .generate(world, rand, new BlockPos(i1, k1, j1));
                    }
                }

                if (rand.nextInt(120) == 0) {
                    final int k21 = chunkX + rand.nextInt(16) + 8;
                    final int j23 = rand.nextInt(60) + 60;
                    final int k24 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenRtgWildWheat(rand.nextInt(3)).generate(world, rand, new BlockPos(k21, j23, k24));
                }
            } else {
                if (rand.nextInt(4) == 0) {
                    final int j6 = chunkX + rand.nextInt(16) + 8;
                    final int k10 = chunkZ + rand.nextInt(16) + 8;
                    final int z52 = world.getHeight(j6, k10);

                    if (z52 < 120) {
                        final WorldGenerator worldgenerator = rand.nextInt(3) != 0
                                ? new WorldGenSmallPine(1 + rand.nextInt(3), 3 + rand.nextInt(5), 0)
                                : new WorldGenSmallSpruce(rand.nextInt(2));
                        worldgenerator.generate(world, rand, new BlockPos(j6, z52, k10));
                    }
                }

                if (rand.nextInt(2) == 0) {
                    final int i1 = chunkX + rand.nextInt(16) + 8;
                    final int j1 = chunkZ + rand.nextInt(16) + 8;
                    final int k1 = world.getHeight(i1, j1);
                    if (k1 < 110) {
                        new WorldGenRtgShrub(rand.nextInt(4) + 1, rand.nextInt(2), rand.nextInt(2))
                                .generate(world, rand, new BlockPos(i1, k1, j1));
                    }
                }
            }

            if (rand.nextInt(20) == 0) {
                final int j16 = chunkX + rand.nextInt(16) + 8;
                final int j18 = rand.nextInt(100);
                final int j21 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenPumpkin().generate(world, rand, new BlockPos(j16, j18, j21));
            }

            for (int f23 = 0; f23 < 4; f23++) {
                final int j15 = chunkX + rand.nextInt(16) + 8;
                final int j17 = rand.nextInt(128);
                final int j20 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenRtgFlowers(new int[] { 9, 0, 3 }).generate(world, rand, new BlockPos(j15, j17, j20));
            }

            for (int l14 = 0; l14 < 8; l14++) {
                final int l19 = chunkX + rand.nextInt(16) + 8;
                final int k22 = rand.nextInt(128);
                final int j24 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenRtgGrass(WorldGenRtgGrass.tallGrass())
                        .generate(world, rand, new BlockPos(l19, k22, j24));
            }
        }));
    }
}
