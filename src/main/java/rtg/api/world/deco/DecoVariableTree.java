package rtg.api.world.deco;

import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenAbstractTree;
import net.minecraft.world.gen.feature.WorldGenTrees;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.DecorateBiomeEvent.Decorate;
import net.minecraftforge.fml.common.eventhandler.Event;
import rtg.RTGConfig;
import rtg.api.event.DecorateBiomeEventRTG;
import rtg.api.util.BlockUtil;
import rtg.api.util.BlockUtil.MatchType;
import rtg.api.util.ChunkInfo;
import rtg.api.util.Logger;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.IRealisticBiome;
import rtg.api.world.gen.feature.WorldGenShrubRTG;
import rtg.api.world.gen.feature.tree.rtg.TreeDensityLimiter;
import rtg.api.world.gen.feature.tree.rtg.TreeMaterials;
import rtg.api.world.gen.feature.tree.rtg.TreeRTG;

import java.util.Random;

/**
 * Variable Trees
 * This class make trees of variable type and height based on a noise parameter from ChunkInfo
 *
 */

abstract public class DecoVariableTree extends DecoTree {

    protected final TreeMaterials.Picker materialsPicker = new TreeMaterials.Picker();
    protected TreeRTG tallTree;
    protected TreeRTG mediumTree;
    protected TreeRTG smallTree;
    protected TreeMaterials materials;

    protected int tallTreeMinimumHeight = 21; // shortest allowed tall tree
    protected int tallTreeMinimumVariability = 9; // this less 1 (Random.nextInt()) added to minimum for largest allowed medium tree
    protected int mediumTreeMinimumHeight = 12; // etc.
    protected int mediumTreeMinimumVariability = 5;
    protected int smallTreeMinimumHeight = 7;
    protected int smallTreeMinimumVariability = 3;
    protected int vanillaTreeMinimumHeight = 2;
    protected int vanillaTreeMinimumVariability = 2;

    protected float averageHeightSqrt = 4.4f; // average tree height square root; trees vary
    protected float heightNoiseVariability = 2f; // maximum change in average height up or down from noise
    // can go up or down so range is twice this number
    protected float localHeightSqrtVariability = 0.25f; // similar but tree to tree;

    protected float saplingChance = .1f; // chance a tree will be shorter than expected

    // 缓存计算值
    protected transient Float cachedAverageHeightSqrt;
    protected transient Float cachedHeightNoiseVariability;
    protected transient WorldGenAbstractTree cachedVanillaTree;

    public DecoVariableTree() {

    }

    public void changeAverageHeightSqrt(float change) {
        averageHeightSqrt += change;
        cachedAverageHeightSqrt = null; // 清除缓存
    }

    public void changeHeightNoiseVariability(float change) {
        heightNoiseVariability += change;
        cachedHeightNoiseVariability = null; // 清除缓存
    }

    public int smallestSaplingHeight() {
        return largestVanillaTree() + 1;
    }

    @Override
    public void generate(final IRealisticBiome biome, final RTGWorld rtgWorld, final Random rand, final ChunkPos chunkPos, final float river, final boolean hasVillage, ChunkInfo chunkInfo) {

        // 使用父类的配置检查优化
        if (!isConfigEnabled(biome)) {
            return;
        }

        final BlockPos offsetPos = getOffsetPos(chunkPos);

        // 计算噪声值 - 只计算一次
        float noise = distribution.getValue(offsetPos, rtgWorld.treeDistributionNoise());

        // 提前检查确定性树生成条件
        if (!isDeterministicTreeConditionMet(noise)) {
            return;
        }

        /*
         * Determine how many trees we're going to try to generate (loopCount).
         * The actual number of trees that end up being generated could be *less* than this value,
         * depending on environmental conditions.
         */
        int loopCount = calculateLoopCount(noise);

        if (loopCount < 1) {
            return;
        }

        // 应用配置乘数
        loopCount = this.applyConfigMultipliers(loopCount, biome);

        if (loopCount < 1) {
            return;
        }

        /*
         * Since RTG posts a TREE event for each batch of trees it tries to generate (instead of one event per chunk),
         * we post this custom event so that we can pass the number of trees RTG expects to generate in each batch.
         */
        DecorateBiomeEventRTG.DecorateRTG event = new DecorateBiomeEventRTG.DecorateRTG(rtgWorld.world(), rand, offsetPos, Decorate.EventType.TREE, loopCount);
        MinecraftForge.TERRAIN_GEN_BUS.post(event);

        if (event.getResult() != Event.Result.DENY) {

            loopCount = event.getModifiedAmount();
            if (loopCount < 1) {
                return;
            }

            // 提前处理树叶调整
            DecoBase.tweakTreeLeaves(this, false, true);

            // 预计算村庄检查需要的参数
            final boolean shouldCheckVillage = hasVillage;

            TreeDensityLimiter treesRemaining = new TreeDensityLimiter(loopCount);

            while (treesRemaining.notDone()) {
                final BlockPos pos = offsetPos.add(rand.nextInt(16), 0, rand.nextInt(16));
                int y = rtgWorld.world().getHeight(pos).getY();

                // 优化条件检查顺序：先检查简单的条件
                if (y < this.minY || y > this.maxY) {
                    treesRemaining.allowed(1f, rand);
                    continue;
                }

                if (!isValidTreeCondition(noise, rand)) {
                    treesRemaining.allowed(1f, rand);
                    continue;
                }

                // 村庄检查放到最后，因为这是最耗性能的检查
                if (shouldCheckVillage && !isValidVillagePosition(rtgWorld, pos)) {
                    treesRemaining.allowed(1f, rand);
                    continue;
                }

                // get a suitable tree Type
                doVariableGenerate(rand, chunkInfo, pos, y, treesRemaining);
            }
        } else if (RTGConfig.enableDebugging()) {
            Logger.debug("Tree generation was cancelled @ ChunkPos{}", chunkPos);
        }
    }

