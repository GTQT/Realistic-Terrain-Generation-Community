package rtg.mixins.core;

import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 绕过 {@link ExtendedBlockStorage#set} 直接写 storage 时，需要自己维护两个引用计数：
 * {@code blockRefCount} 决定 {@code isEmpty()}（影响 top filled segment / 存盘跳过），
 * {@code tickRefCount} 决定随机刻是否跳过该段。
 */
@Mixin(ExtendedBlockStorage.class)
public interface ExtendedBlockStorageAccess {

    @Accessor("blockRefCount")
    void rtgc$setBlockRefCount(int count);

    @Accessor("tickRefCount")
    void rtgc$setTickRefCount(int count);

    @Accessor("blockRefCount")
    int rtgc$getBlockRefCount();

    @Accessor("tickRefCount")
    int rtgc$getTickRefCount();
}
