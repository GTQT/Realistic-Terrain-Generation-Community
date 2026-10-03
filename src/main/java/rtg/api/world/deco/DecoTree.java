package rtg.api.world.deco;

import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockLog.EnumAxis;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.DecorateBiomeEvent.Decorate;
import net.minecraftforge.fml.common.eventhandler.Event;
import rtg.RTGConfig;
import rtg.api.event.DecorateBiomeEventRTG;
import rtg.api.util.ChunkInfo;
import rtg.api.util.Distribution;
import rtg.api.util.Logger;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.IRealisticBiome;
import rtg.api.world.gen.feature.tree.rtg.TreeDensityLimiter;
import rtg.api.world.gen.feature.tree.rtg.TreeMaterials;
import rtg.api.world.gen.feature.tree.rtg.TreeRTG;

import java.util.Random;


/**
 * @author WhichOnesPink
 */
public class DecoTree extends AbstractDecoTree {

    public static final double MAX_TREE_DENSITY = 5.0D;

    /**
     * rtgc 全局降密系数（**相对上游的有意偏离**）。
     *
     * <h2>为什么要有它</h2>
     *
     * 新树系统（T2–T6）上线后，树的实际数量 ≈
     * {@code loopCount × RTGConfig.treeDensityMultiplier × 群系 TREE_DENSITY_MULTIPLIER}，
     * 而上游这两个倍率的默认都是 1.0 —— 等于**完全没有节流**。上游把 34 个群系一次性
     * 全按它自己的密度配好，实测在本包里密到卡顿。
     *
     * <h2>为什么做成代码常量、而不是改配置默认值</h2>
     *
     * 已有世界的 {@code config/RTG/rtg.cfg} 里存着旧值，{@code loadConfig()} 会覆盖
     * {@code initConfig()} 设的默认值 —— 只改默认值对已经在跑的世界**无效**。
     * 做成常量则与配置无关、立刻生效，且只有这一个旋钮（不会和配置倍率重复相乘）。
     *
     * <p>0.4 ≈ 降到 2.5 倍之一（用户要求"调低二到三倍"）。
     * 想再调只需改这一个数：0.33 ≈ 三分之一，0.5 ≈ 一半。
     */
    public static final float TREE_DENSITY_REDUCTION = 0.4f;

    protected int loops;
    protected float strengthFactorForLoops; // If set, this overrides and dynamically calculates 'loops' based on the strength parameter.
    protected boolean strengthNoiseFactorForLoops; // If true, this overrides and dynamically calculates 'loops' based on (noise * strength)
    protected boolean strengthNoiseFactorXForLoops; // If true, this overrides and dynamically calculates 'loops' based on (noise * X * strength)
    protected TreeType treeType; // Enum for the various tree presets.
    protected TreeRTG tree;
    protected WorldGenerator worldGen;
    // distribution 已上提到父类 AbstractDecoTree（上游结构），此处不再重复声明。
    protected TreeCondition treeCondition; // Enum for the various conditions/chances for tree gen.
    protected float treeConditionNoise; // Only applies to a noise-related TreeCondition.
    protected float treeConditionNoise2; // Only applies to a noise-related TreeCondition.
    protected int treeConditionChance; // Only applies to a chance-related TreeCondition.
    protected float treeConditionFloat; // Multi-purpose float.
    protected int minY; // Lower height restriction.
    protected int maxY; // Upper height restriction.
    protected IBlockState logBlock;
    protected IBlockState leavesBlock;
    // 底层 API 变动（移植上游新树系统 / T4）：上游 DecoTree 有 branchBlock（树枝方块），
    // 由 setLogBlock 派生、可被 setBranchBlock 覆盖，供树苗路径与 4 参 doGenerate 使用。
    protected IBlockState branchBlock;
    protected int minSize; // Min tree height (only used with certain tree presets)
    protected int maxSize; // Max tree height (only used with certain tree presets)
    protected int minTrunkSize; // Min tree height (only used with certain tree presets)
    protected int maxTrunkSize; // Max tree height (only used with certain tree presets)
    protected int minCrownSize; // Min tree height (only used with certain tree presets)
    protected int maxCrownSize; // Max tree height (only used with certain tree presets)
    protected boolean noLeaves;
    protected boolean leavesTweaked = false; // 只在首次generate时调整叶子属性

