package rtg.api.world.biome;

import net.minecraft.block.BlockLeaves;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeDecorator;
import net.minecraft.world.chunk.ChunkPrimer;
import rtg.RTGConfig;
import rtg.api.config.BiomeConfig;
import rtg.api.util.ChunkInfo;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase.BeachType;
import rtg.api.world.biome.RealisticBiomeBase.RiverType;
import rtg.api.world.deco.AbstractDeco;
import rtg.api.world.deco.DecoBase;
import rtg.api.world.deco.collection.DecoCollectionBase;
import rtg.api.world.deco.collection.DecoCollectionDesertRiver;
import rtg.api.world.gen.feature.tree.rtg.TreeRTG;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;

import java.util.Collection;
import java.util.Random;


public interface IRealisticBiome {

    Biome baseBiome();

    ResourceLocation baseBiomeResLoc();

    int baseBiomeId();

    RiverType getRiverType();

    BeachType getBeachType();

    IRealisticBiome getRiverBiome();

    IRealisticBiome getBeachBiome();

    Biome preferredBeach();

    BiomeConfig getConfig();

    TerrainBase terrain();

    SurfaceBase surface();

    void rReplace(final ChunkPrimer primer, final BlockPos blockPos, final int x, final int y, final int depth, final RTGWorld rtgWorld, final float[] noise, final float river, final Biome[] base);

    void rReplace(ChunkPrimer primer, int i, int j, int x, int y, int depth, RTGWorld rtgWorld, float[] noise, float river, Biome[] base);

    float rNoise(RTGWorld rtgWorld, int x, int y, float border, float river);

    /**
     * RWG {@code rwg/biomes/realistic/RealisticBiomeBase.generateMapGen}（L186-198）的 rtgc 版签名 ——
     * 「地图生成器」钩子：某群系有机会把**自己的**地标（火山锥等）盖进本区块的方块数组。
     *
     * <p><b>RWG 的驱动链</b>：{@code ChunkGeneratorRealistic.provideChunk}（L216-233）在
     * 「地形已填好、地表替换之前」遍历 {@code mapGenBiomes[k] > 0f} 的每个群系（k 是该区块
     * **中心采样点**上混合权重非零的现实主义编号），逐个调 {@code generateMapGen}；
     * 基类实现是 k=5 的 11×11「候选中心」双重循环（见
     * {@link RealisticBiomeBase#generateMapGen}），每个候选点按自己的种子调一次
     * {@link #rMapGen}。**只有** {@code rMapGen} 非空的群系才会真正落方块。
     *
     * <p><b>签名适配（三处，都是 API 形态差异，不是算法差异）</b>：
     * <ul>
     *   <li>{@code Block[] blocks + byte[] metadata} → {@code ChunkPrimer primer}
     *       （同仓既有约定：1.12.2 的方块自带状态，没有独立的 metadata 数组）；</li>
     *   <li>{@code ChunkManagerRealistic cmr} → 由实现自己取
     *       {@code RtgLayoutAccess.current()}（rtgc 的布局是**世界级单例**，
     *       生成器不持有它，见 {@code RtgLayoutAccess} 的类注释）；</li>
     *   <li>{@code NoiseGenerator perlin + CellNoise cell} → 实现内用
     *       {@code rtgWorld.simplexInstance(0)}（同一算法的 P 与 C 实例，见
     *       {@code RealisticBiomeBOPTropicalIsland#rMapGen}）与自建的
     *       {@code new RtgCellNoise(worldSeed, (short) 0)}。</li>
     * </ul>
     *
     * @param worldSeed 世界种子（RWG 的 {@code Long seed} 形参 / {@code world.getSeed()}）
     * @param chunkX    当前区块的**区块** X 坐标
     * @param chunkZ    当前区块的**区块** Z 坐标
     * @param noise     该区块的 256 列地形高度数组（RWG 的 {@code testHeight}，
     *                  rtgc 里是 {@code ChunkLandscape.noise}，索引口径同为 {@code x * 16 + z}）；
     *                  地标是**就地抬高**它，故必须传地表替换正在用的那个活数组，不能是副本。
     */
    default void generateMapGen(RTGWorld rtgWorld, ChunkPrimer primer, Random mapRand, long worldSeed,
            int chunkX, int chunkZ, float[] noise) {
    }

