package rtg.world.biome;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.feature.*;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoRtgDecorator;
import rtg.api.world.gen.feature.*;

import java.util.Arrays;
import java.util.Collections;


/**
 * RWG 沙漠/峡谷族：
 * <ul>
 *   <li>{@link #addDesertMountains} ← {@code desert/RealisticBiomeDesertMountains}</li>
 *   <li>{@link #addDuneValley} ← {@code desert/RealisticBiomeDuneValley}</li>
 *   <li>{@link #addCanyon} ← {@code red/RealisticBiomeCanyon}</li>
 * </ul>
 *
 * <p>落点：rtgc 的 `DesertHills` / `DesertM` 地形是 {@code TerrainHilly(230,120,0)}（RWG
 * DesertMountains）；`BOPOutback` 地形是 {@code TerrainDuneValley(300)}；`BOPCrag` 地形是
 * {@code TerrainCanyon}。三者与 RWG 源同族。
 */
public final class RtgDesertDecos {

    private static final java.util.Collection<Block> BLOB_GROUND = Collections.unmodifiableCollection(
            Arrays.asList(Blocks.GRASS, Blocks.DIRT, Blocks.STONE, Blocks.GRAVEL, Blocks.SAND));

    private RtgDesertDecos() {}

    /** {@code RealisticBiomeDesertMountains.rDecorate}，strength = 1。 */
    public static void addDesertMountains(final RealisticBiomeBase biome) {

        biome.addDeco(new DecoRtgDecorator((world, rand, chunkX, chunkZ, river, rtgWorld) -> {

            if (rand.nextInt(2) == 0) {
                final int i1 = chunkX + rand.nextInt(16) + 8;
                final int j1 = chunkZ + rand.nextInt(16) + 8;
                final int k1 = world.getHeight(i1, j1);
                if (k1 < 85) {
                    new WorldGenBlob(Blocks.COBBLESTONE.getDefaultState(), 0, BLOB_GROUND, false)
                            .generate(world, rand, new BlockPos(i1, k1, j1));
                }
            }

            if (river > 0.7f) {
                if (river > 0.86f) {
                    for (int b33 = 0; b33 < 10; b33++) {
                        final int j6 = chunkX + rand.nextInt(16) + 8;
                        final int k10 = chunkZ + rand.nextInt(16) + 8;
                        final int z52 = world.getHeight(j6, k10);

                        if (z52 < 100f || (z52 < 120f && rand.nextInt(10) == 0)) {
                            final WorldGenerator worldgenerator = rand.nextInt(4) != 0
                                    ? new WorldGenShrub(Blocks.LOG.getDefaultState(), Blocks.LEAVES.getDefaultState())
                                    : new WorldGenSavannah(1);
                            worldgenerator.generate(world, rand, new BlockPos(j6, z52, k10));
                        }
                    }
                }

                for (int k18 = 0; k18 < 12; k18++) {
                    final int k21 = chunkX + rand.nextInt(16) + 8;
                    final int j23 = rand.nextInt(160);
                    final int k24 = chunkZ + rand.nextInt(16) + 8;
                    if (j23 < 120f) {
                        new WorldGenRtgCacti(false).generate(world, rand, new BlockPos(k21, j23, k24));
                    }
                }

                for (int f25 = 0; f25 < 2; f25++) {
                    final int i18 = chunkX + rand.nextInt(16) + 8;
                    final int i23 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenReed().generate(world, rand, new BlockPos(i18, 60 + rand.nextInt(8), i23));
                }

                if (rand.nextInt(28) == 0) {
                    final int j16 = chunkX + rand.nextInt(16) + 8;
                    final int j18 = rand.nextInt(128);
                    final int j21 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenPumpkin().generate(world, rand, new BlockPos(j16, j18, j21));
                }

                for (int f23 = 0; f23 < 3; f23++) {
                    final int j15 = chunkX + rand.nextInt(16) + 8;
                    final int j17 = rand.nextInt(128);
                    final int j20 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenRtgFlowers(new int[] { 9, 9, 9, 9, 3, 3, 3, 3, 3, 2, 2, 2, 11, 11, 11 })
                            .generate(world, rand, new BlockPos(j15, j17, j20));
                }

                for (int l14 = 0; l14 < 15; l14++) {
                    final int l19 = chunkX + rand.nextInt(16) + 8;
                    final int k22 = rand.nextInt(128);
                    final int j24 = chunkZ + rand.nextInt(16) + 8;

                    if (rand.nextInt(6) == 0) {
                        new WorldGenRtgGrass(net.minecraft.block.BlockDoublePlant.EnumPlantType.GRASS)
                                .generate(world, rand, new BlockPos(l19, k22, j24));
                    } else {
                        new WorldGenRtgGrass(WorldGenRtgGrass.tallGrass())
                                .generate(world, rand, new BlockPos(l19, k22, j24));
                    }
                }
            }

            for (int k18 = 0; k18 < 12; k18++) {
                final int k21 = chunkX + rand.nextInt(16) + 8;
                final int j23 = rand.nextInt(160);
                final int k24 = chunkZ + rand.nextInt(16) + 8;
                if (j23 < 120f) {
                    new WorldGenRtgCacti(false).generate(world, rand, new BlockPos(k21, j23, k24));
                }
            }

            for (int i15 = 0; i15 < 3; i15++) {
                final int i17 = chunkX + rand.nextInt(16) + 8;
                final int i20 = rand.nextInt(160);
                final int l22 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenDeadBush().generate(world, rand, new BlockPos(i17, i20, l22));
            }
        }));
    }

