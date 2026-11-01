package rtg.init;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.BiomeDictionary.Type;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import rtg.RTG;
import rtg.RTGConfig;
import rtg.api.RTGAPI;
import rtg.api.util.Logger;
import rtg.api.util.UtilityClass;
import rtg.api.world.biome.IRealisticBiome;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.compat.ModCompat.Mods;
import rtg.world.biome.RtgBaseBiome;
import rtg.world.biome.RtgOceanBiome;
import rtg.world.biome.realistic.biomesoplenty.*;
import rtg.world.biome.realistic.land.RealisticBiomeIslandVolcano;
import rtg.world.biome.realistic.rtg.*;
import rtg.world.biome.realistic.thaumcraft.RealisticBiomeTCEerie;
import rtg.world.biome.realistic.thaumcraft.RealisticBiomeTCMagicalForest;
import rtg.world.biome.realistic.vanilla.*;

import javax.annotation.Nullable;
import java.util.function.Function;


@UtilityClass
public final class BiomeInit {

    private BiomeInit() {

    }

    public static void init() {

        init_minecraft();
        if (Mods.biomesoplenty.isLoaded()) {
            init_biomesoplenty();
        }
        if (Mods.thaumcraft.isLoaded()) {
            init_thaumcraft();
        }

        // This must be done after all biomes have been initialised so that they are all available.
        RTGAPI.initPatchBiome(RTGConfig.patchBiome());
    }

    /**
     * 注册 rtgc 自己的 6 个海洋群系：3 个深海 + 3 个浅海（D5 收尾）。
     *
     * <p>它们抄自 RWG 自己的 {@code BaseBiomeOcean}（温度/降雨/动物逐条一致，见
     * {@link RtgOceanBiome}）。此前 rtgc 只有 1 个深海 + 1 个可用的浅海：
     * <ul>
     *   <li>深海：SNOW/HOT/WET 三个槽位都借用 COLD 的 {@code deep_ocean}；</li>
     *   <li>浅海：COLD/HOT/WET 三个槽位**全都是** BOP 的 {@code kelp_forest}
     *       （它靠字典的 OCEAN 标签被当成海洋，最后一个写入的赢了槽位）。</li>
     * </ul>
     *
     * <p>现在按 RWG {@code Support.java:156-175} 的槽位表补齐（对照表见
     * {@link RealisticBiomeRtgOcean}）：
     * {@code oceanShallowCold/Hot/Wet} 在 RWG 里分别是 {@code baseOceanCold/Hot/Wet}，
     * 即**RWG 自己注册的群系**，所以这里也注册 rtgc 自己的：
     * {@code shallow_cold_ocean} / {@code shallow_hot_ocean} / {@code shallow_wet_ocean}。
     *
     * <p><b>为什么名字里都带深度词与 {@code ocean}</b>：
     * {@code RtgBiomeCategorizer} 就是按这两点把它们归位的 ——
     * {@code isOcean} 认名字里的 {@code ocean}（也认字典的 OCEAN 标签，见下），
     * {@code name.contains("deep")} 决定进深/浅海槽位；而气候由温度/降雨自动落位
     * （ICE 0.0 → SNOW、COLD 0.5 → COLD、HOT 0.8/0.2 → HOT、WET 0.9/0.9 → WET）。
     * 自己的海洋（{@code RtgOceanBiome}）在槽位上有**最高优先级**（见
     * {@code RtgBiomeCategorizer.apply} 的 authShallow/authDeep）—— 否则原版的
     * {@code minecraft:ocean} 会把 {@code shallow_cold_ocean} 顶掉。
     *
     * <p><b>不 fail-soft</b>：这里不包 try/catch。注册只可能因为"注册表已经收口"而失败，
     * 那是启动期的硬错误 —— 静默跳过一半海洋群系只会让槽位莫名其妙退化，比直接崩更难查。
     * 同理也没有幂等哨兵：FML 只派发一次 preInit，真被调两次就该在启动时炸出来
     * （注册表对同名注册抛 {@code registered twice}）。
     */
    private static void init_rtgc_oceans() {

        // 深海三件。RWG 的 oceanDeepSnow/Hot/Wet 用的是它自己注册的 baseOceanCold/Hot/Wet。
        // ⚠ 故意没有 deep_cold_ocean：冷带深海必须留着 minecraft:deep_ocean ——
        //   StructureOceanMonument 的候选列表只认这一个群系，换掉海底神殿就再也不生成了。
        registerRtgOcean("deep_ice_ocean", "Deep Ice Ocean", RtgOceanBiome.Kind.ICE, false,
                Type.OCEAN, Type.COLD, Type.SNOWY);
        registerRtgOcean("deep_hot_ocean", "Deep Hot Ocean", RtgOceanBiome.Kind.HOT, false,
                Type.OCEAN, Type.HOT, Type.DRY, Type.SANDY);
        registerRtgOcean("deep_wet_ocean", "Deep Wet Ocean", RtgOceanBiome.Kind.WET, false,
                Type.OCEAN, Type.WET, Type.JUNGLE);

        // 浅海三件。⚠ 故意没有 shallow_snow_ocean：雪带浅海继续用 minecraft:frozen_ocean
        // （会结浮冰，观感与原版一致）；RWG 这一格用的是温度 0.5 的 baseOceanCold，不结冰。
        registerRtgOcean("shallow_cold_ocean", "Shallow Cold Ocean", RtgOceanBiome.Kind.COLD, true,
                Type.OCEAN, Type.COLD);
        registerRtgOcean("shallow_hot_ocean", "Shallow Hot Ocean", RtgOceanBiome.Kind.HOT, true,
                Type.OCEAN, Type.HOT, Type.DRY, Type.SANDY);
        registerRtgOcean("shallow_wet_ocean", "Shallow Wet Ocean", RtgOceanBiome.Kind.WET, true,
                Type.OCEAN, Type.WET, Type.JUNGLE);
    }

