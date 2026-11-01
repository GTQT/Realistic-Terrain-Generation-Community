package rtg.world.biome;

import net.minecraft.block.BlockOldLog;
import net.minecraft.block.BlockPlanks;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.feature.WorldGenBirchTree;
import net.minecraft.world.gen.feature.WorldGenPumpkin;
import net.minecraft.world.gen.feature.WorldGenTrees;
import net.minecraft.world.gen.feature.WorldGenerator;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoRtgDecorator;
import rtg.api.world.gen.feature.*;


/**
 * RWG 红木族（{@code land/RealisticBiomeRedwood} 与 {@code land/RealisticBiomeRedwoodSnow}）。
 *
 * <p>两者的 {@code rDecorate} **逐行相同**（只有 {@code SurfaceMountainStone} vs
 * {@code SurfaceMountainSnow} 不同），所以共用一份实现。
 *
 * <p>落点说明（推断）：RWG 的“红木”实际用的是**巨型松树**（{@code DecoLargePine}），
 * 与 MC `MegaTaiga` 系列的巨型云杉最接近，所以把四个巨型针叶林群系接到这里。
 * 它们上一轮的地形是 {@code TerrainMountainRiver}（RWG TaigaHills），装饰独立于地形。
 */
public final class RtgRedwoodDecos {

    private RtgRedwoodDecos() {}

