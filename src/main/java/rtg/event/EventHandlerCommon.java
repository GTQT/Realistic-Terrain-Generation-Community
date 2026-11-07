package rtg.event;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.feature.WorldGenLiquids;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.DecorateBiomeEvent;
import net.minecraftforge.event.terraingen.DecorateBiomeEvent.Decorate;
import net.minecraftforge.event.terraingen.SaplingGrowTreeEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import rtg.RTGConfig;
import rtg.api.RTGAPI;
import rtg.api.util.*;
import rtg.api.util.ChunkGenerationProfiler.Category;
import rtg.api.util.ChunkGenerationProfiler.Counter;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.IRealisticBiome;
import rtg.api.world.gen.feature.tree.rtg.RTGSaplingManager;
import rtg.world.biome.BiomeProviderBOP;
import rtg.world.biome.BiomeProviderRTG;

import java.util.HashSet;
import java.util.Random;


@UtilityClass
public final class EventHandlerCommon
{
    private EventHandlerCommon() {}

    /**
     * 树木接管管理器（移植上游新树系统 / T6）。
     *
     * <p>由各群系的 {@code initDecos()} 通过 {@code useTreeManager()} 或
     * {@code suppressBOPBiome(...)} 登记。{@code IRealisticBiome.rDecorate} 与
     * 本类的 {@link #takeoverTreeGeneration} 都靠它回答"这个群系的树归 RTG 管吗"。
     */
    public static TreeGenerationManager treeGenerationManager = new TreeGenerationManager();

    public static void init() {
        MinecraftForge.TERRAIN_GEN_BUS.register(EventHandlerCommon.class);
        // 上游 74cf4fd「Lighting bug reduction」加：区块载入事件需要 EVENT_BUS。
        MinecraftForge.EVENT_BUS.register(EventHandlerCommon.class);
        // 无头 500 区块基准：只有显式 -Drtg.benchmark=true 时才注册，正常游戏零开销。
        if (Boolean.getBoolean("rtg.benchmark")) {
            MinecraftForge.EVENT_BUS.register(rtg.debug.BenchmarkProbe.class);
        }
    }

    // ==================== 树木接管（上游新树系统 / T6） ====================

    private static final HashSet<WorldChunkPos> chunkIDs = new HashSet<>();

    /**
     * 区块+世界的复合键。上游原样（用 hashCode 相加 + equals 双判）。
     *
     * <p>用途见 {@link #takeoverTreeGeneration}：防止同一区块在 TREE 装饰事件里被递归处理。
     */
    private static class WorldChunkPos {

        final World world;
        final ChunkPos chunkPos;

        WorldChunkPos(World _world, ChunkPos _chunkPos) {
            world = _world;
            chunkPos = _chunkPos;
        }

        public int hashCode() {
            return world.hashCode() + chunkPos.hashCode();
        }

        public boolean equals(Object candidate) {
            if (!(candidate instanceof WorldChunkPos)) {
                return false;
            }
            WorldChunkPos compared = (WorldChunkPos) candidate;
            return world.equals(compared.world) && chunkPos.equals(compared.chunkPos);
        }
    }

