package rtg.api.world.deco.collection;

import net.minecraft.block.BlockPlanks.EnumType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.feature.WorldGenTrees;
import net.minecraft.world.gen.feature.WorldGenerator;
import rtg.api.config.BiomeConfig;
import rtg.api.util.BlockUtil;
import rtg.api.util.Distribution;
import rtg.api.world.RTGWorld;
import rtg.api.world.deco.DecoBase;
import rtg.api.world.deco.DecoDoubleGrass;
import rtg.api.world.deco.DecoGrass;
import rtg.api.world.deco.DecoFallenTree;
import rtg.api.world.deco.DecoFallenTree.LogCondition;
import rtg.api.world.deco.DecoFlowersRTG;
import rtg.api.world.deco.DecoVariableSpruce;
import rtg.api.world.deco.DecoShrub;
import rtg.api.world.deco.DecoTree;
import rtg.api.world.deco.DecoTree.TreeCondition;
import rtg.api.world.deco.DecoTree.TreeType;
import rtg.api.world.deco.DecoVariableBirch;
import rtg.api.world.deco.DecoVariableFallenTree;
import rtg.api.world.deco.DecoVariableMaterialTree;
import rtg.api.world.deco.DecoVariableOak;
import rtg.api.world.deco.helper.DecoHelper5050;
import rtg.api.world.deco.helper.DecoHelperRandomSplit;
import rtg.api.world.gen.feature.tree.rtg.TreeMaterials;
import rtg.api.world.gen.feature.tree.rtg.TreeRTG;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGPiceaPungens;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGPiceaSitchensis;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGPinusPonderosa;

import static net.minecraft.block.BlockFlower.EnumFlowerType.ALLIUM;
import static net.minecraft.block.BlockFlower.EnumFlowerType.BLUE_ORCHID;
import static net.minecraft.block.BlockFlower.EnumFlowerType.DANDELION;
import static net.minecraft.block.BlockFlower.EnumFlowerType.HOUSTONIA;
import static net.minecraft.block.BlockFlower.EnumFlowerType.ORANGE_TULIP;
import static net.minecraft.block.BlockFlower.EnumFlowerType.OXEYE_DAISY;
import static net.minecraft.block.BlockFlower.EnumFlowerType.PINK_TULIP;
import static net.minecraft.block.BlockFlower.EnumFlowerType.POPPY;
import static net.minecraft.block.BlockFlower.EnumFlowerType.RED_TULIP;
import static net.minecraft.block.BlockFlower.EnumFlowerType.WHITE_TULIP;

import java.util.Random;


/**
 * @author WhichOnesPink
 */
public class DecoCollectionForest extends DecoCollectionBase {

    // Restored to Master-level: divisor 100 (was 837 global), factor 8 (was 2.5)
    private Distribution forestDistribution = new Distribution(100f, 6f, 0.8f);
    private Distribution treeFrequencyDistribution = new Distribution(100f, 8f, 0.8f);

    private float tallMin = -1f;
    private float tallMax = 3f;

    public DecoCollectionForest(BiomeConfig config) {

        super(config);

        this
        .addDeco(variableTrees(tallMin, tallMax));

        this
            .addDeco(variableLogs(), config.ALLOW_LOGS.get()) // Add some fallen trees of the oak and spruce variety (50/50 distribution).
            .addDeco(shrubsOak()) // Shrubs to fill in the blanks.
            .addDeco(shrubsSpruce()) // Fewer spruce shrubs than oak.
            .addDeco(flowers()) // Only 1-block tall flowers so we can see the trees better.
            .addDeco(grass())  // Restore grass — was missing entirely
        ;

    }


