package rtg.world.biome;

import net.minecraft.block.BlockOldLeaf;
import net.minecraft.block.BlockOldLog;
import net.minecraft.block.BlockPlanks;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.feature.WorldGenMegaJungle;
import net.minecraft.world.gen.feature.WorldGenShrub;
import net.minecraft.world.gen.feature.WorldGenTrees;
import net.minecraft.world.gen.feature.WorldGenerator;

import rtg.api.util.noise.PoissonPointNoise;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoRtgDecorator;
import rtg.api.world.gen.feature.WorldGenBlob;
import rtg.api.world.gen.feature.WorldGenRtgFlowers;
import rtg.api.world.gen.feature.WorldGenRtgGrass;
import rtg.api.world.gen.feature.WorldGenRtgJungle;


/**
 * RWG 丛林族（{@code land/RealisticBiomeJungleHills}）。
 *
 * <p>rtgc 的 `VanillaJungleHills` / `JungleEdge` / `ExtremeHillsPlus(+M)` 在上一轮都把地形接到了
 * {@code TerrainHilly(230f, 120f, 50f)}，依据就是 RWG 的 {@code RealisticBiomeJungleHills}，
 * 装饰沿用同一来源。
 *
 * <p>RWG 原文：2 次树（1/5 橡木灌木、1/3 巨型丛林树、否则带藤丛林树）+ 15 组草。
 */
public final class RtgJungleDecos {

    private static final java.util.Collection<net.minecraft.block.Block> BLOB_GROUND =
            java.util.Collections.unmodifiableCollection(java.util.Arrays.asList(
                    Blocks.GRASS, Blocks.DIRT, Blocks.STONE, Blocks.GRAVEL, Blocks.SAND));

    private RtgJungleDecos() {}