    /** {@code RealisticBiomeDuneValley.rDecorate}，strength = 1。 */
    public static void addDuneValley(final RealisticBiomeBase biome) {

        biome.addDeco(new DecoRtgDecorator((world, rand, chunkX, chunkZ, river, rtgWorld) -> {

            if (rand.nextInt(2) == 0) {
                final int i1 = chunkX + rand.nextInt(16) + 8;
                final int j1 = chunkZ + rand.nextInt(16) + 8;
                final int k1 = world.getHeight(i1, j1);
                if (k1 < 85) {
                    new WorldGenBlob(Blocks.COBBLESTONE.getDefaultState(), 0, BLOB_GROUND, false)
                            .generate(world, rand, new BlockPos(i1, k1, j1));
                }
            }

            for (int k18 = 0; k18 < 12; k18++) {
                final int k21 = chunkX + rand.nextInt(16) + 8;
                final int j23 = rand.nextInt(160);
                final int k24 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenRtgCacti(false).generate(world, rand, new BlockPos(k21, j23, k24));
            }

            for (int i15 = 0; i15 < 3; i15++) {
                final int i17 = chunkX + rand.nextInt(16) + 8;
                final int i20 = rand.nextInt(160);
                final int l22 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenDeadBush().generate(world, rand, new BlockPos(i17, i20, l22));
            }

            if (river > 0.7f) {
                if (river > 0.86f) {
                    for (int b33 = 0; b33 < 10; b33++) {
                        final int j6 = chunkX + rand.nextInt(16) + 8;
                        final int k10 = chunkZ + rand.nextInt(16) + 8;
                        final int z52 = world.getHeight(j6, k10);

                        if (z52 < 100f || (z52 < 120f && rand.nextInt(10) == 0)) {
                            final WorldGenerator worldgenerator = rand.nextInt(4) != 0
                                    ? new WorldGenShrub(Blocks.LOG.getDefaultState(), Blocks.LEAVES.getDefaultState())
                                    : new WorldGenSavannah(1);
                            worldgenerator.generate(world, rand, new BlockPos(j6, z52, k10));
                        }
                    }
                }

                for (int k18 = 0; k18 < 12; k18++) {
                    final int k21 = chunkX + rand.nextInt(16) + 8;
                    final int j23 = rand.nextInt(160);
                    final int k24 = chunkZ + rand.nextInt(16) + 8;
                    if (j23 < 120f) {
                        new WorldGenRtgCacti(false).generate(world, rand, new BlockPos(k21, j23, k24));
                    }
                }

                for (int f25 = 0; f25 < 2; f25++) {
                    final int i18 = chunkX + rand.nextInt(16) + 8;
                    final int i23 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenReed().generate(world, rand, new BlockPos(i18, 60 + rand.nextInt(8), i23));
                }

                if (rand.nextInt(28) == 0) {
                    final int j16 = chunkX + rand.nextInt(16) + 8;
                    final int j18 = rand.nextInt(128);
                    final int j21 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenPumpkin().generate(world, rand, new BlockPos(j16, j18, j21));
                }

                for (int f23 = 0; f23 < 3; f23++) {
                    final int j15 = chunkX + rand.nextInt(16) + 8;
                    final int j17 = rand.nextInt(128);
                    final int j20 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenRtgFlowers(new int[] { 9, 9, 9, 9, 3, 3, 3, 3, 3, 2, 2, 2, 11, 11, 11 })
                            .generate(world, rand, new BlockPos(j15, j17, j20));
                }

                for (int l14 = 0; l14 < 15; l14++) {
                    final int l19 = chunkX + rand.nextInt(16) + 8;
                    final int k22 = rand.nextInt(128);
                    final int j24 = chunkZ + rand.nextInt(16) + 8;

                    if (rand.nextInt(6) == 0) {
                        new WorldGenRtgGrass(net.minecraft.block.BlockDoublePlant.EnumPlantType.GRASS)
                                .generate(world, rand, new BlockPos(l19, k22, j24));
                    } else {
                        new WorldGenRtgGrass(WorldGenRtgGrass.tallGrass())
                                .generate(world, rand, new BlockPos(l19, k22, j24));
                    }
                }
            }
        }));
    }

    /** {@code RealisticBiomeCanyon.rDecorate}，strength = 1。 */
    public static void addCanyon(final RealisticBiomeBase biome) {

        biome.addDeco(new DecoRtgDecorator((world, rand, chunkX, chunkZ, river, rtgWorld) -> {

            {
                final int i1 = chunkX + rand.nextInt(16) + 8;
                final int j1 = chunkZ + rand.nextInt(16) + 8;
                final int k1 = world.getHeight(i1, j1);
                if (k1 < 70) {
                    new WorldGenBlob(Blocks.MOSSY_COBBLESTONE.getDefaultState(), 0, BLOB_GROUND, false)
                            .generate(world, rand, new BlockPos(i1, k1, j1));
                }
            }

            if (river > 0.8f) {
                for (int b1 = 0; b1 < 10; b1++) {
                    final int j6 = chunkX + rand.nextInt(16) + 8;
                    final int k10 = chunkZ + rand.nextInt(16) + 8;
                    final int z52 = world.getHeight(j6, k10);
                    new WorldGenShrub(Blocks.LOG.getDefaultState(), Blocks.LEAVES.getDefaultState())
                            .generate(world, rand, new BlockPos(j6, z52, k10));
                }
            } else {
                for (int b1 = 0; b1 < 5; b1++) {
                    final int j6 = chunkX + rand.nextInt(16) + 8;
                    final int k10 = chunkZ + rand.nextInt(16) + 8;
                    final int z52 = world.getHeight(j6, k10);
                    new WorldGenShrub(Blocks.LOG.getDefaultState(), Blocks.LEAVES.getDefaultState())
                            .generate(world, rand, new BlockPos(j6, z52, k10));
                }

                if (rand.nextInt(3) == 0) {
                    final int i18 = chunkX + rand.nextInt(16) + 8;
                    final int i23 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenReed().generate(world, rand, new BlockPos(i18, 60 + rand.nextInt(8), i23));
                }

                if (rand.nextInt(28) == 0) {
                    final int j16 = chunkX + rand.nextInt(16) + 8;
                    final int j18 = rand.nextInt(128);
                    final int j21 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenPumpkin().generate(world, rand, new BlockPos(j16, j18, j21));
                }

                for (int i15 = 0; i15 < 5; i15++) {
                    final int i17 = chunkX + rand.nextInt(16) + 8;
                    final int i20 = rand.nextInt(160);
                    final int l22 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenDeadBush().generate(world, rand, new BlockPos(i17, i20, l22));
                }

                for (int k18 = 0; k18 < 25; k18++) {
                    final int k21 = chunkX + rand.nextInt(16) + 8;
                    final int j23 = rand.nextInt(160);
                    final int k24 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenRtgCacti(true).generate(world, rand, new BlockPos(k21, j23, k24));
                }
            }
        }));
    }

