package rtg.mixins.core;

import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@link Chunk} 的内部数组，供 {@code FastChunkAssembler} 直接塞入自己拼好的段，
 * 以及 {@code FastOreContext} 判断一次矿石写入是否需要走完整的 {@code Chunk#setBlockState}。
 */
@Mixin(Chunk.class)
public interface ChunkStorageAccess {

    @Accessor("storageArrays")
    ExtendedBlockStorage[] rtgc$getStorageArrays();

    /** 每列的"最高非空气方块 +1"，{@code setBlockState} 会在这里做增量维护。 */
    @Accessor("heightMap")
    int[] rtgc$getHeightMap();

    /** 每列的降水高度；写方块时若越界会被重置为 -999 等待重算。 */
    @Accessor("precipitationHeightMap")
    int[] rtgc$getPrecipitationHeightMap();
}
