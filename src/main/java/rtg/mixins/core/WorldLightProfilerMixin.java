package rtg.mixins.core;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import rtg.api.util.ChunkGenerationProfiler;
import rtg.api.util.ChunkGenerationProfiler.Counter;

/**
 * {@code World#checkLight} / {@code World#checkLightFor} 的耗时拆解。
 *
 * <p>{@code Chunk#checkLight} 的发光方块扫描里，每次 {@code world.checkLight(pos)} 实测约 2.7µs：
 * 它先跑一次天光、再一次方块光，而每次 {@code checkLightFor} 又要做两次
 * {@code isAreaLoaded(16/18)}（各 9/25 次区块查表）、一次 {@code getRawLight}（6 个邻居），
 * 最后才决定要不要跑光照更新队列。这里把这几段分开计时，用来判断下一步该攻哪里。
 *
 * <p>只计时，不改行为；profiler 关闭时全部是空操作。
 */
@Mixin(World.class)
public abstract class WorldLightProfilerMixin {

    /** 是否用等价实现替换 {@code getRawLight}（{@code -Drtg.fastRawLight=false} 关闭）。 */
    private static final boolean FAST_RAW = !"false".equalsIgnoreCase(System.getProperty("rtg.fastRawLight", "true"));
    /** 调试：逐次比对快路径与原版结果（{@code -Drtg.fastRawLightVerify=true}）。 */
    private static final boolean VERIFY_RAW = Boolean.getBoolean("rtg.fastRawLightVerify");
    private static final EnumFacing[] RTGC_FACINGS = EnumFacing.values();
    private static final ThreadLocal<MutableBlockPos> RTGC_NEIGHBOUR = ThreadLocal.withInitial(MutableBlockPos::new);
    private static final ThreadLocal<MutableBlockPos> RTGC_LIGHT_POS = ThreadLocal.withInitial(MutableBlockPos::new);
    private static int rtgc$rawVerifyMismatches;

    @Inject(method = "checkLight", at = @At("HEAD"))
    private void rtgc$startWorldCheckLight(final BlockPos pos, final CallbackInfoReturnable<Boolean> cir) {
        if (!ChunkGenerationProfiler.isEnabled()) {
            return;
        }
        RTGC_WORLD_CHECK_LIGHT_START.set(System.nanoTime());
    }

    @Inject(method = "checkLight", at = @At("RETURN"))
    private void rtgc$endWorldCheckLight(final BlockPos pos, final CallbackInfoReturnable<Boolean> cir) {
        final Long start = RTGC_WORLD_CHECK_LIGHT_START.get();
        if (start == null) {
            return;
        }
        RTGC_WORLD_CHECK_LIGHT_START.remove();
        ChunkGenerationProfiler.add(Counter.WORLD_CHECK_LIGHT_NS, System.nanoTime() - start);
    }

