package rtg.world.biome.realistic.biomesoplenty;


import biomesoplenty.api.block.BOPBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import rtg.api.util.Distribution;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoTree;
import rtg.api.world.deco.DecoTreeSet;
import rtg.api.world.gen.feature.tree.bop.BOPTreeMaterials;
import rtg.api.world.gen.feature.tree.rtg.TreeRTG;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGCeibaPentandra;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGCeibaRosea;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGResizable;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceGrassland;
import rtg.api.world.terrain.TerrainBase;
import rtg.event.EventHandlerCommon;

import java.util.ArrayList;


public class RealisticBiomeBOPMangrove extends RealisticBiomeBase {

    public RealisticBiomeBOPMangrove(final Biome biome) { super(biome); }

    @Override
    public void initDecos() {

        EventHandlerCommon.treeGenerationManager.suppressBOPBiome(this.baseBiome());
        TreeRTG petandraTree = new TreeRTGCeibaPentandra();
        petandraTree.setMaterials(BOPTreeMaterials.mangrove);
        ArrayList<IBlockState> ground = petandraTree.getValidGroundBlocks();
        ground.add(BOPBlocks.mud.getDefaultState());
        petandraTree.setValidGroundBlocks(ground);
        TreeRTGResizable variablePetandra = new TreeRTGResizable(petandraTree);
        variablePetandra.changeAverageHeightSqrt(-1f);
        variablePetandra.changeHeightNoiseVariability(-1);
        DecoTree petandraDeco = new DecoTree(variablePetandra);

        TreeRTG roseaTree = new TreeRTGCeibaRosea();
        roseaTree.setMaterials(BOPTreeMaterials.mangrove);
        roseaTree.setValidGroundBlocks(ground);
        TreeRTGResizable variableRosea = new TreeRTGResizable(roseaTree);
        variableRosea.changeAverageHeightSqrt(-1f);
        variableRosea.changeHeightNoiseVariability(-1);
        DecoTree roseaDeco = new DecoTree(variableRosea);

        DecoTreeSet treeCombination = new DecoTreeSet();
        treeCombination.add(petandraDeco, 10);
        treeCombination.add(roseaDeco, 5);
        treeCombination.setDistribution(new Distribution(RTGWorld.getTreeFrequencyNoiseDivisor(), 3f, 6.0f));

        this.treeGenerator = treeCombination;
    }

    @Override
    public Biome preferredBeach() {
        return baseBiome();
    }

    @Override
    public void initConfig() {
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPMangrove();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }

    public static class TerrainBOPMangrove extends TerrainBase {

        public TerrainBOPMangrove() {
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG SupportBOP.java:532-537  mangrove -> TerrainSwampRiver()
            return terrainSwampRiver(x, y, rtgWorld, river);
        }
    }

}
