package rtg.world.biome;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.feature.WorldGenPumpkin;
import net.minecraft.world.gen.feature.WorldGenReed;
import net.minecraft.world.gen.feature.WorldGenShrub;
import net.minecraft.world.gen.feature.WorldGenerator;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoRtgDecorator;
import rtg.api.world.gen.feature.*;

import java.util.Arrays;
import java.util.Collections;


/**
 * RWG 稀树森林族（{@code savanna/RealisticBiomeSavannaForest}）。
 *
 * <p>用于 rtgc 的稀树变体（M / Plateau）：RWG 没有 mutated/plateau 概念，变体按“同族”
 * 推断接这一份。它的三段分支比 {@code RealisticBiomeSavanna} 多了一条 150 格噪声带
 * （{@code n ∈ (-0.25, 0.25)}），且仙人掌只在噪声高的一侧加密。
 */
public final class RtgSavannaDecos {

    private static final java.util.Collection<Block> BLOB_GROUND = Collections.unmodifiableCollection(
            Arrays.asList(Blocks.GRASS, Blocks.DIRT, Blocks.STONE, Blocks.GRAVEL, Blocks.SAND));

    private RtgSavannaDecos() {}

    /** {@code RealisticBiomeSavannaForest.rDecorate}，strength = 1。 */
    public static void addSavannaForest(final RealisticBiomeBase biome) {

        biome.addDeco(new DecoRtgDecorator((world, rand, chunkX, chunkZ, river, rtgWorld) -> {

            {
                final int i1 = chunkX + rand.nextInt(16) + 8;
                final int j1 = chunkZ + rand.nextInt(16) + 8;
                final int k1 = world.getHeight(i1, j1);
                new WorldGenBlob(Blocks.COBBLESTONE.getDefaultState(), 0, BLOB_GROUND, false)
                        .generate(world, rand, new BlockPos(i1, k1, j1));
            }

            // RWG 的沙地噪声带（注意 +1000 偏移）
            final float n = rtgWorld.simplexInstance(0).noise2f((chunkX + 1000f) / 150f, chunkZ / 150f);

            if (river > 0.8f) {
                for (int b33 = 0; b33 < 15; b33++) {
                    final int j6 = chunkX + rand.nextInt(16) + 8;
                    final int k10 = chunkZ + rand.nextInt(16) + 8;
                    final int z52 = world.getHeight(j6, k10);

                    if (z52 < 100f || (z52 < 120f && rand.nextInt(10) == 0)) {
                        final WorldGenerator worldgenerator = rand.nextInt(3) != 0
                                ? new WorldGenShrub(Blocks.LOG.getDefaultState(), Blocks.LEAVES.getDefaultState())
                                : rand.nextInt(10) == 0 ? new WorldGenSavannah(0) : new WorldGenSavannah(2);
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
            } else if (n > -0.25f && n < 0.25f) {
                for (int b33 = 0; b33 < 7; b33++) {
                    final int j6 = chunkX + rand.nextInt(16) + 8;
                    final int k10 = chunkZ + rand.nextInt(16) + 8;
                    final int z52 = world.getHeight(j6, k10);

                    if (z52 < 100f || (z52 < 120f && rand.nextInt(10) == 0)) {
                        final WorldGenerator worldgenerator = rand.nextInt(9) == 0
                                ? new WorldGenShrub(Blocks.LOG.getDefaultState(), Blocks.LEAVES.getDefaultState())
                                : rand.nextInt(7) == 0 ? new WorldGenSavannah(1) : new WorldGenSavannah(2);
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
            } else {
                final int a = 3 - (int) (rtgWorld.simplexInstance(0).noise2f(chunkX / 100f, chunkZ / 100f) * 7);
                if (a < 1 || rand.nextInt(a) == 0) {
                    final int j6 = chunkX + rand.nextInt(16) + 8;
                    final int k10 = chunkZ + rand.nextInt(16) + 8;
                    final int z52 = world.getHeight(j6, k10);

                    if (z52 < 100f || (z52 < 120f && rand.nextInt(10) == 0)) {
                        final WorldGenerator worldgenerator = rand.nextBoolean()
                                ? new WorldGenShrub(Blocks.LOG.getDefaultState(), Blocks.LEAVES.getDefaultState())
                                : rand.nextInt(5) == 0 ? new WorldGenSavannah(0) : new WorldGenSavannah(1);
                        worldgenerator.generate(world, rand, new BlockPos(j6, z52, k10));
                    }
                }

                if (n > 0.25f) {
                    for (int k18 = 0; k18 < 13; k18++) {
                        final int k21 = chunkX + rand.nextInt(16) + 8;
                        final int k24 = chunkZ + rand.nextInt(16) + 8;
                        final int j23 = world.getHeight(k21, k24);
                        if (j23 < 120f) {
                            new WorldGenRtgCacti(false).generate(world, rand, new BlockPos(k21, j23, k24));
                        }
                    }
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

                if (rand.nextInt(6) == 0) {
                    new WorldGenRtgGrass(net.minecraft.block.BlockDoublePlant.EnumPlantType.GRASS)
                            .generate(world, rand, new BlockPos(l19, k22, j24));
                } else {
                    new WorldGenRtgGrass(WorldGenRtgGrass.tallGrass())
                            .generate(world, rand, new BlockPos(l19, k22, j24));
                }
            }
        }));
    }
}
