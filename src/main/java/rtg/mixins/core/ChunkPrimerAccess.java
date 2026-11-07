package rtg.mixins.core;

import net.minecraft.world.chunk.ChunkPrimer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@link ChunkPrimer} 内部的 {@code char[65536]}：里面存的是全局 block state id
 * （{@code Block.BLOCK_STATE_IDS}），直接按 id 搬运比逐格 {@code getBlockState()} 快一个数量级。
 */
@Mixin(ChunkPrimer.class)
public interface ChunkPrimerAccess {

    @Accessor("data")
    char[] rtgc$getData();
}
