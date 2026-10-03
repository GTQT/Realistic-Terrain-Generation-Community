package rtg.api.world.biome;

import net.minecraft.init.Biomes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraftforge.common.BiomeDictionary;
import rtg.RTG;
import rtg.RTGConfig;
import rtg.api.RTGAPI;
import rtg.api.config.BiomeConfig;
import rtg.api.world.RTGWorld;
import rtg.api.world.deco.AbstractDeco;
import rtg.api.world.deco.DecoBase;
import rtg.api.world.deco.collection.DecoCollectionBase;
import rtg.api.world.gen.feature.tree.rtg.TreeRTG;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceRiverOasis;
import rtg.api.world.terrain.TerrainBase;
import rtg.compat.ModCompat.Mods;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.Random;


public abstract class RealisticBiomeBase implements IRealisticBiome {

    private static final double INV_240 = 1.0 / 240.0;
    private static final double INV_80 = 1.0 / 80.0;
    private static final double INV_30 = 1.0 / 30.0;
    private final Biome baseBiome;
    private final ResourceLocation baseBiomeResLoc;
    private final int baseBiomeId;
    private final RiverType riverType;
    private final BeachType beachType;
    private final BiomeConfig config;
    private final TerrainBase terrain;
    private final SurfaceBase surface;
    private final SurfaceBase surfaceRiver;
    private final Collection<DecoBase> decos;
    // TODO: [1.12] To be removed. All trees need to be a Deco and be added through #addDeco.
    @Deprecated
    private final Collection<TreeRTG> rtgTrees;

    /**
     * 树装饰集合（移植上游新树系统 / T5）。
     *
     * <p>上游把"这个群系的树"单独放在这个字段里，由各群系在 {@code initDecos()} 里指派
     * （{@code treeGenerator = new DecoCollectionXxxTrees(getConfig())} 这类），
     * 再由 {@code IRealisticBiome.rDecorate} 在 {@code allowVanillaTrees()} 为假时调用它。
     *
     * <p>默认是**空集合**（不是 null）：这样接线之前行为完全不变。
     */
    protected AbstractDeco treeGenerator;

    public RealisticBiomeBase(@Nonnull final Biome baseBiome) {
        this(baseBiome, RiverType.NORMAL, BeachType.NORMAL);
    }

    public RealisticBiomeBase(@Nonnull final Biome baseBiome, @Nonnull final RiverType riverType) {
        this(baseBiome, riverType, BeachType.NORMAL);
    }

    public RealisticBiomeBase(@Nonnull final Biome baseBiome, @Nonnull final BeachType beachType) {
        this(baseBiome, RiverType.NORMAL, beachType);
    }

    public RealisticBiomeBase(@Nonnull final Biome baseBiome, @Nonnull final RiverType riverType, @Nonnull final BeachType beachType) {

        ResourceLocation resloc = baseBiome.getRegistryName();
        if (resloc == null) {
            throw new IllegalStateException(String.format("Biome with ID: %s, of class: %s, does not have a registry name set.",
                    Biome.getIdForBiome(baseBiome), baseBiome.getClass().getName()));
        }

        this.baseBiome = baseBiome;
        this.baseBiomeResLoc = resloc;
        this.baseBiomeId = Biome.getIdForBiome(baseBiome);
        this.riverType = riverType;
        this.beachType = beachType;

        this.config = new BiomeConfig(getConfigFile());
        initConfig();
        this.config.loadConfig();// Must be done before anything using configs.

        this.terrain = initTerrain();
        this.surface = initSurface();
        this.surfaceRiver = new SurfaceRiverOasis(config);
        this.decos = new ArrayList<>();
        this.rtgTrees = new ArrayList<>();
        // 上游同款默认值：空集合（DecoCollectionBase 本身不产生任何装饰）
        this.treeGenerator = new DecoCollectionBase(this.config);

        initDecos();

        overrideDecorations();
    }

    @Override
    public BiomeConfig getConfig() {
        return this.config;
    }