    /**
     * 在**非 RTG 世界类型**里接手树木生成（移植上游 T6）。
     *
     * <p>场景：玩家用原版/其它世界类型，但装了 RTG 并希望那些世界里也长 RTG 的树。
     * 拦下原版 TREE 装饰事件，改由 {@code rtgBiome.getTreeDecos()} 生成。
     *
     * <p>两处相对上游的有意偏离（用户授权自行判断）：
     * <ol>
     *   <li>上游判 {@code instanceof BiomeProviderRTG}；本仓库两个 provider 是兄弟类，
     *       故复用本类既有的 {@link #isRTGWorld} 同时覆盖 BOP。</li>
     *   <li>上游在 RTG 世界类型下会走 {@code rtgBiome.getTreeDecos().generate(...)}
     *       这条分支（因为它的 {@code ChunkGeneratorRTG} 自己不调树装饰）。
     *       **本仓库不同**：{@code ChunkGeneratorRTG} 的 {@code rDecorate} 已经会调
     *       {@code getTreeDecos()}，这里再调一次就会**重复出树**。故 RTG 世界一律交给
     *       生成器，本方法只负责"把原版这批树拦掉"。</li>
     * </ol>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void takeoverTreeGeneration(DecorateBiomeEvent.Decorate event) {

        if (!RTGConfig.treesInNonRTGWorlds()) {
            return;
        }
        if (event.getType() != Decorate.EventType.TREE) {
            return;
        }
        // RTG 自己（rDecorate 里立的树）走的是自定义事件，别拦自己的。
        if (event instanceof rtg.api.event.DecorateBiomeEventRTG.DecorateRTG) {
            return;
        }
        if (event.getWorld().isRemote) {
            return;
        }

        final WorldChunkPos chunkID = new WorldChunkPos(event.getWorld(), event.getChunkPos());
        if (chunkIDs.contains(chunkID)) {
            event.setResult(Event.Result.DENY);
            return;
        }

        final BlockPos center = event.getChunkPos().getBlock(8, 64, 8);
        final Biome biome = event.getWorld().getBiomeProvider().getBiome(center);
        if (!treeGenerationManager.managingBiome(biome)) {
            return;
        }

        final IRealisticBiome rtgBiome = RTGAPI.getRTGBiome(biome);
        if (rtgBiome == null || rtgBiome.getConfig().DISABLE_RTG_DECORATIONS.get()) {
            return;
        }

        // 已经在跑 RTG 生成器的世界：树由 ChunkGeneratorRTG.populate 负责，这里只拦截原版的。
        if (isRTGWorld(event.getWorld())) {
            event.setResult(Event.Result.DENY);
            return;
        }

        chunkIDs.add(chunkID);
        try {
            final ChunkInfo chunkInfo = new ChunkInfo(event.getChunkPos(), RTGWorld.getInstance(event.getWorld()));
            rtgBiome.getTreeDecos().generate(rtgBiome, RTGWorld.getInstance(event.getWorld()),
                    event.getRand(), event.getChunkPos(), 0, false, chunkInfo);
        } finally {
            chunkIDs.remove(chunkID);
        }
        event.setResult(Event.Result.DENY);
    }

    // ==================== 光照修补 ====================
    // 上游的权宜修法：自上而下扫描，找“不透明块正上方的空气格”；天光比斜邻最大值
    // 低至少 2 时重算。它不是根因修复，只是降低光照 bug 的发生率。
    //
    // 与上游 74cf4fd 的差异：
    //   ① 世界判定同时接受 BiomeProviderBOP/RTG；
    //   ② 默认直接 World#checkLight，不再写 WOOL 再清空；-Drtg.fixLightingUseWool=true 可回退；
    //   ③ 删除上游未使用的局部变量。

    private static boolean alreadyFixing = false;

    /**
     * 每个区块最多被四个对角邻居各扫一次。只有目标已 populated 并准备扫描时才登记，
     * 因此“目标还没 populated”不会误标记，后续邻居仍会补扫。
     * 容量 4096（约 64×64 区域），按插入顺序淘汰。
     */
    private static final ChunkTracker lightChecked = new ChunkTracker(4096);

    /**
     * 默认直接 {@code World#checkLight}：只重算天光/方块光，不写方块、不改高度图、
     * 不通知邻居。旧版“写 WOOL 再清空”会触发两次邻居和观察者更新，可用
     * {@code -Drtg.fixLightingUseWool=true} 回退。
     */
    private static final boolean FIX_LIGHTING_USE_WOOL =
            Boolean.getBoolean("rtg.fixLightingUseWool");

    /** 供单元测试直接验证的列扫描统计。 */
    static final class FixLightingStats {
        int sectionSkips;
        int repeatSkips;
        int checks;
        int fixes;
    }

    /** 只暴露扫描需要的列视图，避免测试依赖真实 Chunk/World。 */
    interface FixLightingColumn {
        IBlockState get(int y);

        boolean isEmptySection(int sectionY);
    }

    /** 列扫描对外部光照/修复动作的最小依赖。 */
    interface FixLightingSink {
        int skyLight(int x, int y, int z);

        int adjacentLight(int x, int y, int z);

        void fix(int x, int y, int z);
    }

