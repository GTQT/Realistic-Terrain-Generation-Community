package rtg.world.biome.realistic.vanilla;


import net.minecraft.init.Biomes;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceMountainStone;
import rtg.api.world.terrain.TerrainBase;
import rtg.world.biome.RtgJungleDecos;


public class RealisticBiomeVanillaExtremeHillsPlus extends RealisticBiomeBase {

    public static Biome biome = Biomes.EXTREME_HILLS_WITH_TREES;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaExtremeHillsPlus() {

        super(biome, BeachType.STONE);
    }

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_RIVERS.set(false);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_BLOCK).set("");
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaExtremeHillsPlus();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainStone(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, false, null, 1f, 1.5f, 60f, 65f, 1.5f);
    }

    @Override
    public void initDecos() {

        // 依据：地形是 RWG `land/RealisticBiomeJungleHills -> TerrainHilly(230f,120f,50f)`，
        // 装饰沿用同一来源（RWG 的 ExtremeHillsPlus 没有独立条目）。
        RtgJungleDecos.addJungleHills(this);
    }

    @Override
    public boolean allowVanillaTrees() {
        return false;
    }

    /**
     * RWG-style hilly terrain — rolling mountains with lake basin carving.
     * Uses the original RWG TerrainHilly formula for dramatic peaks and deep valleys.
     */
    public static class TerrainVanillaExtremeHillsPlus extends TerrainBase {

        public TerrainVanillaExtremeHillsPlus() {
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // 依据：RWG 的 `TerrainHilly` **全仓 hills width 恒为 230f**
            //（`(150,50,0)` 的 150 是沙漠特例；`SupportCC` 有一个 100f）。
            // width=240f 在 RWG 里查无此文。
            // 其余与 RWG 的 `land\RealisticBiomeJungleHills -> TerrainHilly(230f, 120f, 50f)` 同值
            //（3 参写法按附录 E6 补齐 lakeWidth=260f / terrainHeight=68f）。
            return terrainHilly(x, y, rtgWorld, river, 230f, 120f, 50f, 260f, 68f);
        }
    }

}
