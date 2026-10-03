package rtg.world.biome.realistic.vanilla;

import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkPrimer;
import rtg.api.util.PlateauUtil;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.collection.DecoCollectionDesertRiver;
import rtg.api.world.deco.collection.DecoCollectionMesa;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;

import rtg.api.world.surface.SurfaceMesa;


public class RealisticBiomeVanillaMesaPlateau extends RealisticBiomeBase {

    public RealisticBiomeVanillaMesaPlateau() {

        super(Biomes.MESA_CLEAR_ROCK);
    }

    @Override
    public Biome preferredBeach() {
        return baseBiome();
    }

    @Override
    public void initConfig() {
        this.getConfig().SURFACE_WATER_LAKE_MULT.set(0.1f);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
        this.getConfig().addProperty(this.getConfig().ALLOW_CACTUS).set(true);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_BLOCK).set("");
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_2_BLOCK).set("");
        this.getConfig().addProperty(this.getConfig().ALLOW_PLATEAU_MODIFICATIONS).set(false);
        this.getConfig().addProperty(this.getConfig().PLATEAU_GRADIENT_BLOCK_LIST).set(PlateauUtil.getMesaPlateauBlocks());
    }

    @Override
    public TerrainBase initTerrain() {
        return new TerrainRTGMesaPlateau();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMesa(getConfig(), Blocks.SAND.getDefaultState(), Blocks.SAND.getDefaultState(), (byte) 1);
    }

    @Override
    public void rReplace(ChunkPrimer primer, int i, int j, int x, int y, int depth, RTGWorld rtgWorld, float[] noise, float river, Biome[] base) {

        this.rReplaceWithRiver(primer, i, j, x, y, depth, rtgWorld, noise, river, base);
    }

    @Override
    public void initDecos() {
        this.addDecoCollection(new DecoCollectionDesertRiver(this.getConfig()));
        this.addDecoCollection(new DecoCollectionMesa(this.getConfig()));
    }

    @Override
    public void overrideDecorations() {
        baseBiome().decorator.cactiPerChunk = -999;
    }

    /**
     * 台地（RWG {@code savanna\RealisticBiomeMesaPlains.java} → {@code terrain = new TerrainMesa()}）。
     * <p>
     * rtgc 原先这里是自造的"抖动 + Voronoi 高原"：
     * {@code bordercap = border * 3.5f - 2.5f}、{@code rivercap = 3f * river}、
     * {@code PlateauUtil.stepIncrease(...)} —— 全是 RWG 没有的东西。
     * 其中 {@code rivercap} 还写着 rtgc 的旧 river 约定（1 = 最强河流）；
     * 1.0.10 把 river 改回 RWG 的原样透传后，它已失去意义。
     * 现整体换成 RWG 的 {@code TerrainMesa}（`VanillaMesaPlateauF/FM/M` 共用本类，一并生效）。
     */
    public static class TerrainRTGMesaPlateau extends TerrainBase {

        public TerrainRTGMesaPlateau() {
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            return terrainMesa(x, y, rtgWorld, border, river);
        }
    }

}
