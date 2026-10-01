package rtg.world.biome.realistic.vanilla;

import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.collection.DecoCollectionJungle;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;

import rtg.api.world.surface.SurfaceGrassland;


public class RealisticBiomeVanillaJungle extends RealisticBiomeBase {

    public static Biome biome = Biomes.JUNGLE;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaJungle() {

        super(biome);
    }

    @Override
    public void initConfig() {
        this.getConfig().SURFACE_WATER_LAKE_MULT.set(0.5f);
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
        this.getConfig().addProperty(this.getConfig().ALLOW_CACTUS).set(true);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_BLOCK).set("");
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaJungle();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
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

    public static class TerrainVanillaJungle extends TerrainBase {

        public TerrainVanillaJungle() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG `Support.java` 里 **`BiomeGenBase.jungle` 的显式条目**：
            //     new RealisticBiomeSupport(jungle, RWGBiomes.baseRiverWet,
            //                               new TerrainHighland(0f, 140f, 68f, 200f),
            //                               new SurfaceGrassland(jungle.topBlock, jungle.fillerBlock,
            //                                                    Blocks.stone, Blocks.cobblestone))
            //
            // ⚠ 此前接的是 `TerrainHilly(230f,120f,50f,260f,68f)`（注释写"照抄 RWG JungleHills"）——
            // 那是 RWG **另一个**群系（`land/RealisticBiomeJungleHills`）的家族，不是 MC `jungle` 的条目。
            // RWG 对 MC jungle 有 active 条目，就该用它。
            return terrainHighland(x, y, rtgWorld, river, 0f, 140f, 68f, 200f);
        }
    }

}
