package rtg.world.biome;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.gen.layer.GenLayerRiverMix;

import rtg.api.util.Logger;
import rtg.api.world.RTGWorld;
import rtg.compat.ModCompat.Mods;


public class BiomeProviderRTG extends BiomeProvider {

    /** 本世界的 RWG 布局（由 {@link RtgLayoutAccess} 按种子持有）。 */
    private final RtgBiomeLayout layout;
//  private final RTGWorld rtgWorld;
//  private final RTGChunkGenSettings generatorSettings;
//  private GenLayer genBiomes;
//  private GenLayer biomeIndexLayer;

    public BiomeProviderRTG(RTGWorld rtgWorld) {

        super(rtgWorld.world().getWorldInfo());

        // RWG 布局在这里建立并按种子缓存（此时 BiomeInit 早已跑完、群系已注册）。
        // 分类结果（各池大小）会打进日志，供运行期核对。
        //
        // ⚠ **客户端只读、不建**：客户端没有真实世界种子（WorldClient 由 WorldSettings(0L) 构造，
        // `getSeed()` 恒为 0），若在客户端调用 forSeed(0) 会把服务端正在用的全局布局**整体覆盖**，
        // 导致"加入之后生成的区块"与"出生点已生成的区块"地形断层。
        // 而 getBiome 读的是静态的 RtgLayoutAccess，所以客户端只要**在调用时**有布局即可 ——
        // 构造时没有也不影响（那时会 fail-soft 回落到原版 GenLayer，之后布局就绪即自动生效）。
        this.layout = rtgWorld.world().isRemote
                ? RtgLayoutAccess.current()
                : RtgLayoutAccess.forSeed(rtgWorld.seed());

//      this.rtgWorld = rtgWorld; //new RTGWorld(world)
//      this.generatorSettings = rtgWorld.getGeneratorSettings();

//      GenLayer[] agenlayer = GenLayer.initializeAllBiomeGenerators(seed, worldType, ChunkGeneratorSettings.Factory.jsonToFactory(world.getWorldInfo().getGeneratorOptions()).build());
//      agenlayer = getModdedBiomeGenerators(worldType, seed, agenlayer);
//      this.genBiomes = agenlayer[0]; //maybe this will be needed
//      this.biomeIndexLayer = agenlayer[1];
        maybeRemoveRivers();
    }

    /**
     * RWG 布局接管群系选择。
     * <p>
     * <b>为什么只覆写这一个方法就够</b>：{@code ChunkGeneratorRTG} **不是**批量调用
     * {@code getBiomes(...)}，而是逐列调用本方法（{@code ChunkGeneratorRTG:801} 与 {@code :865}），
     * 再经 {@code RTGAPI.getRTGBiome(mcId)} 映射回现实主义群系。
     * 所以覆写本方法即换掉了整条群系布局链路。
     * <p>
     * <b>fail-soft</b>：任何拿不到布局群系的情况（布局未就绪、池为空、异常）都回退到父类的
     * GenLayer 结果，而不是崩掉世界生成。日志里会记录各池计数与空池告警。
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

    /**
     * 结构选址：数据源换成 RTG 布局（原版语义：候选列表 + 半径 + 4 格采样）。
     *
     * <p>RWG 的 {@code ChunkManagerRealistic:1044} 是**纯地形判据**（忽略列表与半径），
     * 但那一套在 1.12.2 会让**海底神殿一个都不生成**（它传 {@code WATER_BIOMES} 且海洋列高度
     * 34–52，过不了 RWG 的"≥ 62"闸）。所以这里只换数据源、保留原版语义 ——
     * 证据与完整推演见 {@link RtgTerrainQuery} 的类注释。
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
     * {@code WorldServer} 打 "Unable to find spawn biome" 并把出生点放回 (8, y, 8) —— 不崩，
     * 但出生点几乎总在 0,0 附近。故保留原版算法（含蓄水池抽样），只换数据源。
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
     * 批量群系查询（1.12.2 的 {@code getBiomes(Biome[], x, z, w, h, cacheFlag)}）。
     *
     * <h2>为什么必须覆写 —— 这曾经是一个真 bug</h2>
     *
     * 不覆写时这里走**父类的 GenLayer 实现**，而 rtgc 的 provider 把 {@code biomeIndexLayer}
     * 设成了 **BOP 自己那套 GenLayer**（{@code BiomeProviderBOP.setupRTGGenLayers}）——
     * 那是一张**与 RTG 布局毫无关系的群系表**，而且它的 {@code BOPWorldSettings}
     * 还是用 **rtgc 的 generator options 字符串**构造的。
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

    /** 见 {@code BiomeProviderBOP.noteGenLayerFallback} —— F3 走的是哪条路的判定点。 */
    private static final java.util.concurrent.atomic.AtomicLong GENLAYER_FALLBACK_COUNT =
            new java.util.concurrent.atomic.AtomicLong();

    private static void noteGenLayerFallback(final BlockPos pos, final Biome fallback) {
        final long n = GENLAYER_FALLBACK_COUNT.incrementAndGet();
        if (n <= 5L || n % 20000L == 0L) {
            Logger.warn("[RTG] ⚠ 布局未命中，F3/群系查询回落到原版 GenLayer：({}, {}) → {}（第 {} 次）"
                            + " side={} thread={} 布局未建立次数={}",
                    pos.getX(), pos.getZ(),
                    fallback == null ? "-" : fallback.getRegistryName(), n,
                    net.minecraftforge.fml.common.FMLCommonHandler.instance().getSide(),
                    Thread.currentThread().getName(),
                    RtgLayoutAccess.noLayoutCount());
        }
    }

    private void maybeRemoveRivers() {
        /*
         * If the river layer is an instance of GenLayerRiverRTG (ie: no mods have altered the GenLayers from WorldTypeEvent$InitBiomeGens)
         * then leave the layers alone since it will handle rivers, otherwise the layers have been altered by another mod, such as
         * Geographicraft, so we need to remove the river layer.
         */
        if (Mods.geographicraft.isLoaded()) return;// Geographicraft has *always* removed vanilla rivers for RTG.
        //if (!(genBiomes instanceof GenLayerRiverRTG)) {
        if (genBiomes instanceof GenLayerRiverMix) {
            // Overwrite the river layer with the biome layer to kill vanilla rivers.
            Logger.debug("Removing vanilla river layer");
            ((GenLayerRiverMix)genBiomes).riverPatternGeneratorChain = ((GenLayerRiverMix)genBiomes).biomePatternGeneratorChain;
        } else {
            Logger.error("Failed to remove the vanilla river layer; Wrong GenLayer type: {}", genBiomes.getClass().getName());
        }
        //}
    }
}
