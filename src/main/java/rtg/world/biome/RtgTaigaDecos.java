package rtg.world.biome;

import net.minecraft.block.Block;
import net.minecraft.block.BlockOldLog;
import net.minecraft.block.BlockPlanks;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.feature.WorldGenBush;
import net.minecraft.world.gen.feature.WorldGenLakes;
import net.minecraft.world.gen.feature.WorldGenPumpkin;
import net.minecraft.world.gen.feature.WorldGenerator;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoRtgDecorator;
import rtg.api.world.gen.feature.*;

import java.util.Arrays;
import java.util.Collections;


/**
 * RWG 针叶林族（{@code land/RealisticBiomeTaigaHills}）的装饰体。
 *
 * <p>rtgc 的 8 个针叶林群系（Taiga / TaigaHills / ColdTaiga / ColdTaigaHills / MegaTaiga /
 * MegaTaigaHills / MegaSpruceTaiga / RedwoodTaigaHills）在上一轮全部把地形接到了
 * {@code TerrainMountainRiver}，依据就是 RWG 的 {@code RealisticBiomeTaigaHills}。
 * 装饰沿用同一个来源，保证「地形/地表/装饰」三者同源。
 */
public final class RtgTaigaDecos {

    private static final java.util.Collection<Block> BLOB_GROUND = Collections.unmodifiableCollection(
            Arrays.asList(Blocks.GRASS, Blocks.DIRT, Blocks.STONE, Blocks.GRAVEL, Blocks.SAND));

    private RtgTaigaDecos() {}

    /** {@code RealisticBiomeTaigaHills.rDecorate}，strength = 1。 */
    public static void addTaigaHills(final RealisticBiomeBase biome) {

        biome.addDeco(new DecoRtgDecorator((world, rand, chunkX, chunkZ, river, rtgWorld) -> {

            // 巨石：6 次；低于 95 且（低于 64 或 1/15 概率）才放。
            for (int l = 0; l < 6; l++) {
                final int i1 = chunkX + rand.nextInt(16) + 8;
                final int j1 = chunkZ + rand.nextInt(16) + 8;
                final int k1 = world.getHeight(i1, j1);
                if (k1 < 95 && (k1 < 64 || rand.nextInt(15) == 0)) {
                    new WorldGenBlob(Blocks.MOSSY_COBBLESTONE.getDefaultState(), 0, BLOB_GROUND, false)
                            .generate(world, rand, new BlockPos(i1, k1, j1));
                }
            }

            // 树：密度由 100 格噪声控制；低海拔大松树，高海拔小树。
            final float density = rtgWorld.simplexInstance(0).noise2f(chunkX / 100f, chunkZ / 100f) * 12f + 4f;
            for (int b1 = 0; b1 < density; b1++) {
                final int j6 = chunkX + rand.nextInt(16) + 8;
                final int k10 = chunkZ + rand.nextInt(16) + 8;
                final int z52 = world.getHeight(j6, k10);

                if (z52 < 90) {
                    final WorldGenerator worldgenerator = rand.nextInt(10) != 0
                            ? new WorldGenPineTree(4, rand.nextInt(4) == 0 ? 1 : 0)
                            : rand.nextInt(3) != 0 ? new WorldGenSmallPine(3 + rand.nextInt(6), 6 + rand.nextInt(8), 0)
                                    : new WorldGenSmallSpruce(rand.nextInt(2) + 1);
                    worldgenerator.generate(world, rand, new BlockPos(j6, z52, k10));
                } else if (z52 < 120) {
                    final WorldGenerator worldgenerator = rand.nextInt(4) != 0
                            ? new WorldGenSmallPine(1 + rand.nextInt(3), 3 + rand.nextInt(5), rand.nextInt(2))
                            : new WorldGenSmallSpruce(rand.nextInt(2));
                    worldgenerator.generate(world, rand, new BlockPos(j6, z52, k10));
                }
            }

            // 倒木
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

            // 灌木：4 次
            for (int f24 = 0; f24 < 4; f24++) {
                final int i1 = chunkX + rand.nextInt(16) + 8;
                final int j1 = chunkZ + rand.nextInt(16) + 8;
                final int k1 = world.getHeight(i1, j1);
                if (k1 < 110) {
                    new WorldGenRtgShrub(rand.nextInt(4) + 1, rand.nextInt(2), rand.nextInt(2))
                            .generate(world, rand, new BlockPos(i1, k1, j1));
                }
            }

            // 野麦/作物
            if (rand.nextInt(150) == 0) {
                final int k21 = chunkX + rand.nextInt(16) + 8;
                final int j23 = rand.nextInt(60) + 60;
                final int k24 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenRtgWildWheat(rand.nextInt(3)).generate(world, rand, new BlockPos(k21, j23, k24));
            }

            // 南瓜
            if (rand.nextInt(20) == 0) {
                final int j16 = chunkX + rand.nextInt(16) + 8;
                final int j18 = rand.nextInt(100);
                final int j21 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenPumpkin().generate(world, rand, new BlockPos(j16, j18, j21));
            }

            // 花：4 组
            for (int f23 = 0; f23 < 4; f23++) {
                final int j15 = chunkX + rand.nextInt(16) + 8;
                final int j17 = rand.nextInt(128);
                final int j20 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenRtgFlowers(new int[] { 9, 0, 3 }).generate(world, rand, new BlockPos(j15, j17, j20));
            }

            // 草：12 组，全是单格高草
            for (int l14 = 0; l14 < 12; l14++) {
                final int l19 = chunkX + rand.nextInt(16) + 8;
                final int k22 = rand.nextInt(128);
                final int j24 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenRtgGrass(WorldGenRtgGrass.tallGrass())
                        .generate(world, rand, new BlockPos(l19, k22, j24));
            }
        }));
    }

