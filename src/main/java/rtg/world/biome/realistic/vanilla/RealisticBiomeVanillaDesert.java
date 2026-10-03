package rtg.world.biome.realistic.vanilla;

import net.minecraft.init.Biomes;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkPrimer;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.collection.DecoCollectionDesert;
import rtg.api.world.deco.collection.DecoCollectionDesertRiver;
import rtg.api.world.gen.RTGChunkGenSettings;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;

import rtg.api.world.surface.SurfaceDesertMountain;


public class RealisticBiomeVanillaDesert extends RealisticBiomeBase {

    public static Biome biome = Biomes.DESERT;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaDesert() {

        super(biome);
    }

    @Override
    public void initConfig() {
        this.getConfig().SURFACE_WATER_LAKE_MULT.set(0.0f);
        this.getConfig().ALLOW_VILLAGES.set(true);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
        this.getConfig().SURFACE_FILLER_BLOCK.set("minecraft:sandstone");
        this.getConfig().addProperty(this.getConfig().ALLOW_CACTUS).set(true);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaDesert();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceDesertMountain(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, false, null, 0f, 1.5f, 60f, 65f, 1.5f);
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

    /**
     * 沙丘（RTG 时代的实现，**没有 RWG 对应物**）。
     *
     * <p>它用的是 {@code terrainPolar} 的**参数化**重载 —— 算式与 RWG 的 {@code TerrainPolar}
     * 逐字相同，只是把 5 个字面量提成形参，好让本群系传入自己的沙丘参数。
     *
     * <p>RWG 的沙漠族是 {@code desert\RealisticBiomeDesert → TerrainHilly(150f, 50f, 0f)}，
     * <b>没有</b>沙丘；RWG 的沙丘在 {@code RealisticBiomeDuneValley → TerrainDunes}。
     * 本群系保持 RTG 的沙丘实现，理由有两条：
     * <ol>
     *   <li>它是 {@code sandDuneHeight} 这个 GUI 滑条（1–10）的**唯一**消费者，
     *       换成 {@code terrainHilly} 会让该滑条变成死配置；</li>
     *   <li>视觉上它产出的是沙丘脊线，与"MC 沙漠"的观感相符；换成
     *       {@code terrainHilly(150,50,0)} 会变成普通的沙漠丘陵。</li>
     * </ol>
     * 这是一处**已知偏离**，见 {@code docs/rwg-port-gaps.md} §20。若你要"完全照抄"，
     * 改法是把下面这行换成
     * {@code return terrainHilly(x, y, rtgWorld, river, 150f, 50f, 0f, 260f, 68f);}
     *（与 {@code BOPColdDesert} 现在的写法一致），代价是滑条失效。
     */
    public static class TerrainVanillaDesert extends TerrainBase {

        public TerrainVanillaDesert() {

            super(64);
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            RTGChunkGenSettings settings = rtgWorld.getGeneratorSettings();
            float duneHeight = (minDuneHeight + settings.sandDuneHeight);

            duneHeight *= (1f + rtgWorld.simplexInstance(2).noise2f(x / 330f, y / 330f)) / 2f;

            float stPitch = 200f;    // The higher this is, the more smoothly dunes blend with the terrain
            float stFactor = duneHeight;
            float hPitch = 70;    // Dune scale
            float hDivisor = 40;

            return terrainPolar(x, y, rtgWorld, river, stPitch, stFactor, hPitch, hDivisor, base) + groundNoise(x, y, 1f, rtgWorld);
        }
    }

}
