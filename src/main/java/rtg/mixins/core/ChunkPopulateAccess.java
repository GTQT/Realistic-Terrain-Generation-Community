package rtg.mixins.core;

import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.IChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 暴露 {@link Chunk#populate(IChunkGenerator)}，供队列强制完成排队区块。 */
@Mixin(Chunk.class)
public interface ChunkPopulateAccess {

    @Invoker("populate")
    void rtgc$populate(IChunkGenerator generator);
}
