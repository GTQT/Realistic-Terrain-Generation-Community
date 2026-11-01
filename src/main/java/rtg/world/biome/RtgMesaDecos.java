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
 * RWG 台地/红土族：
 * <ul>
 *   <li>{@link #addMesa} ← {@code red/RealisticBiomeMesa}</li>
 *   <li>{@link #addMesaPlains} ← {@code savanna/RealisticBiomeMesaPlains}</li>
 * </ul>
 *
 * <p>rtgc 的 Mesa / MesaBryce / MesaPlateau* 地形全部是 {@code terrainMesa}（RWG 的
 * {@code TerrainMesa}），与这两个源群系同族；MesaBryce 在 RWG 没有独立条目，按同族推断。
 */
public final class RtgMesaDecos {

    private static final java.util.Collection<Block> BLOB_GROUND = Collections.unmodifiableCollection(
            Arrays.asList(Blocks.GRASS, Blocks.DIRT, Blocks.STONE, Blocks.GRAVEL, Blocks.SAND));

    private RtgMesaDecos() {}

    /** {@code RealisticBiomeMesa.rDecorate}，strength = 1。 */
    public static void addMesa(final RealisticBiomeBase biome) {

        biome.addDeco(new DecoRtgDecorator((world, rand, chunkX, chunkZ, river, rtgWorld) -> {

            {
                final int i1 = chunkX + rand.nextInt(16) + 8;
                final int j1 = chunkZ + rand.nextInt(16) + 8;
                final int k1 = world.getHeight(i1, j1);
                if (k1 < 83) {
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
                for (int b1 = 0; b1 < 3; b1++) {
                    final int j6 = chunkX + rand.nextInt(16) + 8;
                    final int k10 = chunkZ + rand.nextInt(16) + 8;
                    final int z52 = world.getHeight(j6, k10);

                    if (z52 < 90) {
                        new WorldGenShrub(Blocks.LOG.getDefaultState(), Blocks.LEAVES.getDefaultState())
                                .generate(world, rand, new BlockPos(j6, z52, k10));
                    }
                }

                for (int i15 = 0; i15 < 3; i15++) {
                    final int i17 = chunkX + rand.nextInt(16) + 8;
                    final int i20 = 60 + rand.nextInt(40);
                    final int l22 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenDeadBush().generate(world, rand, new BlockPos(i17, i20, l22));
                }

                for (int k18 = 0; k18 < 18; k18++) {
                    final int k21 = chunkX + rand.nextInt(16) + 8;
                    final int j23 = 60 + rand.nextInt(40);
                    final int k24 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenCactus().generate(world, rand, new BlockPos(k21, j23, k24));
                }
            }
        }));
    }

    /** {@code RealisticBiomeMesaPlains.rDecorate}，strength = 1。 */
    public static void addMesaPlains(final RealisticBiomeBase biome) {

        biome.addDeco(new DecoRtgDecorator((world, rand, chunkX, chunkZ, river, rtgWorld) -> {

            {
                final int i1 = chunkX + rand.nextInt(16) + 8;
                final int j1 = chunkZ + rand.nextInt(16) + 8;
                final int k1 = world.getHeight(i1, j1);
                if (k1 < 83) {
                    new WorldGenBlob(Blocks.COBBLESTONE.getDefaultState(), 0, BLOB_GROUND, false)
                            .generate(world, rand, new BlockPos(i1, k1, j1));
                }
            }

            if (river > 0.7f) {
                for (int b33 = 0; b33 < 6; b33++) {
                    final int j6 = chunkX + rand.nextInt(16) + 8;
                    final int k10 = chunkZ + rand.nextInt(16) + 8;
                    final int z52 = world.getHeight(j6, k10);

                    if (z52 < 90) {
                        final WorldGenerator worldgenerator = rand.nextInt(3) != 0
                                ? new WorldGenShrub(Blocks.LOG.getDefaultState(), Blocks.LEAVES.getDefaultState())
                                : new WorldGenSavannah(1, false);
                        worldgenerator.generate(world, rand, new BlockPos(j6, z52, k10));
                    }
                }
            } else if (rand.nextInt(2) == 0) {
                final int j6 = chunkX + rand.nextInt(16) + 8;
                final int k10 = chunkZ + rand.nextInt(16) + 8;
                final int z52 = world.getHeight(j6, k10);

                if (z52 < 90) {
                    final WorldGenerator worldgenerator = rand.nextInt(4) != 0
                            ? new WorldGenShrub(Blocks.LOG.getDefaultState(), Blocks.LEAVES.getDefaultState())
                            : rand.nextInt(12) == 0 ? new WorldGenSavannah(0) : new WorldGenSavannah(1);
                    worldgenerator.generate(world, rand, new BlockPos(j6, z52, k10));
                }
            }

            for (int i15 = 0; i15 < 5; i15++) {
                final int i17 = chunkX + rand.nextInt(16) + 8;
                final int i20 = 64 + rand.nextInt(100);
                final int l22 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenDeadBush().generate(world, rand, new BlockPos(i17, i20, l22));
            }

            for (int k18 = 0; k18 < 15; k18++) {
                final int k21 = chunkX + rand.nextInt(16) + 8;
                final int j23 = 64 + rand.nextInt(80);
                final int k24 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenRtgCacti(false).generate(world, rand, new BlockPos(k21, j23, k24));
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
                new WorldGenRtgGrass(WorldGenRtgGrass.tallGrass())
                        .generate(world, rand, new BlockPos(l19, k22, j24));
            }
        }));
    }
}
