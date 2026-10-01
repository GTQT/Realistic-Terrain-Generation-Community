package rtg.world.biome;

import biomesoplenty.api.generation.Generators;
import biomesoplenty.common.world.BOPWorldSettings;
import biomesoplenty.common.world.layer.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.gen.layer.*;

import rtg.api.util.Logger;

import static rtg.RTGConfig.*;

public class BiomeProviderBOP extends BiomeProvider {

    /** 本世界的 RWG 布局（由 {@link RtgLayoutAccess} 按种子持有，与 RTG 侧共用同一套）。 */
    private final RtgBiomeLayout layout;

    public BiomeProviderBOP(long seed, WorldType worldType, String chunkProviderSettings) {
        this(seed, worldType, chunkProviderSettings, true);
    }

    /**
     * @param buildLayout {@code false} 时**不建立**布局，只取当前已有的那个（客户端用）。
     *                    见 {@link #BiomeProviderBOP(World)} 的说明。
     */
    private BiomeProviderBOP(long seed, WorldType worldType, String chunkProviderSettings,
                             final boolean buildLayout) {
        super();


        // load the settings object
        // note on the client side, chunkProviderSettings is an empty string
        // I'm not sure if this is a bug or deliberate, but it might have some consequences when the biomes/genlayers are different between client and server
        // The same thing happens in vanilla minecraft
        Logger.debug("BOP world settings: {}", chunkProviderSettings);
        BOPWorldSettings settings = new BOPWorldSettings(chunkProviderSettings);

        // set up all the gen layers
        GenLayer[] agenlayer = setupRTGGenLayers(seed, settings);
        agenlayer = getModdedBiomeGenerators(worldType, seed, agenlayer);
        this.genBiomes = Generators.biomeGenLayer = agenlayer[0];
        this.biomeIndexLayer = Generators.biomeIndexLayer = agenlayer[1];

        // RWG 布局接管群系选择（与 RTG 侧共用同一套布局）。
        // BOP 自己的 GenLayer 仍被构造 —— 它服务于下面未覆写的批量查询与结构选址。
        //
        // ⚠ **客户端只读、不建**（buildLayout == false）：客户端没有真实世界种子
        // （WorldClient 由 `WorldSettings(0L)` 构造，`getSeed()` 恒为 0），
        // 在客户端调 `forSeed(0)` 会把服务端正在用的全局布局**整体覆盖**，
        // 造成"加入之后生成的区块"与"出生点已生成的区块"之间的地形断层。
        //
        // 而 `getBiome` 读的是**静态**的 {@link RtgLayoutAccess}，所以客户端只要在**调用时**
        // 布局已就绪即可 —— 构造那一刻没有也不影响：那时会 fail-soft 回落到 BOP 的 GenLayer，
        // 之后服务端把布局建好，同一个 provider 实例**下一次调用就自动走布局**。
        this.layout = buildLayout ? RtgLayoutAccess.forSeed(seed) : RtgLayoutAccess.current();
    }

    /**
     * RWG 布局接管群系选择。见 {@code BiomeProviderRTG#getBiome} 的说明：
     * rtgc 的生成器逐列调用本方法，故覆写它即换掉整条群系布局链路。
     * 拿不到布局群系时回退到 BOP 的 GenLayer 结果（fail-soft）。
     */
    @Override
    public Biome getBiome(final BlockPos pos) {
        final Biome b = RtgLayoutAccess.mcBiomeAt(pos.getX(), pos.getZ());
        if (b != null) {
            return b;
        }
        final Biome fallback = super.getBiome(pos);
        noteGenLayerFallback(pos, fallback);
        return fallback;
    }

    @Override
    public Biome getBiome(final BlockPos pos, final Biome defaultBiome) {
        final Biome b = RtgLayoutAccess.mcBiomeAt(pos.getX(), pos.getZ());
        if (b != null) {
            return b;
        }
        final Biome fallback = super.getBiome(pos, defaultBiome);
        noteGenLayerFallback(pos, fallback);
        return fallback;
    }

