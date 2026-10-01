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


public class RealisticBiomeVanillaMesa extends RealisticBiomeBase {

    public static Biome biome = Biomes.MESA;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaMesa() {

        super(biome);
    }

    @Override
    public Biome preferredBeach() {
        return biome;
    }

    @Override
    public void initConfig() {
        this.getConfig().SURFACE_WATER_LAKE_MULT.set(0.1f);
        this.getConfig().addProperty(this.getConfig().ALLOW_CACTUS).set(true);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_BLOCK).set("");
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_2_BLOCK).set("");
        this.getConfig().addProperty(this.getConfig().ALLOW_PLATEAU_MODIFICATIONS).set(false);
        this.getConfig().addProperty(this.getConfig().PLATEAU_GRADIENT_BLOCK_LIST).set(PlateauUtil.getMesaPlateauBlocks());
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaMesa();
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

    public static class TerrainVanillaMesa extends TerrainBase {

        public TerrainVanillaMesa() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // 依据：RWG red\RealisticBiomeMesa.java -> `terrain = new TerrainMesa()`
            //（全 RWG 只有 RealisticBiomeMesa 与 RealisticBiomeMesaPlains 用它）
            return terrainMesa(x, y, rtgWorld, border, river);
        }
    }

}