    // 优化村庄检查逻辑
    @Override
    protected boolean isValidVillagePosition(final RTGWorld rtgWorld, final BlockPos pos) {
        // 简化村庄检查逻辑，避免重复调用
        return !BlockUtil.checkVerticalBlocks(MatchType.ALL, rtgWorld.world(), pos, -1, Blocks.FARMLAND) &&
                BlockUtil.checkAreaBlocks(MatchType.ALL_IGNORE_REPLACEABLE, rtgWorld.world(), pos, 2);
    }

    public void doVariableGenerate(Random rand, ChunkInfo chunkInfo, BlockPos column, int y, TreeDensityLimiter treesRemaining) {

        float averageHeightIndex = chunkInfo.treeHeightNoiseValue();

        float noise = averageHeightIndex;
        // value is -1 to 1, so adjust to be in [2.5,5.5], the targeted range of height square *root*
        averageHeightIndex *= getHeightNoiseVariability();
        averageHeightIndex += getAverageHeightSqrt();

        // a little tree to tree variability. Math is different from noise math because noises are [-1,1] and randoms [0,1]
        float actualHeightIndex = averageHeightIndex + rand.nextFloat() * localHeightSqrtVariability * 2f - localHeightSqrtVariability;

        // shrink down if high
        if (y > 70) {
            float heightReduction = ((float) (y - 70)) / 20f;
            actualHeightIndex -= heightReduction;

            // no negatives!
            if (actualHeightIndex < 0) {
                treesRemaining.occupy(0.3f);// use up a little to avoid infinite loops
                return;  //too small, no tree generated.
            }
        }

        // square for actual height
        int actualHeight = (int) (actualHeightIndex * actualHeightIndex);

        // occasional smaller saplings
        if (rand.nextFloat() < saplingChance && actualHeight > vanillaTreeMinimumHeight) {
            actualHeight = vanillaTreeMinimumHeight + rand.nextInt(actualHeight - vanillaTreeMinimumHeight);
        }

        // the generate step is separated out because
        doGenerate(chunkInfo.world(), rand, column.up(y), actualHeight, treesRemaining);
    }

    public void doGenerate(World world, Random rand, BlockPos pos, int actualHeight, TreeDensityLimiter treesRemaining) {
        if (actualHeight > tallTreeMinimumHeight + rand.nextInt(tallTreeMinimumVariability)) {
            this.generateTallTree(world, rand, pos, actualHeight, materials, treesRemaining);
        } else if (actualHeight > mediumTreeMinimumHeight + rand.nextInt(mediumTreeMinimumVariability)) {
            this.generateMediumTree(world, rand, pos, actualHeight, materials, treesRemaining);
        } else if (actualHeight > smallTreeMinimumHeight + rand.nextInt(smallTreeMinimumVariability)) {
            this.generateSmallTree(world, rand, pos, actualHeight, materials, treesRemaining);
        } else if (actualHeight > vanillaTreeMinimumHeight + rand.nextInt(vanillaTreeMinimumVariability)) {
            if (treesRemaining.allowed(0.5f, rand)) {
                getVanillaTree().generate(world, rand, pos);
            }
        } else {
            if (treesRemaining.allowed(0.7f, rand)) {
                new WorldGenShrubRTG(actualHeight, materials.log, materials.leaves, false).generate(world, rand, pos);
            }
        }
    }

