package rtg;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Biomes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.common.config.Property.Type;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;
import net.minecraftforge.fml.client.event.ConfigChangedEvent.OnConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import rtg.api.RTGAPI;
import rtg.api.util.BlockUtil;
import rtg.api.util.Logger;
import rtg.api.world.deco.DecoTree;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static net.minecraft.util.math.MathHelper.getInt;

@SuppressWarnings("WeakerAccess")
@Mod.EventBusSubscriber(modid = RTG.MODID)
public final class RTGConfig {
    public static final String LANG_ROOT = "rtgconfig.";
    private static Configuration config;

    private RTGConfig() {
    }

    public static void init(FMLPreInitializationEvent event) {
        if (config == null) {
            config = new Configuration(RTGAPI.getConfigPath().resolve(event.getSuggestedConfigurationFile().getName()).toFile());
        }
        config.load();
        Arrays.stream(Setting.values()).forEach(RTGConfig::generateProperty);
        sync();
    }

    private static void sync() {

        RTGConfig.config.getCategoryNames().stream()
                .map(c -> config.getCategory(c))
                .forEach(cat -> cat.setLanguageKey(LANG_ROOT + cat.getQualifiedName()));

        if (config.hasChanged()) {
            Logger.debug("[RTGConfig#sync] Config changed; Saving...");
            config.save();
        }

        // 按类型回读所有设置，避免漏掉某个类型后配置值不生效。
        Arrays.stream(Setting.values()).forEach(setting -> {
            final Property property = getProperty(setting);
            switch (setting.getType()) {
                case BOOLEAN:
                    setting.setCurVal(property.getBoolean());
                    break;
                case INTEGER:
                    setting.setCurVal(property.getInt());
                    break;
                case DOUBLE:
                    setting.setCurVal(property.getDouble());
                    break;
                case STRING:
                    if (setting.isArray()) {
                        setting.setCurVal(property.getStringList());
                    } else {
                        setting.setCurVal(property.getString());
                    }
                    break;
                case MOD_ID:
                case COLOR:
                default:
                    // 本配置未使用 COLOR；defaultWorldType（MOD_ID）的属性实际以字符串存储，
                    // 沿用历史上 getBoolean() 的语义以保持行为不变。
                    setting.setCurVal(property.getBoolean());
                    break;
            }
        });

        // Sets the shadow blocks during init. After they are set they can not be changed until the game is restarted.
        RTGAPI.setShadowBlocks(shadowStoneBlock(), shadowDesertBlock());
    }

    @SuppressWarnings("ConstantConditions")
    private static Property generateProperty(Setting setting) {

        switch (setting.getType()) {

            case BOOLEAN:
                return config.get(setting.getCategory(), setting.name(), (Boolean) setting.getCurVal(), setting.getComment())
                        .setLanguageKey(setting.getLangKey())
                        .setRequiresMcRestart(setting.reqRestart());

            case STRING:
                if (setting.isArray()) {
                    return config.get(setting.getCategory(), setting.name(), (String[]) setting.getCurVal(), setting.getComment())
                            .setLanguageKey(setting.getLangKey())
                            .setRequiresMcRestart(setting.reqRestart());
                } else {
                    return config.get(setting.getCategory(), setting.name(), (String) setting.getCurVal(), setting.getComment())
                            .setLanguageKey(setting.getLangKey())
                            .setRequiresMcRestart(setting.reqRestart());
                }

            case INTEGER:
                return config.get(setting.getCategory(), setting.name(), (Integer) setting.getCurVal(), setting.getComment())
                        .setMinValue((Integer) setting.getMinVal())
                        .setMaxValue((Integer) setting.getMaxVal())
                        .setLanguageKey(setting.getLangKey())
                        .setRequiresMcRestart(setting.reqRestart());

            case DOUBLE:
                return config.get(setting.getCategory(), setting.name(), (Double) setting.getCurVal(), setting.getComment())
                        .setMinValue((Double) setting.getMinVal())
                        .setMaxValue((Double) setting.getMaxVal())
                        .setLanguageKey(setting.getLangKey())
                        .setRequiresMcRestart(setting.reqRestart());
        }
        return config.get(setting.getCategory(), setting.name(), setting.getDefVal().toString(), setting.getComment())
                .setLanguageKey(setting.getLangKey())
                .setRequiresMcRestart(setting.reqRestart());
    }

