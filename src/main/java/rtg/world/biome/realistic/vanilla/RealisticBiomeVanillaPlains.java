package rtg.world.biome.realistic.vanilla;

import net.minecraft.block.BlockPlanks.EnumType;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import rtg.api.util.BlockUtil;
import rtg.api.util.Distribution;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.*;
import rtg.api.world.deco.helper.DecoHelperThisOrThat;
import rtg.api.world.gen.feature.tree.rtg.TreeRTG;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGQuercusRobur;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;


import static net.minecraft.block.BlockFlower.EnumFlowerType.*;
import rtg.api.world.surface.SurfaceGrassland;


public class RealisticBiomeVanillaPlains extends RealisticBiomeBase {

    public static Biome biome = Biomes.PLAINS;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaPlains() {

        super(biome);
    }

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_VILLAGES.set(true);
        this.getConfig().addProperty(this.getConfig().ALLOW_WHEAT).set(true);
        this.getConfig().addProperty(this.getConfig().WHEAT_CHANCE).set(50);
        this.getConfig().addProperty(this.getConfig().WHEAT_MIN_Y).set(63);
        this.getConfig().addProperty(this.getConfig().WHEAT_MAX_Y).set(255);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaPlains();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }

    @Override
    public void initDecos() {

        //Sparse wheat
        DecoCrop decoCropWheat = new DecoCrop();
        decoCropWheat.setSize(4);
        decoCropWheat.setDensity(4);
        decoCropWheat.setChance(this.getConfig().WHEAT_CHANCE.get());
        decoCropWheat.setType(3);
        decoCropWheat.setWater(false);
        decoCropWheat.setMinY(this.getConfig().WHEAT_MIN_Y.get());
        decoCropWheat.setMaxY(this.getConfig().WHEAT_MAX_Y.get());
        this.addDeco(decoCropWheat, this.getConfig().ALLOW_WHEAT.get());

        // Very sparse shrubs.
        DecoShrub decoShrubOak = new DecoShrub();
        decoShrubOak.setLogBlock(Blocks.LOG.getDefaultState());
        decoShrubOak.setLeavesBlock(Blocks.LEAVES.getDefaultState());
        decoShrubOak.setMaxY(110);
        decoShrubOak.setLoops(1);
        decoShrubOak.setChance(36);
        this.addDeco(decoShrubOak);

        // The occasional flower.
        DecoFlowersRTG decoFlowersRTG = new DecoFlowersRTG()
            .addFlowers(POPPY, BLUE_ORCHID, ALLIUM, HOUSTONIA, RED_TULIP, ORANGE_TULIP, WHITE_TULIP, PINK_TULIP, OXEYE_DAISY, DANDELION)
            .setMaxY(128);
        this.addDeco(decoFlowersRTG);

        // Very rare fat oak/birch trees.

        TreeRTG roburTree1 = new TreeRTGQuercusRobur();
        roburTree1.setLogBlock(Blocks.LOG.getDefaultState());
        roburTree1.setLeavesBlock(Blocks.LEAVES.getDefaultState());
        roburTree1.setMinTrunkSize(3);
        roburTree1.setMaxTrunkSize(5);
        roburTree1.setMinCrownSize(7);
        roburTree1.setMaxCrownSize(9);
        this.addTree(roburTree1);

        DecoTree oakTrees = new DecoTree(roburTree1);
        oakTrees.setTreeType(DecoTree.TreeType.RTG_TREE);
        oakTrees.setTreeCondition(DecoTree.TreeCondition.NOISE_GREATER_AND_RANDOM_CHANCE);
        oakTrees.setDistribution(new Distribution(100f, 6f, 0.8f));
        oakTrees.setTreeConditionNoise(0.4f);
        oakTrees.setTreeConditionChance(48);

        TreeRTG roburTree2 = new TreeRTGQuercusRobur();
        roburTree2.setLogBlock(BlockUtil.getStateLog(EnumType.BIRCH));
        roburTree2.setLeavesBlock(BlockUtil.getStateLeaf(EnumType.BIRCH));
        roburTree2.setMinTrunkSize(3);
        roburTree2.setMaxTrunkSize(5);
        roburTree2.setMinCrownSize(7);
        roburTree2.setMaxCrownSize(9);
        this.addTree(roburTree2);

        DecoTree birchTrees = new DecoTree(roburTree2);
        birchTrees.setTreeType(DecoTree.TreeType.RTG_TREE);
        birchTrees.setTreeCondition(DecoTree.TreeCondition.NOISE_GREATER_AND_RANDOM_CHANCE);
        birchTrees.setDistribution(new Distribution(100f, 6f, 0.8f));
        birchTrees.setTreeConditionNoise(0.4f);
        birchTrees.setTreeConditionChance(48);

        this.addDeco(new DecoHelperThisOrThat(4, DecoHelperThisOrThat.ChanceType.NOT_EQUALS_ZERO, oakTrees, birchTrees));
    }

    @Override
    public void overrideDecorations() {
        baseBiome().decorator.grassPerChunk = 5;
        baseBiome().decorator.flowersPerChunk = -999;
        baseBiome().decorator.treesPerChunk = -999;
    }

    @Override
    public boolean overridesHardcoded() {
        return true;
    }

    public static class TerrainVanillaPlains extends TerrainBase {

        public TerrainVanillaPlains() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // **推断**：RWG 没有"MC 平原"这个对应物（它的 RWG_CONTINENT 模式只用自造群系）。
            // 依据是 RWG 里承担"平坦陆地"角色的 TerrainGrasslandFlats
            //（RealisticBiomeSavanna / RealisticBiomeHotForest / RealisticBiomeDarkRedwoodPlains 都用它）。
            return terrainGrasslandFlats(x, y, rtgWorld, river);
        }
    }

}
