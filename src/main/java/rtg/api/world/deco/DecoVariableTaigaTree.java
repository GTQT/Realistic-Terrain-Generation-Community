package rtg.api.world.deco;

/*
 * Author @Zeno410
 * This class chooses a oak material or spruce material for a conifer tree using the materials picker and then calls the appropriate variable treee
 */

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.DecorateBiomeEvent.Decorate;
import net.minecraftforge.fml.common.eventhandler.Event;
import rtg.RTGConfig;
import rtg.api.event.DecorateBiomeEventRTG;
import rtg.api.util.ChunkInfo;
import rtg.api.util.Logger;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.IRealisticBiome;
import rtg.api.world.gen.feature.tree.rtg.TreeDensityLimiter;
import rtg.api.world.gen.feature.tree.rtg.TreeMaterials;

import java.util.Random;

public class DecoVariableTaigaTree extends DecoTree {

    protected final TreeMaterials.Picker materialsPicker = new TreeMaterials.Picker();
    protected final TreeMaterials.Chooser chooser = TreeMaterials.inSpruceForest;
    protected DecoVariableSpruce oakTree = new DecoVariableSpruce();
    protected DecoVariableSpruce spruceTree = new DecoVariableSpruce();

    public DecoVariableTaigaTree() {
        oakTree.materials = TreeMaterials.Picker.oak;
        // increase tree variability
        oakTree.localHeightSqrtVariability = 1f;
        spruceTree.localHeightSqrtVariability = 1f;
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
        float loopCount = calculateLoopCountF(noise);

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
        DecorateBiomeEventRTG.DecorateRTG event = new DecorateBiomeEventRTG.DecorateRTG(rtgWorld.world(), rand, offsetPos, Decorate.EventType.TREE, (int) loopCount);
        MinecraftForge.TERRAIN_GEN_BUS.post(event);

        if (event.getResult() != Event.Result.DENY) {

            int newLoopCount = event.getModifiedAmount();
            if ((int) loopCount != newLoopCount) {
                loopCount = newLoopCount;
            }
            if (loopCount < 0.3) {
                Logger.info("FractionalTree", "");
                return;
            }

            // 提前处理树叶调整
            DecoBase.tweakTreeLeaves(this, false, true);

            // 预计算村庄检查需要的参数
            final boolean shouldCheckVillage = hasVillage;

            int tries = 0;
            TreeDensityLimiter treesRemaining = new TreeDensityLimiter(loopCount);

            while (treesRemaining.notDone()) {
                final BlockPos pos = offsetPos.add(rand.nextInt(16), 0, rand.nextInt(16));
                tries++;
                if (tries > 100) {
                    throw new RuntimeException();
                }

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
                TreeMaterials materials = chooser.materialFor(pos, rtgWorld, rand);
                // using spruce design for the oak
                if (materials.equals(TreeMaterials.Picker.oak)) {
                    oakTree.doVariableGenerate(rand, chunkInfo, pos, y, treesRemaining);
                } else if (materials.equals(TreeMaterials.Picker.spruce)) {
                    spruceTree.doVariableGenerate(rand, chunkInfo, pos, y, treesRemaining);
                } else {
                    throw new RuntimeException();
                }
            }
        } else if (RTGConfig.enableDebugging()) {
            Logger.debug("Tree generation was cancelled @ ChunkPos{}", chunkPos);
        }
    }

    @Override
    @Deprecated
    public boolean properlyDefined() {
        // override DecoTree because we don't have just one tree.
        return true;
    }

    public void changeAvgHeightSqrt(float change) {
        oakTree.averageHeightSqrt += change;
        spruceTree.averageHeightSqrt += change;
    }

    public void changeHeightVariability(float change) {
        oakTree.heightNoiseVariability += change;
        spruceTree.heightNoiseVariability += change;
    }
}