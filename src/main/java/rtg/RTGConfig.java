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

        Arrays.stream(Setting.values()).forEach(setting -> {
            switch (setting) {
                case worldTypeNotification:
                case enableDebugging:
                case additionalBiomeInfo:
                case lushRiverbanksInDesert:
                case rtgTreesFromSaplings:
                case treesCanGenerateOnSand:
                case shrubsBelowSurface:
                case barkCoveredLogs:
                    setting.setCurVal(getProperty(setting).getBoolean());
                    break;
                case patchBiome:
                case shadowStoneBlock:
                case shadowDesertBlock:
                    setting.setCurVal(getProperty(setting).getString());
                    break;
                case surfaceBlendRadius:
                    break;
                case riverDepth:
                    setting.setCurVal(getProperty(setting).getDouble());
                    break;
                case waterFeatureWidthMultiplier:
                    setting.setCurVal(getProperty(setting).getDouble());
                    break;
                case treeDensityMultiplier:
                    setting.setCurVal(getProperty(setting).getDouble());
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

    public static int getBiomeSize() {
        return (Integer) Setting.biomeSize.getCurVal();
    }

    public static int getRiverSize() {
        return (Integer) Setting.riverSize.getCurVal();
    }

    public static int getLandScheme() {
        return (Integer) Setting.landScheme.getCurVal();
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

    public static int surfaceBlendRadius() {
        return (Integer) Setting.surfaceBlendRadius.getCurVal();
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
        trees(common);

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

        biomeSize(Type.INTEGER, Category.geography,
                "Number of times biomes are scaled and refined.\n" +
                        "!Smaller values result in smaller, more fragmented biomes with more frequent terrain transitions!",
                6, 1, 10),

        riverSize(Type.INTEGER, Category.geography,
                "Density and complexity of river distribution.\n" +
                        "!Larger values lead to denser and more intricate river systems!",
                4, 1, 10),

        landScheme(Type.INTEGER, Category.geography,
                "Global land and sea distribution.\n" +
                        "!1: Vanilla distribution (no clear preference) 2: Continents 3: Archipelagos!",
                1, 1, 3),

        islandScheme(Type.INTEGER, Category.geography,
                "Global island and ocean distribution.\n" +
                        "!1: Vanilla distribution (no clear preference) 2: Continents 3: Archipelagos!",
                1, 1, 3),

        tempScheme(Type.INTEGER, Category.geography,
                "Temperature distribution patterns.\n" +
                        "!1: Latitude-based 2: Small areas 3: Medium areas 4: Large areas 5: Random!",
                4, 1, 5),

        rainScheme(Type.INTEGER, Category.geography,
                "Precipitation distribution patterns.\n" +
                        "!1: Small areas 2: Medium areas 3: Large areas 4: Random!",
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

        surfaceBlendRadius(Type.INTEGER, Category.surface,
                "The maximum distance surfaces will blend into each other if enabled for two adjacent biomes.\n" +
                        "By default, surface blending is only enabled for beaches. You can control that in biome settings",
                32, 8, 32),

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

        treeDensityMultiplier(Type.DOUBLE, Category.trees,
                "This setting allows you to alter the amount of RTG trees that generate globally in all biomes.\n" +
                        "This setting is compounded with the density setting found in the biome configs and only affects trees generated by RTG." +
                        "Trees generated by a biome's native decorator will adhere to their own density rules.\n" +
                        "values below 1.0 will decrease the amount of trees, values above 1.0 will increase the amount of trees" +
                        "The combination of this value and the biome-specific value will never exceed 5.0",
                1.0D, 0.0D, DecoTree.MAX_TREE_DENSITY),

        treesCanGenerateOnSand(Type.BOOLEAN, Category.trees,
                "Set this to FALSE to prevent trees from generating on sand.\n" +
                        "This setting only affects trees generated by RTG. Trees generated by a biome's decorator\n" +
                        "will adhere to their own generation rules. (RTG's Palm Trees ignore this setting.)",
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
                1.0, 0.1, 10.0);

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
