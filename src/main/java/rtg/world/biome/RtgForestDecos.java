package rtg.world.biome;

import net.minecraft.block.BlockOldLog;
import net.minecraft.block.BlockPlanks;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.feature.*;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoRtgDecorator;
import rtg.api.world.gen.feature.*;


/**
 * RWG 温带森林族（{@code forest/RealisticBiomeWoodHills} 与 {@code forest/RealisticBiomeDarkRedwood}）。
 *
 * <p>对应的 rtgc 群系全部在上一轮把地形接到了这两个 RWG 类（见各自 {@code Terrain*} 的注释）：
 * <ul>
 *   <li>{@link #addWoodHills}：Forest / ForestHills / BirchForest / BirchForestHills / BirchForestHillsM</li>
 *   <li>{@link #addDarkRedwood}：RoofedForest / RoofedForestM（M 变体按“同族”推断）</li>
 * </ul>
 *
 * <p>⚠ 一处**最近亲替换**：RWG {@code DecoBigTree} 是 1.7.10 版 {@code WorldGenBigTree} 的定制副本，
 * 1.12 的原版 {@code WorldGenBigTree} 是同一算法的直系后代（默认高度 7–13 也一致），故直接复用，
 * 不再逐行搬 359 行。
 */
public final class RtgForestDecos {

    private RtgForestDecos() {}

    /** {@code RealisticBiomeWoodHills.rDecorate}，strength = 1。 */
    public static void addWoodHills(final RealisticBiomeBase biome) {

        biome.addDeco(new DecoRtgDecorator((world, rand, chunkX, chunkZ, river, rtgWorld) -> {

            // 树密度：80 格噪声 × 20，封顶 10。
            float l = rtgWorld.simplexInstance(0).noise2f(chunkX / 80f, chunkZ / 80f) * 20f;
            l = Math.min(l, 10f);

            for (int b2 = 0; b2 < l; b2++) {
                final int j6 = chunkX + rand.nextInt(16) + 8;
                final int k10 = chunkZ + rand.nextInt(16) + 8;
                final int z52 = world.getHeight(j6, k10);

                if (z52 < 120) {
                    final WorldGenerator worldgenerator = rand.nextInt(10) == 0 ? new WorldGenBigTree(false)
                            : rand.nextInt(6) != 0 ? new WorldGenTrees(false) : new WorldGenBirchTree(false, false);
                    worldgenerator.generate(world, rand, new BlockPos(j6, z52, k10));
                }
            }

            if (l > -1f) {
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

    /** {@code RealisticBiomeDarkRedwood.rDecorate}，strength = 1。 */
    public static void addDarkRedwood(final RealisticBiomeBase biome) {

        biome.addDeco(new DecoRtgDecorator((world, rand, chunkX, chunkZ, river, rtgWorld) -> {

            final float l = rtgWorld.simplexInstance(0).noise2f(chunkX / 80f, chunkZ / 80f) * 60f + 10f;

            for (int b1 = 0; b1 < l; b1++) {
                if (rand.nextInt(6) == 0) {
                    final int j6 = chunkX + rand.nextInt(16) + 8;
                    final int k10 = chunkZ + rand.nextInt(16) + 8;
                    final int z52 = world.getHeight(j6, k10);

                    if (z52 < 110) {
                        final WorldGenerator worldgenerator = new WorldGenLargePine(
                                (int) (14 - ((z52 - 70) / 5f)) + rand.nextInt(6),
                                (int) (15 - ((z52 - 70) / 5f)) + rand.nextInt(10),
                                1, 1);
                        worldgenerator.generate(world, rand, new BlockPos(j6, z52, k10));
                    }
                }
            }

            if (l > 5f) {
                for (int b2 = 0; b2 < 3; b2++) {
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

}