    /** {@code RealisticBiomeDesert.rDecorate}，strength = 1（原 VanillaDesert 内联实现，现共享给 BOPColdDesert）。 */
    public static void addDesert(final RealisticBiomeBase biome) {

        biome.addDeco(new DecoRtgDecorator((world, rand, chunkX, chunkZ, river, rtgWorld) -> {

            if (rand.nextInt(4) == 0) {
                final int i1 = chunkX + rand.nextInt(16) + 8;
                final int j1 = chunkZ + rand.nextInt(16) + 8;
                final int k1 = world.getHeight(i1, j1);
                if (k1 < 85) {
                    new WorldGenBlob(Blocks.COBBLESTONE.getDefaultState(), 0, BLOB_GROUND, false)
                            .generate(world, rand, new BlockPos(i1, k1, j1));
                }
            }

            if (river > 0.7f) {
                if (river > 0.86f) {
                    for (int b33 = 0; b33 < 10; b33++) {
                        final int j6 = chunkX + rand.nextInt(16) + 8;
                        final int k10 = chunkZ + rand.nextInt(16) + 8;
                        final int z52 = world.getHeight(j6, k10);

                        if (z52 < 100f || (z52 < 120f && rand.nextInt(10) == 0)) {
                            final WorldGenerator worldgenerator = rand.nextInt(4) != 0
                                    ? new WorldGenShrub(Blocks.LOG.getDefaultState(), Blocks.LEAVES.getDefaultState())
                                    : new WorldGenSavannah(1);
                            worldgenerator.generate(world, rand, new BlockPos(j6, z52, k10));
                        }
                    }
                }

                for (int k18 = 0; k18 < 12; k18++) {
                    final int k21 = chunkX + rand.nextInt(16) + 8;
                    final int j23 = rand.nextInt(160);
                    final int k24 = chunkZ + rand.nextInt(16) + 8;
                    if (j23 < 120f) {
                        new WorldGenRtgCacti(false).generate(world, rand, new BlockPos(k21, j23, k24));
                    }
                }

                for (int f25 = 0; f25 < 2; f25++) {
                    final int i18 = chunkX + rand.nextInt(16) + 8;
                    final int i23 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenReed().generate(world, rand, new BlockPos(i18, 60 + rand.nextInt(8), i23));
                }

                if (rand.nextInt(28) == 0) {
                    final int j16 = chunkX + rand.nextInt(16) + 8;
                    final int j18 = rand.nextInt(128);
                    final int j21 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenPumpkin().generate(world, rand, new BlockPos(j16, j18, j21));
                }

                for (int f23 = 0; f23 < 3; f23++) {
                    final int j15 = chunkX + rand.nextInt(16) + 8;
                    final int j17 = rand.nextInt(128);
                    final int j20 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenRtgFlowers(new int[] { 9, 9, 9, 9, 3, 3, 3, 3, 3, 2, 2, 2, 11, 11, 11 })
                            .generate(world, rand, new BlockPos(j15, j17, j20));
                }

                for (int l14 = 0; l14 < 15; l14++) {
                    final int l19 = chunkX + rand.nextInt(16) + 8;
                    final int k22 = rand.nextInt(128);
                    final int j24 = chunkZ + rand.nextInt(16) + 8;

                    if (rand.nextInt(6) == 0) {
                        new WorldGenRtgGrass(net.minecraft.block.BlockDoublePlant.EnumPlantType.GRASS)
                                .generate(world, rand, new BlockPos(l19, k22, j24));
                    } else {
                        new WorldGenRtgGrass(WorldGenRtgGrass.tallGrass())
                                .generate(world, rand, new BlockPos(l19, k22, j24));
                    }
                }
            }

            for (int k18 = 0; k18 < 5; k18++) {
                final int k21 = chunkX + rand.nextInt(16) + 8;
                final int j23 = 64 + rand.nextInt(64);
                final int k24 = chunkZ + rand.nextInt(16) + 8;
                if (j23 < 120f) {
                    new WorldGenRtgCacti(false).generate(world, rand, new BlockPos(k21, j23, k24));
                }
            }

            for (int i15 = 0; i15 < 1; i15++) {
                final int i17 = chunkX + rand.nextInt(16) + 8;
                final int i20 = 64 + rand.nextInt(64);
                final int l22 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenDeadBush().generate(world, rand, new BlockPos(i17, i20, l22));
            }
        }));
    }

