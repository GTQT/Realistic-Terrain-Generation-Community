package rtg.mixins.core;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import rtg.api.util.ChunkGenerationProfiler;
import rtg.api.util.ChunkGenerationProfiler.Counter;

/**
 * World 侧计数器：统计成功的方块写入，并按方块类别分桶。
 *
 * <p>分桶是必要的：原木的 {@code lightOpacity} 默认是 255，写入同样触发
 * {@code World#checkLight}；只统计树叶会漏掉原木。
 *
 * <p>profiler 关闭时只读一次 volatile 标志。
 */
@Mixin(World.class)
public abstract class WorldProfilerMixin {

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void rtgc$countOpaqueWrite(final BlockPos pos, final IBlockState state, final int flags,
                                       final CallbackInfoReturnable<Boolean> cir) {
        if (!ChunkGenerationProfiler.isEnabled() || !Boolean.TRUE.equals(cir.getReturnValue())) {
            return;
        }
        final Block block = state.getBlock();
        if (block == Blocks.LOG || block == Blocks.LOG2) {
            ChunkGenerationProfiler.count(Counter.OPAQUE_LOG_WRITES);
        } else if (block == Blocks.LEAVES || block == Blocks.LEAVES2) {
            ChunkGenerationProfiler.count(Counter.OPAQUE_LEAVES_WRITES);
        } else if (block == Blocks.WATER || block == Blocks.FLOWING_WATER) {
            ChunkGenerationProfiler.count(Counter.OPAQUE_WATER_WRITES);
        } else if (block == Blocks.ICE || block == Blocks.FROSTED_ICE) {
            ChunkGenerationProfiler.count(Counter.OPAQUE_ICE_WRITES);
        } else if (block == Blocks.WEB) {
            ChunkGenerationProfiler.count(Counter.OPAQUE_WEB_WRITES);
        } else if (state.getLightOpacity() > 0) {
            ChunkGenerationProfiler.count(Counter.OPAQUE_OTHER_WRITES);
        }
    }

    @Inject(method = "checkLight", at = @At("HEAD"))
    private void rtgc$countCheckLight(final BlockPos pos, final CallbackInfoReturnable<Boolean> cir) {
        ChunkGenerationProfiler.count(Counter.CHECK_LIGHT_CALLS);
    }
}
