package rtg.world.biome.realistic.vanilla;

import net.minecraft.init.Biomes;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceMountainStone;
import rtg.api.world.terrain.TerrainBase;
import rtg.world.biome.RtgJungleDecos;


public class RealisticBiomeVanillaJungleEdge extends RealisticBiomeBase {

    public static Biome biome = Biomes.JUNGLE_EDGE;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaJungleEdge() {

        super(biome);
    }

    @Override
    public void initConfig() {
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaJungleEdge();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainStone(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, false, null, 1f, 1.5f, 60f, 65f, 1.5f);
    }

    @Override
    public void initDecos() {

        // RWG `land/RealisticBiomeJungleHills.rDecorate`（地形/地表/装饰同源）。
        RtgJungleDecos.addJungleHills(this);
    }

    @Override
    public boolean allowVanillaTrees() {
        return false;
    }

    @Override
    public void overrideDecorations() {
        baseBiome().decorator.grassPerChunk = 30; // Vanilla = 25
        baseBiome().decorator.flowersPerChunk = -999; // Vanilla = 4
        baseBiome().decorator.treesPerChunk = 2; // Vanilla = 2
    }

    public static class TerrainVanillaJungleEdge extends TerrainBase {

        public TerrainVanillaJungleEdge() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG JungleHills -> TerrainHilly(230f,120f,50f)
            return terrainHilly(x, y, rtgWorld, river, 230f, 120f, 50f, 260f, 68f);
        }
    }

}