    public DecoTree() {

        super();

        /*
         * Default values.
         * These can be overridden when configuring the Deco object in the realistic biome.
         */
        this.setLoops(1);
        this.setStrengthFactorForLoops(0f);
        this.setStrengthNoiseFactorForLoops(false);
        this.setStrengthNoiseFactorXForLoops(false);
        this.setTreeType(TreeType.RTG_TREE);
        this.tree = null;
        this.worldGen = null;
        this.setDistribution(new Distribution(100f, 5f, 0.8f));
        this.setTreeCondition(TreeCondition.NOISE_GREATER_AND_RANDOM_CHANCE);
        this.setTreeConditionNoise(0f);
        this.setTreeConditionNoise2(0f);
        this.setTreeConditionFloat(0f);
        this.setTreeConditionChance(1);
        this.setMinY(63); // No underwater trees by default.
        this.setMaxY(230); // Sensible upper height limit by default.
        this.setLogBlock(Blocks.LOG.getDefaultState());
        this.setLeavesBlock(Blocks.LEAVES.getDefaultState());
        this.setMinSize(2);
        this.setMaxSize(4);
        this.setMinTrunkSize(2);
        this.setMaxTrunkSize(4);
        this.setMinCrownSize(2);
        this.setMaxCrownSize(4);
        this.setNoLeaves(false);
    }

    public DecoTree(DecoTree source) {

        this();
        this.setLoops(source.loops);
        this.setStrengthFactorForLoops(source.strengthFactorForLoops);
        this.setStrengthNoiseFactorForLoops(source.strengthNoiseFactorForLoops);
        this.setStrengthNoiseFactorXForLoops(source.strengthNoiseFactorXForLoops);
        this.setTreeType(source.treeType);
        this.tree = source.tree;
        this.worldGen = source.worldGen;
        this.setDistribution(source.distribution);
        this.setTreeCondition(source.treeCondition);
        this.setTreeConditionNoise(source.treeConditionNoise);
        this.setTreeConditionNoise2(source.treeConditionNoise2);
        this.setTreeConditionFloat(source.treeConditionFloat);
        this.setTreeConditionChance(source.treeConditionChance);
        this.setMinY(source.minY);
        this.setMaxY(source.maxY);
        this.setLogBlock(source.logBlock);
        this.setLeavesBlock(source.leavesBlock);
        this.setMinSize(source.minSize);
        this.setMaxSize(source.maxSize);
        this.setMinTrunkSize(source.minTrunkSize);
        this.setMaxTrunkSize(source.maxTrunkSize);
        this.setMinCrownSize(source.minCrownSize);
        this.setMaxCrownSize(source.maxCrownSize);
        this.setNoLeaves(source.noLeaves);
    }

    public DecoTree(TreeRTG tree) {

        this();
        this.tree = tree;
        this.setLogBlock(tree.getLogBlock());
        this.setLeavesBlock(tree.getLeavesBlock());
        this.setMinTrunkSize(tree.getMinTrunkSize());
        this.setMaxTrunkSize(tree.getMaxTrunkSize());
        this.setMinCrownSize(tree.getMinCrownSize());
        this.setMaxCrownSize(tree.getMaxCrownSize());
        this.setNoLeaves(tree.getNoLeaves());
    }

    public DecoTree(WorldGenerator worldGen) {

        this();
        this.worldGen = worldGen;
    }

    // TODO: [1.12] Both `tree` and `worldGen` are WorldGenerators so there is no reason to treat them differently.
    //              All RTG-specific aspects of the TreeRTG WorldGenerator should be passed at the time of object
    //              creation and those objects should *only* be created at the time of generation instead of this
    //              class hanging on to a single object that gets reused. Choosing which generator to use can simply
    //              be done by checking the TreeType. This change would negate the need for this confusing check.
    @Override
    @Deprecated
    public boolean properlyDefined() {

        if (this.treeType == TreeType.RTG_TREE) {
            if (this.tree == null) {
                return false;
            }
        }
        return super.properlyDefined();
    }

