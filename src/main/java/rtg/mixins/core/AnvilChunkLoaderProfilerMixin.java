package rtg.mixins.core;

import net.minecraft.world.World;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rtg.api.util.ChunkGenerationProfiler;
import rtg.api.util.ChunkGenerationProfiler.Counter;

/**
 * 统计"这个区块从存档文件读回并构建成 {@code Chunk} 对象"的耗时。
 * 覆盖：区域文件读取+解压、NBT 解析、{@code readChunkFromNBT} 建 Chunk。
 */
@Mixin(AnvilChunkLoader.class)
public abstract class AnvilChunkLoaderProfilerMixin {

    private static final ThreadLocal<Long> RTGC_DISK_PROBE_START = new ThreadLocal<>();

    @Inject(method = "loadChunk", at = @At("HEAD"))
    private void rtgc$startDiskProbe(final World worldIn, final int x, final int z, final CallbackInfo ci) {
        if (ChunkGenerationProfiler.isEnabled()) {
            RTGC_DISK_PROBE_START.set(System.nanoTime());
        }
    }

    @Inject(method = "loadChunk", at = @At("RETURN"))
    private void rtgc$endDiskProbe(final World worldIn, final int x, final int z, final CallbackInfo ci) {
        final Long start = RTGC_DISK_PROBE_START.get();
        if (start == null) {
            return;
        }
        RTGC_DISK_PROBE_START.remove();
        ChunkGenerationProfiler.add(Counter.CHUNK_DISK_PROBE_NS, System.nanoTime() - start);
    }
}