    @SubscribeEvent
    public static void fixLightingOnLoad(ChunkEvent.Load loadEvent) {

        if (loadEvent.getWorld().isRemote) return;
        if (!isRTGWorld(loadEvent.getWorld())) return;
        if (alreadyFixing) return;
        alreadyFixing = true;
        long fixStart = ChunkGenerationProfiler.start(Category.FIX_LIGHTING_ON_LOAD);
        try {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    fixLighting(loadEvent.getChunk(), x, z);
                }
            }
        } finally {
            // onLoad 不在当前区块的计时 map 内，只进 Category 聚合，避免污染正在生成的区块。
            ChunkGenerationProfiler.endExternal(Category.FIX_LIGHTING_ON_LOAD, fixStart);
            alreadyFixing = false;
        }
    }

    private static void fixLighting(World world, ChunkPos chunkPos) {

        Chunk chunk = world.getChunk(chunkPos.x, chunkPos.z);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                fixLighting(chunk, x, z);
            }
        }
    }

    private static void fixLighting(Chunk chunk, int x, int z) {

        ChunkGenerationProfiler.count(Counter.FIX_LIGHTING_COLUMNS);
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        IBlockState top = getFixLightingState(sections, x, 255, z);
        if (top.getBlock() != Blocks.AIR) return;

        FixLightingStats stats = new FixLightingStats();
        scanFixLightingColumn(new ChunkFixLightingColumn(sections, x, z), x, z,
                new ChunkFixLightingSink(chunk), stats);
        if (stats.sectionSkips > 0) {
            ChunkGenerationProfiler.add(Counter.FIX_LIGHTING_SECTION_SKIPS, stats.sectionSkips);
        }
        if (stats.repeatSkips > 0) {
            ChunkGenerationProfiler.add(Counter.FIX_LIGHTING_AIR_REPEAT_SKIPS, stats.repeatSkips);
        }
    }

    /**
     * 从 y=255 向下扫到 y=49，检查每个“不透明块正上方空气格”的天光。
     *
     * <p>空 section 整段跳过；同一 {@code lastAir} 连续遇到多个不透明块只检查一次。
     * 修复动作由 sink 决定（默认 checkLight，可回退 WOOL）。
     */
    static void scanFixLightingColumn(FixLightingColumn column, int x, int z,
                                      FixLightingSink sink, FixLightingStats stats) {

        int lastAir = 255;
        int lastCheckedAir = -1;
        for (int sectionY = 240; sectionY >= 48; sectionY -= 16) {
            if (column.isEmptySection(sectionY)) {
                stats.sectionSkips++;
                lastAir = sectionY;
                continue;
            }
            int minY = Math.max(49, sectionY);
            for (int y = sectionY + 15; y >= minY; y--) {
                IBlockState state = column.get(y);
                if (state.getBlock() == Blocks.AIR) {
                    lastAir = y;
                    continue;
                }
                if (!state.isOpaqueCube()) {
                    continue;
                }
                if (lastAir == lastCheckedAir) {
                    stats.repeatSkips++;
                    continue;
                }
                lastCheckedAir = lastAir;
                stats.checks++;
                int lighting = sink.skyLight(x, lastAir, z);
                if (lighting < sink.adjacentLight(x, lastAir, z) - 1) {
                    stats.fixes++;
                    sink.fix(x, lastAir, z);
                }
            }
        }
    }

    private static IBlockState getFixLightingState(ExtendedBlockStorage[] sections, int x, int y, int z) {

        ExtendedBlockStorage section = sections[y >> 4];
        if (section == null || section.isEmpty()) return Blocks.AIR.getDefaultState();
        return section.get(x, y & 15, z);
    }

    private static final class ChunkFixLightingColumn implements FixLightingColumn {

        private final ExtendedBlockStorage[] sections;
        private final int x;
        private final int z;

        ChunkFixLightingColumn(ExtendedBlockStorage[] sections, int x, int z) {
            this.sections = sections;
            this.x = x;
            this.z = z;
        }

        @Override
        public IBlockState get(int y) {
            return getFixLightingState(sections, x, y, z);
        }

        @Override
        public boolean isEmptySection(int sectionY) {
            ExtendedBlockStorage section = sections[sectionY >> 4];
            return section == null || section.isEmpty();
        }
    }

    private static final class ChunkFixLightingSink implements FixLightingSink {

        private final Chunk chunk;

        ChunkFixLightingSink(Chunk chunk) {
            this.chunk = chunk;
        }

        @Override
        public int skyLight(int x, int y, int z) {
            return chunk.getLightFor(EnumSkyBlock.SKY, new BlockPos(x, y, z));
        }

        @Override
        public int adjacentLight(int x, int y, int z) {
            return getAdjacentBlockLight(chunk, x, y, z);
        }

        @Override
        public void fix(int x, int y, int z) {
            BlockPos pos = chunk.getPos().getBlock(x, y, z);
            if (FIX_LIGHTING_USE_WOOL) {
                ChunkGenerationProfiler.count(Counter.FIX_LIGHTING_WOOL);
                chunk.getWorld().setBlockState(pos, Blocks.WOOL.getDefaultState(), 3);
                chunk.getWorld().setBlockToAir(pos);
            } else {
                ChunkGenerationProfiler.count(Counter.FIX_LIGHTING_DIRECT_LIGHT);
                chunk.getWorld().checkLight(pos);
            }
        }
    }

    private static int getAdjacentBlockLight(Chunk chunk, int x, int y, int z) {

        // 上游只查四个斜邻，不是四正邻；保持原语义。
        int result = getBlockLight(chunk, x + 1, y, z);
        result = Math.max(getBlockLight(chunk, x + 1, y, z + 1), result);
        result = Math.max(getBlockLight(chunk, x - 1, y, z + 1), result);
        result = Math.max(getBlockLight(chunk, x - 1, y, z - 1), result);
        return result;
    }

    private static int getBlockLight(Chunk chunk, int x, int y, int z) {

        if ((x < 0) || (x > 15) || (z < 0) || (z > 15)) return 0;
        return chunk.getLightFor(EnumSkyBlock.SKY, new BlockPos(x, y, z));
    }

    // 区块填充完成后检查四个角邻（±1, ±1），只修补已 populated 的目标。
    public static void fixLightingAround(World world, ChunkPos pos) {

        if (!isRTGWorld(world)) return;
        fixLightingCorner(world, pos.x + 1, pos.z + 1);
        fixLightingCorner(world, pos.x - 1, pos.z + 1);
        fixLightingCorner(world, pos.x - 1, pos.z - 1);
        fixLightingCorner(world, pos.x + 1, pos.z - 1);
    }

    private static void fixLightingCorner(World world, int chunkX, int chunkZ) {

        if (!world.isChunkGeneratedAt(chunkX, chunkZ)) return;
        Chunk targeted = world.getChunk(chunkX, chunkZ);
        if (!targeted.isPopulated()) return;
        ChunkPos targetPos = new ChunkPos(chunkX, chunkZ);
        if (!lightChecked.addIfNeeded(world, targetPos)) {
            ChunkGenerationProfiler.count(Counter.FIX_LIGHTING_CORNER_SKIPS);
            return;
        }
        ChunkGenerationProfiler.count(Counter.FIX_LIGHTING_CORNER_SCANS);
        fixLighting(world, targetPos);
    }

    private static boolean isRTGWorld(final World world) {

        return world.getBiomeProvider() instanceof BiomeProviderBOP
                || world.getBiomeProvider() instanceof BiomeProviderRTG;
    }

    // TERRAIN_GEN_BUS
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDecorateBiome(final DecorateBiomeEvent.Decorate event) {

        final World world = event.getWorld();
        if (world.getBiomeProvider() instanceof BiomeProviderBOP || world.getBiomeProvider() instanceof BiomeProviderRTG) {
            final Decorate.EventType eventType = event.getType();
            if (eventType == Decorate.EventType.LAKE_WATER || eventType == Decorate.EventType.LAKE_LAVA) {
                event.setResult(Event.Result.DENY);
                generateFalls(world, event.getRand(), event.getChunkPos(), eventType);
            }
        }
    }

    private static void generateFalls(final World world, final Random rand, final ChunkPos chunkPos, final Decorate.EventType type) {
        final BlockPos offsetpos = new BlockPos(chunkPos.x * 16 + 8, 0, chunkPos.z * 16 + 8);
        switch (type) {
            case LAKE_WATER:
                // reduced chance due to reduced random y level
                for (int i = 0; i < 20; i++) {
                    (new WorldGenLiquids(Blocks.FLOWING_WATER))
                        .generate(world, rand, offsetpos.add(rand.nextInt(16), rand.nextInt(64) + 8, rand.nextInt(16)));
                }
                break;
            case LAKE_LAVA:
                // reduced chance due to reduced random y level
                for (int i = 0; i < 5; i++) {
                    (new WorldGenLiquids(Blocks.FLOWING_LAVA))
                        .generate(world, rand, offsetpos.add(rand.nextInt(16), rand.nextInt(rand.nextInt(rand.nextInt(64) + 8) + 8), rand.nextInt(16)));
                }
                break;
            default:
        }
    }

 // TERRAIN_GEN_BUS
    @SubscribeEvent
    // 底层 API 变动（移植上游新树系统 / T4）：新版 RTGSaplingManager 把整套树苗判定收进了
    // 静态 RTGSaplingManager.manage(event)，旧版那一长串（countSaplingGroup / is2x2 /
    // obtuseAngle / finishGeneration + instance 方法）在上游已被删除，故此处按其目标形态重写。
    // 保留本仓库的 fixLighting 调用（上游在同一位置也调它）。
    public static void variableSaplingGrowTreeRTG(SaplingGrowTreeEvent event) {

        final World world = event.getWorld();

        // skip if RTG saplings are disabled or this world does not use BiomeProviderBOP/RTG
        if (!RTGConfig.rtgTreesFromSaplings() || !isRTGWorld(world)) {
            Logger.debug("[SaplingGrowTreeEvent] Aborting: RTG trees are disabled, or not an RTG dimension");
            return;
        }

        if (RTGSaplingManager.manage(event)) {
            fixLighting(world, new ChunkPos(event.getPos()));
        }
    }
}