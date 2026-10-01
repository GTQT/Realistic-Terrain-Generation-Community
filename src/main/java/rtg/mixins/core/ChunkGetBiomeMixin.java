package rtg.mixins.core;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.chunk.Chunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import rtg.api.world.biome.IRealisticBiome;
import rtg.world.biome.RtgBiomeLayout;
import rtg.world.biome.RtgLayoutAccess;


/**
 * <b>F3 的读取点</b>。
 *
 * <h2>为什么打在这里，而不是在外面补数据</h2>
 * F3 的原文只有一句（{@code GuiOverlayDebug.java:142}）：
 * <pre>
 *   list.add("Biome: " + chunk.getBiome(blockpos, this.mc.world.getBiomeProvider()).getBiomeName());
 * </pre>
 * 而 {@code World.getBiome(pos)} 也是同一条链
 * （{@code WorldProvider.getBiomeForCoordsBody} → {@code chunk.getBiome(pos, provider)}）。
 * 也就是说**客户端一切"这个坐标是什么群系"的提问最终都落到这个方法上**。
 *
 * <p>原版方法体读的是区块的 <b>byte 数组</b>：
 * <pre>
 *   int k = this.blockBiomeArray[j << 4 | i] & 255;
 *   if (k == 255) { ... }
 *   Biome biome1 = Biome.getBiome(k);
 * </pre>
 * 但装了 REID 之后该方法体被替换成读它的 <b>int 数组</b>，而实测那个 int 数组
 * **整场游戏都不是服务端的数据**（字节错位读出来的小数字），于是 F3 显示的是
 * 蘑菇岛 / 蘑菇岛岸 / 深海 / 海洋这类"随便撞上的小编号"。
 *
 * <p>与其在包、数组、REID 的同步路径上追（那几条路都不可控），不如**直接在读取点回答**：
 * 单人游戏里客户端与服务端同进程、共享同一个 {@link RtgLayoutAccess} 静态布局，
 * 所以这里能给出**与地形完全一致**的答案。
 *
 * <h2>作用范围（很小）</h2>
 * <ul>
 *   <li>只在**客户端**（{@code world.isRemote}）生效 —— 服务端的区块数组本来就是对的；</li>
 *   <li>只在**布局存在**时生效 —— 多人游戏的客户端没有布局，此处原样放行，行为不变；</li>
 *   <li>只是**读取**，不写任何数组、不碰 REID、不改生成结果。</li>
 * </ul>
 */
@Mixin(Chunk.class)
public abstract class ChunkGetBiomeMixin {

    @Inject(method = "getBiome", at = @At("HEAD"), cancellable = true)
    private void rtgc$answerFromLayout(final BlockPos pos, final BiomeProvider provider,
                                       final CallbackInfoReturnable<Biome> cir) {

        final Chunk self = (Chunk) (Object) this;
        final World world = self.getWorld();
        if (world == null || !world.isRemote) {
            return;                     // 服务端：区块数组本来就是服务端写的，不动
        }
        final RtgBiomeLayout layout = RtgLayoutAccess.current();
        if (layout == null) {
            return;                     // 多人游戏：客户端没有布局，保持原行为
        }
        final IRealisticBiome realistic = RtgLayoutAccess.biomeAt(pos.getX(), pos.getZ());
        if (realistic != null) {
            cir.setReturnValue(realistic.baseBiome());
        }
    }
}