    @Override
    public void generate(final IRealisticBiome biome, final RTGWorld rtgWorld, final Random rand, final ChunkPos chunkPos, final float river, final boolean hasVillage, ChunkInfo chunkInfo) {

        // ====== 早期退出：密度乘数接近0时直接跳过，避免无效开销 ======
        if (RTGConfig.treeDensityMultiplier() <= 0.001 || biome.getConfig().TREE_DENSITY_MULTIPLIER.get() <= 0.001) {
            return;
        }

        final BlockPos offsetPos = getOffsetPos(chunkPos);
        final World world = rtgWorld.world();

        float noise = distribution.getValue(offsetPos, rtgWorld.treeDistributionNoise());

        // Cascading priority (matching Master's last-wins behavior):
        // strengthNoiseFactorXForLoops > strengthNoiseFactorForLoops > strengthFactorForLoops > loops
        int loopCount = this.loops;
        if (this.strengthFactorForLoops > 0f) {
            loopCount = (int) this.strengthFactorForLoops;
        }
        if (this.strengthNoiseFactorForLoops) {
            loopCount = (int) noise;
        }
        if (this.strengthNoiseFactorXForLoops) {
            loopCount = (int) (noise * this.strengthFactorForLoops);
        }

        if (loopCount < 1) {
            return;
        }

        loopCount = this.applyConfigMultipliers(loopCount, biome);
        if (loopCount < 1) {
            return;
        }

        /*
         * Since RTG posts a TREE event for each batch of trees it tries to generate (instead of one event per chunk),
         * we post this custom event so that we can pass the number of trees RTG expects to generate in each batch.
         *
         * This provides more contextual information to mods like Recurrent Complex, which can use the info to better
         * determine how to handle each batch of trees.
         *
         * Because the custom event extends DecorateBiomeEvent.Decorate, it still works with mods that don't need
         * the additional context.
         */
        //TODO [1.12] Trees should just generate how they do in the vanilla BiomeDecorator::genDecorations and use the Forge event.
        DecorateBiomeEventRTG.DecorateRTG event = new DecorateBiomeEventRTG.DecorateRTG(world, rand, offsetPos, Decorate.EventType.TREE, loopCount);
        MinecraftForge.TERRAIN_GEN_BUS.post(event);

        if (event.getResult() != Event.Result.DENY) {
            loopCount = event.getModifiedAmount();
            if (loopCount < 1) { return; }

            // ====== 只在首次generate时调整叶子属性，避免每区块重复withProperty() ======
            if (!leavesTweaked) {
                DecoBase.tweakTreeLeaves(this, false, true);
                leavesTweaked = true;
            }

            if (hasVillage) {
                return;
            }

            final int minY = this.minY;
            final int maxY = this.maxY;

            final int chunkX = chunkPos.x * 16;
            final int chunkZ = chunkPos.z * 16;

            final int randRange = 16;

            // ====== 批量预检：先用缓存高度筛选有效位置，再生成树 ======
            // 使用原始类型数组，零GC压力
            int[] xs = new int[loopCount];
            int[] zs = new int[loopCount];
            int[] ys = new int[loopCount];
            int validCount = 0;
            for (int attempts = 0; attempts < loopCount * 2 && validCount < loopCount; attempts++) {
                int x = chunkX + rand.nextInt(randRange);
                int z = chunkZ + rand.nextInt(randRange);
                int y = chunkInfo.getHeight(x, z);
                if (y > maxY || y < minY) continue;
                if (!isValidTreeCondition(noise, rand)) continue;
                xs[validCount] = x;
                zs[validCount] = z;
                ys[validCount] = y;
                validCount++;
            }
            for (int i = 0; i < validCount; i++) {
                doGenerate(rand, rtgWorld, chunkInfo, new BlockPos(xs[i], 0, zs[i]), ys[i]);
            }
        }
        else if (RTGConfig.enableDebugging()) {
            Logger.debug("Tree generation was cancelled @ ChunkPos{}", chunkPos);
        }
    }

