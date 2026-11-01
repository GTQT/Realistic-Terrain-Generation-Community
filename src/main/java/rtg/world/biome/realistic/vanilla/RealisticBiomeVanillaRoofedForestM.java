package rtg.world.biome.realistic.vanilla;

import net.minecraft.init.Biomes;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceMountainStone;
import rtg.api.world.terrain.TerrainBase;
import rtg.world.biome.RtgForestDecos;


/**
 * 原版黑森林（变体）。装饰跟随基准群系 {@link RealisticBiomeVanillaRoofedForest}
 * —— RWG {@code forest/RealisticBiomeDarkRedwood.rDecorate}。
 *
 * <p>⚠ 地形仍是 RTG 的 {@code terrainGrasslandMountains}（上一轮未迁到 RWG 的
 * {@code TerrainHilly}），所以这里属于「装饰按基准群系、地形保留现状」的**推断**接法。
 */
public class RealisticBiomeVanillaRoofedForestM extends RealisticBiomeBase {

    public static Biome biome = Biomes.MUTATED_ROOFED_FOREST;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaRoofedForestM() {

        super(biome);
    }

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_BLOCK).set("");
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaRoofedForestM();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainStone(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, false, null, 0.6f);
    }

    @Override
    public void initDecos() {

        // 推断：与基准群系 RoofedForest 同源（RWG DarkRedwood）。
        RtgForestDecos.addDarkRedwood(this);
    }

    @Override
    public boolean allowVanillaTrees() {
        return false;
    }

    public static class TerrainVanillaRoofedForestM extends TerrainBase {

        public TerrainVanillaRoofedForestM() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            return terrainGrasslandMountains(x, y, rtgWorld, river);
        }
    }
}
