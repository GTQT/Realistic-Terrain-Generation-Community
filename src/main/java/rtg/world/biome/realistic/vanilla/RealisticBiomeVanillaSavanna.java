package rtg.world.biome.realistic.vanilla;

import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.feature.WorldGenPumpkin;
import net.minecraft.world.gen.feature.WorldGenReed;
import net.minecraft.world.gen.feature.WorldGenShrub;
import net.minecraft.world.gen.feature.WorldGenerator;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoRtgDecorator;
import rtg.api.world.gen.feature.*;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceGrasslandMix1;
import rtg.api.world.terrain.TerrainBase;

import java.util.Arrays;
import java.util.Collections;


/**
 * 原版稀树草原的 RWG 外壳。地形照抄 RWG {@code savanna/RealisticBiomeSavanna}：
 * {@code TerrainGrasslandFlats}。
 *
 * <p>地表按 **RTG+ {@code RealisticBiomeVanillaSavanna}** 的写法：
 * {@code SurfaceGrasslandMix1(grass, dirt, grass, stone, cobble, 13f, .27f)}
 * —— mix 块是**草**（可由 {@code SURFACE_MIX_BLOCK} 覆盖），不是 rwg2 的沙。
 * rwg2 传 {@code Blocks.sand}，会在草原上撒出一片片散点沙斑；RTG+ 用草，视觉上就是纯草。
 * 两份上游此处不一致，本项目按用户决定采用 RTG+。
 *
 * <p>装饰本轮改为 RWG {@code RealisticBiomeSavanna.rDecorate} 的**逐行移植**
 * （{@link DecoRtgDecorator} 里就是原方法体，{@code strength = 1f}）。原来的
 * {@code DecoCollectionSavanna} / {@code DecoCollectionSavannaTrees} 是 RTG 自己的稀疏版本
 * （金合欢灌木 + 倒木 + 巨石 + 双草），与 RWG 的 15×稀树树 + 12×仙人掌 + 芦苇 + 南瓜 + 花 + 草不同。
 */
public class RealisticBiomeVanillaSavanna extends RealisticBiomeBase {

    public static Biome biome = Biomes.SAVANNA;
    public static Biome river = Biomes.RIVER;

    /** RWG {@code DecoBlob} 认定的可落地表。 */
    private static final java.util.Collection<net.minecraft.block.Block> BLOB_GROUND = Collections.unmodifiableCollection(
            Arrays.asList(Blocks.GRASS, Blocks.DIRT, Blocks.STONE, Blocks.GRAVEL, Blocks.SAND));

