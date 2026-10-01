package rtg.world.biome.realistic.vanilla;

import net.minecraft.init.Biomes;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkPrimer;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.collection.DecoCollectionDesert;
import rtg.api.world.deco.collection.DecoCollectionDesertRiver;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;

import rtg.api.world.surface.SurfaceMountainStone;


public class RealisticBiomeVanillaDesertM extends RealisticBiomeBase {

    public static Biome biome = Biomes.MUTATED_DESERT;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaDesertM() {

        super(biome);
    }

    @Override
    public void initConfig() {
        this.getConfig().SURFACE_WATER_LAKE_MULT.set(0.0f);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
        this.getConfig().ALLOW_RIVERS.set(false);
        this.getConfig().addProperty(this.getConfig().ALLOW_CACTUS).set(true);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaDesertM();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainStone(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, false, null, 0f, 1.5f, 60f, 65f, 1.5f);
    }

    @Override
    public void rReplace(ChunkPrimer primer, int i, int j, int x, int y, int depth, RTGWorld rtgWorld, float[] noise, float river, Biome[] base) {

        this.rReplaceWithRiver(primer, i, j, x, y, depth, rtgWorld, noise, river, base);
    }

    @Override
    public void initDecos() {

        this.addDecoCollection(new DecoCollectionDesertRiver(this.getConfig()));
        this.addDecoCollection(new DecoCollectionDesert(this.getConfig()));
    }

    @Override
    public void overrideDecorations() {
        baseBiome().decorator.cactiPerChunk = -999;
    }

    public static class TerrainVanillaDesertM extends TerrainBase {

        public TerrainVanillaDesertM() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // 依据：RWG 沙漠族的地形只有四个 —— Desert(150,50,0) / DesertMountains(230,120,0) /
            // DuneValley(TerrainDunes) / Oasis(230,120,20,60,63)，**没有** GrasslandHills。
            // 原先的 `(70f, 200f, 7f, …)` 在 RWG 全仓找不到对应的 `new TerrainGrasslandHills(...)`。
            // "Desert M"（变异沙漠）在 RWG 无对应群系，按**同类类推**取沙漠族的山地档
            // （与 VanillaDesertHills 同值；RWG 的 desert\DesertMountains 与
            //  red\RedDesertMountains 本来就共用这一组参数）。
            return terrainHilly(x, y, rtgWorld, river, 230f, 120f, 0f, 260f, 68f);
        }
    }

}
