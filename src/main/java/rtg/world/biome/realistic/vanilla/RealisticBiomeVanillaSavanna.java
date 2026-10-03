package rtg.world.biome.realistic.vanilla;

import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.collection.DecoCollectionSavanna;
import rtg.api.world.deco.collection.trees.DecoCollectionSavannaTrees;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceGrasslandMix1;
import rtg.api.world.terrain.TerrainBase;


public class RealisticBiomeVanillaSavanna extends RealisticBiomeBase {

    public static Biome biome = Biomes.SAVANNA;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaSavanna() {

        super(biome);
    }

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_VILLAGES.set(true);
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
        this.getConfig().addProperty(this.getConfig().ALLOW_CACTUS).set(true);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_BLOCK).set("");
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaSavanna();
    }

    @Override
    public SurfaceBase initSurface() {
        // 照抄 RWG `savanna/RealisticBiomeSavanna.java:35-42` 的**同一份配对**：
        //     terrain = new TerrainGrasslandFlats();
        //     surface = new SurfaceGrasslandMix1(Blocks.grass, Blocks.dirt, Blocks.sand,
        //                                        Blocks.stone, Blocks.cobblestone, 13f, 0.27f);
        // （地形那一半早就照它接了，地表那一半此前留在 `SurfaceGrassland` 上 —— 只接了一半。）
        return new SurfaceGrasslandMix1(getConfig(), Blocks.GRASS.getDefaultState(), Blocks.DIRT.getDefaultState(),
                Blocks.SAND.getDefaultState(), Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState(),
                13f, 0.27f);
    }
    
    @Override
    public boolean allowVanillaTrees() {
    	return false;
    }
    

    @Override
    public void initDecos() {

        //this.addDecoCollection(new DecoCollectionDesertRiver(this.getConfig()));
        this.addDecoCollection(new DecoCollectionSavanna(this.getConfig()));
        this.treeGenerator = new DecoCollectionSavannaTrees(this.getConfig());
    }

    @Override
    public void overrideDecorations() {
        baseBiome().decorator.cactiPerChunk = -999;
    }

    public static class TerrainVanillaSavanna extends TerrainBase {

        public TerrainVanillaSavanna() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // 依据：RWG savanna\RealisticBiomeSavanna.java -> `terrain = new TerrainGrasslandFlats()`
            return terrainGrasslandFlats(x, y, rtgWorld, river);
        }
    }

}