    private static Property getProperty(Setting setting) {
        Property ret = null;
        if (config.hasCategory(setting.getCategory())) {
            ret = config.getCategory(setting.getCategory()).get(setting.name());
        }
        return ret != null ? ret : generateProperty(setting);
    }

    public static boolean worldTypeNotification() {
        return (Boolean) Setting.worldTypeNotification.getCurVal();
    }

    public static boolean enableDefaultWorldType() {
        return (Boolean) Setting.defaultWorldType.getCurVal();
    }

    public static int getBiomeSize() {
        return (Integer) Setting.biomeSize.getCurVal();
    }

    public static int getRiverSize() {
        return (Integer) Setting.riverSize.getCurVal();
    }

    /**
     * 把 {@code geography.riverSize}（1–10，默认 4）映射为河谷阈值的缩放系数。
     * 默认值 4 对应系数 1.0；值越大，被判定为河谷的范围越宽、河网越密。
     */
    public static float riverSizeFactor() {
        return 1f + (getRiverSize() - 4) * 0.1f;
    }

    private static double dbl(Setting setting) {
        return ((Double) setting.getCurVal()).doubleValue();
    }

    public static int getIslandScheme() {
        return (Integer) Setting.islandScheme.getCurVal();
    }

    public static int getTempScheme() {
        return (Integer) Setting.tempScheme.getCurVal();
    }

    public static int getRainScheme() {
        return (Integer) Setting.rainScheme.getCurVal();
    }

    public static boolean enableDebugging() {
        return (Boolean) Setting.enableDebugging.getCurVal();
    }

    public static boolean additionalBiomeInfo() {
        return (Boolean) Setting.additionalBiomeInfo.getCurVal();
    }

    public static Biome patchBiome() {
        final String cfgBiome = (String) Setting.patchBiome.getCurVal();
        final Biome biome;
        if ((biome = getBiomeFromCfgString(cfgBiome)) == null) {
            Logger.error("Erroneous patch biome set in config: {} (non-existant). Using default.", cfgBiome);
            return Biomes.PLAINS;
        }
        return biome;
    }

    public static Set<String> getBlacklistMods() {
        return Arrays.stream((String[]) Setting.blacklistMods.getCurVal()).collect(Collectors.toSet());
    }

    public static boolean lushRiverbanksInDesert() {
        return (Boolean) Setting.lushRiverbanksInDesert.getCurVal();
    }

    @Nullable
    public static IBlockState shadowStoneBlock() {
        return BlockUtil.getBlockStateFromCfgString((String) Setting.shadowStoneBlock.getCurVal());
    }

    @Nullable
    public static IBlockState shadowDesertBlock() {
        return BlockUtil.getBlockStateFromCfgString((String) Setting.shadowDesertBlock.getCurVal());
    }

    public static boolean rtgTreesFromSaplings() {
        return (Boolean) Setting.rtgTreesFromSaplings.getCurVal();
    }

    public static double treeDensityMultiplier() {
        return (Double) Setting.treeDensityMultiplier.getCurVal();
    }

    public static boolean treesCanGenerateOnSand() {
        return (Boolean) Setting.treesCanGenerateOnSand.getCurVal();
    }

    // 移植上游新树系统 / T6：接管机制总开关。
    public static boolean treesInNonRTGWorlds() {
        return (Boolean) Setting.treesInNonRTGWorlds.getCurVal();
    }

