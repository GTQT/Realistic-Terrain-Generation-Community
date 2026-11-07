package rtg.mixins.core;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@code World#getRawLight}（1.12 里是 private）给 {@link WorldLightProfilerMixin} 计时用。
 * 它每次要做 6 次邻居光照读 + 一次方块发光值查询，是 {@code checkLightFor} 里除 BFS 之外最重的一步。
 */
@Mixin(World.class)
public interface WorldLightAccess {

    @Invoker("getRawLight")
    int rtgc$invokeGetRawLight(BlockPos pos, EnumSkyBlock lightType);
}