    /**
     * RWG {@code rwg/biomes/realistic/RealisticBiomeBase.rMapGen}（L200-201）的 rtgc 版签名：
     * 单个**候选地标中心**的一次机会。基类为空实现，由群系按需覆写。
     *
     * <p><b>⚠ 形参顺序的坑（照抄 RWG 的实际语义，而不是它的形参名）</b>：
     * RWG 基类把这一对写成 {@code (…, int chunkX, int chunkY, int baseX, int baseY, …)}，
     * 但 {@code generateMapGen} 的调用点传的是 {@code rMapGen(…, baseX, baseY, chunkX, chunkY, …)}
     * ——即**基类声明的名字与实参语义相反**。子类
     * {@code RealisticBiomeIslandTropical}（L56-58）覆写时用的名字才是对的：
     * 第 6/7 位 = 候选中心（{@code baseX/baseY}），第 8/9 位 = 当前区块（{@code chunkX/chunkY}）。
     * rtgc 统一按**语义**命名（本方法与 {@code generateMapGen} 的实参顺序一一对应），
     * 覆写者按此理解即可。
     *
     * @param baseX  候选地标中心的**区块** X 坐标（RWG 的 {@code baseX}，可为负、可远离本区块）
     * @param baseZ  候选地标中心的**区块** Z 坐标
     * @param chunkX 当前区块的**区块** X 坐标（落方块时的目标区块）
     * @param chunkZ 当前区块的**区块** Z 坐标
     * @param noise  同 {@link #generateMapGen}：该区块 256 列的地形高度活数组
     */
    default void rMapGen(RTGWorld rtgWorld, ChunkPrimer primer, Random mapRand, int baseX, int baseZ,
            int chunkX, int chunkZ, float[] noise) {
    }

    double waterLakeMult();

    double lavaLakeMult();

    void initDecos();

    public boolean allowVanillaTrees();

    Collection<DecoBase> getDecos();

    // TODO: [1.12] To be removed. All trees need to be a Deco and be added through #addDeco.
    @Deprecated
    Collection<TreeRTG> getTrees();

    /**
     * 这个群系的**树装饰集合**（移植上游新树系统 / T5）。
     *
     * <p>上游 {@code IRealisticBiome:79} 就是这一行（无 default 实现）。
     * rtgc 侧由唯一实现类 {@code RealisticBiomeBase} 覆写，返回其 {@code treeGenerator} 字段。
     */
    AbstractDeco getTreeDecos();

    /**
     * 把这个群系登记进树木接管管理器（移植上游新树系统 / T6）。
     *
     * <p>群系在 {@code initDecos()} 里调一次，表示"本群系的树由 RTG 负责"。
     * 登记之后 {@code rDecorate} 与 {@code EventHandlerCommon.takeoverTreeGeneration}
     * 才认为该群系归 RTG 管。
     */
    default void useTreeManager() {
        rtg.event.EventHandlerCommon.treeGenerationManager.manageBiome(baseBiome());
    }

    /**
     * Adds a deco object to the list of biome decos.
     * The 'allowed' parameter allows us to pass biome config booleans dynamically when configuring the decos in the biome.
     */
    default void addDeco(DecoBase deco, boolean allowed) {

        if (allowed) {

            Collection<DecoBase> decos = this.getDecos();

            if (!deco.properlyDefined()) {
                throw new RuntimeException(deco.toString());
            }

            decos.add(deco);
        }
    }

    // Use this method to override a base biome's decorations.
    default void overrideDecorations() {}

    /**
     * Convenience method for addDeco() where 'allowed' is assumed to be true.
     */
    default void addDeco(DecoBase deco) {
        if (!deco.properlyDefined()) {
            throw new RuntimeException(deco.toString());
        }
        this.addDeco(deco, true);
    }