    @Override
    public final Biome baseBiome() {
        return baseBiome;
    }

    @Override
    public RiverType getRiverType() {
        return riverType;
    }

    @Override
    public BeachType getBeachType() {
        return beachType;
    }

    @Override
    public Biome preferredBeach() {
        return this.beachType.getBiome();
    }

    @Override
    public IRealisticBiome getRiverBiome() {
        return this.riverType.getRTGBiome();
    }

    @Override
    public IRealisticBiome getBeachBiome() {
        IRealisticBiome rbb = RTGAPI.getRTGBiome(Biome.getIdForBiome(this.preferredBeach()));
        int configBiomeId = this.getConfig().BEACH_BIOME.get();
        if (configBiomeId > -1) {
            rbb = RTGAPI.getRTGBiome(configBiomeId);
        }
        return rbb;
    }

    @Override
    public boolean allowVanillaTrees() {
        return true;
    }

    @Override
    public void overrideDecorations() {
        //baseBiome().decorator.grassPerChunk = -999;
    }

    @Override
    public Collection<DecoBase> getDecos() {
        return this.decos;
    }

    @Override
    public Collection<TreeRTG> getTrees() {
        return this.rtgTrees;
    }

    /**
     * 树装饰集合（移植上游新树系统 / T5）。
     *
     * <p>对齐上游 {@code RealisticBiomeBase:169-171}：返回 {@code treeGenerator}。
     * 注意上游 {@code IRealisticBiome} 里**没有**给这个方法的 default 实现，
     * 每个实现类都要提供；本仓库只有这一个实现类，故在此覆写。
     */
    @Override
    public AbstractDeco getTreeDecos() {
        return this.treeGenerator;
    }

    @Override
    public ResourceLocation baseBiomeResLoc() {
        return baseBiomeResLoc;
    }

    @Override
    public int baseBiomeId() {
        return this.baseBiomeId;
    }

    /**
     * RWG {@code rwg/biomes/realistic/RealisticBiomeBase.generateMapGen}（L186-198）的**逐行照抄**。
     *
     * <pre>
     * RWG：                                              rtgc：
     *   int k = 5;                                        同名同值
     *   mapRand.setSeed(seed);                            seed → worldSeed
     *   long l  = (mapRand.nextLong() / 2L) * 2L + 1L;    逐字
     *   long l1 = (mapRand.nextLong() / 2L) * 2L + 1L;    逐字
     *   for (baseX = chunkX - k; baseX &lt;= chunkX + k; …   逐字
     *     for (baseY = chunkY - k; …)                     逐字（baseY → baseZ，仅是命名）
     *       mapRand.setSeed((long) baseX * l + (long) baseY * l1 ^ seed);
     *       rMapGen(…, baseX, baseY, chunkX, chunkY, …);  逐字（形参名见接口注释的「顺序的坑」）
     * </pre>
     *
     * <p><b>本方法的语义</b>：枚举以当前区块为中心、半径 {@code k = 5} 的 11×11 = 121 个
     * **候选地标中心**（{@code baseX/baseZ}），每个候选点用「世界种子 ⊕ (baseX·l + baseZ·l1)」
     * 重新播种 {@code mapRand}，然后交给 {@link #rMapGen} 决定是否在**当前区块**
     * （{@code chunkX/chunkZ}）落方块。因此地标会「从远处糊过来」：某个候选中心离本区块再远，
     * 只要它自己的种子通过了门控，就会把属于它的那部分锥体画进本区块。
     *
     * <p>{@code l}/{@code l1} 是 RWG 的两条奇偶性为奇的「跳跃步长」——{@code mapRand} 先被
     * {@code setSeed(worldSeed)} 归一，故这两个值只依赖世界种子，**与区块无关**；
     * 加上每个候选点的 {@code setSeed}，整段逻辑对每个群系都是自足的纯函数
     * （不依赖其它群系是否也跑了 {@code generateMapGen}）。
     *
     * <p>rtgc 侧的差异只有一处，且不在本方法体内：调用点（{@code ChunkGeneratorRTG.provideChunk}）
     * 由父级接线，见 {@link IRealisticBiome#generateMapGen} 的说明。
     * <p>{@code mapRand} 必须是调用方复用的实例（RWG 是 {@code ChunkGeneratorRealistic.mapRand}）：
     * 本方法一开始就 {@code setSeed}，所以复用不会串状态。
     */
    @Override
    public void generateMapGen(RTGWorld rtgWorld, ChunkPrimer primer, Random mapRand, long worldSeed,
            int chunkX, int chunkZ, float[] noise) {
        int k = 5;
        mapRand.setSeed(worldSeed);
        long l = (mapRand.nextLong() / 2L) * 2L + 1L;
        long l1 = (mapRand.nextLong() / 2L) * 2L + 1L;
        for (int baseX = chunkX - k; baseX <= chunkX + k; baseX++) {
            for (int baseZ = chunkZ - k; baseZ <= chunkZ + k; baseZ++) {
                mapRand.setSeed((long) baseX * l + (long) baseZ * l1 ^ worldSeed);
                rMapGen(rtgWorld, primer, mapRand, baseX, baseZ, chunkX, chunkZ, noise);
            }
        }
    }