    /**
     * 注册 RWG 自有的陆地群系（{@link RtgBaseBiome}），每个群系配一个 {@code RealisticBiomeRtg*} 包装。
     * 与 {@link #init_rtgc_oceans()} 一样，必须在 preInit 调用（注册表在 preInit 之后就收口了）。
     */
    private static void init_rtgc_rtg_biomes() {

        registerRtgBiome("rtg_oasis", "Oasis", RtgBaseBiome.Kind.OASIS,
                RealisticBiomeRtgOasis::new,
                Type.HOT, Type.WET, Type.JUNGLE, Type.SANDY);
        registerRtgBiome("rtg_red_oasis", "Red Oasis", RtgBaseBiome.Kind.OASIS,
                RealisticBiomeRtgRedOasis::new,
                Type.HOT, Type.WET, Type.JUNGLE, Type.SANDY);
        registerRtgBiome("rtg_dune_valley", "Dune Valley", RtgBaseBiome.Kind.HOT_DESERT,
                RealisticBiomeRtgDuneValley::new,
                Type.HOT, Type.DRY, Type.SANDY);
        registerRtgBiome("rtg_savanna_dunes", "Savanna Dunes", RtgBaseBiome.Kind.HOT_PLAINS,
                RealisticBiomeRtgSavannaDunes::new,
                Type.HOT, Type.DRY, Type.SANDY, Type.SAVANNA);
        registerRtgBiome("rtg_canyon", "Canyon", RtgBaseBiome.Kind.HOT_PLAINS,
                RealisticBiomeRtgCanyon::new,
                Type.HOT, Type.DRY, Type.SANDY, Type.MESA);
        registerRtgBiome("rtg_canyon_forest", "Canyon Forest", RtgBaseBiome.Kind.HOT_FOREST,
                RealisticBiomeRtgCanyonForest::new,
                Type.HOT, Type.DRY, Type.MESA, Type.FOREST);
        registerRtgBiome("rtg_stone_mountains", "Stone Mountains", RtgBaseBiome.Kind.HOT_PLAINS,
                RealisticBiomeRtgStoneMountains::new,
                Type.HOT, Type.DRY, Type.MOUNTAIN);
        registerRtgBiome("rtg_stone_mountains_cactus", "Stone Mountains Cactus", RtgBaseBiome.Kind.HOT_PLAINS,
                RealisticBiomeRtgStoneMountainsCactus::new,
                Type.HOT, Type.DRY, Type.MOUNTAIN);
        registerRtgBiome("rtg_hot_forest", "Hot Forest", RtgBaseBiome.Kind.HOT_FOREST,
                RealisticBiomeRtgHotForest::new,
                Type.HOT, Type.DRY, Type.FOREST);
        registerRtgBiome("rtg_polar", "Polar", RtgBaseBiome.Kind.SNOW_DESERT,
                RealisticBiomeRtgPolar::new,
                Type.SNOWY, Type.COLD, Type.WASTELAND);
        registerRtgBiome("rtg_snow_lakes", "Snowy Lakes", RtgBaseBiome.Kind.SNOW_FOREST,
                RealisticBiomeRtgSnowLakes::new,
                Type.SNOWY, Type.COLD, Type.CONIFEROUS, Type.FOREST);
        registerRtgBiome("rtg_tundra_hills", "Tundra Hills", RtgBaseBiome.Kind.COLD_PLAINS,
                RealisticBiomeRtgTundraHills::new,
                Type.COLD, Type.MOUNTAIN);
        registerRtgBiome("rtg_taiga_plains", "Taiga Plains", RtgBaseBiome.Kind.COLD_FOREST,
                RealisticBiomeRtgTaigaPlains::new,
                Type.COLD, Type.CONIFEROUS, Type.FOREST);
        registerRtgBiome("rtg_high_rainforest", "High Rainforest", RtgBaseBiome.Kind.JUNGLE,
                RealisticBiomeRtgHighRainforest::new,
                Type.HOT, Type.WET, Type.JUNGLE, Type.FOREST);
        registerRtgBiome("rtg_jungle_canyon", "Jungle Canyon", RtgBaseBiome.Kind.JUNGLE,
                RealisticBiomeRtgJungleCanyon::new,
                Type.HOT, Type.WET, Type.JUNGLE, Type.MESA);
    }