    // ========== 原版矿脉开关（格雷科技整合包） ==========
    //
    // GT 的 WorldgenModule 在 ORE_GEN_BUS 上以 HIGH 优先级拒绝 COAL/IRON/GOLD/REDSTONE/DIAMOND/
    // LAPIS/QUARTZ/EMERALD，所以这些原版矿在 GT 包里本来就不生成；但 DIRT/GRAVEL/DIORITE/
    // GRANITE/ANDESITE 仍会跑满 48 条矿脉的探针循环（约 0.5ms/区块）。这里在 GT 确实拒绝原版矿时
    // 直接把整个 genStandardOre1/2 跳过。
    //
    // -Drtg.skipVanillaOres=true|false 可强制覆盖（基准 A/B 用）。
    private static Boolean gregTechDisablesVanillaOres;

    public static boolean skipVanillaOreGen() {
        final String override = System.getProperty("rtg.skipVanillaOres");
        if (override != null) {
            return Boolean.parseBoolean(override);
        }
        if (!(Boolean) Setting.skipVanillaOresWhenGregTech.getCurVal()) {
            return false;
        }
        return gregTechDisablesVanillaOres();
    }

    private static boolean gregTechDisablesVanillaOres() {
        if (gregTechDisablesVanillaOres == null) {
            boolean result = false;
            if (net.minecraftforge.fml.common.Loader.isModLoaded("gregtech")) {
                try {
                    // 反射读 GT 自己的开关：整合包可能把它关掉，那时原版矿脉必须保留。
                    final Class<?> holder = Class.forName("gregtech.common.ConfigHolder");
                    final Object worldgen = holder.getField("worldgen").get(null);
                    result = worldgen.getClass().getField("disableVanillaOres").getBoolean(worldgen);
                } catch (Throwable t) {
                    // 装了 GT 但读不到配置（版本差异）：按 GT 的默认行为处理。
                    result = true;
                }
            }
            gregTechDisablesVanillaOres = result;
            rtg.api.util.Logger.debug("[RTGConfig] 原版矿脉跳过=%s（GT 检测）", result);
        }
        return gregTechDisablesVanillaOres;
    }

    public static boolean shrubsBelowSurface() {
        return (Boolean) Setting.shrubsBelowSurface.getCurVal();
    }

    public static boolean barkCoveredLogs() {
        return (Boolean) Setting.barkCoveredLogs.getCurVal();
    }

    public static float riverDepth() {
        float result = ((Double) Setting.riverDepth.getCurVal()).floatValue();
        return result;
    }

    public static float waterFeatureWidthMultiplier() {
        float result = ((Double) Setting.waterFeatureWidthMultiplier.getCurVal()).floatValue();
        return result;
    }

    public static void toggleWorldTypeNotification() {
        getProperty(Setting.worldTypeNotification).setValue(false);
        sync();

    }

    @SubscribeEvent
    public static void onConfigChange(OnConfigChangedEvent event) {
        if (RTG.MODID.equals(event.getModID())) {
            sync();
        }
    }

    @Nullable
    public static Biome getBiomeFromCfgString(final String cfgString) {
        ResourceLocation rl = new ResourceLocation(cfgString);
        return ForgeRegistries.BIOMES.containsKey(rl) ? ForgeRegistries.BIOMES.getValue(rl) : null;
    }

    public static Biome getBiomeFromCfgString(final String cfgString, final Biome fallback) {
        Biome biome = getBiomeFromCfgString(cfgString);
        return biome != null ? biome : fallback;
    }

    public enum Category {

        client(null),
        common(null),
        geography(common),
        debug(common),
        surface(common),
        trees(common),
        worldgen(common);

        private final Category parent;

        Category(@Nullable Category parentIn) {
            this.parent = parentIn;
        }

        @Override
        public String toString() {
            if (parent != null) {
                return parent + "." + name();
            } else return name();
        }
    }

    public enum Setting {
        worldTypeNotification(Type.BOOLEAN, Category.client,
                "When enabled, this will display an informational message about RTG when entering the Customize World screen.\n" +
                        "This will display once and automatically disable itself.",
                true),