    /**
     * RWG 的 {@code RealisticBiomeBase.rNoise} 是**原样透传**：
     * {@code return terrain.generateNoise(perlin, cell, x, y, ocean, border, river);}
     * —— 对 {@code border} / {@code river} 不做任何再加工。
     *
     * <p><b>rtgc 原先在此挂了一整套 RTG 时代的河道／湖泊变换（{@code newrNoise}）：
     * {@code lakePressure} → {@code lakeToRiverProportions} →
     * {@code riverAdjustedforDepthDifference} → {@code riverFlattening}。
     * 默认配置下该变换实测把 RWG 的 {@code river} <b>反相</b>：</b>
     *
     * <pre>
     *   RWG 输入（0 = 河心，1 = 内陆）   地形函数实收
     *            0.00                      1.00
     *            0.50                      0.52
     *            1.00                      0.04
     * </pre>
     *
     * 而所有已移植的地形函数都按 RWG 约定写作 {@code m = noise * strength * river}
     * （陆地 = 1）：于是陆地上山体项被削到 4%、世界被压成平板，河心反而拿到满幅山体
     * （沿每条河长出山墙）。<b>这正是"公式照抄了却不像 RWG"的根因</b>，故整套变换已删除。
     *
     * <p>本方法现在与 RWG 逐字同构。唯一保留的 rtgc 配置项是关掉本群系的河流
     * （{@code ALLOW_RIVERS}）；RWG 无此开关，而它的默认值为 {@code true}，
     * 因此默认行为与 RWG 完全一致。
     */
    @Override
    public float rNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

        final float effectiveRiver = this.getConfig().ALLOW_RIVERS.get() ? river : 1f;

