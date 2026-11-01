package rtg.world.gen.worley;

import rtg.api.world.gen.RTGChunkGenSettings;

/**
 * WorleyCaves 的形状参数（不可变）。
 *
 * <p>字段与上游 `fluke.worleycaves.config.Configs.ConfigCaveGen` 一一对应，但**取值来源不同**：
 * 上游是模组全局 config，这里是每个世界自己的 {@link RTGChunkGenSettings}。
 * 理由：洞穴形状会改变新生成的地形，属于世界生成参数，必须随存档/`level-type` 一起走，
 * 否则同一个存档在别人的客户端上会长出另一套洞穴。
 *
 * <p>默认值不写在这里，而是由 {@link RTGChunkGenSettings.Factory} 提供（每世界可改）。
 * 相对上游做了两处观感适配：① `maxCaveHeight` 128 → 200（RTG 山地峰顶常见 150–200，
 * 沿用 128 会让"上半座山一个洞都没有"）；② `noiseCutoff` −0.18 → −0.28（洞厅更大）、
 * `surfaceCutoff`/`easeInDepth` 收紧（地表洞口更小更少）。取舍过程见
 * `docs/worley-caves-merge-feasibility.md` §6。
 */
public final class WorleyCaveParams {

    /** 洞穴规模阈值（上游 `noiseCutoffValue`）：越小洞越大。 */
    public final float noiseCutoff;
    /** 破土密度阈值（上游 `surfaceCutoffValue`）：地表附近用更严的阈值，越大越少破土。 */
    public final float surfaceCutoff;
    /** 三轴位移幅度（上游 `warpAmplifier`）：越大越"打结"，且随深度线性增强。 */
    public final float warpAmplifier;
    /** 破土抑制深度（上游 `easeInDepth`）：距地表 N×2 格内改用 `surfaceCutoff`。 */
    public final int easeInDepth;
    /** 洞穴高度上限：超过这个高度不采样（上游默认 128，本仓库用 200）。 */
    public final int maxCaveHeight;
    /** 洞穴高度下限。 */
    public final int minCaveHeight;
    /** 该高度以下的空腔填岩浆。 */
    public final int lavaDepth;
    /** 水平压缩：<1 拉伸、>1 压缩（影响洞穴的"横向尺度"）。 */
    public final float xzCompression;
    /** 垂直压缩（上游 `verticalCompressionMultiplier`）：越大洞越扁、竖井越多。 */
    public final float yCompression;
    /** 是否允许替换非原版岩石（`Material.ROCK`），对应上游 `allowReplaceMoreBlocks`。 */
    public final boolean allowReplaceMoreBlocks;

    private WorleyCaveParams(float noiseCutoff, float surfaceCutoff, float warpAmplifier, int easeInDepth,
                             int maxCaveHeight, int minCaveHeight, int lavaDepth,
                             float xzCompression, float yCompression, boolean allowReplaceMoreBlocks) {
        this.noiseCutoff = noiseCutoff;
        this.surfaceCutoff = surfaceCutoff;
        this.warpAmplifier = Math.max(0f, warpAmplifier);
        this.easeInDepth = Math.max(1, easeInDepth);
        // 上限 250 而不是 256：雕刻循环会读 localY+1 的方块，localY 最大 = maxCaveHeight-1，
        // 留 6 格余量保证 y+1 <= 255（ChunkPrimer.getBlockState 对越界 y 不做保护）。
        this.maxCaveHeight = Math.max(16, Math.min(250, maxCaveHeight));
        this.minCaveHeight = Math.max(0, Math.min(this.maxCaveHeight, minCaveHeight));
        this.lavaDepth = Math.max(0, Math.min(64, lavaDepth));
        this.xzCompression = xzCompression;
        this.yCompression = Math.max(0.1f, yCompression);
        this.allowReplaceMoreBlocks = allowReplaceMoreBlocks;
    }

    /**
     * 从世界的生成设置里取参数。
     *
     * <p>`worleyCaveSize` / `worleySurfaceCutoff` / `worleyWarpAmplifier` / `worleyMaxHeight` /
     * `worleyEaseInDepth` 是玩家可调项；两个压缩系数与 `allowReplaceMoreBlocks` 沿用上游默认，
     * 不暴露 —— 它们会把洞穴从"洞厅"变成"竖井群"，普通玩家用不上，留在 JSON 里手改即可。
     */
    public static WorleyCaveParams from(RTGChunkGenSettings settings) {
        return new WorleyCaveParams(
                settings.worleyCaveSize,
                settings.worleySurfaceCutoff,
                settings.worleyWarpAmplifier,
                settings.worleyEaseInDepth,
                settings.worleyMaxHeight,
                1,
                10,
                1.0f,
                2.0f,
                true);
    }

    @Override
    public String toString() {
        return "WorleyCaveParams{size=" + noiseCutoff + ", surface=" + surfaceCutoff
                + ", warp=" + warpAmplifier + ", easeIn=" + easeInDepth
                + ", maxY=" + maxCaveHeight + ", lavaY=" + lavaDepth + '}';
    }
}