        defaultWorldType(Type.MOD_ID, Category.common,
                "RTGC To be the default world type for new worlds.\n" +
                        "!This setting is not used for existing worlds!",
                true),

        biomeSize(Type.INTEGER, Category.geography,
                "Number of times biomes are scaled and refined.\n" +
                        "!Smaller values result in smaller, more fragmented biomes with more frequent terrain transitions!\n" +
                        "!仅装 Biomes O' Plenty 时生效（它驱动的是 BOP 自己的 GenLayer 栈；!\n" +
                        "!本仓库的基础群系来自原版 GenLayer，没有对应机制可接）。!",
                5, 1, 10),

        riverSize(Type.INTEGER, Category.geography,
                "Density and complexity of river distribution.\n" +
                        "!Larger values lead to denser and more intricate river systems!",
                4, 1, 10),

        islandScheme(Type.INTEGER, Category.geography,
                "Global island and ocean distribution.\n" +
                        "!1: Vanilla distribution (no clear preference) 2: Continents 3: Archipelagos!\n" +
                        "!仅装 Biomes O' Plenty 时生效（同上）。本项控制的是 BOP 的「小岛」层。!",
                1, 1, 3),

        tempScheme(Type.INTEGER, Category.geography,
                "Temperature distribution patterns.\n" +
                        "!1: Latitude-based 2: Small areas 3: Medium areas 4: Large areas 5: Random!\n" +
                        "!仅装 Biomes O' Plenty 时生效（同上）。!",
                3, 1, 5),

        rainScheme(Type.INTEGER, Category.geography,
                "Precipitation distribution patterns.\n" +
                        "!1: Small areas 2: Medium areas 3: Large areas 4: Random!\n" +
                        "!仅装 Biomes O' Plenty 时生效（同上）。!",
                3, 1, 4),

        enableDebugging(Type.BOOLEAN, Category.debug,
                "Enable extra debug logging.\n" +
                        "!This setting has a severe performance penalty. Only enable if you know what you are doing!",
                false),

        additionalBiomeInfo(Type.BOOLEAN, Category.debug,
                "Enable the logging of additional biome information on startup.",
                false),

        patchBiome(Type.STRING, Category.debug,
                "If RTG encounters an unsupported biome it will generate this biome instead.\n" +
                        "This uses the standard ResourceLocation format: mod_id:biome_registry_name",
                "minecraft:plains"),

        blacklistMods(Type.STRING, Category.debug,
                "A blacklist of mods whose biomes will never be supported, so ignore them.\n" +
                        "This will only suppress log warnings during initialization.",
                "appliedenergistics2",
                "galacticraftcore",
                "galacticraftplanets",
                "extraplanets",
                "moreplanets",
                "twilightforest"
        ),

        lushRiverbanksInDesert(Type.BOOLEAN, Category.surface,
                "Set this to FALSE to prevent RTG from generating lush river bank decorations in hot biomes,\n" +
                        "like Desert and Mesa. Lush decorations consist of tallgrass, trees, shrubs, and other flora.",
                true, true),

        shadowStoneBlock(Type.STRING, Category.surface,
                "The block to use for stone terrain shadowing, typically seen on the cliffs of stone mountains.\n" +
                        "Leave blank to disable",
                "minecraft:stained_hardened_clay[color=cyan]", true),

        shadowDesertBlock(Type.STRING, Category.surface,
                "The block to use for desert terrain shadowing, typically seen on the cliffs of desert mountains.\n" +
                        "Leave blank to disable",
                "minecraft:stained_hardened_clay[color=gray]", true),

        rtgTreesFromSaplings(Type.BOOLEAN, Category.trees,
                "Set this to TRUE to allow RTG's custom trees to grow from groups of vanilla saplings.\n" +
                        "Otherwise RTG's custom trees cannot be grown",
                true),