    /** {@code RealisticBiomeOasis.rDecorate}，strength = 1。 */
    public static void addOasis(final RealisticBiomeBase biome) {

        biome.addDeco(new DecoRtgDecorator((world, rand, chunkX, chunkZ, river, rtgWorld) -> {

            if (rand.nextInt(2) == 0) {
                final int i1 = chunkX + rand.nextInt(16) + 8;
                final int j1 = chunkZ + rand.nextInt(16) + 8;
                final int k1 = world.getHeight(i1, j1);
                if (k1 < 85) {
                    new WorldGenBlob(Blocks.COBBLESTONE.getDefaultState(), 0, BLOB_GROUND, false)
                            .generate(world, rand, new BlockPos(i1, k1, j1));
                }
            }

            for (int b33 = 0; b33 < 4; b33++) {
                final int j6 = chunkX + rand.nextInt(16) + 8;
                final int k10 = chunkZ + rand.nextInt(16) + 8;
                final int z52 = world.getHeight(j6, k10);

                if (z52 < 71f) {
                    final WorldGenerator worldgenerator = rand.nextInt(4) != 0
                            ? new WorldGenShrub(Blocks.LOG.getDefaultState(), Blocks.LEAVES.getDefaultState())
                            : rand.nextInt(6) == 0
                                    ? new rtg.api.world.gen.feature.WorldGenPalm()
                                    : new WorldGenSavannah(1, false);
                    worldgenerator.generate(world, rand, new BlockPos(j6, z52, k10));
                }
            }

            for (int k18 = 0; k18 < 12; k18++) {
                final int k21 = chunkX + rand.nextInt(16) + 8;
                final int j23 = rand.nextInt(160);
                final int k24 = chunkZ + rand.nextInt(16) + 8;
                if (j23 < 120f) {
                    new WorldGenRtgCacti(false).generate(world, rand, new BlockPos(k21, j23, k24));
                }
            }

            for (int f25 = 0; f25 < 2; f25++) {
                final int i18 = chunkX + rand.nextInt(16) + 8;
                final int i23 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenReed().generate(world, rand, new BlockPos(i18, 60 + rand.nextInt(8), i23));
            }

            if (rand.nextInt(28) == 0) {
                final int j16 = chunkX + rand.nextInt(16) + 8;
                final int j18 = rand.nextInt(128);
                final int j21 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenPumpkin().generate(world, rand, new BlockPos(j16, j18, j21));
            }

            for (int f23 = 0; f23 < 3; f23++) {
                final int j15 = chunkX + rand.nextInt(16) + 8;
                final int j17 = rand.nextInt(128);
                final int j20 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenRtgFlowers(new int[] { 9, 9, 9, 9, 3, 3, 3, 3, 3, 2, 2, 2, 11, 11, 11 })
                        .generate(world, rand, new BlockPos(j15, j17, j20));
            }

            for (int l14 = 0; l14 < 15; l14++) {
                final int l19 = chunkX + rand.nextInt(16) + 8;
                final int k22 = rand.nextInt(128);
                final int j24 = chunkZ + rand.nextInt(16) + 8;

                if (rand.nextInt(6) == 0) {
                    new WorldGenRtgGrass(net.minecraft.block.BlockDoublePlant.EnumPlantType.GRASS)
                            .generate(world, rand, new BlockPos(l19, k22, j24));
                } else {
                    new WorldGenRtgGrass(WorldGenRtgGrass.tallGrass())
                            .generate(world, rand, new BlockPos(l19, k22, j24));
                }
            }

            for (int k18 = 0; k18 < 12; k18++) {
                final int k21 = chunkX + rand.nextInt(16) + 8;
                final int j23 = rand.nextInt(160);
                final int k24 = chunkZ + rand.nextInt(16) + 8;
                if (j23 < 120f) {
                    new WorldGenRtgCacti(false).generate(world, rand, new BlockPos(k21, j23, k24));
                }
            }

            for (int i15 = 0; i15 < 3; i15++) {
                final int i17 = chunkX + rand.nextInt(16) + 8;
                final int i20 = rand.nextInt(160);
                final int l22 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenDeadBush().generate(world, rand, new BlockPos(i17, i20, l22));
            }
        }));
    }