    /**
     * 把新建的群系登记进 Forge 的群系表、补上字典标签，再挂上它的 RTG 包装。
     *
     * <p>三步的顺序不能换，包装也不能提前造：
     * <ul>
     *   <li>{@code register} 要求对象先有 registry name —— Forge 不收 null 名；</li>
     *   <li>{@code BiomeDictionary.addTypes} 会校验群系已经在 {@code ForgeRegistries.BIOMES} 里；</li>
     *   <li>{@code RealisticBiomeBase} 的构造器会读 {@code getRegistryName()}（null 直接抛），
     *       并把 {@code Biome.getIdForBiome(biome)} 存下来 —— 所以要等注册完再 apply。</li>
     * </ul>
     */
    private static void registerNewBiome(final String regName, final Biome biome, final Type[] types,
                                         final Function<Biome, IRealisticBiome> realistic) {

        biome.setRegistryName(RTG.MODID, regName);
        ForgeRegistries.BIOMES.register(biome);
        BiomeDictionary.addTypes(biome, types);
        RTGAPI.addRTGBiomes(realistic.apply(biome));
    }

    /**
     * 注册一个 rtgc 自己的海洋群系（{@link RtgOceanBiome}），包装成 {@link RealisticBiomeRtgOcean}。
     *
     * @param shallow RWG {@code RealisticBiomeOcean} 的 shallow 参数：true = 海底 y≈52，false = y≈34
     */
    private static void registerRtgOcean(final String regName, final String displayName,
                                         final RtgOceanBiome.Kind kind, final boolean shallow,
                                         final Type... types) {

        final RtgOceanBiome biome = new RtgOceanBiome(kind, displayName);
        registerNewBiome(regName, biome, types, b -> new RealisticBiomeRtgOcean(b, shallow));

        Logger.info("[RTG] 注册{}海洋群系 rtgc:{}（id={}）",
                shallow ? "浅海" : "深海", regName, Biome.getIdForBiome(biome));
    }

    /**
     * 注册一个 RWG 自有的陆地群系（{@link RtgBaseBiome}），包装类由 {@code factory} 决定。
     */
    private static void registerRtgBiome(final String regName, final String displayName,
                                         final RtgBaseBiome.Kind kind,
                                         final Function<Biome, IRealisticBiome> factory,
                                         final Type... types) {

        final RtgBaseBiome biome = new RtgBaseBiome(kind, displayName);
        registerNewBiome(regName, biome, types, factory);

        Logger.info("[RTG] 注册 RWG 自有群系 rtgc:{}（id={}，{}）",
                regName, Biome.getIdForBiome(biome), displayName);
    }