        // 注意：默认值保持上游的 1.0D。
        // rtgc 的降密不靠这里，而是靠 DecoTree.TREE_DENSITY_REDUCTION（代码级固定系数）——
        // 因为已有世界的 config/RTG/rtg.cfg 里存的旧值会覆盖默认值，
        // 只改默认值对已经在跑的世界无效（实测树量爆炸那次就是这么来的）。
        treeDensityMultiplier(Type.DOUBLE, Category.trees,
                "This setting allows you to alter the amount of RTG trees that generate globally in all biomes.\n" +
                        "This setting is compounded with the density setting found in the biome configs and only affects trees generated by RTG." +
                        "Trees generated by a biome's native decorator will adhere to their own density rules.\n" +
                        "values below 1.0 will decrease the amount of trees, values above 1.0 will increase the amount of trees" +
                        "The combination of this value and the biome-specific value will never exceed 5.0" +
                        "NOTE: rtgc additionally applies a fixed global reduction (see DecoTree.TREE_DENSITY_REDUCTION).",
                1.0D, 0.0D, DecoTree.MAX_TREE_DENSITY),

        treesCanGenerateOnSand(Type.BOOLEAN, Category.trees,
                "Set this to FALSE to prevent trees from generating on sand.\n" +
                        "This setting only affects trees generated by RTG. Trees generated by a biome's decorator\n" +
                        "will adhere to their own generation rules. (RTG's Palm Trees ignore this setting.)",
                true),

        // 移植上游新树系统 / T6：接管机制的总开关（见 EventHandlerCommon.takeoverTreeGeneration）。
        treesInNonRTGWorlds(Type.BOOLEAN, Category.trees,
                "Set this to FALSE to prevent using RTG trees in other world types.",
                true),

        shrubsBelowSurface(Type.BOOLEAN, Category.trees,
                "Set this to FALSE to prevent shrub trunks from generating below the surface.",
                true),

        barkCoveredLogs(Type.BOOLEAN, Category.trees,
                "Set this to FALSE to prevent the trunks of RTG trees from using the 'all-bark' texture model.\n" +
                        "For more information, visit https://minecraft.wiki/w/Java_Edition_data_values/Pre-flattening#Wood",
                true),

        riverDepth(Type.DOUBLE, Category.surface,
                "Average depth of rivers. ",
                57.0, 53.0, 60.0),

        waterFeatureWidthMultiplier(Type.DOUBLE, Category.surface,
                "Multiplier to average width of rivers and lakes. ",
                1.0, 0.1, 10.0),

        // 格雷科技整合包：GT 用自己按 3×3 区块网格生成的矿脉取代原版矿物，原版矿脉装饰器的
        // 探针循环可以整段跳过（见 RTGConfig.skipVanillaOreGen 与 BiomeDecoratorMixin）。
        skipVanillaOresWhenGregTech(Type.BOOLEAN, Category.worldgen,
                "Skip vanilla ore generation (BiomeDecorator#genStandardOre1/2) when GregTech is installed.\n" +
                        "GregTech replaces vanilla ores with its own vein system and already denies COAL/IRON/GOLD/\n" +
                        "REDSTONE/DIAMOND/LAPIS/QUARTZ/EMERALD through OreGenEvent.GenerateMinable. This switch also\n" +
                        "skips the DIRT/GRAVEL/DIORITE/GRANITE/ANDESITE veins that GregTech leaves enabled, saving\n" +
                        "roughly 0.5ms per chunk during world generation.\n" +
                        "!Only takes effect while GregTech's own 'disableVanillaOres' is enabled!\n" +
                        "!Set to FALSE to keep vanilla ore veins in GregTech packs!",
                true);

        private final Type type;
        private final boolean isArray;
        private final Category category;
        private final String comment;
        private final boolean reqRestart;

        private final Object defVal;
        private final Object minVal;
        private final Object maxVal;
        private Object curVal;