    /** {@code land/RealisticBiomeTaigaPlains.rDecorate}，strength = 1。 */
    public static void addTaigaPlains(final RealisticBiomeBase biome) {

        biome.addDeco(new DecoRtgDecorator((world, rand, chunkX, chunkZ, river, rtgWorld) -> {

            if (rand.nextInt(15) == 0) {
                final int i2 = chunkX + rand.nextInt(16) + 8;
                final int i8 = chunkZ + rand.nextInt(16) + 8;
                final int l4 = world.getHeight(i2, i8);
                if (l4 > 63) {
                    new WorldGenLakes(Blocks.WATER).generate(world, rand, new BlockPos(i2, l4, i8));
                }
            }

            for (int l = 0; l < 3; l++) {
                final int i1 = chunkX + rand.nextInt(16) + 8;
                final int j1 = chunkZ + rand.nextInt(16) + 8;
                final int k1 = world.getHeight(i1, j1);
                if (k1 < 95 && (k1 < 64 || rand.nextInt(7) == 0)) {
                    new WorldGenBlob(Blocks.MOSSY_COBBLESTONE.getDefaultState(), 0, BLOB_GROUND, false)
                            .generate(world, rand, new BlockPos(i1, k1, j1));
                }
            }

            final float l = rtgWorld.simplexInstance(0).noise2f(chunkX / 100f, chunkZ / 100f) * 6f + 0.8f;
            for (int b1 = 0; b1 < l * 4f; b1++) {
                final int j6 = chunkX + rand.nextInt(16) + 8;
                final int k10 = chunkZ + rand.nextInt(16) + 8;
                final int z52 = world.getHeight(j6, k10);
                final WorldGenerator worldgenerator = rand.nextInt(4) == 0 ? new WorldGenSmallSpruce(1 + rand.nextInt(2))
                        : rand.nextInt(6) == 0
                                ? new WorldGenSmallPine(1 + rand.nextInt(3), 4 + rand.nextInt(4), 0)
                                : new WorldGenSmallPine(4 + rand.nextInt(6), 5 + rand.nextInt(10), 0);
                worldgenerator.generate(world, rand, new BlockPos(j6, z52, k10));
            }

            if (l > 0f && rand.nextInt(6) == 0) {
                final int x22 = chunkX + rand.nextInt(16) + 8;
                final int z22 = chunkZ + rand.nextInt(16) + 8;
                final int y22 = world.getHeight(x22, z22);
                new WorldGenLog(Blocks.LOG.getDefaultState()
                        .withProperty(BlockOldLog.VARIANT, BlockPlanks.EnumType.SPRUCE),
                        Blocks.LEAVES.getDefaultState(), 3 + rand.nextInt(4))
                        .generate(world, rand, new BlockPos(x22, y22, z22));
            }

            for (int b = 0; b < 2; b++) {
                final int i1 = chunkX + rand.nextInt(16) + 8;
                final int j1 = chunkZ + rand.nextInt(16) + 8;
                final int k1 = world.getHeight(i1, j1);
                final int size = rand.nextInt(10) == 0 ? rand.nextInt(5) + 4 : rand.nextInt(4) + 1;
                new WorldGenRtgShrub(size, rand.nextInt(2), rand.nextInt(2))
                        .generate(world, rand, new BlockPos(i1, k1, j1));
            }

            if (rand.nextInt(3) == 0) {
                final int k15 = chunkX + rand.nextInt(16) + 8;
                final int k17 = rand.nextInt(64) + 64;
                final int k20 = chunkZ + rand.nextInt(16) + 8;
                final net.minecraft.block.Block mushroom = rand.nextBoolean()
                        ? Blocks.BROWN_MUSHROOM : Blocks.RED_MUSHROOM;
                new WorldGenBush((net.minecraft.block.BlockBush) mushroom)
                        .generate(world, rand, new BlockPos(k15, k17, k20));
            }

            if (rand.nextInt(20) == 0) {
                final int j16 = chunkX + rand.nextInt(16) + 8;
                final int j18 = rand.nextInt(128);
                final int j21 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenPumpkin().generate(world, rand, new BlockPos(j16, j18, j21));
            }

            if (rand.nextInt(150) == 0) {
                final int k21 = chunkX + rand.nextInt(16) + 8;
                final int j23 = rand.nextInt(60) + 60;
                final int k24 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenRtgWildWheat(rand.nextInt(3)).generate(world, rand, new BlockPos(k21, j23, k24));
            }

            for (int f23 = 0; f23 < 4; f23++) {
                final int j15 = chunkX + rand.nextInt(16) + 8;
                final int j17 = rand.nextInt(128);
                final int j20 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenRtgFlowers(new int[] { 9, 0, 3 }).generate(world, rand, new BlockPos(j15, j17, j20));
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
