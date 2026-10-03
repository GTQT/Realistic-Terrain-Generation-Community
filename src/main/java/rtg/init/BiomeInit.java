package rtg.init;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import rtg.RTGConfig;
import rtg.api.RTGAPI;
import rtg.api.util.UtilityClass;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.compat.ModCompat.Mods;
import rtg.world.biome.realistic.biomesoplenty.*;
import rtg.world.biome.realistic.land.RealisticBiomeIslandVolcano;
import rtg.world.biome.realistic.thaumcraft.RealisticBiomeTCEerie;
import rtg.world.biome.realistic.thaumcraft.RealisticBiomeTCMagicalForest;
import rtg.world.biome.realistic.vanilla.*;

import javax.annotation.Nullable;


@UtilityClass
public final class BiomeInit {

    /** {@link #init_rtgc_oceans()} 是否已经跑过（注册表不允许同名注册两次）。 */
    private static boolean rtgcOceansInit = false;

    private BiomeInit() {

    }
    //峭壁

    public static void init() {

        init_minecraft();
        if (Mods.biomesoplenty.isLoaded()) { init_biomesoplenty(); }
        if (Mods.thaumcraft.isLoaded()) { init_thaumcraft(); }

        // This must be done after all biomes have been initialised so that they are all available.
        RTGAPI.initPatchBiome(RTGConfig.patchBiome());
    }

