package rtg.world.biome.realistic.vanilla;


import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.deco.collection.DecoCollectionTaiga;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceMountainSnow;


public class RealisticBiomeVanillaTaigaHills extends RealisticBiomeBase {

    public static Biome biome = Biomes.TAIGA_HILLS;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaTaigaHills() {

        super(biome, BeachType.STONE);
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

        return new TerrainVanillaTaigaHills();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainSnow(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, true, Blocks.SAND.getDefaultState(), 0.2f);
    }

    @Override
    public void initDecos() {

        this.addDecoCollection(new DecoCollectionTaiga(this.getConfig(), 10f));
    }

    public boolean allowVanillaTrees() {return false;}
    
    public static class TerrainVanillaTaigaHills extends TerrainBase {

        private float hillStrength = 40f;

        public TerrainVanillaTaigaHills() {

            this(72f, 40f);
        }

        public TerrainVanillaTaigaHills(float bh, float hs) {

            base = bh;
            hillStrength = hs;
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // 依据：RWG land\RealisticBiomeTaigaHills -> TerrainMountainRiver()；与基准群系 VanillaTaiga 同值
            // 原先的 GrasslandHills(70f, 180f, 7f, ...) 在 RWG 全仓查无此文，且比基准群系**更平**（方向反了）。
            return terrainMountainRiver(x, y, rtgWorld, river);
        }
    }

}
