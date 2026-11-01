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


public class RealisticBiomeVanillaColdTaigaHills extends RealisticBiomeBase {

    public static Biome biome = Biomes.COLD_TAIGA_HILLS;
    public static Biome river = Biomes.FROZEN_RIVER;

    public RealisticBiomeVanillaColdTaigaHills() {

        super(biome, RiverType.FROZEN, BeachType.COLD);
    }

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaColdTaigaHills();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainSnow(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, true, Blocks.SAND.getDefaultState(), 0.2f);
    }
    
    @Override
    public boolean allowVanillaTrees() {
    	return false;
    }
    
    @Override
    public void initDecos() {

        // RWG `land/RealisticBiomeTaigaHills.rDecorate`（地形/地表/装饰同源；冷针叶林同族）。
        RtgTaigaDecos.addTaigaHills(this);
    }

    public static class TerrainVanillaColdTaigaHills extends TerrainBase {

        public TerrainVanillaColdTaigaHills() {

            base = 72f;
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // 照抄 RWG land\RealisticBiomeTaigaHills.java -> TerrainMountainRiver()（冷针叶林丘陵与 Taiga 同族）
            return terrainMountainRiver(x, y, rtgWorld, river);
        }
    }

}