    // TODO: [1.12] The DecoCollection* classes should be removed and replaced by utility methods that return Collection<DecoBase>s that
    //              are added the same as in #addDeco
    @Deprecated
    default void addDecoCollection(DecoCollectionBase decoCollection) {

        // Don't add the desert river deco collection if the user has disabled it.
        if (decoCollection instanceof DecoCollectionDesertRiver) {
            if (!RTGConfig.lushRiverbanksInDesert()) {
                return;
            }
        }

        // Add this collection's decos to master deco list.
        if (decoCollection.decos.size() > 0) {
            for (int i = 0; i < decoCollection.decos.size(); i++) {
                this.addDeco(decoCollection.decos.get(i));
            }
        }

        // If there are any tree decos in this collection, then add the individual TreeRTG objects to master tree list.
        if (decoCollection.rtgTrees.size() > 0) {
            for (int i = 0; i < decoCollection.rtgTrees.size(); i++) {
                this.addTree(decoCollection.rtgTrees.get(i));
            }
        }
    }

    /**
     * Adds a tree to the list of RTG trees associated with this biome.
     * The 'allowed' parameter allows us to pass biome config booleans dynamically when configuring the trees in the biome.
     */
    // TODO: [1.12] To be removed. All trees need to be a Deco and be added through #addDeco.
    @Deprecated
    default void addTree(TreeRTG tree, boolean allowed) {

        if (allowed) {

            /*
             * 底层 API 变动（移植上游新树系统）：新版 TreeRTG 去掉了 saplingBlock 字段与
             * setSaplingBlock(../getSaplingBlock()。树苗改由 RTGSaplingManager 从树叶推导，
             * 不再需要在这里给每棵树塞一份树苗数据 —— 故原第一行已删除。
             */

            /*
             * Make sure all leaves delay their decay to prevent insta-despawning of leaves (e.g. Swamp Willow)
             * The try/catch is a safeguard against trees that use leaves which aren't an instance of BlockLeaves.
             */
            try {
                IBlockState leaves = tree.getLeavesBlock().withProperty(BlockLeaves.CHECK_DECAY, false);
                tree.setLeavesBlock(leaves);
            }
            catch (Exception ignore) {
                // Do nothing.
            }

            this.getTrees().add(tree);
        }
    }

    /**
     * Convenience method for addTree() where 'allowed' is assumed to be true.
     */
    // TODO: [1.12] To be removed. All trees need to be a Deco and be added through #addDeco.
    @Deprecated
    default void addTree(TreeRTG tree) {
        this.addTree(tree, true);
    }

    default void rDecorate(final RTGWorld rtgWorld, final Random rand, final ChunkPos chunkPos, final float river, final boolean hasVillage, final float[] noise) {
        ChunkInfo info = new ChunkInfo(chunkPos, rtgWorld, noise);

        // 归因计时（只在 profiler 打开时累计；见 ChunkInfo 的装饰耗时说明）
        ChunkInfo.noteDecoratedBiome(String.valueOf(this.baseBiomeResLoc()));
        final long tOwn = System.nanoTime();
        for (DecoBase deco : this.getDecos()) {
            if (deco.preGenerate(river)) {
                ChunkInfo.noteInvocation();      // D4 诊断：记录"实际被调用了几个 deco"
                final long tDeco = System.nanoTime();
                deco.generate(this, rtgWorld, rand, chunkPos, river, hasVillage, info);
                ChunkInfo.noteDeco(deco.getClass().getSimpleName(), System.nanoTime() - tDeco);
            }
        }
        ChunkInfo.noteOwnDecoNs(System.nanoTime() - tOwn);

        // 树装饰：allowVanillaTrees() 为真时交给原版 Biome.decorate；
        // 为假时由 getTreeDecos().generate(...) 生成 RTG 树。
        // BOP 群系的树由其 GenerationManager 管理，因此还要检查 treeGenerationManager
        // 是否已接管该群系；被接管时同样走 getTreeDecos()。
        if ((!this.allowVanillaTrees()
                || rtg.event.EventHandlerCommon.treeGenerationManager.managingBiome(this.baseBiome()))
                && this.getTreeDecos() != null) {
            final long tTrees = System.nanoTime();
            this.getTreeDecos().generate(this, rtgWorld, rand, chunkPos, river, hasVillage, info);
            ChunkInfo.noteDeco("treeGenerator", System.nanoTime() - tTrees);
        }

        // 海洋群系把这段推迟到冰雪之后（RWG 的 rDecorateAfterIce），
        // 见 defersVanillaDecorateUntilAfterIce()。
        if (!defersVanillaDecorateUntilAfterIce()) {
            vanillaDecorate(rtgWorld, rand, chunkPos);
        }
    }