    /** {@code RealisticBiomeJungleHills.rDecorate}，strength = 1。 */
    public static void addJungleHills(final RealisticBiomeBase biome) {

        final net.minecraft.block.state.IBlockState jungleLog = Blocks.LOG.getDefaultState()
                .withProperty(BlockOldLog.VARIANT, BlockPlanks.EnumType.JUNGLE);
        final net.minecraft.block.state.IBlockState jungleLeaves = Blocks.LEAVES.getDefaultState()
                .withProperty(BlockOldLeaf.VARIANT, BlockPlanks.EnumType.JUNGLE);

        biome.addDeco(new DecoRtgDecorator((world, rand, chunkX, chunkZ, river, rtgWorld) -> {

            for (int b33 = 0; b33 < 2; b33++) {
                final int j6 = chunkX + rand.nextInt(16) + 8;
                final int k10 = chunkZ + rand.nextInt(16) + 8;
                final int z52 = world.getHeight(j6, k10);

                if (z52 < 100 || rand.nextInt(3) == 0) {
                    final WorldGenerator worldgenerator = rand.nextInt(5) == 0
                            ? new WorldGenShrub(Blocks.LOG.getDefaultState(), Blocks.LEAVES.getDefaultState())
                            : rand.nextInt(3) == 0
                                    ? new WorldGenMegaJungle(false, 5, 10, jungleLog, jungleLeaves)
                                    : new WorldGenTrees(false, 3 + rand.nextInt(5), jungleLog, jungleLeaves, true);
                    worldgenerator.generate(world, rand, new BlockPos(j6, z52, k10));
                }
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

    /** {@code land/RealisticBiomeJungleCanyon.rDecorate}，strength = 1。 */
    public static void addJungleCanyon(final RealisticBiomeBase biome) {

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

            for (int a = 0; a < 5; a++) {
                final int j6 = chunkX + rand.nextInt(16) + 8;
                final int k10 = chunkZ + rand.nextInt(16) + 8;
                final int z52 = world.getHeight(j6, k10);
                new WorldGenRtgJungle(1 + rand.nextInt(4), 0, 5f, 2, 0.32f, 0.14f, false)
                        .generate(world, rand, new BlockPos(j6, z52, k10));
            }
        }));
    }

    /** RWG {@code RealisticBiomeHighRainforest} 的大树布点参数（逐条照抄）。 */
    private static final double LARGE_TREE_SPACING = 12D;
    private static final int LARGE_TREE_PLACEMENT_ROUNDS = 2;
    private static final long LARGE_TREE_SEED_SALT = 0x3C6EF372FE94F82BL;
    private static long largeTreeSeed = Long.MIN_VALUE;
    private static PoissonPointNoise largeTreePoints;

    /** {@code land/RealisticBiomeHighRainforest.rDecorate}，strength = 1。 */
    public static void addHighRainforest(final RealisticBiomeBase biome) {

        biome.addDeco(new DecoRtgDecorator((world, rand, chunkX, chunkZ, river, rtgWorld) -> {

            for (int l = 0; l < 2; l++) {
                final int i1 = chunkX + rand.nextInt(16) + 8;
                final int j1 = chunkZ + rand.nextInt(16) + 8;
                final int k1 = world.getHeight(i1, j1);
                new WorldGenBlob(Blocks.MOSSY_COBBLESTONE.getDefaultState(), 0, BLOB_GROUND, false)
                        .generate(world, rand, new BlockPos(i1, k1, j1));
            }

            final PoissonPointNoise points = largeTreePoints(rtgWorld.seed());
            final double cellSize = points.getCellSize();
            final int minimumX = chunkX + 8;
            final int minimumZ = chunkZ + 8;
            final int maximumX = minimumX + 16;
            final int maximumZ = minimumZ + 16;
            final int minimumCellX = (int) Math.floor(minimumX / cellSize);
            final int maximumCellX = (int) Math.floor((maximumX - 1) / cellSize);
            final int minimumCellZ = (int) Math.floor(minimumZ / cellSize);
            final int maximumCellZ = (int) Math.floor((maximumZ - 1) / cellSize);
            final double[] candidate = new double[2];

            for (int cellZ = minimumCellZ; cellZ <= maximumCellZ; cellZ++) {
                for (int cellX = minimumCellX; cellX <= maximumCellX; cellX++) {
                    for (int round = 0; round < points.getRounds(); round++) {
                        if (!points.isAccepted(cellX, cellZ, round)) {
                            continue;
                        }
                        points.getCandidate(cellX, cellZ, round, candidate);
                        final int treeX = (int) Math.floor(candidate[0]);
                        final int treeZ = (int) Math.floor(candidate[1]);
                        if (treeX < minimumX || treeX >= maximumX || treeZ < minimumZ || treeZ >= maximumZ) {
                            continue;
                        }
                        final int y = world.getHeight(treeX, treeZ);
                        new WorldGenRtgJungle(15 + rand.nextInt(15), 4 + rand.nextInt(2), 14f, 3, 0.2f, 0.25f, true)
                                .generate(world, rand, new BlockPos(treeX, y, treeZ));
                    }
                }
            }

            for (int attempt = 0; attempt < 13; attempt++) {
                final int x = chunkX + rand.nextInt(16) + 8;
                final int y = rand.nextInt(128);
                final int z = chunkZ + rand.nextInt(16) + 8;
                if (rand.nextInt(6) == 0) {
                    new WorldGenRtgGrass(rand.nextBoolean()
                            ? net.minecraft.block.BlockDoublePlant.EnumPlantType.GRASS
                            : net.minecraft.block.BlockDoublePlant.EnumPlantType.FERN)
                            .generate(world, rand, new BlockPos(x, y, z));
                } else {
                    new WorldGenRtgGrass(rand.nextBoolean()
                            ? WorldGenRtgGrass.tallGrass()
                            : WorldGenRtgGrass.fern())
                            .generate(world, rand, new BlockPos(x, y, z));
                }
            }

            for (int attempt = 0; attempt < 2; attempt++) {
                final int x = chunkX + rand.nextInt(16) + 8;
                final int y = rand.nextInt(128);
                final int z = chunkZ + rand.nextInt(16) + 8;
                new WorldGenRtgFlowers(new int[] { 1, 1, 1, 8 }).generate(world, rand, new BlockPos(x, y, z));
            }
        }));
    }

    private static synchronized PoissonPointNoise largeTreePoints(final long worldSeed) {

        final long seed = worldSeed ^ LARGE_TREE_SEED_SALT;
        if (largeTreePoints == null || largeTreeSeed != seed) {
            largeTreeSeed = seed;
            largeTreePoints = new PoissonPointNoise(seed, LARGE_TREE_SPACING, LARGE_TREE_PLACEMENT_ROUNDS);
        }
        return largeTreePoints;
    }
}