    /** {@code RealisticBiomeRedwood.rDecorate}，strength = 1。 */
    public static void addRedwood(final RealisticBiomeBase biome) {

        biome.addDeco(new DecoRtgDecorator((world, rand, chunkX, chunkZ, river, rtgWorld) -> {

            // 巨型松树：80 格噪声 ×40 +10 次机会，每次 1/10 落地。
            final float l = rtgWorld.simplexInstance(0).noise2f(chunkX / 80f, chunkZ / 80f) * 40f + 10f;
            for (int b1 = 0; b1 < l; b1++) {
                if (rand.nextInt(10) == 0) {
                    final int j6 = chunkX + rand.nextInt(16) + 8;
                    final int k10 = chunkZ + rand.nextInt(16) + 8;
                    final int z52 = world.getHeight(j6, k10);

                    if (z52 < 110) {
                        final WorldGenerator worldgenerator = new WorldGenLargePine(
                                (int) (14 - ((z52 - 70) / 5f)) + rand.nextInt(8),
                                (int) (15 - ((z52 - 70) / 5f)) + rand.nextInt(12),
                                0, 0);
                        worldgenerator.generate(world, rand, new BlockPos(j6, z52, k10));
                    }
                }
            }

            // 噪声高的地方再补 6 棵小树（1/4 小松，其余橡树/白桦）。
            if (l > 5f) {
                for (int b2 = 0; b2 < 6; b2++) {
                    final int j6 = chunkX + rand.nextInt(16) + 8;
                    final int k10 = chunkZ + rand.nextInt(16) + 8;
                    final int z52 = world.getHeight(j6, k10);

                    if (z52 < 120) {
                        final WorldGenerator worldgenerator = rand.nextInt(4) == 0
                                ? new WorldGenSmallPine(4 + rand.nextInt(7), 6 + rand.nextInt(9), 0)
                                : rand.nextInt(6) != 0 ? new WorldGenTrees(false) : new WorldGenBirchTree(false, false);
                        worldgenerator.generate(world, rand, new BlockPos(j6, z52, k10));
                    }
                }
            }

            if (rand.nextInt(4) == 0) {
                final int x22 = chunkX + rand.nextInt(16) + 8;
                final int z22 = chunkZ + rand.nextInt(16) + 8;
                final int y22 = world.getHeight(x22, z22);
                if (y22 < 100) {
                    new WorldGenLog(
                            Blocks.LOG.getDefaultState()
                                    .withProperty(BlockOldLog.VARIANT, BlockPlanks.EnumType.SPRUCE),
                            Blocks.LEAVES.getDefaultState(), 3 + rand.nextInt(4))
                            .generate(world, rand, new BlockPos(x22, y22, z22));
                }
            }

            for (int f24 = 0; f24 < 3; f24++) {
                final int i1 = chunkX + rand.nextInt(16) + 8;
                final int j1 = chunkZ + rand.nextInt(16) + 8;
                final int k1 = world.getHeight(i1, j1);
                if (k1 < 110) {
                    new WorldGenRtgShrub(rand.nextInt(4) + 1, 0, rand.nextInt(3))
                            .generate(world, rand, new BlockPos(i1, k1, j1));
                }
            }

            if (rand.nextInt(150) == 0) {
                final int k21 = chunkX + rand.nextInt(16) + 8;
                final int j23 = rand.nextInt(60) + 60;
                final int k24 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenRtgWildWheat(rand.nextInt(3)).generate(world, rand, new BlockPos(k21, j23, k24));
            }

            if (rand.nextInt(15) == 0) {
                final int j16 = chunkX + rand.nextInt(16) + 8;
                final int j18 = rand.nextInt(100);
                final int j21 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenPumpkin().generate(world, rand, new BlockPos(j16, j18, j21));
            }

            for (int f23 = 0; f23 < 8; f23++) {
                final int j15 = chunkX + rand.nextInt(16) + 8;
                final int j17 = rand.nextInt(128);
                final int j20 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenRtgFlowers(new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 })
                        .generate(world, rand, new BlockPos(j15, j17, j20));
            }

            for (int l14 = 0; l14 < 12; l14++) {
                final int l19 = chunkX + rand.nextInt(16) + 8;
                final int k22 = rand.nextInt(128);
                final int j24 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenRtgGrass(WorldGenRtgGrass.tallGrass())
                        .generate(world, rand, new BlockPos(l19, k22, j24));
            }
        }));
    }

    /**
     * {@code RealisticBiomeHotForest.rDecorate} 与 {@code RealisticBiomeHotRedwood.rDecorate}，
     * strength = 1。两者逐行相同（已比对），故共用这一份实现。
     */
    public static void addHotForest(final RealisticBiomeBase biome) {

        final java.util.Collection<net.minecraft.block.Block> ground =
                java.util.Collections.unmodifiableCollection(java.util.Arrays.asList(
                        Blocks.GRASS, Blocks.DIRT, Blocks.STONE, Blocks.GRAVEL, Blocks.SAND));

        biome.addDeco(new DecoRtgDecorator((world, rand, chunkX, chunkZ, river, rtgWorld) -> {

            if (rand.nextInt(15) == 0) {
                final int i2 = chunkX + rand.nextInt(16) + 8;
                final int i8 = chunkZ + rand.nextInt(16) + 8;
                final int l4 = world.getHeight(i2, i8);
                if (l4 > 63) {
                    new net.minecraft.world.gen.feature.WorldGenLakes(Blocks.WATER)
                            .generate(world, rand, new BlockPos(i2, l4, i8));
                }
            }

            for (int l = 0; l < 3; l++) {
                final int i1 = chunkX + rand.nextInt(16) + 8;
                final int j1 = chunkZ + rand.nextInt(16) + 8;
                final int k1 = world.getHeight(i1, j1);
                if (k1 < 95 && (k1 < 64 || rand.nextInt(7) == 0)) {
                    new rtg.api.world.gen.feature.WorldGenBlob(Blocks.COBBLESTONE.getDefaultState(), 0, ground, false)
                            .generate(world, rand, new BlockPos(i1, k1, j1));
                }
            }

            // RWG: l = noise((chunkX+16)/60, (chunkZ+16)/60) * 6 + 0.2
            final float l = rtgWorld.simplexInstance(0)
                    .noise2f((chunkX + 16f) / 60f, (chunkZ + 16f) / 60f) * 6f + 0.2f;

            for (int b1 = 0; b1 < l * 4f; b1++) {
                final int j6 = chunkX + rand.nextInt(16) + 8;
                final int k10 = chunkZ + rand.nextInt(16) + 8;
                final int z52 = world.getHeight(j6, k10);

                // RWG 的 DecoBirch 用原版白桦近似（RTG 没有 1:1 的 DecoBirch）
                final WorldGenerator worldgenerator = rand.nextInt(6) == 0 ? new WorldGenTrees(false)
                        : rand.nextInt(12) == 0 ? new WorldGenBirchTree(false, false)
                                : rand.nextInt(6) == 0 ? new WorldGenSmallPine(3 + rand.nextInt(2), 3 + rand.nextInt(3), 0)
                                        : new WorldGenSmallPine(6 + rand.nextInt(5), 3 + rand.nextInt(6), 0);
                worldgenerator.generate(world, rand, new BlockPos(j6, z52, k10));
            }

            if (l < 0.5f) {
                for (int l14 = 0; l14 < (l + 2f) * 4f; l14++) {
                    final int l19 = chunkX + rand.nextInt(16) + 8;
                    final int k22 = 64 + rand.nextInt(64);
                    final int j24 = chunkZ + rand.nextInt(16) + 8;
                    new rtg.api.world.gen.feature.WorldGenWaterGrass(
                            rtg.api.world.gen.feature.WorldGenRtgGrass.tallGrass(), 65)
                            .generate(world, rand, new BlockPos(l19, k22, j24));
                }
            }

            if (l > 0f && rand.nextInt(6) == 0) {
                final int x22 = chunkX + rand.nextInt(16) + 8;
                final int z22 = chunkZ + rand.nextInt(16) + 8;
                final int y22 = world.getHeight(x22, z22);
                new WorldGenLog(Blocks.LOG.getDefaultState(), Blocks.LEAVES.getDefaultState(),
                        3 + rand.nextInt(4)).generate(world, rand, new BlockPos(x22, y22, z22));
            }

            for (int b = 0; b < 2; b++) {
                final int i1 = chunkX + rand.nextInt(16) + 8;
                final int j1 = chunkZ + rand.nextInt(16) + 8;
                final int k1 = world.getHeight(i1, j1);
                final int size = rand.nextInt(10) == 0 ? rand.nextInt(5) + 4 : rand.nextInt(4) + 1;
                new WorldGenRtgShrub(size, rand.nextInt(2), 0).generate(world, rand, new BlockPos(i1, k1, j1));
            }

            if (rand.nextInt(3) == 0) {
                final int k15 = chunkX + rand.nextInt(16) + 8;
                final int k17 = rand.nextInt(64) + 64;
                final int k20 = chunkZ + rand.nextInt(16) + 8;
                final net.minecraft.block.Block mushroom = rand.nextBoolean()
                        ? Blocks.BROWN_MUSHROOM : Blocks.RED_MUSHROOM;
                new net.minecraft.world.gen.feature.WorldGenBush(
                        (net.minecraft.block.BlockBush) mushroom).generate(world, rand, new BlockPos(k15, k17, k20));
            }

            if (rand.nextInt(20) == 0) {
                final int j16 = chunkX + rand.nextInt(16) + 8;
                final int j18 = rand.nextInt(128);
                final int j21 = chunkZ + rand.nextInt(16) + 8;
                new net.minecraft.world.gen.feature.WorldGenPumpkin().generate(world, rand, new BlockPos(j16, j18, j21));
            }

            for (int f23 = 0; f23 < 4; f23++) {
                final int j15 = chunkX + rand.nextInt(16) + 8;
                final int j17 = rand.nextInt(128);
                final int j20 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenRtgFlowers(new int[] { 9, 9, 9, 9, 3, 3, 3, 3, 3, 2, 2, 2, 11, 11, 11 })
                        .generate(world, rand, new BlockPos(j15, j17, j20));
            }

            for (int l14 = 0; l14 < 10; l14++) {
                final int l19 = chunkX + rand.nextInt(16) + 8;
                final int k22 = rand.nextInt(128);
                final int j24 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenRtgGrass(WorldGenRtgGrass.tallGrass())
                        .generate(world, rand, new BlockPos(l19, k22, j24));
            }
        }));
    }
}
