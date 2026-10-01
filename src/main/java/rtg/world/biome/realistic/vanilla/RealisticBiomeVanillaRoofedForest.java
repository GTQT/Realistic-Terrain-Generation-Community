package rtg.world.biome.realistic.vanilla;

import net.minecraft.block.BlockPlanks.EnumType;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.feature.WorldGenBigMushroom;
import net.minecraftforge.event.terraingen.DecorateBiomeEvent;
import rtg.api.util.BlockUtil;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.*;
import rtg.api.world.deco.helper.DecoHelperRandomSplit;
import rtg.api.world.deco.helper.DecoHelperThisOrThat;
import rtg.api.world.gen.feature.tree.rtg.TreeRTG;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGAcaciaBucheri;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGCeibaPentandra;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGRhizophoraMucronata;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;

import rtg.api.world.surface.SurfaceMountainStone;


public class RealisticBiomeVanillaRoofedForest extends RealisticBiomeBase {

    public static Biome biome = Biomes.ROOFED_FOREST;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaRoofedForest() {

        super(biome);
    }

    @Override
    public void initConfig() {
        this.getConfig().SURFACE_WATER_LAKE_MULT.set(0.5f);
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
        this.getConfig().addProperty(this.getConfig().ALLOW_COBWEBS).set(true);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_BLOCK).set("");
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaRoofedForest();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainStone(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, false, null, 0f, 1.5f, 60f, 65f, 1.5f);
    }

    @Override
    public void initDecos() {

        DecoMushrooms decoMushrooms = new DecoMushrooms();
        decoMushrooms.setChance(4);
        decoMushrooms.setMaxY(90);
        decoMushrooms.setRandomType(DecoMushrooms.RandomType.ALWAYS_GENERATE);
        this.addDeco(decoMushrooms);

        TreeRTG mucronataTree = new TreeRTGRhizophoraMucronata();
        mucronataTree.setLogBlock(BlockUtil.getStateLog(EnumType.DARK_OAK));
        mucronataTree.setLeavesBlock(BlockUtil.getStateLeaf(EnumType.DARK_OAK));
        mucronataTree.setMaxAllowedObstruction(TreeRTG.ROOFED_FOREST_LIGHT_OBSTRUCTION_LIMIT);
        
        mucronataTree.setMinTrunkSize(2);
        mucronataTree.setMaxTrunkSize(3);
        mucronataTree.setMinCrownSize(5);
        mucronataTree.setMaxCrownSize(8);
        mucronataTree.setNoLeaves(false);
        this.addTree(mucronataTree);

        DecoTree mangroveTree = new DecoTree(mucronataTree);
        mangroveTree.setTreeType(DecoTree.TreeType.RTG_TREE);
        mangroveTree.setTreeCondition(DecoTree.TreeCondition.RANDOM_CHANCE);
        mangroveTree.setTreeConditionChance(1);
        mangroveTree.setStrengthFactorForLoops(6f);
        mangroveTree.setMaxY(110);
        //this.addDeco(mangroveTree);

        TreeRTG pentandraTree = new TreeRTGCeibaPentandra(5f, 3, 0.32f, 0.1f);;
        pentandraTree.setLogBlock(BlockUtil.getStateLog(EnumType.DARK_OAK));
        pentandraTree.setLeavesBlock(BlockUtil.getStateLeaf(EnumType.DARK_OAK));
        pentandraTree.setMaxAllowedObstruction(TreeRTG.ROOFED_FOREST_LIGHT_OBSTRUCTION_LIMIT);
        pentandraTree.setMinTrunkSize(2);
        pentandraTree.setMaxTrunkSize(3);
        pentandraTree.setMinCrownSize(5);
        pentandraTree.setMaxCrownSize(8);
        pentandraTree.setNoLeaves(false);
        this.addTree(pentandraTree);

        DecoTree ceibaPentandraTree = new DecoTree(pentandraTree);
        ceibaPentandraTree.setTreeType(DecoTree.TreeType.RTG_TREE);
        ceibaPentandraTree.setTreeCondition(DecoTree.TreeCondition.RANDOM_CHANCE);
        ceibaPentandraTree.setTreeConditionChance(1);
        ceibaPentandraTree.setStrengthFactorForLoops(4f);
        ceibaPentandraTree.setMaxY(110);
        this.addDeco(ceibaPentandraTree);

        TreeRTG bucheriTree = new TreeRTGAcaciaBucheri();
        bucheriTree.setLogBlock(BlockUtil.getStateLog(EnumType.DARK_OAK));
        bucheriTree.setLeavesBlock(BlockUtil.getStateLeaf(EnumType.DARK_OAK));
        bucheriTree.setTrunkSize(5);
        bucheriTree.setMinTrunkSize(3);
        bucheriTree.setMaxTrunkSize(6);
        bucheriTree.setMinCrownSize(3);
        bucheriTree.setMaxCrownSize(8);
        bucheriTree.setMaxAllowedObstruction(TreeRTG.ROOFED_FOREST_LIGHT_OBSTRUCTION_LIMIT);
        //this.addTree(bucheriTree);

        DecoTree bucheriTrees = new DecoTree(bucheriTree);
        bucheriTrees.setLoops(3);
        bucheriTrees.setTreeType(DecoTree.TreeType.RTG_TREE);
        bucheriTrees.setTreeCondition(DecoTree.TreeCondition.RANDOM_CHANCE);
        bucheriTrees.setTreeConditionChance(1);
        this.addDeco(bucheriTrees);

        DecoHelperRandomSplit decoHelperRandomSplit = new DecoHelperRandomSplit();
        decoHelperRandomSplit.decos = new DecoBase[]{mangroveTree, ceibaPentandraTree, bucheriTrees};
        decoHelperRandomSplit.chances = new int[]{11, 11, 2};
        this.addDeco(decoHelperRandomSplit);

//        DecoWorldGen decoCanopyTree = new DecoWorldGen(new WorldGenCanopyTree(false), DecorateBiomeEvent.Decorate.EventType.TREE);
//        decoCanopyTree.setMinY(63);
//        decoCanopyTree.setMaxY(100);
//        decoCanopyTree.setChance(1);
//        this.addDeco(decoCanopyTree);

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

        DecoShrub darkOakShrub = new DecoShrub();
        darkOakShrub.setLogBlock(BlockUtil.getStateLog(EnumType.DARK_OAK));
        darkOakShrub.setLeavesBlock(BlockUtil.getStateLeaf(EnumType.DARK_OAK));
        darkOakShrub.setMaxY(100);
        darkOakShrub.setLoopMultiplier(3f);

        DecoShrub oakShrub = new DecoShrub();
        oakShrub.setLogBlock(Blocks.LOG.getDefaultState());
        oakShrub.setLeavesBlock(Blocks.LEAVES.getDefaultState());
        oakShrub.setMaxY(100);
        oakShrub.setLoopMultiplier(3f);

        this.addDeco(new DecoHelperThisOrThat(4, DecoHelperThisOrThat.ChanceType.NOT_EQUALS_ZERO, darkOakShrub, oakShrub));

        DecoBoulder decoBoulder = new DecoBoulder();
        decoBoulder.setBoulderBlock(Blocks.MOSSY_COBBLESTONE.getDefaultState());
        decoBoulder.setChance(16);
        decoBoulder.setMaxY(80);
        decoBoulder.setStrengthFactor(2f);
        this.addDeco(decoBoulder);

        DecoCobwebs decoCobwebs = new DecoCobwebs();
        decoCobwebs.setChance(1);
        decoCobwebs.setMinY(63);
        decoCobwebs.setMaxY(76);
        decoCobwebs.setStrengthFactor(24f);
        decoCobwebs.setAdjacentBlock(BlockUtil.getStateLog(EnumType.DARK_OAK));
        decoCobwebs.setMinAdjacents(2);
        this.addDeco(decoCobwebs, this.getConfig().ALLOW_COBWEBS.get());

        DecoDeadBush decoDeadBush = new DecoDeadBush();
        decoDeadBush.setMaxY(100);
        decoDeadBush.setChance(2);
        decoDeadBush.setStrengthFactor(2f);
        this.addDeco(decoDeadBush);

        DecoWorldGen decoBigShroom = new DecoWorldGen(new WorldGenBigMushroom(), DecorateBiomeEvent.Decorate.EventType.BIG_SHROOM);
        decoBigShroom.setMinY(63);
        decoBigShroom.setMaxY(100);
        decoBigShroom.setChance(8);
        this.addDeco(decoBigShroom);
    }

    @Override
    public void overrideDecorations() {
        baseBiome().decorator.grassPerChunk = 3;
        baseBiome().decorator.flowersPerChunk = 1;
    }

    @Override
    public boolean overridesHardcoded() {
        return true;
    }

    public static class TerrainVanillaRoofedForest extends TerrainBase {

        public TerrainVanillaRoofedForest() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG DarkRedwood -> TerrainHilly(230f,120f,0f)
            return terrainHilly(x, y, rtgWorld, river, 230f, 120f, 0f, 260f, 68f);
        }
    }

}
