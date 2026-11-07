package rtg.mixins.core;

import net.minecraft.util.BitArray;
import net.minecraft.world.chunk.BlockStateContainer;
import net.minecraft.world.chunk.IBlockStatePalette;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@link BlockStateContainer} 的内部结构，用于把 {@code char[]} 里的全局 id 直接按位写进
 * {@code storage}（要求 palette 已切到 registry 模式，此时 storage 里存的就是全局 id）。
 */
@Mixin(BlockStateContainer.class)
public interface BlockStateContainerAccess {

    @Accessor("storage")
    BitArray rtgc$getStorage();

    @Accessor("storage")
    void rtgc$setStorage(BitArray storage);

    @Accessor("palette")
    IBlockStatePalette rtgc$getPalette();

    @Accessor("palette")
    void rtgc$setPalette(IBlockStatePalette palette);

    @Accessor("bits")
    int rtgc$getBits();

    @Accessor("bits")
    void rtgc$setBits(int bits);
}
