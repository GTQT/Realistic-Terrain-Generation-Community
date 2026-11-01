package rtg.world.biome;

import net.minecraft.block.BlockDoublePlant;
import net.minecraft.block.BlockOldLeaf;
import net.minecraft.block.BlockPlanks;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoRtgDecorator;
import rtg.api.world.gen.feature.WorldGenBlob;
import rtg.api.world.gen.feature.WorldGenMangrove;
import rtg.api.world.gen.feature.WorldGenRtgGrass;
import rtg.api.world.gen.feature.WorldGenWaterGrass;


/**
 * RWG 海岸族（{@code coast/RealisticBiomeCoastDunes}）。
 *
 * <p>rtgc 的 5 个海滩群系（Beach / Stone Beach / BOP Origin Beach / BOP White Beach /
 * BOP Gravel Beach）地表都是 {@code SurfaceCoastDunes}，装饰沿用同一来源。
 * RWG 这个群系的 {@code rDecorate} 只有“水草带”一种装饰（11 次，三种植物）。
 */
public final class RtgCoastDecos {

    private RtgCoastDecos() {}

    /** {@code RealisticBiomeCoastDunes.rDecorate}，strength = 1。 */
    public static void addCoastDunes(final RealisticBiomeBase biome) {

        biome.addDeco(new DecoRtgDecorator((world, rand, chunkX, chunkZ, river, rtgWorld) -> {

            for (int l14 = 0; l14 < 11; l14++) {
                final int l19 = chunkX + rand.nextInt(16) + 8;
                final int k22 = 64 + rand.nextInt(64);
                final int j24 = chunkZ + rand.nextInt(16) + 8;

                if (rand.nextInt(5) == 0) {
                    new WorldGenWaterGrass(BlockDoublePlant.EnumPlantType.GRASS, 69)
                            .generate(world, rand, new BlockPos(l19, k22, j24));
                }
                if (rand.nextInt(3) == 0) {
                    // RWG: Blocks.leaves meta 4 = 橡木叶 + 不衰减
                    new WorldGenWaterGrass(Blocks.LEAVES.getDefaultState()
                            .withProperty(BlockOldLeaf.VARIANT, BlockPlanks.EnumType.OAK)
                            .withProperty(BlockOldLeaf.DECAYABLE, false), 67)
                            .generate(world, rand, new BlockPos(l19, k22, j24));
                } else {
                    new WorldGenWaterGrass(WorldGenRtgGrass.tallGrass(), 65)
                            .generate(world, rand, new BlockPos(l19, k22, j24));
                }
            }
        }));
    }

    /** {@code RealisticBiomeCoastMangrove.rDecorate}，strength = 1。 */
    public static void addCoastMangrove(final RealisticBiomeBase biome) {

        biome.addDeco(new DecoRtgDecorator((world, rand, chunkX, chunkZ, river, rtgWorld) -> {

            for (int l = 0; l < 2; l++) {
                final int i1 = chunkX + rand.nextInt(16) + 8;
                final int j1 = chunkZ + rand.nextInt(16) + 8;
                final int k1 = world.getHeight(i1, j1);
                if (k1 < 68) {
                    new WorldGenBlob(Blocks.COBBLESTONE.getDefaultState(), 0,
                            java.util.Collections.unmodifiableCollection(java.util.Arrays.asList(
                                    Blocks.GRASS, Blocks.DIRT, Blocks.STONE, Blocks.GRAVEL, Blocks.SAND)), false)
                            .generate(world, rand, new BlockPos(i1, k1, j1));
                }
            }

            for (int a = 0; a < 5; a++) {
                final int j6 = chunkX + rand.nextInt(16) + 8;
                final int k10 = chunkZ + rand.nextInt(16) + 8;
                final int z52 = world.getHeight(j6, k10);
                WorldGenMangrove.oak(1 + rand.nextInt(4), 4 + rand.nextInt(2), 5f, 2, 0.32f, 0.14f)
                        .generate(world, rand, new BlockPos(j6, z52, k10));
            }

            for (int l14 = 0; l14 < 9; l14++) {
                final int l19 = chunkX + rand.nextInt(16) + 8;
                final int k22 = 64 + rand.nextInt(64);
                final int j24 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenWaterGrass(BlockDoublePlant.EnumPlantType.GRASS, 10)
                        .generate(world, rand, new BlockPos(l19, k22, j24));
            }
        }));
    }
}
