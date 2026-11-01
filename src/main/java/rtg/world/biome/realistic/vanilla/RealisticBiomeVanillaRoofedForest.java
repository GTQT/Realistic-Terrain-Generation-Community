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
 * 原版黑森林。地形/地表照抄 RWG {@code forest/RealisticBiomeDarkRedwood}：
 * {@code TerrainHilly(230f, 120f, 0f)} + {@code SurfaceMountainStone(grass, dirt, ...)}。
 *
 * <p>装饰本轮改为 RWG {@code RealisticBiomeDarkRedwood.rDecorate} 的逐行移植
 * （大松树 + 小松/橡树/白桦 + 倒木 + 灌木 + 野麦 + 南瓜 + 8×花 + 12×草）。
 * 原来的 RTG 深色橡木/红树/龙脑香组合已移除。
 */
public class RealisticBiomeVanillaRoofedForest extends RealisticBiomeBase {

    public static Biome biome = Biomes.ROOFED_FOREST;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaRoofedForest() {

        super(biome);
    }

    @Override
    public void initConfig() {
        this.getConfig().SURFACE_WATER_LAKE_MULT.set(0.5f);
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
        this.getConfig().addProperty(this.getConfig().ALLOW_COBWEBS).set(true);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_BLOCK).set("");
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaRoofedForest();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainStone(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, false, null, 0f, 1.5f, 60f, 65f, 1.5f);
    }

    @Override
    public void initDecos() {

        // RWG `forest/RealisticBiomeDarkRedwood.rDecorate`（地形/地表/装饰同源）。
        RtgForestDecos.addDarkRedwood(this);
    }

    @Override
    public boolean allowVanillaTrees() {
        return false;
    }

    @Override
    public void overrideDecorations() {
        baseBiome().decorator.grassPerChunk = 3;
        baseBiome().decorator.flowersPerChunk = 1;
    }

    @Override
    public boolean overridesHardcoded() {
        return true;
    }

    public static class TerrainVanillaRoofedForest extends TerrainBase {

        public TerrainVanillaRoofedForest() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG DarkRedwood -> TerrainHilly(230f,120f,0f)
            return terrainHilly(x, y, rtgWorld, river, 230f, 120f, 0f, 260f, 68f);
        }
    }
}
