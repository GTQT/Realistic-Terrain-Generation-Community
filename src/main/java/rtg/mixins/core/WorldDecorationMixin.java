package rtg.mixins.core;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rtg.api.util.FastDecoContext;

/**
 * 装饰期间的方块读写快路径。
 *
 * <p>只在 {@code BiomeDecorator#genDecorations} 打开的 {@link FastDecoContext} 内生效。
 * 目标区块已加载时直接读写 {@link Chunk}，跳过 {@code World} 的 chunk 查找和
 * {@code markAndNotifyBlock}；未加载时回退原版路径，保持跨区块边界语义。
 *
 * <p>写入仍走 {@code Chunk#setBlockState}，因此光照、tile entity、break/onBlockAdded
 * 都保留，只去掉生成期无用的邻居/客户端通知。
 */
@Mixin(World.class)
public abstract class WorldDecorationMixin {

    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void rtgc$readDecoratedBlock(BlockPos pos, CallbackInfoReturnable<IBlockState> cir) {
        if (!FastDecoContext.isActive()) {
            return;
        }
        World world = (World) (Object) this;
        if (world.isOutsideBuildHeight(pos)) {
            return;
        }
        Chunk chunk = world.getChunkProvider().getLoadedChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk != null) {
            cir.setReturnValue(chunk.getBlockState(pos));
        }
    }

    @Inject(method = "setBlockState", at = @At("HEAD"), cancellable = true)
    private void rtgc$writeDecoratedBlock(BlockPos pos, IBlockState state, int flags,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (!FastDecoContext.isActive()) {
            return;
        }
        World world = (World) (Object) this;
        if (world.isOutsideBuildHeight(pos)) {
            return;
        }
        Chunk chunk = world.getChunkProvider().getLoadedChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk != null) {
            cir.setReturnValue(chunk.setBlockState(pos, state) != null);
        }
    }
}