    /**
     * 注册 rtgc 自己的 6 个海洋群系：3 个深海 + 3 个浅海（D5 收尾）。
     *
     * <p>它们抄自 RWG 自己的 {@code BaseBiomeOcean}（温度/降雨/动物逐条一致，见
     * {@link rtg.world.biome.RtgOceanBiome}）。此前 rtgc 只有 1 个深海 + 1 个可用的浅海：
     * <ul>
     *   <li>深海：SNOW/HOT/WET 三个槽位都借用 COLD 的 {@code deep_ocean}；</li>
     *   <li>浅海：COLD/HOT/WET 三个槽位**全都是** BOP 的 {@code kelp_forest}
     *       （它靠字典的 OCEAN 标签被当成海洋，最后一个写入的赢了槽位）。</li>
     * </ul>
     *
     * <p>现在按 RWG {@code Support.java:156-175} 的槽位表补齐（对照表见
     * {@link rtg.world.biome.realistic.rtg.RealisticBiomeRtgOcean}）：
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
     * <p><b>fail-soft</b>：注册失败（例如注册表已被冻结）只记 ERROR 并**整体跳过** ——
     * 布局会像以前一样继续借用，不会因为少了几个海洋群系就崩掉世界生成。
     */
    private static void init_rtgc_oceans() {

        // 幂等：注册表不允许同一个名字注册两次（重复调用只跳过，不再走一遍注册/包装）
        if (rtgcOceansInit) {
            return;
        }
        rtgcOceansInit = true;

        final Object[][] defs = {
                // {注册名, 显示名, Kind, 字典类型, 是否浅海（RWG RealisticBiomeOcean 的 shallow 参数）}
                //
                // ⚠ 这里**故意没有** `deep_cold_ocean`：冷带的深海继续用原版的
                // `minecraft:deep_ocean`。两个理由，都是查证过的：
                //   ① RWG 自己的槽位表就是这么分的 —— `oceanDeepCold =
                //      new RealisticBiomeOcean(BiomeGenBase.deepOcean, …)`（Support.java:172），
                //      `baseOceanCold` 只用在雪带；
                //   ② 原版 **海底神殿** 的候选列表是
                //      `StructureOceanMonument.SPAWN_BIOMES = Arrays.asList(Biomes.DEEP_OCEAN)`
                //      （1.12.2 源码实测，`areBiomesViable(..., 16, SPAWN_BIOMES)`）——
                //      它**只认 deep_ocean 本身**。若把四种深海全换成新群系，
                //      `deep_ocean` 就一列都不生成 ⇒ 海底神殿会彻底消失。
                { "deep_ice_ocean", "Deep Ice Ocean", rtg.world.biome.RtgOceanBiome.Kind.ICE,
                        new net.minecraftforge.common.BiomeDictionary.Type[] {
                                net.minecraftforge.common.BiomeDictionary.Type.OCEAN,
                                net.minecraftforge.common.BiomeDictionary.Type.COLD,
                                net.minecraftforge.common.BiomeDictionary.Type.SNOWY },
                        Boolean.FALSE },
                { "deep_hot_ocean", "Deep Hot Ocean", rtg.world.biome.RtgOceanBiome.Kind.HOT,
                        new net.minecraftforge.common.BiomeDictionary.Type[] {
                                net.minecraftforge.common.BiomeDictionary.Type.OCEAN,
                                net.minecraftforge.common.BiomeDictionary.Type.HOT,
                                net.minecraftforge.common.BiomeDictionary.Type.DRY,
                                net.minecraftforge.common.BiomeDictionary.Type.SANDY },
                        Boolean.FALSE },
                { "deep_wet_ocean", "Deep Wet Ocean", rtg.world.biome.RtgOceanBiome.Kind.WET,
                        new net.minecraftforge.common.BiomeDictionary.Type[] {
                                net.minecraftforge.common.BiomeDictionary.Type.OCEAN,
                                net.minecraftforge.common.BiomeDictionary.Type.WET,
                                net.minecraftforge.common.BiomeDictionary.Type.JUNGLE },
                        Boolean.FALSE },
                // ---- 浅海三件（RWG `oceanShallowCold/Hot/Wet` = `baseOceanCold/Hot/Wet`）----
                //
                // ⚠ 这里**故意没有** `shallow_snow_ocean` / `shallow_ice_ocean`：雪带的浅海继续用
                // 原版的 `minecraft:frozen_ocean`（会产生浮冰，观感与雪带一致）。
                // RWG 那边雪带浅海用的是它的 `baseOceanCold`（温度 0.5、**不结冰**）——
                // rtgc 的槽位按群系温度自动归类，温度 0.5 的群系占不住 SNOW 槽，
                // 要照抄就得用 ICE（温度 0.0），那会与现有 `deep_ice_ocean`（雪带深海）重复，
                // 且让雪带海面结冰这件事与原版一致反而更好。故保留 frozen_ocean（口径差异，已记录）。
                { "shallow_cold_ocean", "Shallow Cold Ocean", rtg.world.biome.RtgOceanBiome.Kind.COLD,
                        new net.minecraftforge.common.BiomeDictionary.Type[] {
                                net.minecraftforge.common.BiomeDictionary.Type.OCEAN,
                                net.minecraftforge.common.BiomeDictionary.Type.COLD },
                        Boolean.TRUE },
                { "shallow_hot_ocean", "Shallow Hot Ocean", rtg.world.biome.RtgOceanBiome.Kind.HOT,
                        new net.minecraftforge.common.BiomeDictionary.Type[] {
                                net.minecraftforge.common.BiomeDictionary.Type.OCEAN,
                                net.minecraftforge.common.BiomeDictionary.Type.HOT,
                                net.minecraftforge.common.BiomeDictionary.Type.DRY,
                                net.minecraftforge.common.BiomeDictionary.Type.SANDY },
                        Boolean.TRUE },
                { "shallow_wet_ocean", "Shallow Wet Ocean", rtg.world.biome.RtgOceanBiome.Kind.WET,
                        new net.minecraftforge.common.BiomeDictionary.Type[] {
                                net.minecraftforge.common.BiomeDictionary.Type.OCEAN,
                                net.minecraftforge.common.BiomeDictionary.Type.WET,
                                net.minecraftforge.common.BiomeDictionary.Type.JUNGLE },
                        Boolean.TRUE },
        };

        try {
            for (final Object[] def : defs) {
                final String regName = (String) def[0];
                final boolean isShallow = (Boolean) def[4];
                final rtg.world.biome.RtgOceanBiome biome = new rtg.world.biome.RtgOceanBiome(
                        (rtg.world.biome.RtgOceanBiome.Kind) def[2], (String) def[1]);

                biome.setRegistryName(rtg.RTG.MODID, regName);
                net.minecraftforge.fml.common.registry.ForgeRegistries.BIOMES.register(biome);
                net.minecraftforge.common.BiomeDictionary.addTypes(biome,
                        (net.minecraftforge.common.BiomeDictionary.Type[]) def[3]);

                RTGAPI.addRTGBiomes(new rtg.world.biome.realistic.rtg.RealisticBiomeRtgOcean(biome, isShallow));
                rtg.api.util.Logger.info("[RTG] 注册{}海洋群系 rtgc:{}（id={}）",
                        isShallow ? "浅海" : "深海", regName, Biome.getIdForBiome(biome));
            }
        } catch (final Throwable t) {
            rtg.api.util.Logger.error("[RTG] ⚠ 注册 rtgc 自己的海洋群系失败，已整体跳过 —— "
                    + "槽位会像以前一样借用同一个群系（不影响世界生成）：{}", t);
        }
    }

    public static void preInit() {
        RTGAPI.addRTGBiomes(
                RealisticBiomeBase.RiverType.NORMAL.setRTGBiome(new RealisticBiomeVanillaRiver()),
                RealisticBiomeBase.RiverType.FROZEN.setRTGBiome(new RealisticBiomeVanillaFrozenRiver()),
                RealisticBiomeBase.BeachType.NORMAL.setRTGBiome(new RealisticBiomeVanillaBeach()),
                RealisticBiomeBase.BeachType.STONE.setRTGBiome(new RealisticBiomeVanillaStoneBeach()),
                RealisticBiomeBase.BeachType.COLD.setRTGBiome(new RealisticBiomeVanillaColdBeach())
        );

        // ⚠ 必须在 **preInit** 注册：MC/Forge 的群系注册表在注册事件阶段结束时就**冻结**了，
        // 放到 init()（FMLInitialization）里会抛 "registry frozen" —— 那正是本方法加 try/catch
        // 的原因（真被拒也只记 ERROR 并跳过，不影响世界生成）。
        init_rtgc_oceans();
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
    private static Biome getBiome(final ResourceLocation resloc)
    {
        return ForgeRegistries.BIOMES.getValue(resloc);
    }
}
