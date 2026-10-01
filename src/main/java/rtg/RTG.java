package rtg;

import net.minecraft.client.Minecraft;
import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.*;
import rtg.RTGConfig.RTGGuiConfigFactory;
import rtg.api.RTGAPI;
import rtg.api.util.PlateauUtil;
import rtg.compat.ModCompat;
import rtg.event.EventHandlerCommon;
import rtg.init.BiomeInit;
import rtg.server.RTGCommandTree;
import rtg.world.WorldTypeRTG;

import java.nio.file.Paths;


@SuppressWarnings({"unused", "WeakerAccess"})
@Mod(
        modid = "rtgc",
        name = "RTG Community",
        version = RTG.VERSION,
        dependencies = "required-after:forge@[14.23.5.2847,);after:biomesoplenty@[7.0.1.2441,);after:traverse@[1.6.0,2.0.0)",
        guiFactory = RTGGuiConfigFactory.LOCATION,
        acceptableRemoteVersions = "*"
)
public class RTG {

    public static final String MODID = "rtgc";
    /**
     * 版本号取构建期生成的 {@link rtg.rtgc.Tags#VERSION}（来源 {@code gradle.properties} 的
     * {@code mod_version}）。
     *
     * <p>此前这里硬编码为 {@code "1.0.0"}，于是**无论装的是哪一版，日志里都写
     * {@code rtgc@1.0.0}** —— 排查时无法从日志确认玩家到底跑的哪个构建。
     * {@code Tags.VERSION} 是编译期常量，可直接用于 {@code @Mod(version = ...)}。
     */
    public static final String VERSION = rtg.rtgc.Tags.VERSION;
    public static final String API_ID = "rtgapi";

    @Mod.Instance(MODID)
    public static RTG instance;

    public static RTGProxy proxy;
    private static boolean DISABLE_DECORATIONS;
    private static boolean DISABLE_SURFACES;
    private static boolean DECO_DEBUG;
    private static boolean LAYOUT_DEBUG;

    public RTG() {
    }

    public static RTG getInstance() {
        return instance;
    }

    public static RTGProxy getProxy() {
        return proxy;
    }

    public static boolean decorationsDisable() {
        return DISABLE_DECORATIONS;
    }

    public static boolean surfacesDisabled() {
        return DISABLE_SURFACES;
    }

    /**
     * D4 诊断开关：为 true 时，每个区块的装饰结束后会打一行
     * {@code [RTG-DECO]} 记录**区块中心群系**与**实际被调用的 deco 数**。
     *
     * <p>用法：启动参数加 {@code -Drtg.debugDecorations}。
     * 默认关闭 —— 每个区块一行会把日志刷爆，只在排查"没有地表装饰"时开。
     *
     * <p>为什么需要它：`该群系有多少 deco` 与 `实际调用了几个` 是两件事
     * （{@code preGenerate(river)} 会过滤、山地链的概率缩放会过滤、
     * 关闭装饰的分支会整个跳过），只有并排看才能定位卡在哪一步。
     */
    public static boolean decoDebug() {
        return DECO_DEBUG;
    }

    /**
     * 群系布局诊断开关：为 true 时，每次建立布局会打印各「气候 × 位置」池的成员与数量
     * （{@code RtgBiomeCategorizer} 的报告）。
     *
     * <p>用法：启动参数加 {@code -Drtg.debugLayout}。默认关闭 —— 一次十几行，
     * 属于"要看的时候才看"的信息，正常游玩不需要。
     */
    public static boolean layoutDebug() {
        return LAYOUT_DEBUG;
    }

    @Mod.EventHandler
    public void initPre(FMLPreInitializationEvent event) {

        DISABLE_DECORATIONS = System.getProperties().containsKey("rtg.disableDecorations");
        DISABLE_SURFACES = System.getProperties().containsKey("rtg.disableSurfaces");
        DECO_DEBUG = System.getProperties().containsKey("rtg.debugDecorations");
        LAYOUT_DEBUG = System.getProperties().containsKey("rtg.debugLayout");

        RTGAPI.setConfigPath(Paths.get(event.getModConfigurationDirectory().getPath(), RTG.MODID.toUpperCase()));
        RTGConfig.init(event);

        // 初始化区块生成性能分析器
        if (RTGConfig.enableProfiling()) {
            rtg.api.util.ChunkGenerationProfiler.setEnabled(true);
            rtg.api.util.ChunkGenerationProfiler.setLogInterval(RTGConfig.profilerLogInterval());
            rtg.api.util.ChunkGenerationProfiler.setSlowThresholdMs(RTGConfig.profilerSlowThresholdMs());
        }

        RTGAPI.addAllowedDimensionType(DimensionType.OVERWORLD);

        WorldTypeRTG.init();
        ModCompat.init();

        BiomeInit.preInit();// initialise river and beach biomes
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        EventHandlerCommon.init();// TERRAIN_GEN_BUS, ORE_GEN_BUS
        if(RTGConfig.enableDefaultWorldType())DefaultWorldType();
    }

    @Mod.EventHandler
    public void initPost(FMLPostInitializationEvent event) {
        BiomeInit.init();// initialise all biomes supported internally
        ModCompat.doBiomeCheck();
        PlateauUtil.init();
    }

    @Mod.EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        RTGAPI.lockRtgBiomes();// We don't want the biome map to change after this point, so we lock it.
    }

    @Mod.EventHandler
    public void serverStarting(final FMLServerStartingEvent event) {
        event.registerServerCommand(new RTGCommandTree());
    }

    public static void DefaultWorldType() {
        for (int i = 0; i < WorldType.WORLD_TYPES.length; ++i) {
            if (WorldType.WORLD_TYPES[i] == WorldType.byName("rtgc")) {
                WorldType defaultype = WorldType.WORLD_TYPES[0];
                WorldType.WORLD_TYPES[0] = WorldType.WORLD_TYPES[i];
                WorldType.WORLD_TYPES[i] = defaultype;
                break;
            }
        }
    }

    public interface RTGProxy {
        void displayCustomizeWorldScreen(net.minecraft.client.gui.GuiCreateWorld guiCreateWorld);
    }

    public static final class ClientProxy implements RTGProxy {
        @Override
        public void displayCustomizeWorldScreen(net.minecraft.client.gui.GuiCreateWorld guiCreateWorld) {
            Minecraft.getMinecraft().displayGuiScreen(new rtg.client.GuiCustomizeWorldScreenRTG(guiCreateWorld, guiCreateWorld.chunkProviderSettingsJson));
        }
    }

    public static class ServerProxy implements RTGProxy {
        @Override
        public void displayCustomizeWorldScreen(net.minecraft.client.gui.GuiCreateWorld guiCreateWorld) {
        }
    }
}