    /** {@code RealisticBiomeRedOasis.rDecorate}，strength = 1。 */
    public static void addRedOasis(final RealisticBiomeBase biome) {

        biome.addDeco(new DecoRtgDecorator((world, rand, chunkX, chunkZ, river, rtgWorld) -> {

            if (rand.nextInt(2) == 0) {
                final int i1 = chunkX + rand.nextInt(16) + 8;
                final int j1 = chunkZ + rand.nextInt(16) + 8;
                final int k1 = world.getHeight(i1, j1);
                if (k1 < 85) {
                    new WorldGenBlob(Blocks.COBBLESTONE.getDefaultState(), 0, BLOB_GROUND, false)
                            .generate(world, rand, new BlockPos(i1, k1, j1));
                }
            }

            for (int b33 = 0; b33 < 3; b33++) {
                final int j6 = chunkX + rand.nextInt(16) + 8;
                final int k10 = chunkZ + rand.nextInt(16) + 8;
                final int z52 = world.getHeight(j6, k10);

                if (z52 < 100f || (z52 < 120f && rand.nextInt(10) == 0)) {
                    final WorldGenerator worldgenerator = rand.nextInt(4) != 0
                            ? new WorldGenShrub(Blocks.LOG.getDefaultState(), Blocks.LEAVES.getDefaultState())
                            : new WorldGenSavannah(1, false);
                    worldgenerator.generate(world, rand, new BlockPos(j6, z52, k10));
                }
            }

            for (int k18 = 0; k18 < 12; k18++) {
                final int k21 = chunkX + rand.nextInt(16) + 8;
                final int j23 = rand.nextInt(160);
                final int k24 = chunkZ + rand.nextInt(16) + 8;
                if (j23 < 120f) {
                    new WorldGenRtgCacti(false).generate(world, rand, new BlockPos(k21, j23, k24));
                }
            }

            for (int f25 = 0; f25 < 2; f25++) {
                final int i18 = chunkX + rand.nextInt(16) + 8;
                final int i23 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenReed().generate(world, rand, new BlockPos(i18, 60 + rand.nextInt(8), i23));
            }

            if (rand.nextInt(28) == 0) {
                final int j16 = chunkX + rand.nextInt(16) + 8;
                final int j18 = rand.nextInt(128);
                final int j21 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenPumpkin().generate(world, rand, new BlockPos(j16, j18, j21));
            }

            for (int f23 = 0; f23 < 3; f23++) {
                final int j15 = chunkX + rand.nextInt(16) + 8;
                final int j17 = rand.nextInt(128);
                final int j20 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenRtgFlowers(new int[] { 9, 9, 9, 9, 3, 3, 3, 3, 3, 2, 2, 2, 11, 11, 11 })
                        .generate(world, rand, new BlockPos(j15, j17, j20));
            }

            for (int l14 = 0; l14 < 15; l14++) {
                final int l19 = chunkX + rand.nextInt(16) + 8;
                final int k22 = rand.nextInt(128);
                final int j24 = chunkZ + rand.nextInt(16) + 8;

                if (rand.nextInt(6) == 0) {
                    new WorldGenRtgGrass(net.minecraft.block.BlockDoublePlant.EnumPlantType.GRASS)
                            .generate(world, rand, new BlockPos(l19, k22, j24));
                } else {
                    new WorldGenRtgGrass(WorldGenRtgGrass.tallGrass())
                            .generate(world, rand, new BlockPos(l19, k22, j24));
                }
            }

            for (int k18 = 0; k18 < 12; k18++) {
                final int k21 = chunkX + rand.nextInt(16) + 8;
                final int j23 = rand.nextInt(160);
                final int k24 = chunkZ + rand.nextInt(16) + 8;
                if (j23 < 120f) {
                    new WorldGenRtgCacti(false).generate(world, rand, new BlockPos(k21, j23, k24));
                }
            }

            for (int i15 = 0; i15 < 3; i15++) {
                final int i17 = chunkX + rand.nextInt(16) + 8;
                final int i20 = rand.nextInt(160);
                final int l22 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenDeadBush().generate(world, rand, new BlockPos(i17, i20, l22));
            }
        }));
    }

