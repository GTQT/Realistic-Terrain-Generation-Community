package rtg.world.biome.realistic.vanilla;

import net.minecraft.init.Biomes;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.collection.DecoCollectionJungle;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;

import rtg.api.world.surface.SurfaceMountainStone;


public class RealisticBiomeVanillaJungleHills extends RealisticBiomeBase {

    public static Biome biome = Biomes.JUNGLE_HILLS;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaJungleHills() {

        super(biome);
    }

    @Override
    public void initConfig() {
        this.getConfig().SURFACE_WATER_LAKE_MULT.set(0.5f);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
        this.getConfig().addProperty(this.getConfig().ALLOW_CACTUS).set(true);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaJungleHills(72f, 60f);
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainStone(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, false, null, 1f, 1.5f, 60f, 65f, 1.5f);
    }

    @Override
    public void initDecos() {
        this.addDecoCollection(new DecoCollectionJungle(this.getConfig()));
    }

    @Override
    public void overrideDecorations() {
        baseBiome().decorator.grassPerChunk = 30; // Vanilla = 25
        baseBiome().decorator.flowersPerChunk = -999; // Vanilla = 4
        baseBiome().decorator.treesPerChunk = 30; // Vanilla = 50
    }

    public static class TerrainVanillaJungleHills extends TerrainBase {

        private float hillStrength = 60f;

        public TerrainVanillaJungleHills(float bh, float hs) {

            base = bh;
            hillStrength = hs;
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // 依据：RWG land\RealisticBiomeJungleHills -> TerrainHilly(230f, 120f, 50f)；与基准群系 VanillaJungle 同值
            // 原先的 GrasslandHills(70f, 180f, 7f, ...) 在 RWG 全仓查无此文，且比基准群系**更平**（方向反了）。
            return terrainHilly(x, y, rtgWorld, river, 230f, 120f, 50f, 260f, 68f);
        }
    }

}