    public float doGenerate(Random rand, RTGWorld rtgWorld, ChunkInfo chunkInfo, BlockPos pos, int y) {
        // separated out for variable trees; but they later needed more changes so this may not be necessary.
        switch (this.treeType) {

            case RTG_TREE:

                //this.setLogBlock(strength < 0.2f ? BlockUtil.getStateLog(2) : this.logBlock);

                this.tree.setLogBlock(this.logBlock);
                this.tree.setLeavesBlock(this.leavesBlock);
                this.tree.setTrunkSize(getRangedRandom(rand, this.minTrunkSize, this.maxTrunkSize));
                this.tree.setCrownSize(getRangedRandom(rand, this.minCrownSize, this.maxCrownSize));
                this.tree.setNoLeaves(this.noLeaves);
                this.tree.generate(rtgWorld.world(), rand, pos.up(y));

                break;

            case WORLDGEN:

                WorldGenerator worldgenerator = this.worldGen;
                worldgenerator.generate(rtgWorld.world(), rand, pos.up(y));

                break;

            default:
                break;
        }
        return 1f;
    }

    public boolean isValidTreeCondition(float noise, Random rand) {

        boolean noiseGreaterThanMin;
        boolean noiseLessThanMax;
        boolean randomResult;
        boolean valid;

        switch (this.treeCondition) {
            case ALWAYS_GENERATE:
                return true;

            case NOISE_GREATER_AND_RANDOM_CHANCE:
                return (noise > this.treeConditionNoise && rand.nextInt(this.treeConditionChance) == 0);

            case NOISE_LESSER_AND_RANDOM_CHANCE:
                return (noise < this.treeConditionNoise && rand.nextInt(this.treeConditionChance) == 0);

            case NOISE_BETWEEN_AND_RANDOM_CHANCE:
                noiseGreaterThanMin = noise >= this.treeConditionNoise;
                noiseLessThanMax = noise <= this.treeConditionNoise2;
                randomResult = rand.nextInt(this.treeConditionChance) == 0;
                return (noiseGreaterThanMin && noiseLessThanMax && randomResult);

            case RANDOM_CHANCE:
                return rand.nextInt(this.treeConditionChance) == 0;

            case RANDOM_NOT_EQUALS_CHANCE:
                return rand.nextInt(this.treeConditionChance) != 0;

            case DISTRIBUTION_GIVES_CHANCE:

                return rand.nextFloat() < noise;

            default:
                return false;
        }
    }

    public int getLoops() {

        return loops;
    }

    public DecoTree setLoops(int loops) {

        this.loops = loops;
        return this;
    }

    public float getStrengthFactorForLoops() {

        return strengthFactorForLoops;
    }

    public DecoTree setStrengthFactorForLoops(float strengthFactorForLoops) {

        this.strengthFactorForLoops = strengthFactorForLoops;
        return this;
    }

    public boolean isStrengthNoiseFactorForLoops() {

        return strengthNoiseFactorForLoops;
    }

    public DecoTree setStrengthNoiseFactorForLoops(boolean strengthNoiseFactorForLoops) {

        this.strengthNoiseFactorForLoops = strengthNoiseFactorForLoops;
        return this;
    }

    public boolean isStrengthNoiseFactorXForLoops() {

        return strengthNoiseFactorXForLoops;
    }

    public DecoTree setStrengthNoiseFactorXForLoops(boolean strengthNoiseFactorXForLoops) {

        this.strengthNoiseFactorXForLoops = strengthNoiseFactorXForLoops;
        return this;
    }

    public TreeType getTreeType() {

        return treeType;
    }

    public DecoTree setTreeType(TreeType treeType) {

        this.treeType = treeType;
        return this;
    }

    public TreeRTG getTree() {

        return tree;
    }

    public DecoTree setTree(TreeRTG tree) {

        this.tree = tree;
        return this;
    }

    public WorldGenerator getWorldGen() {

        return worldGen;
    }

    public DecoTree setWorldGen(WorldGenerator worldGen) {

        this.worldGen = worldGen;
        return this;
    }

    // distribution 本体与 getDistribution 已上提到父类 AbstractDecoTree（上游结构）。
    // 这里只做一次**协变覆盖**：把返回类型收窄为 DecoTree，否则
    // `new DecoTree(..).setDistribution(..).setTreeCondition(..)` 这类链式调用会断
    // （父类版本返回 AbstractDecoTree，没有 setTreeCondition）。
    @Override
    public DecoTree setDistribution(Distribution distribution) {
        super.setDistribution(distribution);
        return this;
    }

