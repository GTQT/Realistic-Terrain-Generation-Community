package rtg.world;

import net.minecraft.client.Minecraft;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.gen.ChunkGeneratorOverworld;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import rtg.RTG;
import rtg.api.RTGAPI;
import rtg.api.util.Logger;
import rtg.api.world.RTGWorld;
import rtg.compat.ModCompat;
import rtg.world.biome.BiomeProviderBOP;
import rtg.world.biome.BiomeProviderRTG;
import rtg.world.gen.ChunkGeneratorRTG;


public final class WorldTypeRTG extends WorldType {

    private static WorldTypeRTG INSTANCE;

    private WorldTypeRTG() {
        super(RTG.MODID);
    }

    public static WorldTypeRTG getInstance() {
        if (INSTANCE == null) {
            init();
        }
        return INSTANCE;
    }

    public static void init() {
        INSTANCE = new WorldTypeRTG();
    }

    /**
     * 群系 provider。**客户端也返回 rtgc 的 provider**，但必须满足一个前提：
     * 客户端的 provider **只读、不建**布局。
     *
     * <h2>原来为什么只给服务端</h2>
     * 此前这里是 {@code if (!world.isRemote)} —— 客户端一律拿到原版 {@link BiomeProvider}，
     * 于是客户端所有"这个坐标是什么群系"的 provider 查询（{@code getBiomes} /
     * {@code areBiomesViable} / {@code findBiomePosition} / 直接问 provider 的 mod）
     * 得到的都是**原版 GenLayer** 的答案，与实际生成的 RTG 地形无关。实测日志里就是
     * {@code provider类=BiomeProvider}、{@code provider=minecraft:forest}（而布局是 {@code orchard}）。
     *
     * <h2>为什么能安全地给客户端</h2>
     * 客户端没有真实世界种子（1.12.2 的客户端世界由 {@code new WorldSettings(0L, …)} 构造，
     * {@code getSeed()} 恒为 0），所以**客户端绝不能建立布局** —— 那会把服务端正在用的全局布局
     * 覆盖成 seed 0 的，造成出生点区块与之后生成的区块地形断层（实测踩过）。
     * 现在 {@link BiomeProviderBOP} / {@link BiomeProviderRTG} 在 {@code world.isRemote} 时
     * **只取 {@code RtgLayoutAccess.current()}**，而 {@code getBiome} 读的是那个静态布局，
     * 所以客户端"构造时布局可能还没就绪"不成问题 —— 布局一就绪，同一个实例下一次调用即生效。
     *
     * <p>整段包 try/catch：任何失败都退回原版 provider 并记 ERROR，绝不因为群系 provider 把客户端搞崩。
     * {@link #getChunkGenerator} 的 {@code !world.isRemote} 守卫**保持不动** —— 客户端不生成区块。
     */
    @Override
    public BiomeProvider getBiomeProvider(World world) {
        try {
            final DimensionType type = world.provider.getDimensionType();
            if (RTGAPI.isAllowedDimensionType(type)) {
                final BiomeProvider provider = ModCompat.Mods.biomesoplenty.isLoaded()
                        ? new BiomeProviderBOP(world)
                        : new BiomeProviderRTG(RTGWorld.getInstance(world));
                return provider;
            }
            Logger.debug("DimensionType not in whitelist (ID:{}, Type:{}, Suffix:{}).. returning BiomeProvider", type.getId(), type, type.getSuffix());
        } catch (final Throwable t) {
            Logger.error("[RTG] ⚠ 建立 RTG 群系 provider 失败（side={}），退回原版 provider —— "
                    + "客户端的群系查询会变成原版 GenLayer 的答案，与实际生成的地形不符：{}",
                    world.isRemote ? "CLIENT" : "SERVER", t);
        }
        return new BiomeProvider(world.getWorldInfo());
    }

    @Override
    public IChunkGenerator getChunkGenerator(World world, String generatorOptions) {
        if (!world.isRemote) {
            final DimensionType type = world.provider.getDimensionType();
            if (RTGAPI.isAllowedDimensionType(type)) {
                Logger.debug("Allowed DimensionType detected (ID:{}, Type:{}, Suffix:{}).. returning ChunkGeneratorRTG", type.getId(), type, type.getSuffix());
                return new ChunkGeneratorRTG(RTGWorld.getInstance(world));
            } else {
                Logger.debug("DimensionType not in whitelist (ID:{}, Type:{}, Suffix:{}).. returning ChunkGeneratorOverworld", type.getId(), type, type.getSuffix());
            }
        }
        final WorldInfo wi = world.getWorldInfo();
        return new ChunkGeneratorOverworld(world, wi.getSeed(), wi.isMapFeaturesEnabled(), wi.getGeneratorOptions());
    }

    @Override
    public float getCloudHeight() {
        return 384F;
    }

    @Override
    public boolean isCustomizable() {
        return true;
    }

    @Override // Client-only
    public String getTranslationKey() {
        return "gui.createWorld.worldtypename";
    }

    @Override
    // Client-only; we make a proxied call here (no going back to SideOnly) so the dedicated server doesn't flip out with ClassNotFoundException
    @SideOnly(Side.CLIENT)
    public void onCustomizeButton(net.minecraft.client.Minecraft mc, net.minecraft.client.gui.GuiCreateWorld guiCreateWorld) {
        Minecraft.getMinecraft().displayGuiScreen(new rtg.client.GuiCustomizeWorldScreenRTG(guiCreateWorld, guiCreateWorld.chunkProviderSettingsJson));
    }
}
