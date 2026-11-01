package rtg.world.biome.realistic.vanilla;

import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceMountainSnow;
import rtg.api.world.terrain.TerrainBase;
import rtg.world.biome.RtgRedwoodDecos;


/** 巨型针叶林丘陵 —— 装饰同 {@link RealisticBiomeVanillaMegaTaiga}（RWG Redwood）。 */
public class RealisticBiomeVanillaMegaTaigaHills extends RealisticBiomeBase {

    public static Biome biome = Biomes.REDWOOD_TAIGA_HILLS;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaMegaTaigaHills() {

        super(biome, BeachType.STONE);
    }

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaMegaTaigaHills();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainSnow(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, true, Blocks.SAND.getDefaultState(), 0.2f);
    }

    @Override
    public void initDecos() {

        RtgRedwoodDecos.addRedwood(this);
    }

    @Override
    public boolean allowVanillaTrees() {
        return false;
    }

    public static class TerrainVanillaMegaTaigaHills extends TerrainBase {

        public TerrainVanillaMegaTaigaHills() {

            base = 72f;
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // 依据：RWG land\RealisticBiomeTaigaHills -> TerrainMountainRiver()；与基准群系 VanillaMegaTaiga 同值
            return terrainMountainRiver(x, y, rtgWorld, river);
        }
    }
}