        return terrain.generateNoise(rtgWorld, x, y, border, effectiveRiver);
    }

    @Override
    public void rReplace(ChunkPrimer primer, BlockPos blockPos, int x, int y, int depth, RTGWorld rtgWorld, float[] noise, float river, Biome[] base) {
        rReplace(primer, blockPos.getX(), blockPos.getZ(), x, y, depth, rtgWorld, noise, river, base);
    }

    @Override
    public void rReplace(ChunkPrimer primer, int i, int j, int x, int y, int depth, RTGWorld rtgWorld, float[] noise, float river, Biome[] base) {
        if (RTG.surfacesDisabled() || this.getConfig().DISABLE_RTG_SURFACES.get()) {
            return;
        }
        float riverRegion = !this.getConfig().ALLOW_RIVERS.get() ? 0f : river;
        this.surface.paintTerrain(primer, i, j, x, y, depth, rtgWorld, noise, riverRegion, base);
    }

    protected void rReplaceWithRiver(ChunkPrimer primer, int i, int j, int x, int y, int depth, RTGWorld rtgWorld, float[] noise, float river, Biome[] base) {
        if (RTG.surfacesDisabled() || this.getConfig().DISABLE_RTG_SURFACES.get()) {
            return;
        }
        float riverRegion = !this.getConfig().ALLOW_RIVERS.get() ? 0f : river;
        this.surface.paintTerrain(primer, i, j, x, y, depth, rtgWorld, noise, riverRegion, base);
        if (RTGConfig.lushRiverbanksInDesert()) {
            this.surfaceRiver.paintTerrain(primer, i, j, x, y, depth, rtgWorld, noise, riverRegion, base);
        }
    }

    @Override
    public TerrainBase terrain() {
        return this.terrain;
    }

    @Override
    public SurfaceBase surface() {
        return this.surface;
    }

    @Override
    public double waterLakeMult() {
        return this.getConfig().SURFACE_WATER_LAKE_MULT.get();
    }

    @Override
    public double lavaLakeMult() {
        return this.getConfig().SURFACE_LAVA_LAKE_MULT.get();
    }

    private File getConfigFile() {
        final Mods mod = Objects.requireNonNull(Mods.get(baseBiomeResLoc().getNamespace()), "ModCompat.Mods does not have a value for the mod that added this biome.");
        return RTGAPI.getBiomeConfigPath()
                .resolve(mod.getPrettyName())
                .resolve(baseBiomeResLoc().getPath() + ".cfg")
                .toFile();
    }

    public void initConfig() {
    } // for any biome-specific tweaking of config defaults

    protected BeachType determineBeachType() {

        if (baseBiome().getDefaultTemperature() <= 0.05f || BiomeDictionary.hasType(baseBiome(), BiomeDictionary.Type.SNOWY)) {
            return BeachType.COLD;
        }

        float height = baseBiome().getBaseHeight() + (baseBiome().getHeightVariation() * 2f);
        if (height > 1.5f || isTaigaBiome(baseBiome())) {
            return BeachType.STONE;
        }

        return BeachType.NORMAL;
    }

    private boolean isTaigaBiome(Biome biome) {
        return BiomeDictionary.hasType(biome, BiomeDictionary.Type.COLD)
                && BiomeDictionary.hasType(biome, BiomeDictionary.Type.CONIFEROUS)
                && BiomeDictionary.hasType(biome, BiomeDictionary.Type.FOREST);
    }

    public enum BeachType {
        NORMAL,
        STONE,
        COLD;

        private IRealisticBiome rtgBiome;
        private boolean locked = false;

        public Biome getBiome() {
            return (this == STONE) ? Biomes.STONE_BEACH : ((this == COLD) ? Biomes.COLD_BEACH : Biomes.BEACH);
        }

        public IRealisticBiome getRTGBiome() {
            return rtgBiome;
        }

        public IRealisticBiome setRTGBiome(IRealisticBiome rtgBiome) {
            if (!locked) {
                this.rtgBiome = rtgBiome;
                this.locked = true;
            }
            return rtgBiome;
        }

        public BeachType getTypeFromBiome(Biome beachBiome) {
            return (beachBiome == Biomes.STONE_BEACH) ? STONE : ((beachBiome == Biomes.COLD_BEACH) ? COLD : NORMAL);
        }
    }

    public enum RiverType {
        NORMAL,
        FROZEN;

        private IRealisticBiome rtgBiome;
        private boolean locked = false;

        public static RiverType getTypeFromBiome(Biome riverBiome) {
            return (riverBiome == Biomes.FROZEN_RIVER) ? FROZEN : NORMAL;
        }

        public Biome getBiome() {
            return this == NORMAL ? Biomes.RIVER : Biomes.FROZEN_RIVER;
        }

        public IRealisticBiome getRTGBiome() {
            return rtgBiome;
        }

        public IRealisticBiome setRTGBiome(IRealisticBiome rtgBiome) {
            if (!locked) {
                this.rtgBiome = rtgBiome;
                this.locked = true;
            }
            return rtgBiome;
        }
    }
}
