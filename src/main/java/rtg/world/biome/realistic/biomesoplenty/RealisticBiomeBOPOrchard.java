package rtg.world.biome.realistic.biomesoplenty;


import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import rtg.api.util.Distribution;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoFallenTree;
import rtg.api.world.deco.DecoVariableOak;
import rtg.api.world.deco.DecoVariableTree;
import rtg.api.world.gen.feature.tree.bop.BOPTreeMaterials;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGQuercusFalcata;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceGrassland;
import rtg.api.world.terrain.TerrainBase;
import rtg.event.EventHandlerCommon;

import static rtg.api.world.deco.DecoFallenTree.LogCondition.RANDOM_CHANCE;


public class
RealisticBiomeBOPOrchard extends RealisticBiomeBase {

    public RealisticBiomeBOPOrchard(final Biome biome) { super(biome); }

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_VILLAGES.set(true);
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPOrchard(58f, 67f, 25f);
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }

    @Override
    public void initDecos() {

        EventHandlerCommon.treeGenerationManager.suppressBOPBiome(this.baseBiome());

        DecoVariableTree orchardTree = new DecoVariableOak();
        orchardTree.setTallTree(new TreeRTGQuercusFalcata());
        orchardTree.changeAverageHeightSqrt(-.6f);
        orchardTree.changeHeightNoiseVariability(-1);
        orchardTree.setLeafChoice(BOPTreeMaterials.floweringOak());
        orchardTree.setDistribution(new Distribution(RTGWorld.getTreeFrequencyNoiseDivisor(), 1.5f, 5.5f));

        this.treeGenerator = orchardTree;

        DecoFallenTree decoFallenTree = new DecoFallenTree();
        decoFallenTree.getDistribution().setNoiseDivisor(80f);
        decoFallenTree.getDistribution().setNoiseFactor(60f);
        decoFallenTree.getDistribution().setNoiseAddend(-15f);
        decoFallenTree.setLogCondition(RANDOM_CHANCE);
        decoFallenTree.setLogConditionChance(10);
        decoFallenTree.setLogBlock(Blocks.LOG.getDefaultState());
        decoFallenTree.setLeavesBlock(Blocks.LEAVES.getDefaultState());
        decoFallenTree.setMinSize(2);
        decoFallenTree.setMaxSize(3);
        this.addDeco(decoFallenTree, this.getConfig().ALLOW_LOGS.get());
    }

    public static class TerrainBOPOrchard extends TerrainBase {

        private float minHeight;
        private float maxHeight;
        private float hillStrength;

        public TerrainBOPOrchard(float minHeight, float maxHeight, float hillStrength) {

            this.minHeight = minHeight;
            this.maxHeight = (maxHeight > rollingHillsMaxHeight) ? rollingHillsMaxHeight : ((maxHeight < this.minHeight) ? rollingHillsMaxHeight : maxHeight);
            this.hillStrength = hillStrength;
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // 推断：RWG 无 orchard；果园多为缓坡林地，按草原族处理
            return terrainGrasslandHills(x, y, rtgWorld, river, 90f, 180f, 13f, 100f, 38f, 260f, 71f);
        }
    }

}
