package rtg.world.biome.realistic.biomesoplenty;


import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import rtg.api.util.Distribution;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoShrub;
import rtg.api.world.deco.DecoTree;
import rtg.api.world.deco.DecoTreeSet;
import rtg.api.world.deco.collection.DecoCollectionBase;
import rtg.api.world.gen.feature.tree.bop.BOPTreeMaterials;
import rtg.api.world.gen.feature.tree.rtg.TreeRTG;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGQuercusRobur;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceGrassland;
import rtg.api.world.terrain.TerrainBase;
import rtg.event.EventHandlerCommon;


public class RealisticBiomeBOPSacredSprings extends RealisticBiomeBase {

    public RealisticBiomeBOPSacredSprings(final Biome biome) { super(biome); }

    @Override
    public void initDecos() {

        EventHandlerCommon.treeGenerationManager.suppressBOPBiome(this.baseBiome());

        TreeRTGQuercusRobur sacredTree = new TreeRTGQuercusRobur();
        sacredTree.setMinCrownSize(16);
        sacredTree.setMaxCrownSize(20);
        sacredTree.setMinTrunkSize(8);
        sacredTree.setMaxTrunkSize(12);
        sacredTree.setTrunkWidth(2);
        sacredTree.setMaterials(BOPTreeMaterials.sacredOak);
        DecoTree sacredDeco = new DecoTree(sacredTree);

        TreeRTG oakTree = new TreeRTGQuercusRobur();
        oakTree.setMinCrownSize(6);
        oakTree.setMaxCrownSize(10);
        oakTree.setMinTrunkSize(6);
        oakTree.setMaxTrunkSize(8);
        oakTree.setLeafChoice(BOPTreeMaterials.floweringOak());
        DecoTree oakDeco = new DecoTree(oakTree);

        DecoTreeSet treeCombination = new DecoTreeSet();
        treeCombination.add(sacredDeco, 1);
        treeCombination.add(oakDeco, 3);
        treeCombination.setDistribution(new Distribution(RTGWorld.getTreeFrequencyNoiseDivisor(), 2.5f, 4.5f));

        DecoShrub shrubs = new DecoShrub();
        shrubs.setLeafChoice(BOPTreeMaterials.floweringOak());
        shrubs.setLoops(8);

        DecoCollectionBase rtgTreeColl = new DecoCollectionBase(getConfig());
        rtgTreeColl.addDeco(treeCombination);
        rtgTreeColl.addDeco(shrubs);

        this.treeGenerator = rtgTreeColl;
    }

    @Override
    public Biome preferredBeach() {
        return baseBiome();
    }

    @Override
    public void initConfig() {
        this.getConfig().SURFACE_WATER_LAKE_MULT.set(0.5f);
        this.getConfig().ALLOW_RIVERS.set(false);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPSacredSprings(150f, 30f, 68f);
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }

    public static class TerrainBOPSacredSprings extends TerrainBase {

        private float width;
        private float strength;
        private float lakeDepth;
        private float lakeWidth;
        private float terrainHeight;

        public TerrainBOPSacredSprings(float mountainWidth, float mountainStrength, float height) {

            width = mountainWidth;
            strength = mountainStrength;
            terrainHeight = height;
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG SupportBOP.java:680-684  sacredSprings -> TerrainHighland(0f, 120f, 68f, 200f)
            return terrainHighland(x, y, rtgWorld, river, 0f, 120f, 68f, 200f);
        }
    }

}
