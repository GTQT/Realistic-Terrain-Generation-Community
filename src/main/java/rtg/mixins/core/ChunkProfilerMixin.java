package rtg.mixins.core;

import net.minecraft.world.chunk.Chunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rtg.api.util.ChunkGenerationProfiler;
import rtg.api.util.ChunkGenerationProfiler.Counter;

/**
 * Chunk 侧计数器：
 * <ul>
 *   <li>{@code generateSkylightMap} 的同步重建次数；</li>
 *   <li>{@code recheckGaps} 的调用次数与耗时（延后到区块 tick 的部分）。</li>
 * </ul>
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
