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
 * M0 的写入侧计数器：只统计“成功写入”的方块，并按方块类别分桶。
 *
 * <p>为什么必须分桶：旧报告把“树叶写入”当成主要光照开销，但原木的
 * {@code lightOpacity} 默认是 255（{@code Block#setLightOpacity} 对 fullBlock 的默认值），
 * 每根原木同样会触发 {@code World#checkLight}。只数树叶会把原木漏掉。
 *
 * <p>只在 profiler 打开时计数；关闭时一次 volatile 读后返回，不影响正常游玩。
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