        Setting(Type type, Category category, String comment, String... defVal) {
            this(type, category, comment, defVal, null, null, false, true);
        }

        Setting(Type type, Category category, String comment, Object defVal) {
            this(type, category, comment, defVal, false);
        }

        Setting(Type type, Category category, String comment, Object defVal, boolean reqRestart) {
            this(type, category, comment, defVal, null, null, reqRestart, false);
        }

        Setting(Type type, Category category, String comment, Object defVal, @Nullable Object minVal, @Nullable Object maxVal) {
            this(type, category, comment, defVal, minVal, maxVal, false, false);
        }

        Setting(Type type, Category category, String comment, Object defVal, @Nullable Object minVal, @Nullable Object maxVal, boolean reqRestart, boolean isArray) {
            this.type = type;
            this.isArray = isArray;
            this.category = category;
            this.comment = comment;
            this.reqRestart = reqRestart;

            this.defVal = defVal;
            this.minVal = minVal;
            this.maxVal = maxVal;
            this.curVal = defVal;
        }

        Type getType() {
            return type;
        }

        boolean isArray() {
            return isArray;
        }

        String getCategory() {
            return category.toString();
        }

        String getComment() {
            return comment;
        }

        boolean reqRestart() {
            return reqRestart;
        }

        Object getDefVal() {
            return defVal;
        }

        @Nullable
        Object getMinVal() {
            return minVal;
        }

        @Nullable
        Object getMaxVal() {
            return maxVal;
        }

        Object getCurVal() {
            return curVal;
        }

        void setCurVal(Object val) {
            this.curVal = val;
        }

        private String getLangKey() {
            return LANG_ROOT + getCategory() + "." + name();
        }
    }

    public static final class RTGGuiConfig extends GuiConfig {
        RTGGuiConfig(GuiScreen parent) {
            super(parent, getElements(), RTG.MODID, false, false, I18n.format(LANG_ROOT + "title"));
        }

        private static List<IConfigElement> getElements() {
            return RTGConfig.config.getCategoryNames().stream()
                    .filter(cat -> !RTGConfig.config.getCategory(cat).isChild())
                    .map(cat -> new ConfigElement(RTGConfig.config.getCategory(cat)))
                    .collect(Collectors.toList());
        }
    }

    public static int landscapeCacheSize() {
        return getInt("PERFORMANCE.landscape_cache_size", 128);
    }

    // ========== 性能分析器配置 ==========
    //
    // 直接读取系统属性；gradle.properties 的 extra_jvm_args 会原样传给 JVM。

    /** 是否启用区块生成性能分析。通过 JVM 参数 {@code -Drtg.enableProfiling=true} 启用。 */
    public static boolean enableProfiling() {
        return Boolean.getBoolean("rtg.enableProfiling");
    }

    /** 性能分析器每 N 个区块打印一次汇总报告，0 禁用自动打印。通过 {@code -Drtg.profilerLogInterval=N} 设置。 */
    public static int profilerLogInterval() {
        return Integer.getInteger("rtg.profilerLogInterval", 0);
    }

    /** 慢区块阈值（毫秒），超过此阈值的区块将单独输出耗时分解。通过 {@code -Drtg.profilerSlowThresholdMs=N} 设置。 */
    public static int profilerSlowThresholdMs() {
        return Integer.getInteger("rtg.profilerSlowThresholdMs", 20);
    }

    // ==========

    @SuppressWarnings("unused")
    public static final class RTGGuiConfigFactory implements IModGuiFactory {
        public static final String LOCATION = "rtg.RTGConfig$RTGGuiConfigFactory";

        @Override
        public void initialize(Minecraft mc) {
        }

        @Override
        public boolean hasConfigGui() {
            return true;
        }

        @Override
        public GuiScreen createConfigGui(final GuiScreen parentScreen) {
            return new RTGGuiConfig(parentScreen);
        }

        @Nullable
        @Override
        public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
            return null;
        }
    }
}