    public RealisticBiomeVanillaSavanna() {

        super(biome);
    }

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_VILLAGES.set(true);
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
        this.getConfig().addProperty(this.getConfig().ALLOW_CACTUS).set(true);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_BLOCK).set("");
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaSavanna();
    }

    @Override
    public SurfaceBase initSurface() {
        // 地表按 RTG+ `RealisticBiomeVanillaSavanna` 的配对：
        //     surface = new SurfaceVanillaSavanna(getConfig(), grass, dirt, grass, 13f, 0.27f);
        // mix 块是**草** —— rwg2 写的是 Blocks.sand，会在草原上留下散点沙斑。
        // SurfaceGrasslandMix1 仍会读 SURFACE_MIX_BLOCK（默认空 = 用这里传的草）。
        return new SurfaceGrasslandMix1(getConfig(), Blocks.GRASS.getDefaultState(), Blocks.DIRT.getDefaultState(),
                Blocks.GRASS.getDefaultState(), Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState(),
                13f, 0.27f);
    }

    @Override
    public boolean allowVanillaTrees() {
        return false;
    }

    @Override
    public void initDecos() {

        // RWG `savanna/RealisticBiomeSavanna.rDecorate`，strength = 1。
        this.addDeco(new DecoRtgDecorator((world, rand, chunkX, chunkZ, river, rtgWorld) -> {

            // 1× cobblestone boulder（RWG 无条件 1 次；DecoBlob 自己会找地面）。
            {
                final int i1 = chunkX + rand.nextInt(16) + 8;
                final int j1 = chunkZ + rand.nextInt(16) + 8;
                final int k1 = world.getHeight(i1, j1);
                new WorldGenBlob(Blocks.COBBLESTONE.getDefaultState(), 0, BLOB_GROUND, false)
                        .generate(world, rand, new BlockPos(i1, k1, j1));
            }

            if (river > 0.8f) {
                for (int b33 = 0; b33 < 15; b33++) {
                    final int j6 = chunkX + rand.nextInt(16) + 8;
                    final int k10 = chunkZ + rand.nextInt(16) + 8;
                    final int z52 = world.getHeight(j6, k10);

                    final WorldGenerator worldgenerator = rand.nextInt(3) != 0
                            ? new WorldGenShrub(Blocks.LOG.getDefaultState(), Blocks.LEAVES.getDefaultState())
                            : rand.nextInt(7) == 0 ? new WorldGenSavannah(1) : new WorldGenSavannah(2);
                    worldgenerator.generate(world, rand, new BlockPos(j6, z52, k10));
                }

                for (int f25 = 0; f25 < 2; f25++) {
                    final int i18 = chunkX + rand.nextInt(16) + 8;
                    final int i23 = chunkZ + rand.nextInt(16) + 8;
                    new WorldGenReed().generate(world, rand, new BlockPos(i18, 60 + rand.nextInt(8), i23));
                }
            } else if (rtgWorld.simplexInstance(0).noise2f(chunkX / 180f, chunkZ / 180f) > 0.20f) {
                for (int b33 = 0; b33 < 7; b33++) {
                    final int j6 = chunkX + rand.nextInt(16) + 8;
                    final int k10 = chunkZ + rand.nextInt(16) + 8;
                    final int z52 = world.getHeight(j6, k10);

                    final WorldGenerator worldgenerator = rand.nextInt(9) == 0
                            ? new WorldGenShrub(Blocks.LOG.getDefaultState(), Blocks.LEAVES.getDefaultState())
                            : rand.nextInt(7) == 0 ? new WorldGenSavannah(1) : new WorldGenSavannah(2);
                    worldgenerator.generate(world, rand, new BlockPos(j6, z52, k10));
                }
            } else {
                final int a = 3 - (int) (rtgWorld.simplexInstance(0).noise2f(chunkX / 100f, chunkZ / 100f) * 7);
                if (a < 1 || rand.nextInt(a) == 0) {
                    final int j6 = chunkX + rand.nextInt(16) + 8;
                    final int k10 = chunkZ + rand.nextInt(16) + 8;
                    final int z52 = world.getHeight(j6, k10);

                    final WorldGenerator worldgenerator = rand.nextBoolean()
                            ? new WorldGenShrub(Blocks.LOG.getDefaultState(), Blocks.LEAVES.getDefaultState())
                            : rand.nextInt(5) == 0 ? new WorldGenSavannah(0) : new WorldGenSavannah(1);
                    worldgenerator.generate(world, rand, new BlockPos(j6, z52, k10));
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

            for (int k18 = 0; k18 < 12; k18++) {
                final int k21 = chunkX + rand.nextInt(16) + 8;
                final int j23 = rand.nextInt(160);
                final int k24 = chunkZ + rand.nextInt(16) + 8;
                new WorldGenRtgCacti(false).generate(world, rand, new BlockPos(k21, j23, k24));
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

    @Override
    public void overrideDecorations() {
        baseBiome().decorator.cactiPerChunk = -999;
    }

    public static class TerrainVanillaSavanna extends TerrainBase {

        public TerrainVanillaSavanna() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // 依据：RWG savanna\RealisticBiomeSavanna.java -> `terrain = new TerrainGrasslandFlats()`
            return terrainGrasslandFlats(x, y, rtgWorld, river);
        }
    }
}
