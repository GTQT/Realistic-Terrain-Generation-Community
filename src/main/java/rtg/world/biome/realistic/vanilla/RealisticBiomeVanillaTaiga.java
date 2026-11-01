package rtg.world.biome.realistic.vanilla;


import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceMountainSnow;
import rtg.api.world.terrain.TerrainBase;
import rtg.world.biome.RtgTaigaDecos;


public class RealisticBiomeVanillaTaiga extends RealisticBiomeBase {

    public static Biome biome = Biomes.TAIGA;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaTaiga() {

        super(biome, BeachType.STONE);
    }

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_VILLAGES.set(true);
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_BLOCK).set("");
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaTaiga();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainSnow(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, true, Blocks.SAND.getDefaultState(), 0.2f);
    }

    @Override
    public void initDecos() {

        // RWG `land/RealisticBiomeTaigaHills.rDecorate`（地形/地表/装饰同源）。
        RtgTaigaDecos.addTaigaHills(this);
    }

    public boolean allowVanillaTrees() {return false;}
    
    public static class TerrainVanillaTaiga extends TerrainBase {

        public TerrainVanillaTaiga() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG TaigaHills -> TerrainMountainRiver()
            return terrainMountainRiver(x, y, rtgWorld, river);
        }
    }
    
   

}
