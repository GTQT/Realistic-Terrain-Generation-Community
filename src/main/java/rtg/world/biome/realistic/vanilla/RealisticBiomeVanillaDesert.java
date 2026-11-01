package rtg.world.biome.realistic.vanilla;

import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.feature.*;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoRtgDecorator;
import rtg.api.world.gen.RTGChunkGenSettings;
import rtg.api.world.gen.feature.*;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceDesertMountain;
import rtg.api.world.terrain.TerrainBase;


public class RealisticBiomeVanillaDesert extends RealisticBiomeBase {

    public static Biome biome = Biomes.DESERT;
    public static Biome river = Biomes.RIVER;

    /** RWG {@code DecoBlob} 认定的可落地表。 */
    private static final java.util.Collection<net.minecraft.block.Block> BLOB_GROUND =
            java.util.Collections.unmodifiableCollection(java.util.Arrays.asList(
                    Blocks.GRASS, Blocks.DIRT, Blocks.STONE, Blocks.GRAVEL, Blocks.SAND));

    public RealisticBiomeVanillaDesert() {

        super(biome);
    }

    @Override
    public void initConfig() {
        this.getConfig().SURFACE_WATER_LAKE_MULT.set(0.0f);
        this.getConfig().ALLOW_VILLAGES.set(true);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
        this.getConfig().SURFACE_FILLER_BLOCK.set("minecraft:sandstone");
        this.getConfig().addProperty(this.getConfig().ALLOW_CACTUS).set(true);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaDesert();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceDesertMountain(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, false, null, 0f, 1.5f, 60f, 65f, 1.5f);
    }

    @Override
    public void rReplace(ChunkPrimer primer, int i, int j, int x, int y, int depth, RTGWorld rtgWorld, float[] noise, float river, Biome[] base) {

        this.rReplaceWithRiver(primer, i, j, x, y, depth, rtgWorld, noise, river, base);
    }

    @Override
    public void initDecos() {

        // RWG `desert/RealisticBiomeDesert.rDecorate`，strength = 1。
        this.addDeco(new DecoRtgDecorator((world, rand, chunkX, chunkZ, river, rtgWorld) -> {

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

    @Override
    public boolean allowVanillaTrees() {
        return false;
    }

    @Override
    public void overrideDecorations() {
        baseBiome().decorator.cactiPerChunk = -999;
    }

    /**
     * 沙丘（RTG 时代的实现，**没有 RWG 对应物**）。
     *
     * <p>它用的是 {@code terrainPolar} 的**参数化**重载 —— 算式与 RWG 的 {@code TerrainPolar}
     * 逐字相同，只是把 5 个字面量提成形参，好让本群系传入自己的沙丘参数。
     *
     * <p>RWG 的沙漠族是 {@code desert\RealisticBiomeDesert → TerrainHilly(150f, 50f, 0f)}，
     * <b>没有</b>沙丘；RWG 的沙丘在 {@code RealisticBiomeDuneValley → TerrainDunes}。
     * 本群系保持 RTG 的沙丘实现，理由有两条：
     * <ol>
     *   <li>它是 {@code sandDuneHeight} 这个 GUI 滑条（1–10）的**唯一**消费者，
     *       换成 {@code terrainHilly} 会让该滑条变成死配置；</li>
     *   <li>视觉上它产出的是沙丘脊线，与"MC 沙漠"的观感相符；换成
     *       {@code terrainHilly(150,50,0)} 会变成普通的沙漠丘陵。</li>
     * </ol>
     * 这是一处**已知偏离**，见 {@code docs/rwg-port-gaps.md} §20。若你要"完全照抄"，
     * 改法是把下面这行换成
     * {@code return terrainHilly(x, y, rtgWorld, river, 150f, 50f, 0f, 260f, 68f);}
     *（与 {@code BOPColdDesert} 现在的写法一致），代价是滑条失效。
     */
    public static class TerrainVanillaDesert extends TerrainBase {

        public TerrainVanillaDesert() {

            super(64);
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            RTGChunkGenSettings settings = rtgWorld.getGeneratorSettings();
            float duneHeight = (minDuneHeight + settings.sandDuneHeight);

            duneHeight *= (1f + rtgWorld.simplexInstance(2).noise2f(x / 330f, y / 330f)) / 2f;

            float stPitch = 200f;    // The higher this is, the more smoothly dunes blend with the terrain
            float stFactor = duneHeight;
            float hPitch = 70;    // Dune scale
            float hDivisor = 40;

            return terrainPolar(x, y, rtgWorld, river, stPitch, stFactor, hPitch, hDivisor, base) + groundNoise(x, y, 1f, rtgWorld);
        }
    }

}