    public static void preInit() {
        RTGAPI.addRTGBiomes(
                RealisticBiomeBase.RiverType.NORMAL.setRTGBiome(new RealisticBiomeVanillaRiver()),
                RealisticBiomeBase.RiverType.FROZEN.setRTGBiome(new RealisticBiomeVanillaFrozenRiver()),
                RealisticBiomeBase.BeachType.NORMAL.setRTGBiome(new RealisticBiomeVanillaBeach()),
                RealisticBiomeBase.BeachType.STONE.setRTGBiome(new RealisticBiomeVanillaStoneBeach()),
                RealisticBiomeBase.BeachType.COLD.setRTGBiome(new RealisticBiomeVanillaColdBeach())
        );

        // ⚠ 必须在 preInit：Forge 派发完 FMLPreInitializationEvent 之后立刻 fireRegistryEvents，
        // 收口 Biome 注册表；之后再调 BIOMES.register 会抛 "is being added too late"。
        // postInit 的 BiomeInit.init() 只往 RTGAPI 里加包装，不碰注册表，所以放那儿没事。
        init_rtgc_oceans();
        init_rtgc_rtg_biomes();
    }

    private static void init_minecraft() {
        // vanilla rivers and beaches are initialised to enum fields during #preInit
        RTGAPI.addRTGBiomes(
                new RealisticBiomeVanillaBirchForest(),
                new RealisticBiomeVanillaBirchForestHills(),
                new RealisticBiomeVanillaBirchForestHillsM(),
                new RealisticBiomeVanillaBirchForestM(),
                new RealisticBiomeVanillaColdTaiga(),
                new RealisticBiomeVanillaColdTaigaHills(),
                new RealisticBiomeVanillaColdTaigaM(),
                new RealisticBiomeVanillaDeepOcean(),
                new RealisticBiomeVanillaDesert(),
                new RealisticBiomeVanillaDesertHills(),
                new RealisticBiomeVanillaDesertM(),
                new RealisticBiomeVanillaExtremeHills(),
                new RealisticBiomeVanillaExtremeHillsEdge(),
                new RealisticBiomeVanillaExtremeHillsM(),
                new RealisticBiomeVanillaExtremeHillsPlus(),
                new RealisticBiomeVanillaExtremeHillsPlusM(),
                new RealisticBiomeVanillaFlowerForest(),
                new RealisticBiomeVanillaForest(),
                new RealisticBiomeVanillaForestHills(),
                new RealisticBiomeVanillaFrozenOcean(),
                new RealisticBiomeVanillaIceMountains(),
                new RealisticBiomeVanillaIcePlains(),
                new RealisticBiomeVanillaIcePlainsSpikes(),
                new RealisticBiomeVanillaJungle(),
                new RealisticBiomeVanillaJungleEdge(),
                new RealisticBiomeVanillaJungleEdgeM(),
                new RealisticBiomeVanillaJungleHills(),
                new RealisticBiomeVanillaJungleM(),
                new RealisticBiomeVanillaMegaSpruceTaiga(),
                new RealisticBiomeVanillaMegaTaiga(),
                new RealisticBiomeVanillaMegaTaigaHills(),
                new RealisticBiomeVanillaMesa(),
                new RealisticBiomeVanillaMesaBryce(),
                new RealisticBiomeVanillaMesaPlateau(),
                new RealisticBiomeVanillaMesaPlateauF(),
                new RealisticBiomeVanillaMesaPlateauFM(),
                new RealisticBiomeVanillaMesaPlateauM(),
                new RealisticBiomeVanillaMushroomIsland(),
                new RealisticBiomeVanillaMushroomIslandShore(),
                new RealisticBiomeVanillaOcean(),
                new RealisticBiomeVanillaPlains(),
                new RealisticBiomeVanillaRedwoodTaigaHills(),
                new RealisticBiomeVanillaRoofedForest(),
                new RealisticBiomeVanillaRoofedForestM(),
                new RealisticBiomeVanillaSavanna(),
                new RealisticBiomeVanillaSavannaM(),
                new RealisticBiomeVanillaSavannaPlateau(),
                new RealisticBiomeVanillaSavannaPlateauM(),
                new RealisticBiomeVanillaSunflowerPlains(),
                new RealisticBiomeVanillaSwampland(),
                new RealisticBiomeVanillaSwamplandM(),
                new RealisticBiomeVanillaTaiga(),
                new RealisticBiomeVanillaTaigaHills(),
                new RealisticBiomeVanillaTaigaM()
        );
    }

