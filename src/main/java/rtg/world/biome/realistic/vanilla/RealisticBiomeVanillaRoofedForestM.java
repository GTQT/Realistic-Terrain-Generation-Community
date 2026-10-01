package rtg.world.biome.realistic.vanilla;

import net.minecraft.block.BlockPlanks.EnumType;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import rtg.api.util.BlockUtil;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.*;
import rtg.api.world.gen.feature.tree.rtg.TreeRTG;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGRhizophoraMucronata;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;

import rtg.api.world.surface.SurfaceMountainStone;


public class RealisticBiomeVanillaRoofedForestM extends RealisticBiomeBase {

    public static Biome biome = Biomes.MUTATED_ROOFED_FOREST;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaRoofedForestM() {

        super(biome);
    }

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_BLOCK).set("");

    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaRoofedForestM();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainStone(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, false, null, 0.6f);
    }

    @Override
    public void initDecos() {

        DecoBoulder decoBoulder = new DecoBoulder();
        decoBoulder.setBoulderBlock(Blocks.MOSSY_COBBLESTONE.getDefaultState());
        decoBoulder.setChance(20);
        decoBoulder.setMaxY(80);
        decoBoulder.setStrengthFactor(2f);
        this.addDeco(decoBoulder);

        TreeRTG mucronataTree = new TreeRTGRhizophoraMucronata(3, 4, 13f, 0.32f, 0.1f);
        mucronataTree.setLogBlock(BlockUtil.getStateLog(EnumType.DARK_OAK));
        mucronataTree.setLeavesBlock(BlockUtil.getStateLeaf(EnumType.DARK_OAK));
        mucronataTree.setMinTrunkSize(3);
        mucronataTree.setMaxTrunkSize(4);
        mucronataTree.setMinCrownSize(7);
        mucronataTree.setMaxCrownSize(12);
        this.addTree(mucronataTree);

        DecoTree decoTrees = new DecoTree(mucronataTree);
        decoTrees.setStrengthFactorForLoops(6f);
        decoTrees.getDistribution().setNoiseDivisor(80f);
        decoTrees.getDistribution().setNoiseFactor(60f);
        decoTrees.getDistribution().setNoiseAddend(-15f);
        decoTrees.setTreeType(DecoTree.TreeType.RTG_TREE);
        decoTrees.setTreeCondition(DecoTree.TreeCondition.NOISE_GREATER_AND_RANDOM_CHANCE);
        decoTrees.setTreeConditionNoise(0f);
        decoTrees.setTreeConditionChance(1);
        decoTrees.setMaxY(120);
        this.addDeco(decoTrees);

        DecoFallenTree decoFallenTree = new DecoFallenTree();
        decoFallenTree.getDistribution().setNoiseDivisor(80f);
        decoFallenTree.getDistribution().setNoiseFactor(60f);
        decoFallenTree.getDistribution().setNoiseAddend(-15f);
        decoFallenTree.setLogConditionChance(16);
        decoFallenTree.setLogBlock(BlockUtil.getStateLog(EnumType.DARK_OAK));
        decoFallenTree.setLeavesBlock(BlockUtil.getStateLeaf(EnumType.DARK_OAK));
        decoFallenTree.setMinSize(4);
        decoFallenTree.setMaxSize(9);
        this.addDeco(decoFallenTree, this.getConfig().ALLOW_LOGS.get());

        DecoShrub decoShrub = new DecoShrub();
        decoShrub.setMaxY(110);
        decoShrub.setLoopMultiplier(1f);
        this.addDeco(decoShrub);

        DecoDeadBush decoDeadBush = new DecoDeadBush();
        decoDeadBush.setMaxY(128);
        decoDeadBush.setChance(16);
        decoDeadBush.setStrengthFactor(1f);
        this.addDeco(decoDeadBush);

        DecoMushrooms decoMushrooms = new DecoMushrooms();
        decoMushrooms.setMaxY(90);
        decoMushrooms.setRandomType(DecoMushrooms.RandomType.ALWAYS_GENERATE);
        this.addDeco(decoMushrooms);
    }

    public static class TerrainVanillaRoofedForestM extends TerrainBase {

        public TerrainVanillaRoofedForestM() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            return terrainGrasslandMountains(x, y, rtgWorld, river);
        }
    }

}