    /**
     * 批量群系查询（1.12.2 的 {@code getBiomes(Biome[], x, z, w, h, cacheFlag)}）。
     *
     * <h2>为什么必须覆写 —— 这曾经是一个真 bug</h2>
     *
     * 不覆写时这里走**父类的 GenLayer 实现**，也就是本类 {@link #setupRTGGenLayers} 建出来的
     * **BOP 那套 GenLayer**（第 49 行那句注释"它服务于下面未覆写的批量查询与结构选址"说的就是这件事）。
     * 那是一张**与 RTG 布局毫无关系的群系表**，而且它的 {@code BOPWorldSettings}
     * 是用 **rtgc 的 generator options 字符串**构造的。
     *
     * <p><b>用户实测症状</b>：生物指南针搜不到 BOP 群系。Nature's Compass 1.5.1 的搜索
     * 就是对每个候选点调这个方法：字节码实证 {@code BiomeUtils} 里
     * {@code provider.func_76931_a(null, x, z, 1, 1, false)}（{@code func_76931_a} 即
     * {@code getBiomes}，width=height=1）。所以它搜的是"BOP 的 GenLayer 世界"，
     * 不是玩家所在的那个世界。
     *
     * <p><b>RWG 是覆写的</b>：{@code ChunkManagerRealistic:1024} 的
     * {@code loadBlockGeneratorData(...)}（= 1.12.2 的 {@code getBiomes}）与
     * {@code :1009} 的 {@code getBiomesForGeneration(...)} 都转发到 RWG 自己的布局。
     * 本覆写就是把 rtgc 补齐到同一条路上。
     */
    @Override
    public Biome[] getBiomes(final Biome[] listToReuse, final int x, final int z,
                             final int width, final int length, final boolean cacheFlag) {
        return fillFromLayout(listToReuse, x, z, width, length);
    }

    /** 同 {@link #getBiomes}；RWG {@code ChunkManagerRealistic:1009} 也是转发到布局。 */
    @Override
    public Biome[] getBiomesForGeneration(final Biome[] listToReuse, final int x, final int z,
                                          final int width, final int length) {
        return fillFromLayout(listToReuse, x, z, width, length);
    }

