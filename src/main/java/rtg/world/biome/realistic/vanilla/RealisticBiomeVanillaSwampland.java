package rtg.world.biome.realistic.vanilla;

import net.minecraft.block.BlockPlanks.EnumType;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import rtg.api.util.BlockUtil;
import rtg.api.world.RTGWorld;
import rtg.api.world.WaterLevel;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.*;
import rtg.api.world.gen.feature.tree.rtg.TreeRTG;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGPinusPonderosa;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGSalixMyrtilloides;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;

import rtg.api.world.surface.SurfaceGrassland;


public class RealisticBiomeVanillaSwampland extends RealisticBiomeBase {

    public static Biome biome = Biomes.SWAMPLAND;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaSwampland() {

        super(biome);
    }

    @Override
    public Biome preferredBeach() {
        return biome;
    }

    @Override
    public void initConfig() {
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaSwampland();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }

    @Override
    public void initDecos() {

        TreeRTG myrtilloidesTree = new TreeRTGSalixMyrtilloides();
        myrtilloidesTree.setLogBlock(Blocks.LOG.getDefaultState());
        myrtilloidesTree.setLeavesBlock(Blocks.LEAVES.getDefaultState());
        this.addTree(myrtilloidesTree);

        DecoTree decoTrees = new DecoTree(myrtilloidesTree);
        decoTrees.setStrengthNoiseFactorXForLoops(true);
        decoTrees.setStrengthFactorForLoops(1f);
        decoTrees.getDistribution().setNoiseDivisor(80f);
        decoTrees.getDistribution().setNoiseFactor(60f);
        decoTrees.getDistribution().setNoiseAddend(-15f);
        decoTrees.setTreeType(DecoTree.TreeType.RTG_TREE);
        decoTrees.setTreeCondition(DecoTree.TreeCondition.RANDOM_CHANCE);
        decoTrees.setTreeConditionChance(12);
        decoTrees.setMaxY(70);
        this.addDeco(decoTrees);

        TreeRTG ponderosaTree = new TreeRTGPinusPonderosa();
        ponderosaTree.setLogBlock(Blocks.LOG.getDefaultState());
        ponderosaTree.setLeavesBlock(Blocks.LEAVES.getDefaultState());
        ponderosaTree.setMinTrunkSize(3);
        ponderosaTree.setMaxTrunkSize(6);
        ponderosaTree.setMinCrownSize(6);
        ponderosaTree.setMaxCrownSize(12);
        ponderosaTree.setNoLeaves(true);
        this.addTree(ponderosaTree);

        DecoTree deadPineTree = new DecoTree(ponderosaTree);
        deadPineTree.setTreeType(DecoTree.TreeType.RTG_TREE);
        deadPineTree.setTreeCondition(DecoTree.TreeCondition.RANDOM_CHANCE);
        deadPineTree.setTreeConditionChance(18);
        deadPineTree.setMaxY(90);
        this.addDeco(deadPineTree);

        DecoShrub decoShrub = new DecoShrub();
        decoShrub.setMaxY(100);
        decoShrub.setLoopMultiplier(2f);
        this.addDeco(decoShrub);

        DecoFallenTree decoFallenTree = new DecoFallenTree();
        decoFallenTree.getDistribution().setNoiseDivisor(80f);
        decoFallenTree.getDistribution().setNoiseFactor(60f);
        decoFallenTree.getDistribution().setNoiseAddend(-15f);
        decoFallenTree.setLogConditionChance(6);
        decoFallenTree.setLogBlock(BlockUtil.getStateLog(EnumType.DARK_OAK));
        decoFallenTree.setLeavesBlock(BlockUtil.getStateLeaf(EnumType.DARK_OAK));
        decoFallenTree.setMinSize(3);
        decoFallenTree.setMaxSize(6);
        this.addDeco(decoFallenTree, this.getConfig().ALLOW_LOGS.get());

        DecoPumpkin decoPumpkin = new DecoPumpkin();
        decoPumpkin.setMaxY(90);
        decoPumpkin.setRandomFloat(50f);
        this.addDeco(decoPumpkin);
    }

    public static class TerrainVanillaSwampland extends TerrainBase {

        public TerrainVanillaSwampland() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            return terrainMarsh(x, y, rtgWorld, WaterLevel.current().waterSurfaceTop(), river);
        }
    }

}
