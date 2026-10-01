package rtg.event;

import net.minecraft.client.gui.GuiCreateWorld;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import rtg.RTG;
import rtg.RTGConfig;
import rtg.api.world.biome.IRealisticBiome;
import rtg.client.WorldTypeMessageGUI;
import rtg.world.biome.RtgLayoutAccess;


@Mod.EventBusSubscriber(modid = RTG.MODID, value = Side.CLIENT)
public final class EventHandlerClient
{
    private EventHandlerClient() {}

    /**
     * 客户端群系数组回填。
     *
     * <h2>为什么需要它</h2>
     * 装了 REID（JEID）之后，**客户端的区块群系数组不是服务端给的**：字节数组里大量格子是
     * {@code jeid:error_biome}(113)，int 数组则是随索引跳动的垃圾值。而原版
     * {@code Chunk.getBiome}（F3 读的就是它）在这些数组上取数，于是客户端手上的群系
     * 与实际地形无关。
     *
     * <h2>为什么可以在这里补</h2>
     * 单人游戏里客户端与服务端**同进程**，共用同一个 {@link RtgLayoutAccess} 静态布局，
     * 所以客户端完全有能力自己算出该区块正确的群系，不必等 REID 送过来。
     * 多人游戏里客户端没有布局（{@code current() == null}），此处直接跳过，行为不变。
     *
     * <p>这是"把客户端的数据也修正过来"的一层；F3 本身的正确性由
     * {@code rtg.mixins.core.ChunkGetBiomeMixin}（在 {@code Chunk.getBiome} 读取点按布局回答）
     * 保证 —— 两者互补：前者让客户端的群系数据可信，后者保证读取点永不落到 REID 的坏数据上。
     */
    @SubscribeEvent
    public static void onChunkLoad(final ChunkEvent.Load event) {

        final World world = event.getWorld();
        if (world == null || !world.isRemote) {
            return;                     // 服务端自己写的就是对的
        }
        final Chunk chunk = event.getChunk();
        if (chunk == null || chunk.getBiomeArray() == null || chunk.getBiomeArray().length != 256) {
            return;
        }
        backfillChunk(chunk);
    }

    /** 按布局重算该区块 256 列的群系，写入 int 数组与字节数组。 */
    private static void backfillChunk(final Chunk chunk) {

        if (RtgLayoutAccess.current() == null) {
            return;                     // 多人游戏：客户端没有布局
        }
        final net.minecraft.util.math.ChunkPos cp = chunk.getPos();
        final int[] ids = new int[256];
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                final IRealisticBiome rb = RtgLayoutAccess.biomeAt(cp.x * 16 + lx, cp.z * 16 + lz);
                ids[lz << 4 | lx] = rb == null ? 0 : net.minecraft.world.biome.Biome.getIdForBiome(rb.baseBiome());
            }
        }
        if (chunk instanceof org.dimdev.jeid.INewChunk) {
            ((org.dimdev.jeid.INewChunk) chunk).setIntBiomeArray(ids);
        }
        final byte[] bytes = new byte[256];
        for (int i = 0; i < 256; i++) {
            bytes[i] = (byte) ids[i];
        }
        chunk.setBiomeArray(bytes);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) // We want to be last so that our handler avoids race conditions with other mods
    public static void onGuiCreateWorld(final GuiOpenEvent event) {

        GuiScreen gui = event.getGui();
        if (gui instanceof GuiCreateWorld) {

            // Access transformed (private -> public); See: src/main/resources/META-INF/rtg_at.cfg
            String seed = ((GuiCreateWorld)gui).worldSeed;

            // we only display the world type notification if creating a new world, not when recreating from an existing one
            if (seed.isEmpty() && RTGConfig.worldTypeNotification()) {
                RTGConfig.toggleWorldTypeNotification();
                event.setGui(new WorldTypeMessageGUI(gui));
            }
        }
    }
}