    public TreeCondition getTreeCondition() {

        return treeCondition;
    }

    public DecoTree setTreeCondition(TreeCondition treeCondition) {

        this.treeCondition = treeCondition;
        return this;
    }

    public float getTreeConditionNoise() {

        return treeConditionNoise;
    }

    public DecoTree setTreeConditionNoise(float treeConditionNoise) {

        this.treeConditionNoise = treeConditionNoise;
        return this;
    }

    public float getTreeConditionNoise2() {

        return treeConditionNoise2;
    }

    public DecoTree setTreeConditionNoise2(float treeConditionNoise2) {

        this.treeConditionNoise2 = treeConditionNoise2;
        return this;
    }

    public int getTreeConditionChance() {

        return treeConditionChance;
    }

    public DecoTree setTreeConditionChance(int treeConditionChance) {

        this.treeConditionChance = treeConditionChance;
        return this;
    }

    public float getTreeConditionFloat() {

        return treeConditionFloat;
    }

    public DecoTree setTreeConditionFloat(float treeConditionFloat) {

        this.treeConditionFloat = treeConditionFloat;
        return this;
    }

    public int getMinY() {

        return minY;
    }

    public DecoTree setMinY(int minY) {

        this.minY = minY;
        return this;
    }

    public int getMaxY() {

        return maxY;
    }

    public DecoTree setMaxY(int maxY) {

        this.maxY = maxY;
        return this;
    }

    public IBlockState getLogBlock() {

        return logBlock;
    }

    public DecoTree setLogBlock(IBlockState logBlock) {

        this.logBlock = logBlock;
        // 底层 API 变动（移植上游新树系统 / T4）：上游在 setLogBlock 里顺带派生 branchBlock
        // （把原木的 LOG_AXIS 归零作为"树枝"方块），供树苗路径与 doGenerate 使用。
        // 逐行照抄上游 DecoTree.java:417-423。
        try {
            this.branchBlock = logBlock.withProperty(BlockLog.LOG_AXIS, EnumAxis.NONE);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            //e.printStackTrace();
        }
        if (branchBlock == null) {
            branchBlock = logBlock;
        }
        return this;
    }

    public DecoTree setBranchBlock(IBlockState branchBlock) {

        this.branchBlock = branchBlock;
        return this;
    }

    /**
     * 底层 API 变动（移植上游新树系统 / T4）：上游 {@code DecoTree} 有这个方法，
     * 供 {@code RTGSaplingAction} 一次性灌入原木/树枝/树叶三件套。逐行照抄上游。
     */
    public void setMaterials(TreeMaterials materials) {
        setLogBlock(materials.log);
        setBranchBlock(materials.branches);
        setLeavesBlock(materials.leaves);
    }

    public IBlockState getLeavesBlock() {

        return leavesBlock;
    }

    public DecoTree setLeavesBlock(IBlockState leavesBlock) {

        this.leavesBlock = leavesBlock;
        return this;
    }

    public int getMinSize() {

        return minSize;
    }

    public DecoTree setMinSize(int minSize) {

        this.minSize = minSize;
        return this;
    }

    public int getMaxSize() {

        return maxSize;
    }

    public DecoTree setMaxSize(int maxSize) {

        this.maxSize = maxSize;
        return this;
    }

    public int getMinTrunkSize() {

        return minTrunkSize;
    }

    public DecoTree setMinTrunkSize(int minTrunkSize) {

        this.minTrunkSize = minTrunkSize;
        return this;
    }

    public int getMaxTrunkSize() {

        return maxTrunkSize;
    }

    public DecoTree setMaxTrunkSize(int maxTrunkSize) {

        this.maxTrunkSize = maxTrunkSize;
        return this;
    }

    public int getMinCrownSize() {

        return minCrownSize;
    }

    public DecoTree setMinCrownSize(int minCrownSize) {

        this.minCrownSize = minCrownSize;
        return this;
    }

