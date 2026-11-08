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
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * {@code World#checkLightFor} 里 {@code getRawLight} 的等价快路径。
 *
 * <p>原版 {@code getRawLight} 每次要经过 {@code World#getLightFor}：{@code isValid} +
 * {@code isBlockLoaded} + {@code getChunk} 三层区块查表，再乘 7 个位置（自身 + 6 邻居），
 * 单次约 2µs。这里换成等价实现：直接 {@code getLoadedChunk} 一次拿到段再读 nibble，
 * 语义与原版逐一对应。</p>
 */
@Mixin(World.class)
public abstract class WorldLightMixin {

    /** 是否用等价实现替换 {@code getRawLight}（{@code -Drtg.fastRawLight=false} 关闭）。 */
    private static final boolean FAST_RAW = !"false".equalsIgnoreCase(System.getProperty("rtg.fastRawLight", "true"));
    /** 调试：逐次比对快路径与原版结果（{@code -Drtg.fastRawLightVerify=true}）。 */
    private static final boolean VERIFY_RAW = Boolean.getBoolean("rtg.fastRawLightVerify");
    private static final EnumFacing[] RTGC_FACINGS = EnumFacing.values();
    private static final ThreadLocal<MutableBlockPos> RTGC_NEIGHBOUR = ThreadLocal.withInitial(MutableBlockPos::new);
    private static final ThreadLocal<MutableBlockPos> RTGC_LIGHT_POS = ThreadLocal.withInitial(MutableBlockPos::new);
    private static int rtgc$rawVerifyMismatches;

    @Redirect(method = "checkLightFor",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;getRawLight(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/EnumSkyBlock;)I"))
    private int rtgc$replaceGetRawLight(final World world, final BlockPos pos, final EnumSkyBlock lightType) {
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
}
