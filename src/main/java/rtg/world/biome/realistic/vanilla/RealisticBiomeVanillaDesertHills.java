package rtg.world.biome.realistic.vanilla;

import net.minecraft.init.Biomes;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkPrimer;
import rtg.api.world.RTGWorld;
import rtg.api.world.deco.collection.DecoCollectionDesert;
import rtg.api.world.deco.collection.DecoCollectionDesertRiver;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceMountainStone;


public class RealisticBiomeVanillaDesertHills extends RealisticBiomeBase {

    public static Biome biome = Biomes.DESERT_HILLS;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaDesertHills() {

        super(biome);
    }

    @Override
    public void initConfig() {
        this.getConfig().SURFACE_WATER_LAKE_MULT.set(0.0f);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
        this.getConfig().addProperty(this.getConfig().ALLOW_CACTUS).set(true);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaDesertHills();
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

    public static class TerrainVanillaDesertHills extends TerrainBase {

        public TerrainVanillaDesertHills() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // 依据：RWG desert\RealisticBiomeDesertMountains.java -> `terrain = new TerrainHilly(230f, 120f, 0f)`
            //（red\RealisticBiomeRedDesertMountains 用的是同一组参数）。
            // RWG 沙漠族只有四个地形：Desert(150,50,0) / DesertMountains(230,120,0) /
            // DuneValley(TerrainDunes) / Oasis(230,120,20,60,63) —— **没有** GrasslandHills。
            // 原先的 `GrasslandHills(70f, 200f, 7f, …)` 在 RWG 全仓都找不到（RWG 只有
            // steppe/thicket 的 (70f, 180f, …)，且那两条都被 /* */ 注释掉了）。
            // 3 参写法照附录 E6 补齐 RWG 的默认 lakeWidth=260f / terrainHeight=68f。
            return terrainHilly(x, y, rtgWorld, river, 230f, 120f, 0f, 260f, 68f);
        }
    }

}
