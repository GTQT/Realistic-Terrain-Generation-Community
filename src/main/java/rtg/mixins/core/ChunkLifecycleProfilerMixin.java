package rtg.mixins.core;

import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rtg.api.util.ChunkGenerationProfiler;
import rtg.api.util.ChunkGenerationProfiler.Counter;

/**
 * {@code Chunk#onLoad} / {@code Chunk#populate} 包装层的耗时归因。
 *
 * <p>{@code provideChunk} 在 {@code generateChunk}（CHUNK_TOTAL）和 population（POP_TOTAL）
 * 之外还会做两件事：{@code chunk.onLoad()}（触发 {@code ChunkEvent.Load} 的全部监听器，
 * 包括其它模组的）和 {@code chunk.populate(provider, generator)} 的包装层（4 次邻区块查询）。
 * 基准里这两段的耗时以前只能算进"账外时间"，这里单独记账，避免继续猜。
 *
 * <p>只在 {@link ChunkGenerationProfiler#isEnabled()} 时计时；生产环境零开销。
 */
@Mixin(Chunk.class)
public abstract class ChunkLifecycleProfilerMixin {

    private static final ThreadLocal<Long> RTGC_ON_LOAD_START = new ThreadLocal<>();
    private static final ThreadLocal<Long> RTGC_POP_WRAPPER_START = new ThreadLocal<>();

    @Inject(method = "onLoad", at = @At("HEAD"))
    private void rtgc$startOnLoad(final CallbackInfo ci) {
        if (ChunkGenerationProfiler.isEnabled()) {
            RTGC_ON_LOAD_START.set(System.nanoTime());
        }
    }

    @Inject(method = "onLoad", at = @At("RETURN"))
    private void rtgc$endOnLoad(final CallbackInfo ci) {
        final Long start = RTGC_ON_LOAD_START.get();
        if (start == null) {
            return;
        }
        RTGC_ON_LOAD_START.remove();
        ChunkGenerationProfiler.add(Counter.CHUNK_ON_LOAD_NS, System.nanoTime() - start);
    }

    @Inject(method = "populate(Lnet/minecraft/world/chunk/IChunkProvider;Lnet/minecraft/world/gen/IChunkGenerator;)V",
            at = @At("HEAD"))
    private void rtgc$startPopWrapper(final IChunkProvider provider, final IChunkGenerator generator,
                                      final CallbackInfo ci) {
        if (ChunkGenerationProfiler.isEnabled()) {
            ChunkGenerationProfiler.count(Counter.POP_WRAPPER_CALLS);
            RTGC_POP_WRAPPER_START.set(System.nanoTime());
        }
    }

    @Inject(method = "populate(Lnet/minecraft/world/chunk/IChunkProvider;Lnet/minecraft/world/gen/IChunkGenerator;)V",
            at = @At("RETURN"))
    private void rtgc$endPopWrapper(final IChunkProvider provider, final IChunkGenerator generator,
                                    final CallbackInfo ci) {
        final Long start = RTGC_POP_WRAPPER_START.get();
        if (start == null) {
            return;
        }
        RTGC_POP_WRAPPER_START.remove();
        ChunkGenerationProfiler.add(Counter.POP_WRAPPER_NS, System.nanoTime() - start);
    }
}