    /** {@code RealisticBiomeSavannaDunes.rDecorate}，strength = 1。 */
    public static void addSavannaDunes(final RealisticBiomeBase biome) {

        biome.addDeco(new DecoRtgDecorator((world, rand, chunkX, chunkZ, river, rtgWorld) -> {

            if (rand.nextInt(2) == 0) {
                final int i1 = chunkX + rand.nextInt(16) + 8;
                final int j1 = chunkZ + rand.nextInt(16) + 8;
                final int k1 = world.getHeight(i1, j1);
                if (k1 < 85) {
                    new WorldGenBlob(Blocks.COBBLESTONE.getDefaultState(), 0, BLOB_GROUND, false)
                            .generate(world, rand, new BlockPos(i1, k1, j1));
                }
            }

            if (river > 0.7f) {
                if (river > 0.86f) {
                    for (int b33 = 0; b33 < 10; b33++) {
                        final int j6 = chunkX + rand.nextInt(16) + 8;
                        final int k10 = chunkZ + rand.nextInt(16) + 8;
                        final int z52 = world.getHeight(j6, k10);

                        if (z52 < 100f || (z52 < 120f && rand.nextInt(10) == 0)) {
                            final WorldGenerator worldgenerator = rand.nextInt(4) != 0
                                    ? new WorldGenShrub(Blocks.LOG.getDefaultState(), Blocks.LEAVES.getDefaultState())
                                    : new WorldGenSavannah(1);
                            worldgenerator.generate(world, rand, new BlockPos(j6, z52, k10));
                        }
                    }
                }

                for (int k18 = 0; k18 < 12; k18++) {
                    final int k21 = chunkX + rand.nextInt(16) + 8;
                    final int j23 = rand.nextInt(160);
                    final int k24 = chunkZ + rand.nextInt(16) + 8;
                    if (j23 < 120f) {
                        new WorldGenRtgCacti(false).generate(world, rand, new BlockPos(k21, j23, k24));
                    }
                }

                for (int f25 = 0; f25 < 2; f25++) {
                    final int i18 = chunkX + rand.nextInt(16) + 8;
                    final int i23 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenReed().generate(world, rand, new BlockPos(i18, 60 + rand.nextInt(8), i23));
                }

                if (rand.nextInt(28) == 0) {
                    final int j16 = chunkX + rand.nextInt(16) + 8;
                    final int j18 = rand.nextInt(128);
                    final int j21 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenPumpkin().generate(world, rand, new BlockPos(j16, j18, j21));
                }

                for (int f23 = 0; f23 < 3; f23++) {
                    final int j15 = chunkX + rand.nextInt(16) + 8;
                    final int j17 = rand.nextInt(128);
                    final int j20 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenRtgFlowers(new int[] { 9, 9, 9, 9, 3, 3, 3, 3, 3, 2, 2, 2, 11, 11, 11 })
                            .generate(world, rand, new BlockPos(j15, j17, j20));
                }

                for (int l14 = 0; l14 < 15; l14++) {
                    final int l19 = chunkX + rand.nextInt(16) + 8;
                    final int k22 = rand.nextInt(128);
                    final int j24 = chunkZ + rand.nextInt(16) + 8;

                    if (rand.nextInt(6) == 0) {
                        new WorldGenRtgGrass(net.minecraft.block.BlockDoublePlant.EnumPlantType.GRASS)
                                .generate(world, rand, new BlockPos(l19, k22, j24));
                    } else {
                        new WorldGenRtgGrass(WorldGenRtgGrass.tallGrass())
                                .generate(world, rand, new BlockPos(l19, k22, j24));
                    }
                }
            } else {
                if (rand.nextInt(22) == 0) {
                    final int j16 = chunkX + rand.nextInt(16) + 8;
                    final int j18 = rand.nextInt(128);
                    final int j21 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenPumpkin().generate(world, rand, new BlockPos(j16, j18, j21));
                }

                if (rand.nextInt(3) == 0) {
                    final int i18 = chunkX + rand.nextInt(16) + 8;
                    final int i23 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenReed().generate(world, rand, new BlockPos(i18, 60 + rand.nextInt(8), i23));
                }

                for (int f23 = 0; f23 < 3; f23++) {
                    final int j15 = chunkX + rand.nextInt(16) + 8;
                    final int j17 = rand.nextInt(128);
                    final int j20 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenRtgFlowers(new int[] { 9, 9, 9, 9, 3, 3, 3, 3, 3, 2, 2, 2, 11, 11, 11 })
                            .generate(world, rand, new BlockPos(j15, j17, j20));
                }

                if (rand.nextInt(100) == 0) {
                    final int k21 = chunkX + rand.nextInt(16) + 8;
                    final int j23 = rand.nextInt(60) + 60;
                    final int k24 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenRtgWildWheat(rand.nextInt(3)).generate(world, rand, new BlockPos(k21, j23, k24));
                }

                if (rand.nextInt(30) == 0) {
                    final int j6 = chunkX + rand.nextInt(16) + 8;
                    final int k10 = chunkZ + rand.nextInt(16) + 8;
                    final int z52 = world.getHeight(j6, k10);

                    if (z52 < 85) {
                        new WorldGenSavannah(1).generate(world, rand, new BlockPos(j6, z52, k10));
                    }
                }

                for (int k18 = 0; k18 < 36; k18++) {
                    final int k21 = chunkX + rand.nextInt(16) + 8;
                    final int j23 = rand.nextInt(160);
                    final int k24 = chunkZ + rand.nextInt(16) + 8;
                    if (j23 < 90) {
                        new WorldGenRtgCacti(false).generate(world, rand, new BlockPos(k21, j23, k24));
                    }
                }

                for (int l14 = 0; l14 < 8; l14++) {
                    final int l19 = chunkX + rand.nextInt(16) + 8;
                    final int k22 = 60 + rand.nextInt(40);
                    final int j24 = chunkZ + rand.nextInt(16) + 8;

                    if (rand.nextInt(3) == 0) {
                        new WorldGenRtgGrass(net.minecraft.block.BlockDoublePlant.EnumPlantType.GRASS)
                                .generate(world, rand, new BlockPos(l19, k22, j24));
                    } else {
                        new WorldGenRtgGrass(WorldGenRtgGrass.tallGrass())
                                .generate(world, rand, new BlockPos(l19, k22, j24));
                    }
                }
            }
        }));
    }

