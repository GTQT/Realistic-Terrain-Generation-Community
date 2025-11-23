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
import rtg.api.util.noise.ISimplexData2D;
import rtg.api.util.noise.SimplexData2D;
import rtg.api.util.noise.SimplexNoise;
import rtg.api.util.noise.VoronoiResult;
import rtg.api.world.RTGWorld;
import rtg.api.world.deco.DecoBase;
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


public abstract class RealisticBiomeBase implements IRealisticBiome {

    private static final float INV_12 = 1f / 12f;
    private static final float INV_8 = 1f / 8f;
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

    @Override
    public ResourceLocation baseBiomeResLoc() {
        return baseBiomeResLoc;
    }

    @Override
    public int baseBiomeId() {
        return this.baseBiomeId;
    }

    @Override
    public float rNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
        return newrNoise(rtgWorld, x, y, border, river);
    }

    public float newrNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
        // 预计算常用常量
        final boolean allowRivers = this.getConfig().ALLOW_RIVERS.get();
        final float actualRiverProportion = RTGWorld.ACTUAL_RIVER_PROPORTION;
        final float riverFlatteningAddend = RTGWorld.RIVER_FLATTENING_ADDEND;

        if (!allowRivers) {
            float borderForRiver = Math.min(border * 2f, 1f);
            river = 1f - (1f - borderForRiver) * (1f - river);
            return terrain.generateNoise(rtgWorld, x, y, border, river);
        }

        // 预计算湖泊参数
        float lakeStrength = lakePressure(rtgWorld, x, y, border,
                rtgWorld.getLakeFrequency(),
                rtgWorld.getLakeBendSizeLarge(),
                rtgWorld.getLakeBendSizeMedium(),
                rtgWorld.getLakeBendSizeSmall());

        float adjustedLake = lakeToRiverProportions(lakeStrength,
                rtgWorld.getLakeShoreLevel(),
                rtgWorld.getLakeDepressionLevel());

        // 河流调整 - 使用更简洁的数学
        river = Math.max(0f, RTGWorld.riverAdjustedforDepthDifference(river));

        // 湖泊底部区域扩展 - 简化条件判断
        if (adjustedLake < actualRiverProportion) {
            adjustedLake = Math.max(0f, (adjustedLake - actualRiverProportion) * 2f + actualRiverProportion);
        }

        // 合并河流和湖泊 - 优化数学运算
        float combinedRiver;
        if (river < 1f && adjustedLake < 1f) {
            float leastLowering = Math.min(adjustedLake, river);
            float denominator = (1f - river) / river + (1f - adjustedLake) / adjustedLake;
            combinedRiver = 1f / (denominator + 1f);
            combinedRiver = (combinedRiver + leastLowering) * 0.5f; // 用乘法代替除法
        } else {
            combinedRiver = Math.min(adjustedLake, river);
        }

        // 平滑顶部边缘 - 减少重复计算
        float invertedRiver = 1f - combinedRiver;
        invertedRiver = invertedRiver * (invertedRiver / (invertedRiver + 0.05f) * 1.05f);
        combinedRiver = 1f - invertedRiver;

        // 水域平坦化
        float riverFlattening = Math.max(0f, combinedRiver * (1f + riverFlatteningAddend) - riverFlatteningAddend);

        // 生成地形噪声并应用侵蚀
        float terrainNoise = terrain.generateNoise(rtgWorld, x, y, border, riverFlattening);
        return erodedNoise(rtgWorld, x, y, combinedRiver, border, terrainNoise);
    }

    public float erodedNoise(RTGWorld rtgWorld, int x, int y, float river, float border, float biomeHeight) {
        final float actualRiverProportion = RTGWorld.ACTUAL_RIVER_PROPORTION;
        final float lakeBottom = RTGWorld.LAKE_BOTTOM;

        // 早期返回检查
        float riverFlattening = 1f - river;
        riverFlattening -= (1f - actualRiverProportion);

        if (riverFlattening < 0f || biomeHeight <= lakeBottom) {
            return biomeHeight;
        }

        // 标准化河流平坦化值
        riverFlattening /= actualRiverProportion;
        float r = 1f - riverFlattening;

        if (r < 1f) {
            // 预计算噪声参数
            SimplexNoise simplex = rtgWorld.simplexInstance(0);
            float irregularity = simplex.noise2f(x * 0.083333f, y * 0.083333f) * 2f + // 1/12
                    simplex.noise2f(x * 0.125f, y * 0.125f); // 1/8

            // 优化插值计算
            irregularity *= (1f + r);
            float lakeBottomWithIrregularity = lakeBottom + irregularity;

            return biomeHeight * r + lakeBottomWithIrregularity * (1f - r);
        }

        return biomeHeight;
    }

    public float oldErodedNoise(RTGWorld rtgWorld, int x, int y, float river, float border, float biomeHeight) {
        float r;
        // river of actualRiverProportions now maps to 1;
        float riverFlattening = 1f - river;
        riverFlattening = riverFlattening - (1 - RTGWorld.ACTUAL_RIVER_PROPORTION);
        // return biomeHeight if no river effect
        if (riverFlattening < 0) {
            return biomeHeight;
        }
        // what was 1 set back to 1;
        riverFlattening /= RTGWorld.ACTUAL_RIVER_PROPORTION;

        // back to usual meanings: 1 = no river 0 = river
        r = 1f - riverFlattening;

        if ((r < 1f && biomeHeight > 55f)) {
            float irregularity = rtgWorld.simplexInstance(0).noise2f(x * INV_12, y * INV_12) * 2f + rtgWorld.simplexInstance(0).noise2f(x * INV_8, y * INV_8);
            // less on the bottom and more on the sides
            irregularity = irregularity * (1 + r);
            return (biomeHeight * (r)) + ((55f + irregularity)) * (1f - r);
        } else {
            return biomeHeight;
        }
    }

    @Override
    public float lakePressure(RTGWorld rtgWorld, int x, int y, float border, float lakeInterval,
                              float largeBendSize, float mediumBendSize, float smallBendSize) {

        if (!this.getConfig().ALLOW_SCENIC_LAKES.get()) {
            return 1f;
        }

        final double invLakeInterval = 1.0 / lakeInterval;

        double pX = x;
        double pY = y;
        ISimplexData2D jitterData = SimplexData2D.newDisk();

        // 使用预计算的倒数
        rtgWorld.simplexInstance(1).multiEval2D(x * INV_240, y * INV_240, jitterData);
        pX += jitterData.getDeltaX() * largeBendSize;
        pY += jitterData.getDeltaY() * largeBendSize;

        rtgWorld.simplexInstance(0).multiEval2D(x * INV_80, y * INV_80, jitterData);
        pX += jitterData.getDeltaX() * mediumBendSize;
        pY += jitterData.getDeltaY() * mediumBendSize;

        rtgWorld.simplexInstance(4).multiEval2D(x * INV_30, y * INV_30, jitterData);
        pX += jitterData.getDeltaX() * smallBendSize;
        pY += jitterData.getDeltaY() * smallBendSize;

        VoronoiResult lakeResults = rtgWorld.cellularInstance(0).eval2D(pX * invLakeInterval, pY * invLakeInterval);
        return (float) (1.0d - lakeResults.interiorValue());
    }

    public float lakeToRiverProportions(float pressure, float shoreLevel, float topLevel) {
        final float actualRiverProportion = RTGWorld.ACTUAL_RIVER_PROPORTION;

        if (pressure > topLevel) {
            return 1f;
        }

        if (pressure < shoreLevel) {
            return (pressure / shoreLevel) * actualRiverProportion;
        }

        // 预计算分母倒数，用乘法代替除法
        float invRange = 1f / (topLevel - shoreLevel);
        float proportion = (pressure - shoreLevel) * invRange;

        return actualRiverProportion + proportion * (1f - actualRiverProportion);
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