    private DecoTree variableTrees(float noiseMin, float noiseMax) {

        DecoTree result = new DecoVariableMaterialTree(TreeMaterials.inOakForest);

        return result
            .setStrengthFactorForLoops(0f)           // Disabled — let noise drive loop count
            .setTreeType(TreeType.RTG_TREE)
            .setDistribution(treeFrequencyDistribution)
            .setTreeCondition(TreeCondition.ALWAYS_GENERATE)
            .setTreeConditionNoise(noiseMin)
            .setTreeConditionNoise2(noiseMax)
            .setTreeConditionChance(1)
            .setMaxY(120)
            .setStrengthNoiseFactorForLoops(true)    // Noise-based loop count (now properly active)
            .setStrengthNoiseFactorXForLoops(false)
            ;
    }

    private DecoTree oakTrees(float noiseMin, float noiseMax) {

        return new DecoVariableOak()
            .setStrengthFactorForLoops(0f)
            .setTreeType(TreeType.RTG_TREE)
            .setDistribution(treeFrequencyDistribution)
            .setTreeCondition(TreeCondition.ALWAYS_GENERATE)
            .setTreeConditionNoise(noiseMin)
            .setTreeConditionNoise2(noiseMax)
            .setTreeConditionChance(1)
            .setMaxY(120)
            .setStrengthNoiseFactorForLoops(true)
            .setStrengthNoiseFactorXForLoops(false)
            ;
    }

    private DecoTree birchTrees(float noiseMin, float noiseMax) {

        return new DecoVariableBirch()
            .setStrengthFactorForLoops(0f)
            .setTreeType(TreeType.RTG_TREE)
            .setDistribution(treeFrequencyDistribution)
            .setTreeCondition(TreeCondition.ALWAYS_GENERATE)
            .setTreeConditionNoise(noiseMin)
            .setTreeConditionNoise2(noiseMax)
            .setTreeConditionChance(1)
            .setMaxY(120)
            .setStrengthNoiseFactorForLoops(true)
            .setStrengthNoiseFactorXForLoops(false)
            ;
    }

    private DecoTree randomPungensTrees() {

        TreeRTG piceaPungens = new TreeRTGPiceaPungens()
            .setLogBlock(Blocks.LOG.getDefaultState())
            .setLeavesBlock(Blocks.LEAVES.getDefaultState())
            .setMinTrunkSize(2)
            .setMaxTrunkSize(4)
            .setMinCrownSize(5)
            .setMaxCrownSize(8);

        this.addTree(piceaPungens);

        return new DecoTree(piceaPungens)
            .setStrengthFactorForLoops(2f)
            .setTreeType(TreeType.RTG_TREE)
            .setTreeCondition(TreeCondition.RANDOM_CHANCE)
            .setTreeConditionChance(5)
            .setMaxY(100);
    }

    private DecoVariableFallenTree variableLogs() {
        DecoVariableFallenTree result = new DecoVariableFallenTree(DecoVariableFallenTree.Woodland.OAK);
            result = result.setMaxY(80)
            .setMinSize(3)
            .setMaxSize(8);
        return result;
    }

    private DecoShrub shrubsOak() {
        return new DecoShrub()
            .setMaxY(140)
            .setLoopMultiplier(4f)  // Restored from 1f to match Master's strengthFactor=4f
            .setChance(3);
    }

    private DecoShrub shrubsSpruce() {
        return new DecoShrub()
            .setLogBlock(BlockUtil.getStateLog(EnumType.SPRUCE))
            .setLeavesBlock(BlockUtil.getStateLeaf(EnumType.SPRUCE))
            .setMaxY(140)
            .setLoopMultiplier(4f)  // Restored from 1f to match Master's strengthFactor=4f
            .setChance(9);
    }

    private DecoFlowersRTG flowers() {
        return new DecoFlowersRTG()
            .addFlowers(POPPY, BLUE_ORCHID, ALLIUM, HOUSTONIA, RED_TULIP, ORANGE_TULIP, WHITE_TULIP, PINK_TULIP, OXEYE_DAISY, DANDELION)
            .setMaxY(128)
            .setStrengthFactor(6f);
    }

    private DecoGrass grass() {
        return new DecoGrass()
            .setMaxY(128)
            .setLoops(8);  // Restore grass — was completely missing
    }

}