    /**
     * 原版 / BOP 的装饰本体，从 {@code rDecorate} 尾段抽出，供延迟路径复用。
     *
     * <p>海洋群系会把这一步放到冰雪之后；两条路径必须共用同一段代码，避免漂移。
     */
    default void vanillaDecorate(final RTGWorld rtgWorld, final Random rand, final ChunkPos chunkPos) {
        final long tVanilla = System.nanoTime();
        final BlockPos pos = new BlockPos(chunkPos.x * 16, 0, chunkPos.z * 16);
        try {
            if (overridesHardcoded()) {
                this.baseBiome().decorator.decorate(rtgWorld.world(), rand, baseBiome(), pos);
            } else {
                disableVanillaVegetation();
                this.baseBiome().decorate(rtgWorld.world(), rand, pos);
            }
        } finally {
            ChunkInfo.noteVanillaDecoNs(System.nanoTime() - tVanilla);
        }
    }

    /**
     * 是否把原版装饰推迟到冰雪之后（RWG 的 {@code RealisticBiomeOcean} 机制）。
     *
     * <p>海洋群系的 {@code rDecorate} 为空，装饰在 {@code rDecorateAfterIce} 中补上，
     * 否则随后铺的冰会压掉珊瑚、海草。只有混合权重 {@code > 0.3} 的海洋群系才执行。
     *
     * <p>rtgc 中只有 {@code BOPKelpForest}/{@code BOPCoralReef} 返回 true；原版海洋群系
     * 保持 {@code Biome.decorate}，因为其中还包含矿物生成。
     */
    default boolean defersVanillaDecorateUntilAfterIce() {
        return false;
    }

    /**
     * RWG {@code rDecorateAfterIce(world, rand, chunkX, chunkZ, strength)} 的等价物。
     *
     * @param chunkX 区块原点的**方块坐标**（与 RWG 的调用口径一致）
     * @param chunkZ 同上
     * @param strength 该海洋群系在本区块的混合权重；RWG 用它做 {@code > 0.3f} 的门控
     */
    default void rDecorateAfterIce(final RTGWorld rtgWorld, final Random rand,
                                   final int chunkX, final int chunkZ, final float strength) {
        // 默认什么都不做：只有推迟了装饰的群系才需要在这里补上
    }

    /** RTG 已处理对应植被时，关闭原版装饰器中的同类生成，避免重复。 */
    default void disableVanillaVegetation() {
        if (this.allowVanillaTrees()) {
            return;
        }
        BiomeDecorator decorator = this.baseBiome().decorator;
        decorator.extraTreeChance = 0f;
        decorator.treesPerChunk = 0;
        decorator.grassPerChunk = 0;
        decorator.flowersPerChunk = 0;
        decorator.deadBushPerChunk = 0;
        decorator.reedsPerChunk = 0;
        decorator.cactiPerChunk = 0;
        decorator.mushroomsPerChunk = 0;
        decorator.bigMushroomsPerChunk = 0;
        decorator.waterlilyPerChunk = 0;
        decorator.generateFalls = false;
    }

    /**
     * Some biomes have hard-coded decorations.
     * If true, RTG will call the biome decorator's decorate() method instead of the biome's decorate() method.
     */
    default boolean overridesHardcoded() { return false; }

    TerrainBase initTerrain();

    SurfaceBase initSurface();

    void initConfig();
}