    /** {@code RealisticBiomeCanyonForest.rDecorate}，strength = 1。 */
    public static void addCanyonForest(final RealisticBiomeBase biome) {

        biome.addDeco(new DecoRtgDecorator((world, rand, chunkX, chunkZ, river, rtgWorld) -> {

            {
                final int i1 = chunkX + rand.nextInt(16) + 8;
                final int j1 = chunkZ + rand.nextInt(16) + 8;
                final int k1 = world.getHeight(i1, j1);
                if (k1 < 70) {
                    new WorldGenBlob(Blocks.MOSSY_COBBLESTONE.getDefaultState(), 0, BLOB_GROUND, false)
                            .generate(world, rand, new BlockPos(i1, k1, j1));
                }
            }

            if (river > 0.7f) {
                for (int b1 = 0; b1 < 10; b1++) {
                    final int j6 = chunkX + rand.nextInt(16) + 8;
                    final int k10 = chunkZ + rand.nextInt(16) + 8;
                    final int z52 = world.getHeight(j6, k10);
                    new WorldGenShrub(Blocks.LOG.getDefaultState(), Blocks.LEAVES.getDefaultState())
                            .generate(world, rand, new BlockPos(j6, z52, k10));
                }

                if (rand.nextInt(3) == 0) {
                    final int i18 = chunkX + rand.nextInt(16) + 8;
                    final int i23 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenReed().generate(world, rand, new BlockPos(i18, 60 + rand.nextInt(8), i23));
                }

                for (int b33 = 0; b33 < 7; b33++) {
                    final int j6 = chunkX + rand.nextInt(16) + 8;
                    final int k10 = chunkZ + rand.nextInt(16) + 8;
                    final int z52 = world.getHeight(j6, k10);

                    if (z52 < 80) {
                        final WorldGenerator worldgenerator = rand.nextInt(3) == 0
                                ? new WorldGenShrub(Blocks.LOG.getDefaultState(), Blocks.LEAVES.getDefaultState())
                                : new WorldGenSavannah(2, false);
                        worldgenerator.generate(world, rand, new BlockPos(j6, z52, k10));
                    } else if (z52 < 111) {
                        final WorldGenerator worldgenerator = rand.nextInt(3) == 0
                                ? new WorldGenShrub(Blocks.LOG.getDefaultState(), Blocks.LEAVES.getDefaultState())
                                : new WorldGenSavannah(1, false);
                        worldgenerator.generate(world, rand, new BlockPos(j6, z52, k10));
                    }
                }

                for (int f23 = 0; f23 < 3; f23++) {
                    final int j15 = chunkX + rand.nextInt(16) + 8;
                    final int j17 = rand.nextInt(128);
                    final int j20 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenRtgFlowers(new int[] { 9, 9, 9, 9, 3, 3, 3, 3, 3, 2, 2, 2, 11, 11, 11 })
                            .generate(world, rand, new BlockPos(j15, j17, j20));
                }

                for (int l14 = 0; l14 < 8; l14++) {
                    final int l19 = chunkX + rand.nextInt(16) + 8;
                    final int k22 = 60 + rand.nextInt(70);
                    final int j24 = chunkZ + rand.nextInt(16) + 8;

                    if (rand.nextInt(3) == 0) {
                        new WorldGenRtgGrass(net.minecraft.block.BlockDoublePlant.EnumPlantType.GRASS)
                                .generate(world, rand, new BlockPos(l19, k22, j24));
                    } else {
                        new WorldGenRtgGrass(WorldGenRtgGrass.tallGrass())
                                .generate(world, rand, new BlockPos(l19, k22, j24));
                    }
                }
            } else {
                for (int b33 = 0; b33 < 6; b33++) {
                    final int j6 = chunkX + rand.nextInt(16) + 8;
                    final int k10 = chunkZ + rand.nextInt(16) + 8;
                    final int z52 = world.getHeight(j6, k10);

                    if (z52 > 127) {
                        new WorldGenShrub(Blocks.LOG.getDefaultState(), Blocks.LEAVES.getDefaultState())
                                .generate(world, rand, new BlockPos(j6, z52, k10));
                    } else if (z52 < 127) {
                        final WorldGenerator worldgenerator = rand.nextInt(3) != 0
                                ? new WorldGenShrub(Blocks.LOG.getDefaultState(), Blocks.LEAVES.getDefaultState())
                                : new WorldGenSavannah(1, false);
                        worldgenerator.generate(world, rand, new BlockPos(j6, z52, k10));
                    }
                }

                for (int i15 = 0; i15 < 5; i15++) {
                    final int i17 = chunkX + rand.nextInt(16) + 8;
                    final int i20 = 64 + rand.nextInt(100);
                    final int l22 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenDeadBush().generate(world, rand, new BlockPos(i17, i20, l22));
                }

                for (int k18 = 0; k18 < 25; k18++) {
                    final int k21 = chunkX + rand.nextInt(16) + 8;
                    final int j23 = 64 + rand.nextInt(100);
                    final int k24 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenRtgCacti(true).generate(world, rand, new BlockPos(k21, j23, k24));
                }

                for (int f23 = 0; f23 < 3; f23++) {
                    final int j15 = chunkX + rand.nextInt(16) + 8;
                    final int j17 = rand.nextInt(128);
                    final int j20 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenRtgFlowers(new int[] { 9, 9, 9, 9, 3, 3, 3, 3, 3, 2, 2, 2, 11, 11, 11 })
                            .generate(world, rand, new BlockPos(j15, j17, j20));
                }

                for (int l14 = 0; l14 < 8; l14++) {
                    final int l19 = chunkX + rand.nextInt(16) + 8;
                    final int k22 = 60 + rand.nextInt(70);
                    final int j24 = chunkZ + rand.nextInt(16) + 8;

                    if (rand.nextInt(3) == 0) {
                        new WorldGenRtgGrass(net.minecraft.block.BlockDoublePlant.EnumPlantType.GRASS)
                                .generate(world, rand, new BlockPos(l19, k22, j24));
                    } else {
                        new WorldGenRtgGrass(WorldGenRtgGrass.tallGrass())
                                .generate(world, rand, new BlockPos(l19, k22, j24));
                    }
                }
            }
        }));
    }

