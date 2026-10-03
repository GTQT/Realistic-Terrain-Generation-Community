package rtg.world.biome.realistic.biomesoplenty;


import biomesoplenty.api.biome.BOPBiomes;
import net.minecraft.world.biome.Biome;
import rtg.api.util.Distribution;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoLeafVines;
import rtg.api.world.deco.DecoTree;
import rtg.api.world.deco.DecoTreeSet;
import rtg.api.world.deco.collection.DecoCollectionBase;
import rtg.api.world.gen.feature.tree.bop.BOPTreeMaterials;
import rtg.api.world.gen.feature.tree.bop.TreeBOPMediumFir;
import rtg.api.world.gen.feature.tree.rtg.TreeMaterials;
import rtg.api.world.gen.feature.tree.rtg.TreeRTG;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGBetulaPopulifolia;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGResizable;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceMountainStone;
import rtg.api.world.terrain.TerrainBase;
import rtg.event.EventHandlerCommon;


public class RealisticBiomeBOPTemperateRainforest extends RealisticBiomeBase {

    public RealisticBiomeBOPTemperateRainforest(final Biome biome) { super(biome); }

    @Override
    public void initDecos() {

        EventHandlerCommon.treeGenerationManager.suppressBOPBiome(this.baseBiome());

        TreeBOPMediumFir redwoodTree = new TreeBOPMediumFir();
        redwoodTree.setMaterials(BOPTreeMaterials.redwood);
        redwoodTree.setWideningRate(.2f);
        redwoodTree.setLowestVariableTrunkProportion(.4f);
        redwoodTree.setTrunkProportionVariability(.15f);
        TreeRTGResizable variableRedwoodTree = new TreeRTGResizable(redwoodTree);
        variableRedwoodTree.changeAverageHeightSqrt(1.5f);
        variableRedwoodTree.changeHeightNoiseVariability(.2f);
        DecoTree redwoodPopulation = new DecoTree(variableRedwoodTree);

        TreeRTG substoryTree = new TreeRTGBetulaPopulifolia();
        substoryTree.setMaxCrownSize(7);
        substoryTree.setTrunkProportionVariability(.5f);
        substoryTree.setMaterials(TreeMaterials.Picker.oak);
        substoryTree.setAbsoluteMinimumTrunk(0);
        TreeRTGResizable variableSubstoryTree = new TreeRTGResizable(substoryTree);
        variableSubstoryTree.changeLocalNoiseVariability(1f);
        DecoTree substoryPopulation = new DecoTree(variableSubstoryTree);

        DecoTreeSet forest = new DecoTreeSet();
        forest.setDistribution(new Distribution(RTGWorld.getTreeFrequencyNoiseDivisor(), 3.5f, 12.0f));
        forest.add(redwoodPopulation, 8);
        forest.add(substoryPopulation, 3);

        // have to add our own vines to the trees
        DecoLeafVines vines = new DecoLeafVines();
        vines.setLoops(10);

        DecoCollectionBase vinedForest = new DecoCollectionBase(this.getConfig());
        vinedForest.addDeco(forest);
        vinedForest.addDeco(vines);

        this.treeGenerator = vinedForest;
    }

    @Override
    public Biome preferredBeach() {
        return BOPBiomes.gravel_beach.orNull();
    }

    @Override
    public void initConfig() {
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPTemperateRainforest();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainStone(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, false, null, 0.45f);
    }

    public static class TerrainBOPTemperateRainforest extends TerrainBase {

        public TerrainBOPTemperateRainforest() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG SupportBOP.java  temperateRainforest -> TerrainMountainRiver()
            return terrainMountainRiver(x, y, rtgWorld, river);
        }
    }

}