    @Override
    @Deprecated
    public boolean properlyDefined() {
        // override DecoTree because we don't have just one tree.
        return true;
    }

    // 缓存平均高度平方根计算
    protected float getAverageHeightSqrt() {
        if (cachedAverageHeightSqrt == null) {
            cachedAverageHeightSqrt = averageHeightSqrt;
        }
        return cachedAverageHeightSqrt;
    }

    // 缓存高度噪声变异性
    protected float getHeightNoiseVariability() {
        if (cachedHeightNoiseVariability == null) {
            cachedHeightNoiseVariability = heightNoiseVariability;
        }
        return cachedHeightNoiseVariability;
    }

    // 缓存香草树生成器
    protected WorldGenAbstractTree getVanillaTree() {
        if (cachedVanillaTree == null) {
            cachedVanillaTree = new WorldGenTrees(false, 4, materials.log, materials.leaves, false);
        }
        return cachedVanillaTree;
    }

    private void generateTallTree(World world, Random random, BlockPos pos, int actualHeight, TreeMaterials materials, TreeDensityLimiter treesRemaining) {
        float proportionTrunk = tallTree.getLowestVariableTrunkProportion() + random.nextFloat() * tallTree.getTrunkProportionVariability();
        int trunkHeight = (int) (proportionTrunk * (actualHeight - tallTree.getTrunkReserve())) + tallTree.getTrunkReserve();
        if (trunkHeight < 4) trunkHeight = 4;

        tallTree.setLogBlock(materials.log);
        tallTree.setLeavesBlock(materials.leaves);
        tallTree.setBranchBlock(materials.branches);
        tallTree.setTrunkSize(trunkHeight);
        tallTree.setCrownSize(actualHeight - trunkHeight);
        tallTree.setNoLeaves(false);

        if (treesRemaining.allowed(tallTree.estimatedSize(), random)) {
            tallTree.generate(world, random, pos);
        }
    }

    private void generateMediumTree(World world, Random random, BlockPos pos, int actualHeight, TreeMaterials materials, TreeDensityLimiter treesRemaining) {
        float proportionTrunk = mediumTree.getLowestVariableTrunkProportion() + random.nextFloat() * mediumTree.getTrunkProportionVariability();
        int trunkHeight = (int) (proportionTrunk * (actualHeight - mediumTree.getTrunkReserve())) + mediumTree.getTrunkReserve();
        if (trunkHeight < 4) trunkHeight = 4;

        mediumTree.setLogBlock(materials.log);
        mediumTree.setLeavesBlock(materials.leaves);
        mediumTree.setBranchBlock(materials.branches);
        mediumTree.setTrunkSize(trunkHeight);
        mediumTree.setCrownSize(actualHeight - trunkHeight);
        mediumTree.setNoLeaves(false);

        if (treesRemaining.allowed(mediumTree.estimatedSize(), random)) {
            mediumTree.generate(world, random, pos);
        }
    }

    private void generateSmallTree(World world, Random random, BlockPos pos, int actualHeight, TreeMaterials materials, TreeDensityLimiter treesRemaining) {
        float proportionTrunk = smallTree.getLowestVariableTrunkProportion() + random.nextFloat() * smallTree.getTrunkProportionVariability();
        int trunkHeight = (int) (proportionTrunk * (actualHeight - smallTree.getTrunkReserve())) + smallTree.getTrunkReserve();
        if (trunkHeight < 4) trunkHeight = 4;

        smallTree.setLogBlock(materials.log);
        smallTree.setLeavesBlock(materials.leaves);
        smallTree.setBranchBlock(materials.branches);
        smallTree.setTrunkSize(trunkHeight);
        smallTree.setCrownSize(actualHeight - trunkHeight + 2); // need a bit more crown for this algo
        smallTree.setNoLeaves(false);

        if (treesRemaining.allowed(smallTree.estimatedSize(), random)) {
            smallTree.generate(world, random, pos);
        }
    }

    public int largestVanillaTree() {
        return this.smallTreeMinimumHeight + this.smallTreeMinimumVariability - 1;
    }

    protected WorldGenAbstractTree vanillaTree() {
        return getVanillaTree();
    }

    // 重写重置缓存方法，包含子类的缓存
    @Override
    public void resetCache() {
        super.resetCache();
        cachedAverageHeightSqrt = null;
        cachedHeightNoiseVariability = null;
        cachedVanillaTree = null;
    }

}