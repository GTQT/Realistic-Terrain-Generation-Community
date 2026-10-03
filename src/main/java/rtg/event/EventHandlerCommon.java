package rtg.event;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
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
import rtg.api.util.ChunkInfo;
import rtg.api.util.Logger;
import rtg.api.util.UtilityClass;
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

    // ==================== 光照修补（上游 74cf4fd，移植） ====================
    // 上游原话：「Hacky fix to reduce the incidence of lighting bugs. They are less common, but still
    // happening.」——即这是一个降低发生率的权宜修法，不是根因修复。
    //
    // 手法：自上而下扫描每一列，找出「不透明方块正上方的那个空气格」；若它的天光值比四邻中
    // 最大值还低 1 以上（说明天光没算对），就往该格写一次 WOOL 再清成空气，强制触发重算。
    //
    // 移植差异（相对上游 74cf4fd）：
    //   ① 世界判定补上 BiomeProviderBOP —— 上游只判 BiomeProviderRTG，而本仓库两个 provider 是
    //      兄弟类（都 extends BiomeProvider），与 onDecorateBiome 的既有写法保持一致；
    //   ② 上游原版留了 `start` / `lastChecked` 两个写完即弃的局部变量，为免编译器告警已删，
    //      其余逻辑逐行一致。

    private static boolean alreadyFixing = false;

    @SubscribeEvent
    public static void fixLightingOnLoad(ChunkEvent.Load loadEvent) {

        if (loadEvent.getWorld().isRemote) return;
        if (!isRTGWorld(loadEvent.getWorld())) return;
        if (alreadyFixing) return;
        alreadyFixing = true;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                fixLighting(loadEvent.getChunk(), x, z);
            }
        }
        alreadyFixing = false;
    }

    // 上游把这个类的唯一调用点注释掉了，此处保持同样的注释状态（备而不用）。
    //private static ChunkTracker lightChecked = new ChunkTracker(1500);

    private static void fixLighting(World world, ChunkPos chunkPos) {

        // abort if we've done this chunk before
        //if (!lightChecked.addIfNeeded(world, chunkPos)) return;
        Chunk chunk = world.getChunk(chunkPos.x, chunkPos.z);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                fixLighting(chunk, x, z);
            }
        }
    }

    private static void fixLighting(Chunk chunk, int x, int z) {

        if (chunk.getBlockState(x, 255, z).getBlock() != Blocks.AIR) return;
        int lastAir = 255;
        for (int y = 255; y > 48; y--) {
            IBlockState state = chunk.getBlockState(x, y, z);
            Block block = state.getBlock();
            if (block == Blocks.AIR) {
                lastAir = y;
                continue;
            }
            if (state.isOpaqueCube()) {
                BlockPos lastAirLocation = new BlockPos(x, lastAir, z);
                int lighting = chunk.getLightFor(EnumSkyBlock.SKY, lastAirLocation);
                if (lighting < getAdjacentBlockLight(chunk, x, lastAir, z) - 1) {
                    chunk.getWorld().setBlockState(chunk.getPos().getBlock(x, lastAir, z), Blocks.WOOL.getDefaultState(), 3);
                    chunk.getWorld().setBlockToAir(chunk.getPos().getBlock(x, lastAir, z));
                }
            }
        }
    }

    private static int getAdjacentBlockLight(Chunk chunk, int x, int y, int z) {

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

    // 由 ChunkGeneratorRTG.populate 在区块填充完成后调用：检查本区块的四个「角邻」区块
    // （±1, ±1），只对**已填充完成**（isPopulated）的那些做修补。
    public static void fixLightingAround(World world, ChunkPos pos) {

        if (!isRTGWorld(world)) return;
        Chunk targeted;
        if (world.isChunkGeneratedAt(pos.x + 1, pos.z + 1)) {
            targeted = world.getChunk(pos.x + 1, pos.z + 1);
            if (targeted.isPopulated()) {
                fixLighting(world, new ChunkPos(pos.x + 1, pos.z + 1));
            }
        }
        if (world.isChunkGeneratedAt(pos.x - 1, pos.z + 1)) {
            targeted = world.getChunk(pos.x - 1, pos.z + 1);
            if (targeted.isPopulated()) {
                fixLighting(world, new ChunkPos(pos.x - 1, pos.z + 1));
            }
        }
        if (world.isChunkGeneratedAt(pos.x - 1, pos.z - 1)) {
            targeted = world.getChunk(pos.x - 1, pos.z - 1);
            if (targeted.isPopulated()) {
                fixLighting(world, new ChunkPos(pos.x - 1, pos.z - 1));
            }
        }
        if (world.isChunkGeneratedAt(pos.x + 1, pos.z - 1)) {
            targeted = world.getChunk(pos.x + 1, pos.z - 1);
            if (targeted.isPopulated()) {
                fixLighting(world, new ChunkPos(pos.x + 1, pos.z - 1));
            }
        }
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