    public int getMaxCrownSize() {

        return maxCrownSize;
    }

    public DecoTree setMaxCrownSize(int maxCrownSize) {

        this.maxCrownSize = maxCrownSize;
        return this;
    }

    public boolean isNoLeaves() {

        return noLeaves;
    }

    public DecoTree setNoLeaves(boolean noLeaves) {

        this.noLeaves = noLeaves;
        return this;
    }

    public enum TreeType {
        RTG_TREE,
        WORLDGEN
    }

    public enum TreeCondition {
        ALWAYS_GENERATE,
        NOISE_GREATER_AND_RANDOM_CHANCE,
        NOISE_LESSER_AND_RANDOM_CHANCE,
        NOISE_BETWEEN_AND_RANDOM_CHANCE,
        RANDOM_CHANCE,
        RANDOM_NOT_EQUALS_CHANCE,
        DISTRIBUTION_GIVES_CHANCE
    }

    /**
     * 偏离上游（有意）：{@code applyConfigMultipliers(int, ...)} 上游已上提到
     * {@code AbstractDecoTree}，而那个版本**丢掉了 MAX_TREE_DENSITY 钳制**。
     *
     * <p>但本仓库的配置文案（{@code RTGConfig.treeDensityMultiplier} 的注释）明确承诺
     * "The combination of this value and the biome-specific value will never exceed 5.0"。
     * 树的数量走的正是这个 int 重载，放任不管会让该承诺失效（玩家把倍率调高就能突破上限）。
     * 判定为上游疏漏，故在此覆盖回钳制版本，并额外乘上 {@link #TREE_DENSITY_REDUCTION}。
     */
    @Override
    protected int applyConfigMultipliers(final int loopCount, final IRealisticBiome biome) {
        return (int)(loopCount * TREE_DENSITY_REDUCTION
                * Math.min(RTGConfig.treeDensityMultiplier() * biome.getConfig().TREE_DENSITY_MULTIPLIER.get(), MAX_TREE_DENSITY));
    }

    protected float applyConfigMultipliers(final float trees, final IRealisticBiome biome) {
        return (float)(trees * TREE_DENSITY_REDUCTION
                * Math.min(RTGConfig.treeDensityMultiplier() * biome.getConfig().TREE_DENSITY_MULTIPLIER.get(), MAX_TREE_DENSITY));
    }

    /**
     * 底层 API 变动（移植上游新树系统 / T3）：上游 {@code DecoTree} 有这个方法，
     * {@code DecoTreeSet.doVariableGenerate} 会对选中的 DecoTree 调用它。
     * 逐行照抄上游 {@code DecoTree.java:542-551}。
     */
    public void doVariableGenerate(Random rand, ChunkInfo chunkInfo, BlockPos column, int y, TreeDensityLimiter treesRemaining) {
        // this is here to allow combining trees together for sharing a forest density and allowing variable tree sizes
        this.tree.setLogBlock(this.logBlock);
        this.tree.setBranchBlock(this.branchBlock);
        this.tree.setLeavesBlock(this.leavesBlock);
        this.tree.setTrunkSize(getRangedRandom(rand, this.minTrunkSize, this.maxTrunkSize));
        this.tree.setCrownSize(getRangedRandom(rand, this.minCrownSize, this.maxCrownSize));
        this.tree.setNoLeaves(this.noLeaves);
        tree.doVariableGenerate(rand, chunkInfo, column, y, treesRemaining);
    }

    /**
     * 底层 API 变动（移植上游新树系统 / T4）：上游 {@code DecoTree} 多了这个 4 参重载，
     * 供 {@code RTGSaplingManager} 的树苗路径直接调用（不经区块装饰循环）。
     * 逐行照抄上游 {@code DecoTree.java:553-560}。
     */
    public boolean doGenerate(World world, Random rand, BlockPos pos, int actualHeight) {
        this.tree.setLogBlock(this.logBlock);
        this.tree.setBranchBlock(this.branchBlock);
        this.tree.setLeavesBlock(this.leavesBlock);
        this.tree.setNoLeaves(this.noLeaves);
        this.tree.setTreeSize(actualHeight, rand);
        return tree.generate(world, rand, pos);
    }
}