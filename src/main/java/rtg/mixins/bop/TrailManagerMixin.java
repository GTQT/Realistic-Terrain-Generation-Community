package rtg.mixins.bop;

import biomesoplenty.common.remote.TrailManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TrailManager.class)
public abstract class TrailManagerMixin {

    /**
     * 完全跳过原版的联网检查逻辑，直接返回空数据
     */
    @Inject(
            method = "retrieveTrails",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void skipRemoteTrailCheck(CallbackInfo ci) {
        System.out.println("Skip remote trail check");
        // 直接取消原方法执行
        ci.cancel();
    }
}