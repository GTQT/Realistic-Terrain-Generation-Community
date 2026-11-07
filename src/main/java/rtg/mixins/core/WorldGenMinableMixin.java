package rtg.mixins.core;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.feature.WorldGenMinable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rtg.api.util.FastOreContext;

/**
 * 原版矿脉生成期间绕过 {@link World} 的 chunk 查找与通知。
 *
 * <p>只在 {@code BiomeDecorator#generateOres} 打开的 {@link FastOreContext} 内生效。
 * 目标方块所在区块已加载时直接读写 {@link Chunk}；未加载时回退到原版 {@link World}
 * 路径，保持原有边界语义。矿脉形状、随机数调用顺序和替换谓词都不变。
 */
@Mixin(WorldGenMinable.class)
public abstract class WorldGenMinableMixin {

    @Inject(method = "generate", at = @At("HEAD"))
    private void rtgc$countOreVein(World world, java.util.Random rand, BlockPos pos,
                                   CallbackInfoReturnable<Boolean> cir) {
        if (!FastOreContext.isActive()) {
            return;
        }
        FastOreContext.noteVein();
        // 石变种（泥土/沙砾/天然花岗岩）单独计数：格雷科技不会拒绝它们，
        // 是 GT 整合包里唯一剩下的原版矿成本（docs §18）。
        final net.minecraft.block.Block oreBlock =
                ((WorldGenMinable) (Object) this).oreBlock.getBlock();
        if (oreBlock == net.minecraft.init.Blocks.DIRT
                || oreBlock == net.minecraft.init.Blocks.GRAVEL
                || oreBlock == net.minecraft.init.Blocks.STONE) {
            rtg.api.util.ChunkGenerationProfiler.count(
                    rtg.api.util.ChunkGenerationProfiler.Counter.ORE_VEINS_STONE);
        }
    }

    /**
     * 接管原版矿脉：形状/随机数完全照抄，只把逐块 {@code World} 读写换成段存储直读写。
     * 见 {@link rtg.world.gen.FastOreGen}。非原版谓词（模组自定义矿石）不走这里。
     */
    @Inject(method = "generate", at = @At("HEAD"), cancellable = true)
    private void rtgc$fastOreGen(World world, java.util.Random rand, BlockPos pos,
                                 CallbackInfoReturnable<Boolean> cir) {
        if (!FastOreContext.isActive()) {
            return;
        }
        final WorldGenMinable self = (WorldGenMinable) (Object) this;
        if (rtg.world.gen.FastOreGen.canHandle(self)) {
            cir.setReturnValue(rtg.world.gen.FastOreGen.generate(
                    world, rand, pos, self.oreBlock, self.numberOfBlocks));
        }
    }

    @Redirect(
            method = "generate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/IBlockState;"
            )
    )
    private IBlockState rtgc$readOreBlock(World world, BlockPos pos) {
        if (!FastOreContext.isActive()) {
            return world.getBlockState(pos);
        }
        Chunk chunk = FastOreContext.getLoadedChunk(world, pos);
        return chunk == null ? world.getBlockState(pos) : chunk.getBlockState(pos);
    }

    @Redirect(
            method = "generate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;I)Z"
            )
    )
    private boolean rtgc$writeOreBlock(World world, BlockPos pos, IBlockState state, int flags) {
        if (!FastOreContext.isActive()) {
            return world.setBlockState(pos, state, flags);
        }
        return FastOreContext.setOreBlock(world, pos, state, flags);
    }
}
