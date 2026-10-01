package rtg.world.biome.realistic.vanilla;

import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.feature.WorldGenTrees;
import rtg.api.util.Distribution;
import rtg.api.util.PlateauUtil;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoTree;
import rtg.api.world.deco.collection.DecoCollectionDesertRiver;
import rtg.api.world.deco.collection.DecoCollectionMesa;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;

import rtg.api.world.surface.SurfaceMesa;


public class RealisticBiomeVanillaMesaPlateauFM extends RealisticBiomeBase {

    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaMesaPlateauFM() {

        super(Biomes.MUTATED_MESA_ROCK);
    }

    @Override
    public Biome preferredBeach() {
        return super.baseBiome();
    }

    @Override
    public void initConfig() {
        this.getConfig().SURFACE_WATER_LAKE_MULT.set(0.1f);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
        this.getConfig().addProperty(this.getConfig().ALLOW_CACTUS).set(true);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_BLOCK).set("");
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_2_BLOCK).set("");
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_3_BLOCK).set("");
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_4_BLOCK).set("");
        this.getConfig().addProperty(this.getConfig().ALLOW_PLATEAU_MODIFICATIONS).set(false);
        this.getConfig().addProperty(this.getConfig().PLATEAU_GRADIENT_BLOCK_LIST).set(PlateauUtil.getMesaPlateauBlocks());
    }

    @Override
    public TerrainBase initTerrain() {

        return new RealisticBiomeVanillaMesaPlateau.TerrainRTGMesaPlateau();
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

        DecoTree decoTree = new DecoTree(new WorldGenTrees(false));
        decoTree.setLoops(16);
        decoTree.setTreeType(DecoTree.TreeType.WORLDGEN);
        decoTree.setDistribution(new Distribution(24f, 1f, 0f));
        decoTree.setTreeConditionChance(1);
        decoTree.setTreeConditionFloat(4f);
        decoTree.setTreeConditionNoise(0f);
        decoTree.setMinY(74);
        addDeco(decoTree);
    }

    @Override
    public void overrideDecorations() {
        baseBiome().decorator.cactiPerChunk = -999;
    }

}