    /** 把 {@code checkLight} 里的两次 {@code checkLightFor} 按天光/方块光分开计时。 */
    @Redirect(method = "checkLight",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;checkLightFor(Lnet/minecraft/world/EnumSkyBlock;Lnet/minecraft/util/math/BlockPos;)Z"))
    private boolean rtgc$timeCheckLightFor(final World world, final EnumSkyBlock lightType, final BlockPos pos, final BlockPos ignored) {
        if (!ChunkGenerationProfiler.isEnabled()) {
            return world.checkLightFor(lightType, pos);
        }
        final long start = System.nanoTime();
        final boolean result = world.checkLightFor(lightType, pos);
        final long elapsed = System.nanoTime() - start;
        ChunkGenerationProfiler.add(lightType == EnumSkyBlock.SKY
            ? Counter.WORLD_CHECK_LIGHT_SKY_NS
            : Counter.WORLD_CHECK_LIGHT_BLOCK_NS, elapsed);
        return result;
    }

    /** {@code checkLightFor} 的入口计数（天光/方块光各一次）。 */
    @Inject(method = "checkLightFor", at = @At("HEAD"))
    private void rtgc$countCheckLightFor(final EnumSkyBlock lightType, final BlockPos pos, final CallbackInfoReturnable<Boolean> cir) {
        ChunkGenerationProfiler.count(Counter.CHECKLIGHTFOR_CALLS);
    }

    /** 两次 {@code isAreaLoaded}：第 16 格一次、第 18 格一次，进入 BFS 之前的前置检查。 */
    @Redirect(method = "checkLightFor",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;isAreaLoaded(Lnet/minecraft/util/math/BlockPos;IZ)Z"))
    private boolean rtgc$timeIsAreaLoaded(final World world, final BlockPos pos, final int range, final boolean allowEmpty) {
        if (!ChunkGenerationProfiler.isEnabled()) {
            return world.isAreaLoaded(pos, range, allowEmpty);
        }
        final long start = System.nanoTime();
        final boolean result = world.isAreaLoaded(pos, range, allowEmpty);
        ChunkGenerationProfiler.add(Counter.CHECKLIGHTFOR_AREA_NS, System.nanoTime() - start);
        ChunkGenerationProfiler.count(Counter.CHECKLIGHTFOR_AREA_CALLS);
        return result;
    }

    /**
     * {@code getRawLight}：六个邻居 + 自身发光值，是决定要不要进 BFS 的关键一步。
     *
     * <p>原版每次要经过 {@code World#getLightFor}：{@code isValid} + {@code isBlockLoaded} +
     * {@code getChunk} 三层区块查表，再乘 7 个位置（自身 + 6 邻居），单次约 2µs。这里换成等价实现：
     * 直接 {@code getLoadedChunk} 一次拿到段再读 nibble，语义与原版逐一对应。</p>
     */
    @Redirect(method = "checkLightFor",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;getRawLight(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/EnumSkyBlock;)I"))
    private int rtgc$timeGetRawLight(final World world, final BlockPos pos, final EnumSkyBlock lightType) {
        final boolean prof = ChunkGenerationProfiler.isEnabled();
        final long start = prof ? System.nanoTime() : 0L;
        final int result;
        if (FAST_RAW) {
            result = rtgc$rawLightFast(world, pos, lightType);
            if (VERIFY_RAW) {
                final int expected = ((WorldLightAccess) (Object) world).rtgc$invokeGetRawLight(pos, lightType);
                if (expected != result) {
                    if (rtgc$rawVerifyMismatches < 5) {
                        org.apache.logging.log4j.LogManager.getLogger("RTG").warn(
                            "[RTG] fastRawLight 不一致 pos={} type={} fast={} vanilla={}",
                            pos, lightType, result, expected);
                    }
                    ++rtgc$rawVerifyMismatches;
                }
            }
        } else {
            result = ((WorldLightAccess) (Object) world).rtgc$invokeGetRawLight(pos, lightType);
        }
        if (prof) {
            ChunkGenerationProfiler.add(Counter.CHECKLIGHTFOR_RAW_NS, System.nanoTime() - start);
        }
        return result;
    }

    @Unique
    private static int rtgc$rawLightFast(final World world, final BlockPos pos, final EnumSkyBlock lightType) {
        if (lightType == EnumSkyBlock.SKY && world.canSeeSky(pos)) {
            return 15;
        }
        final IBlockState state = world.getBlockState(pos);
        int raw = lightType == EnumSkyBlock.SKY ? 0 : state.getBlock().getLightValue(state, world, pos);
        int opacity = state.getBlock().getLightOpacity(state, world, pos);
        if (opacity < 1) {
            opacity = 1;
        }
        if (opacity >= 15 || raw >= 14) {
            return raw;
        }
        final int x = pos.getX();
        final int y = pos.getY();
        final int z = pos.getZ();
        final MutableBlockPos neighbourPos = RTGC_NEIGHBOUR.get();
        for (final EnumFacing facing : RTGC_FACINGS) {
            neighbourPos.setPos(x + facing.getXOffset(), y + facing.getYOffset(), z + facing.getZOffset());
            final int neighbour = rtgc$lightForFast(world, lightType, neighbourPos) - opacity;
            if (neighbour > raw) {
                raw = neighbour;
            }
        }
        return raw;
    }

    /** 等价于 {@code World#getLightFor(EnumSkyBlock, BlockPos)}，但只做一次非加载式的区块查找。 */
    @Unique
    private static int rtgc$lightForFast(final World world, final EnumSkyBlock lightType, final BlockPos pos) {
        int y = pos.getY();
        if (y < 0) {
            y = 0;
        }
        if (y > 255) {
            return lightType.defaultLightValue;
        }
        final Chunk chunk = world.getChunkProvider().getLoadedChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return lightType.defaultLightValue;
        }
        return chunk.getLightFor(lightType, RTGC_LIGHT_POS.get().setPos(pos.getX(), y, pos.getZ()));
    }


    /** 光照 BFS 真正落地时每格一次 {@code setLightFor}（1.12/Forge 里返回 void），用它的次数与耗时衡量 BFS 规模。 */
    @Inject(method = "setLightFor", at = @At("HEAD"))
    private void rtgc$countSetLightFor(final EnumSkyBlock lightType, final BlockPos pos, final int lightValue,
                                       final CallbackInfo ci) {
        ChunkGenerationProfiler.count(Counter.CHECKLIGHTFOR_SETLIGHT_CALLS);
        if (ChunkGenerationProfiler.isEnabled()) {
            RTGC_SET_LIGHT_START.set(System.nanoTime());
        }
    }

    @Inject(method = "setLightFor", at = @At("RETURN"))
    private void rtgc$timeSetLightFor(final EnumSkyBlock lightType, final BlockPos pos, final int lightValue,
                                      final CallbackInfo ci) {
        final Long start = RTGC_SET_LIGHT_START.get();
        if (start == null) {
            return;
        }
        RTGC_SET_LIGHT_START.remove();
        ChunkGenerationProfiler.add(Counter.CHECKLIGHTFOR_SETLIGHT_NS, System.nanoTime() - start);
    }

    private static final ThreadLocal<Long> RTGC_SET_LIGHT_START = new ThreadLocal<>();

    private static final ThreadLocal<Long> RTGC_WORLD_CHECK_LIGHT_START = new ThreadLocal<>();
}