    /**
     * {@code RealisticBiomeStoneMountains(.Cactus).rDecorate}，strength = 1。
     *
     * @param cactus {@code true} = Cactus 变体：干地 70×仙人掌（y=64+rand64）、a=6 减；
     *               {@code false} = 基准：10×仙人掌（y=rand160）、a=3
     */
    public static void addStoneMountains(final RealisticBiomeBase biome, final boolean cactus) {

        biome.addDeco(new DecoRtgDecorator((world, rand, chunkX, chunkZ, river, rtgWorld) -> {

            {
                final int i1 = chunkX + rand.nextInt(16) + 8;
                final int j1 = chunkZ + rand.nextInt(16) + 8;
                final int k1 = world.getHeight(i1, j1);
                if (k1 < 80) {
                    new WorldGenBlob(Blocks.COBBLESTONE.getDefaultState(), 0, BLOB_GROUND, false)
                            .generate(world, rand, new BlockPos(i1, k1, j1));
                }
            }

            if (river > 0.7f) {
                if (river > 0.86f) {
                    for (int b33 = 0; b33 < 10; b33++) {
                        final int j6 = chunkX + rand.nextInt(16) + 8;
                        final int k10 = chunkZ + rand.nextInt(16) + 8;
                        final int z52 = world.getHeight(j6, k10);

                        if (z52 < 100f || (z52 < 120f && rand.nextInt(10) == 0)) {
                            final WorldGenerator worldgenerator = rand.nextInt(4) != 0
                                    ? new WorldGenShrub(Blocks.LOG.getDefaultState(), Blocks.LEAVES.getDefaultState())
                                    : new WorldGenSavannah(1);
                            worldgenerator.generate(world, rand, new BlockPos(j6, z52, k10));
                        }
                    }
                }

                for (int k18 = 0; k18 < 12; k18++) {
                    final int k21 = chunkX + rand.nextInt(16) + 8;
                    final int j23 = rand.nextInt(160);
                    final int k24 = chunkZ + rand.nextInt(16) + 8;
                    if (j23 < 120f) {
                        new WorldGenRtgCacti(false).generate(world, rand, new BlockPos(k21, j23, k24));
                    }
                }

                for (int f25 = 0; f25 < 2; f25++) {
                    final int i18 = chunkX + rand.nextInt(16) + 8;
                    final int i23 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenReed().generate(world, rand, new BlockPos(i18, 60 + rand.nextInt(8), i23));
                }

                if (rand.nextInt(28) == 0) {
                    final int j16 = chunkX + rand.nextInt(16) + 8;
                    final int j18 = rand.nextInt(128);
                    final int j21 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenPumpkin().generate(world, rand, new BlockPos(j16, j18, j21));
                }

                for (int f23 = 0; f23 < 3; f23++) {
                    final int j15 = chunkX + rand.nextInt(16) + 8;
                    final int j17 = rand.nextInt(128);
                    final int j20 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenRtgFlowers(new int[] { 9, 9, 9, 9, 3, 3, 3, 3, 3, 2, 2, 2, 11, 11, 11 })
                            .generate(world, rand, new BlockPos(j15, j17, j20));
                }

                for (int l14 = 0; l14 < 15; l14++) {
                    final int l19 = chunkX + rand.nextInt(16) + 8;
                    final int k22 = rand.nextInt(128);
                    final int j24 = chunkZ + rand.nextInt(16) + 8;

                    if (rand.nextInt(3) == 0) {
                        new WorldGenRtgGrass(net.minecraft.block.BlockDoublePlant.EnumPlantType.GRASS)
                                .generate(world, rand, new BlockPos(l19, k22, j24));
                    } else {
                        new WorldGenRtgGrass(WorldGenRtgGrass.tallGrass())
                                .generate(world, rand, new BlockPos(l19, k22, j24));
                    }
                }
            } else {
                final int a = (cactus ? 6 : 3)
                        - (int) (rtgWorld.simplexInstance(0).noise2f(chunkX / 100f, chunkZ / 100f) * 10);
                if (a < 1 || rand.nextInt(a) == 0) {
                    final int j6 = chunkX + rand.nextInt(16) + 8;
                    final int k10 = chunkZ + rand.nextInt(16) + 8;
                    final int z52 = world.getHeight(j6, k10);

                    if (z52 < 100f || (z52 < 120f && rand.nextInt(10) == 0)) {
                        final WorldGenerator worldgenerator = rand.nextInt(14) != 0
                                ? new WorldGenShrub(Blocks.LOG.getDefaultState(), Blocks.LEAVES.getDefaultState())
                                : new WorldGenSavannah(1);
                        worldgenerator.generate(world, rand, new BlockPos(j6, z52, k10));
                    }
                }

                for (int k18 = 0; k18 < (cactus ? 70 : 10); k18++) {
                    final int k21 = chunkX + rand.nextInt(16) + 8;
                    final int j23 = cactus ? 64 + rand.nextInt(64) : rand.nextInt(160);
                    final int k24 = chunkZ + rand.nextInt(16) + 8;
                    if (j23 < 120f) {
                        new WorldGenRtgCacti(false).generate(world, rand, new BlockPos(k21, j23, k24));
                    }
                }

                if (rand.nextInt(3) == 0) {
                    final int i18 = chunkX + rand.nextInt(16) + 8;
                    final int i23 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenReed().generate(world, rand, new BlockPos(i18, 60 + rand.nextInt(8), i23));
                }

                if (rand.nextInt(28) == 0) {
                    final int j16 = chunkX + rand.nextInt(16) + 8;
                    final int j18 = rand.nextInt(128);
                    final int j21 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenPumpkin().generate(world, rand, new BlockPos(j16, j18, j21));
                }

                for (int f23 = 0; f23 < 3; f23++) {
                    final int j15 = chunkX + rand.nextInt(16) + 8;
                    final int j17 = rand.nextInt(128);
                    final int j20 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenRtgFlowers(new int[] { 9, 9, 9, 9, 3, 3, 3, 3, 3, 2, 2, 2, 11, 11, 11 })
                            .generate(world, rand, new BlockPos(j15, j17, j20));
                }

                for (int l14 = 0; l14 < 15; l14++) {
                    final int l19 = chunkX + rand.nextInt(16) + 8;
                    final int k22 = rand.nextInt(128);
                    final int j24 = chunkZ + rand.nextInt(16) + 8;

                    if (rand.nextInt(3) == 0) {
                        new WorldGenRtgGrass(net.minecraft.block.BlockDoublePlant.EnumPlantType.GRASS)
                                .generate(world, rand, new BlockPos(l19, k22, j24));
                    } else {
                        new WorldGenRtgGrass(WorldGenRtgGrass.tallGrass())
                                .generate(world, rand, new BlockPos(l19, k22, j24));
                    }
                }
            }
        }));
    }
}
