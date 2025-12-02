package rtg.api.world.deco;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.feature.WorldGenAbstractTree;
import net.minecraft.world.gen.feature.WorldGenSavannaTree;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.DecorateBiomeEvent.Decorate;
import net.minecraftforge.fml.common.eventhandler.Event;
import rtg.RTGConfig;
import rtg.api.event.DecorateBiomeEventRTG;
import rtg.api.util.ChunkInfo;
import rtg.api.util.Logger;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.IRealisticBiome;
import rtg.api.world.gen.feature.tree.rtg.*;

import java.util.Random;

/**
 * Variable Trees
 * This class make trees of variable type and height based on a noise parameter from ChunkInfo
 *
 */

public class DecoVariableAcacia extends DecoVariableTree {

    public DecoVariableAcacia() {
        tallTree = new TreeRTGAcaciaAbyssinicaMega();
        mediumTree = new TreeRTGAcaciaAbyssinica();
        smallTree = new TreeRTGAcaciaBucheri();
        this.materials = TreeMaterials.Picker.acacia;
        this.averageHeightSqrt += 0f;
        mediumTreeMinimumHeight = 11; // shortest allowed tall tree
        mediumTreeMinimumVariability = 3; // this less 1 (Random.nextInt()) added to minimum for largest allowed medium tree
        smallTreeMinimumHeight = 6; // etc.
        smallTreeMinimumVariability = 4;
        averageHeightSqrt = 3f;
        heightNoiseVariability = 1f;
        localHeightSqrtVariability = 0.25f;

    }

    @Override
    public void generate(final IRealisticBiome biome, final RTGWorld rtgWorld, final Random rand, final ChunkPos chunkPos, final float river, final boolean hasVillage, ChunkInfo chunkInfo) {

        // 使用父类的配置检查优化
        if (!isConfigEnabled(biome)) {
            return;
        }

        final BlockPos offsetPos = getOffsetPos(chunkPos);

        float noise = distribution.getValue(offsetPos, rtgWorld.treeDistributionNoise());
        // square and reduce so acacias are usually highly scattered but occasionally dense
        noise = noise * noise / 14f;
        int loopCount = (int) noise;
        if (loopCount < 1 && noise > 0) loopCount = 1;

        // Now let's check the configs to see if we should increase/decrease this value.
        int newCount = this.applyConfigMultipliers(loopCount, biome);

        if (newCount < 1) return;
        if (loopCount != newCount) {
            noise *= (float) newCount / (float) loopCount;
        }

        /*
         * Since RTG posts a TREE event for each batch of trees it tries to generate (instead of one event per chunk),
         * we post this custom event so that we can pass the number of trees RTG expects to generate in each batch.
         */
        DecorateBiomeEventRTG.DecorateRTG event = new DecorateBiomeEventRTG.DecorateRTG(rtgWorld.world(), rand, offsetPos, Decorate.EventType.TREE, loopCount);
        MinecraftForge.TERRAIN_GEN_BUS.post(event);

        if (event.getResult() != Event.Result.DENY) {

            newCount = event.getModifiedAmount();
            if (newCount < 1) return;
            if (loopCount != newCount) {
                noise *= (float) newCount / (float) loopCount;
            }

            // 提前处理树叶调整
            DecoBase.tweakTreeLeaves(this, false, true);

            // 预计算村庄检查需要的参数
            final boolean shouldCheckVillage = hasVillage;

            TreeDensityLimiter treesRemaining = new TreeDensityLimiter(noise);

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
            Logger.debug("Acacia tree generation was cancelled @ ChunkPos{}", chunkPos);
        }
    }

    public int smallestSaplingHeight() {
        return largestVanillaTree() - 1;
    }

    protected WorldGenAbstractTree vanillaTree() {
        return new WorldGenSavannaTree(false);
    }
}