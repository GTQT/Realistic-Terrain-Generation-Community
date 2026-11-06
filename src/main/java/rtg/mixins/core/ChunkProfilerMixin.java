package rtg.mixins.core;

import net.minecraft.world.chunk.Chunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rtg.api.util.ChunkGenerationProfiler;
import rtg.api.util.ChunkGenerationProfiler.Counter;

/**
 * M0 的 Chunk 侧计数器：
 * <ul>
 *   <li>{@code generateSkylightMap} 被调用多少次 —— 每次 setBlockState 改变 section 高度图时
 *       都可能同步重建；旧报告把它算在“延后”里，实际是同步的。</li>
 *   <li>{@code recheckGaps} 的调用次数与耗时 —— 它才是真正延后到区块 tick 的那部分。</li>
 * </ul>
 *
 * <p>{@code recheckGaps} 是 private 方法，按方法名注入即可。
 */
@Mixin(Chunk.class)
public abstract class ChunkProfilerMixin {

    private static final ThreadLocal<Long> RTGC_RECHECK_START = new ThreadLocal<>();

    @Inject(method = "generateSkylightMap", at = @At("HEAD"))
    private void rtgc$countSkylightMap(final CallbackInfo ci) {
        ChunkGenerationProfiler.count(Counter.NEW_SECTION_REBUILDS);
    }

    @Inject(method = "recheckGaps", at = @At("HEAD"))
    private void rtgc$startRecheckGaps(final boolean onlyOne, final CallbackInfo ci) {
        if (ChunkGenerationProfiler.isEnabled()) {
            RTGC_RECHECK_START.set(System.nanoTime());
        }
    }

    @Inject(method = "recheckGaps", at = @At("RETURN"))
    private void rtgc$endRecheckGaps(final boolean onlyOne, final CallbackInfo ci) {
        final Long start = RTGC_RECHECK_START.get();
        if (start == null) {
            return;
        }
        RTGC_RECHECK_START.remove();
        ChunkGenerationProfiler.count(Counter.RECHECK_GAPS_CALLS);
        ChunkGenerationProfiler.add(Counter.RECHECK_GAPS_NS, System.nanoTime() - start);
    }
}