    /**
     * 逐格从 rtgc 布局取 MC 群系。
     *
     * <p>下标约定按 1.12.2 的 {@code GenLayer.getInts}：{@code index = i + j * width}。
     * 已知的唯一调用方（Nature's Compass）用 1×1，约定无关紧要；万一有别的调用方用另一种
     * 约定，最坏情况是"二维查询被转置"——仍然返回**本世界的**群系，
     * 比返回一张无关的 GenLayer 表要好。
     */
    private Biome[] fillFromLayout(final Biome[] listToReuse, final int x, final int z,
                                   final int width, final int length) {
        Biome[] out = listToReuse;
        if (out == null || out.length < width * length) {
            out = new Biome[width * length];
        }
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < length; j++) {
                Biome b = RtgLayoutAccess.mcBiomeAt(x + i, z + j);
                if (b == null) {
                    // fail-soft：与 getBiome 同一条回落路径（布局未就绪才会到这里）
                    pos.setPos(x + i, 64, z + j);
                    b = super.getBiome(pos);
                }
                out[i + j * width] = b;
            }
        }
        return out;
    }

    /**
     * 结构选址：数据源换成 RTG 布局（原版语义：候选列表 + 半径 + 4 格采样）。
     *
     * <p>RWG 的 {@code ChunkManagerRealistic:1044} 是**纯地形判据**（忽略列表与半径），
     * 但那一套在 1.12.2 会让**海底神殿一个都不生成**（它传 {@code WATER_BIOMES}，
     * 而海洋列高度 34–52 过不了 RWG 的"≥ 62"闸）。所以只换数据源、保留原版语义 ——
     * 调用方清单与完整推演见 {@link RtgTerrainQuery} 的类注释。
     */
    @Override
    public boolean areBiomesViable(final int x, final int z, final int radius, final java.util.List<Biome> allowed) {
        final Boolean v = RtgTerrainQuery.biomesViable(x, z, radius, allowed);
        return v != null ? v.booleanValue() : super.areBiomesViable(x, z, radius, allowed);
    }

    /**
     * 要塞/出生点选址：数据源换成 RTG 布局。
     *
     * <p>RWG 的 {@code :1073} 直接返回 {@code null}，在 1.12.2 会让
     * {@code WorldServer} 打 "Unable to find spawn biome" 并把出生点放回 (8, y, 8)。
     * 故保留原版算法（含蓄水池抽样），只换数据源。
     *
     * <p>⚠ **布局存在时不回落到父类**：父类查的是 BOP 那张无关的表，返回的坐标上
     * <b>真实群系未必在候选列表里</b>（出生点会被丢到海里/冰原上）。
     * 找不到就老实返回 {@code null}，让 {@code WorldServer} 走它自己的兜底。
     */
    @Override
    public BlockPos findBiomePosition(final int x, final int z, final int range,
                                      final java.util.List<Biome> biomes, final java.util.Random random) {
        if (RtgLayoutAccess.current() != null) {
            return RtgTerrainQuery.findBiomePosition(x, z, range, biomes, random);
        }
        return super.findBiomePosition(x, z, range, biomes, random);
    }

    /**
     * 「回落到 BOP 自己的 GenLayer」的节流日志。
     *
     * <p>这条回落路径产出的东西与 RTG 布局毫无关系，而且 {@code setupRTGGenLayers} 里就有
     * {@code new GenLayerAddMushroomIsland(5L, mainBranch)} —— 回落时 F3 会显示
     * **海洋 + 蘑菇岛**这一套群系，与地表实际生成的 RTG 地形完全不符。
     * 本类构造函数第 27-28 行的注释早已提到"客户端 chunkProviderSettings 是空字符串，
     * 可能造成客户端与服务端的 GenLayer 不同"，这行日志就是用来确认它是否真的在发生。
     */
    private static final java.util.concurrent.atomic.AtomicLong GENLAYER_FALLBACK_COUNT =
            new java.util.concurrent.atomic.AtomicLong();

    private static void noteGenLayerFallback(final BlockPos pos, final Biome fallback) {
        final long n = GENLAYER_FALLBACK_COUNT.incrementAndGet();
        if (n <= 5L || n % 20000L == 0L) {
            Logger.warn("[RTG] ⚠ 布局未命中，F3/群系查询回落到 BOP GenLayer：({}, {}) → {}（第 {} 次）"
                            + " side={} thread={} 布局未建立次数={}",
                    pos.getX(), pos.getZ(),
                    fallback == null ? "-" : fallback.getRegistryName(), n,
                    net.minecraftforge.fml.common.FMLCommonHandler.instance().getSide(),
                    Thread.currentThread().getName(),
                    RtgLayoutAccess.noLayoutCount());
        }
    }

    /**
     * 由世界构造。**客户端不建立布局**（{@code world.isRemote} ⇒ {@code buildLayout = false}）——
     * 原因见上面私有构造器的说明：客户端的 {@code getSeed()} 恒为 0，建布局会覆盖服务端的。
     */
    public BiomeProviderBOP(World world) {
        this(world.getSeed(), world.getWorldInfo().getTerrainType(), world.getWorldInfo().getGeneratorOptions(),
                !world.isRemote);
    }

    /**
     * 构造 BOP 侧的**陆海层**：恒为「原版形态」。
     * <p>
     * <b>历史</b>：这里原本按 RTG 的 {@code landScheme} 分三支（原版 / BOP 大陆 / BOP 群岛）。
     * 那套 BOP 陆海层的尺度与 RTG 的地形混合体系不可共存（同时启用会出现"两套大陆系统叠加"，
     * 曾表现为世界几乎全是水），因此 BOP 侧保留为原版形态。
     * <p>
     * RTG 的大陆场后来按用户要求**整体删除**（连同 {@code landScheme} 配置本身），
     * 现在本方法只剩"返回原版陆海层"这一个语义，不再依赖任何 RTG 配置。
     */
    public static GenLayer initialLandAndSeaLayer() {
        return vanillaLandAndSeaLayer();
    }

    private static GenLayer vanillaLandAndSeaLayer() {
        GenLayer stack;
        stack = new GenLayerIsland(1L);
        stack = new GenLayerFuzzyZoom(2000L, stack);
        stack = new GenLayerRaggedEdges(1L, stack);
        stack = new GenLayerZoom(2001L, stack);
        stack = new GenLayerRaggedEdges(2L, stack);
        stack = new GenLayerRaggedEdges(50L, stack);
        stack = new GenLayerRaggedEdges(70L, stack);
        stack = new GenLayerRemoveTooMuchOcean(2L, stack); // <--- this is the layer which does 90% of the work, the ones before it are almost pointless
        stack = new GenLayerRaggedEdges(3L, stack);
        stack = new GenLayerZoom(2002L, stack);
        stack = new GenLayerZoom(2003L, stack);
        stack = new GenLayerRaggedEdges(4L, stack);
        return stack;
    }

    // superimpose hot and cold regions an a land and sea layer
    public static GenLayerClimate climateLayer(long worldSeed) {
        GenLayer temperature;
        switch (getTempScheme()) {
            case 1:
            default:
                temperature = new GenLayerTemperatureLatitude(2L, 16, worldSeed);
                break;
            case 2:
                temperature = new GenLayerTemperatureNoise(3L, worldSeed, 0.14D);
                break;
            case 3:
                temperature = new GenLayerTemperatureNoise(4L, worldSeed, 0.08D);
                break;
            case 4:
                temperature = new GenLayerTemperatureNoise(5L, worldSeed, 0.04D);
                break;
            case 5:
                temperature = new GenLayerTemperatureRandom(6L);
                break;
        }

        GenLayer rainfall;
        switch (getRainScheme()) {
            case 1:
                rainfall = new GenLayerRainfallNoise(7L, worldSeed, 0.14D);
                break;
            case 2:
            default:
                rainfall = new GenLayerRainfallNoise(8L, worldSeed, 0.08D);
                break;
            case 3:
                rainfall = new GenLayerRainfallNoise(9L, worldSeed, 0.04D);
                break;
            case 4:
                rainfall = new GenLayerRainfallRandom(10L);
                break;
        }

        GenLayerClimate climate = new GenLayerClimate(103L, temperature, rainfall);
        // stack = new GenLayerEdge(3L, stack, GenLayerEdge.Mode.SPECIAL);
        return climate;
    }

    public static GenLayer allocateBiomes(BOPWorldSettings settings, GenLayer mainBranch, GenLayer subBiomesInit, GenLayerClimate climateLayer) {
        // allocate the basic biomes
        GenLayer biomesLayer = new GenLayerBiomeBOP(200L, mainBranch, climateLayer, settings);

        // magnify everything (using the same seed)
        biomesLayer = new GenLayerZoom(1000L, biomesLayer);
        subBiomesInit = new GenLayerZoom(1000L, subBiomesInit);
        GenLayer climateLayerZoomed = new GenLayerZoom(1000L, climateLayer);

        // 中岛：不叠加 GenLayerBiomeIslands。
        // 原实现在这里按 landScheme 分支（BOP 大陆密度 60 / 群岛密度 4），
        // 那套系统与 RTG 的地形混合不可共存，已随大陆场一起移除。
        // 注意：下面 "tiny islands" 用的是**另一个**配置 islandScheme，仍然生效。

        // magnify everything again (using the same seed)
        biomesLayer = new GenLayerZoom(1000L, biomesLayer);
        subBiomesInit = new GenLayerZoom(1000L, subBiomesInit);
        climateLayerZoomed = new GenLayerZoom(1000L, climateLayerZoomed);

        // add edge biomes
        biomesLayer = new GenLayerBiomeEdgeBOP(1000L, biomesLayer);

        // add sub-biomes (like hills or rare mutated variants) seeded with subBiomesInit
        biomesLayer = new GenLayerSubBiomesBOP(1000L, biomesLayer, subBiomesInit);

        // add tiny islands
        switch (getIslandScheme()) {
            case 3:
                biomesLayer = new GenLayerBiomeIslands(15L, biomesLayer, climateLayerZoomed, 8);
                break;
            case 2:
                biomesLayer = new GenLayerBiomeIslands(15L, biomesLayer, climateLayerZoomed, 60);
                break;
            case 1:
            default:
                biomesLayer = new GenLayerBiomeIslands(15L, biomesLayer, climateLayerZoomed, 12);
                break;
        }

        return biomesLayer;
    }

    public static GenLayer[] setupRTGGenLayers(long worldSeed, BOPWorldSettings settings) {

        int biomeSize = getBiomeSize();

        // first few layers just create areas of land and sea, continents and islands
        GenLayer mainBranch = initialLandAndSeaLayer();

        // add mushroom islands and deep oceans
        mainBranch = new GenLayerAddMushroomIsland(5L, mainBranch);
        mainBranch = new GenLayerLargeIsland(5L, mainBranch);
        mainBranch = new GenLayerDeepOcean(4L, mainBranch);

        // fork off a new branch as a seed for rivers and sub biomes
        GenLayer riversAndSubBiomesInit = new GenLayerRiverInit(100L, mainBranch);

        // create climate layer
        GenLayerClimate climateLayer = climateLayer(worldSeed);

        // allocate the biomes
        mainBranch = allocateBiomes(settings, mainBranch, riversAndSubBiomesInit, climateLayer);

        // do a bit more zooming, depending on biomeSize
        //mainBranch = new GenLayerRareBiome(1001L, mainBranch); - sunflower plains I think
        for (int i = 0; i < biomeSize; ++i) {
            mainBranch = new GenLayerZoom(1000 + i, mainBranch);
            if (i == 0) {
                mainBranch = new GenLayerRaggedEdges(3L, mainBranch);
            }
            if (i == 1 || biomeSize == 1) {
                mainBranch = new GenLayerShoreBOP(1000L, mainBranch);
            }
        }
        mainBranch = new GenLayerSmooth(1000L, mainBranch);

        // RTG uses its own Voronoi-based river system — skip BOP's GenLayer river branch entirely
        GenLayer biomesFinal = new GenLayerVoronoiZoom(10L, mainBranch);

        mainBranch.initWorldGenSeed(worldSeed);
        biomesFinal.initWorldGenSeed(worldSeed);
        return new GenLayer[]{mainBranch, biomesFinal, mainBranch};

    }

}