package rtg.mixins.core;

import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.IChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import rtg.api.util.ChunkPopulationQueue;

/**
 * 把 {@code provideChunk}/{@code loadChunk} 末尾的同步 populate 交给队列。
 *
 * <p>装饰期间读取未加载邻区块会再次走到这里。嵌套区块仍正常生成，但装饰阶段的
 * populate 只入队，避免递归 populate 和 {@code BiomeDecorator} 重入。
 */
@Mixin(ChunkProviderServer.class)
public abstract class ChunkProviderServerPopulationMixin {

    @Shadow
    private WorldServer world;

    @Redirect(
            method = {"provideChunk", "loadChunk"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/chunk/Chunk;populate(Lnet/minecraft/world/chunk/IChunkProvider;Lnet/minecraft/world/gen/IChunkGenerator;)V"
            )
    )
    private void rtgc$populateOrQueue(Chunk chunk, IChunkProvider provider, IChunkGenerator generator) {
        ChunkPopulationQueue.submit(chunk, provider, generator, this.world);
    }
}
