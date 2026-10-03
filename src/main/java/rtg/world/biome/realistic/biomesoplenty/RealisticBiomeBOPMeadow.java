package rtg.world.biome.realistic.biomesoplenty;


import biomesoplenty.api.biome.BOPBiomes;
import net.minecraft.world.biome.Biome;
import rtg.api.util.Distribution;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoTree;
import rtg.api.world.deco.DecoTreeSet;
import rtg.api.world.gen.feature.tree.bop.BOPTreeMaterials;
import rtg.api.world.gen.feature.tree.bop.TreeBOPJacaranda;
import rtg.api.world.gen.feature.tree.bop.TreeBOPMediumFir;
import rtg.api.world.gen.feature.tree.rtg.TreeMaterials;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGBetulaUtilis;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGResizable;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceMountainStone;
import rtg.api.world.terrain.TerrainBase;
import rtg.event.EventHandlerCommon;


public class RealisticBiomeBOPMeadow extends RealisticBiomeBase {

    public RealisticBiomeBOPMeadow(final Biome biome) { super(biome); }

    @Override
    public void initDecos() {

        EventHandlerCommon.treeGenerationManager.suppressBOPBiome(this.baseBiome());
        TreeRTGBetulaUtilis ballonTree = new TreeRTGBetulaUtilis();
        ballonTree.setMaterials(BOPTreeMaterials.fir);
        TreeRTGResizable variableMaple = new TreeRTGResizable(ballonTree);
        variableMaple.changeAverageHeightSqrt(-1f);
        variableMaple.changeHeightNoiseVariability(-.75f);
        DecoTree mapleDeco = new DecoTree(variableMaple);

        TreeBOPJacaranda babyOak = new TreeBOPJacaranda();
        babyOak.setMaterials(TreeMaterials.Picker.oak);
        TreeRTGResizable variableOak = new TreeRTGResizable(babyOak);
        variableOak.changeAverageHeightSqrt(-1f);
        variableOak.changeHeightNoiseVariability(-.75f);
        DecoTree oakDeco = new DecoTree(variableOak);

        TreeBOPMediumFir scatteredPines = new TreeBOPMediumFir();
        scatteredPines.setTrunkProportionVariability(.15f);
        scatteredPines.setMaterials(TreeMaterials.Picker.spruce);
        TreeRTGResizable variablePines = new TreeRTGResizable(scatteredPines);
        variablePines.changeAverageHeightSqrt(-1f);
        variablePines.changeHeightNoiseVariability(-.75f);
        DecoTree pineDeco = new DecoTree(variablePines);

        DecoTreeSet trees = new DecoTreeSet();
        trees.setDistribution(new Distribution(RTGWorld.getTreeFrequencyNoiseDivisor(), .5f, .5f));
        trees.add(pineDeco, 3);
        trees.add(mapleDeco, 1);
        trees.add(oakDeco, 1);

        this.treeGenerator = trees;
    }

    @Override
    public Biome preferredBeach() {
        return BOPBiomes.gravel_beach.orNull();
    }

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_VILLAGES.set(true);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPMeadow();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainStone(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, false, null, 1.2f);
    }

    public static class TerrainBOPMeadow extends TerrainBase {

        public TerrainBOPMeadow() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG SupportBOP.java  meadow -> TerrainMountainSpikes()
            return terrainMountainSpikes(x, y, rtgWorld, river);
        }
    }

}