    private static void init_biomesoplenty() {

        Biome biome;
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("alps"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPAlps(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("alps_foothills"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPAlpsFoothills(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("bamboo_forest"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPBambooForest(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("bayou"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPBayou(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("bog"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPBog(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("boreal_forest"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPBorealForest(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("brushland"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPBrushland(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("chaparral"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPChaparral(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("cherry_blossom_grove"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPCherryBlossomGrove(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("cold_desert"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPColdDesert(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("coniferous_forest"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPConiferousForest(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("coral_reef"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPCoralReef(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("crag"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPCrag(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("dead_forest"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPDeadForest(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("dead_swamp"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPDeadSwamp(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("eucalyptus_forest"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPEucalyptusForest(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("fen"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPFen(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("flower_field"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPFlowerField(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("flower_island"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPFlowerIsland(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("glacier"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPGlacier(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("grassland"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPGrassland(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("gravel_beach"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPGravelBeach(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("grove"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPGrove(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("highland"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPHighland(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("kelp_forest"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPKelpForest(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("land_of_lakes"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPLandOfLakes(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("lavender_fields"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPLavenderFields(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("lush_desert"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPLushDesert(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("lush_swamp"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPLushSwamp(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("mangrove"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPMangrove(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("maple_woods"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPMapleWoods(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("marsh"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPMarsh(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("meadow"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPMeadow(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("moor"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPMoor(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("mountain"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPMountainPeaks(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("mountain_foothills"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPMountainFoothills(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("mystic_grove"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPMysticGrove(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("oasis"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPOasis(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("ominous_woods"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPOminousWoods(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("orchard"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPOrchard(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("origin_beach"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPOriginBeach(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("origin_island"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPOriginIsland(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("outback"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPOutback(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("overgrown_cliffs"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPOvergrownCliffs(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("pasture"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPPasture(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("prairie"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPPrairie(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("quagmire"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPQuagmire(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("rainforest"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPRainforest(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("redwood_forest"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPRedwoodForest(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("redwood_forest_edge"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPRedwoodForestEdge(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("sacred_springs"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPSacredSprings(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("seasonal_forest"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPSeasonalForest(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("shield"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPShield(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("shrubland"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPShrubland(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("snowy_coniferous_forest"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPSnowyConiferousForest(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("snowy_forest"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPSnowyForest(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("snowy_tundra"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPSnowyTundra(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("steppe"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPSteppe(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("temperate_rainforest"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPTemperateRainforest(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("tropical_island"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPTropicalIsland(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("tropical_rainforest"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPTropicalRainforest(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("tundra"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPTundra(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("volcanic_island"))) != null) {
            // RWG 用 **RealisticBiomeIslandVolcano** 包装 BOP 的火山岛（`SupportBOP:49-53`），
            // 并把它记成 `Support.volcanoIsland`：生成器靠它决定"哪一列要长火山锥、灌岩浆"。
            // rtgc 的对应物就是同名静态字段；原来那个 RTG 时代的
            // `RealisticBiomeBOPVolcanicIsland` 已删除 —— RWG 对同一个 MC 群系**只有一个**包装，
            // 而 `RTGAPI` 是按群系索引的 Map（两个包装会让后者把前者顶掉，见 docs §0.5.4 检查 4）。
            final RealisticBiomeIslandVolcano volcanoIsland = new RealisticBiomeIslandVolcano(biome);
            RTGAPI.addRTGBiomes(volcanoIsland);
            RealisticBiomeIslandVolcano.volcanoIsland = volcanoIsland;
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("wasteland"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPWasteland(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("wetland"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPWetland(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("white_beach"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPWhiteBeach(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("woodland"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPWoodland(biome));
        }
        if ((biome = getBiome(Mods.biomesoplenty.getResourceLocation("xeric_shrubland"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeBOPXericShrubland(biome));
        }
    }

    @SuppressWarnings("unused")
    private static void init_thaumcraft() {

        Biome biome;
        if ((biome = getBiome(Mods.thaumcraft.getResourceLocation("magical_forest"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeTCMagicalForest(biome));
        }
        if ((biome = getBiome(Mods.thaumcraft.getResourceLocation("eerie"))) != null) {
            RTGAPI.addRTGBiomes(new RealisticBiomeTCEerie(biome));
        }
    }

    @Nullable
    private static Biome getBiome(final ResourceLocation resloc) {
        return ForgeRegistries.BIOMES.getValue(resloc);
    }
}
