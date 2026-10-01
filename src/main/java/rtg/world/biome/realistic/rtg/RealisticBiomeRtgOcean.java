package rtg.world.biome.realistic.rtg;

import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.collection.DecoCollectionOcean;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceOcean;
import rtg.api.world.terrain.TerrainBase;


/**
 * rtgc 自己的**海洋**群系（{@link rtg.world.biome.RtgOceanBiome}）的现实主义包装。
 *
 * <h2>RWG 侧的原型：一个类，两种深度</h2>
 *
 * RWG 的海洋只有**一个**类 {@code rwg/biomes/realistic/ocean/RealisticBiomeOcean.java}，
 * 浅/深由构造参数决定（{@code RealisticBiomeOcean(BiomeGenBase, boolean shallow,
 * boolean decorateBaseBiome, String variantName)}）。本类照抄这个形状：
 * 一个类 + {@code shallow} 标志，而**不是**浅/深各写一个类。
 *
 * <h2>RWG 的海洋槽位表（{@code Support.java:156-175}），也是 rtgc 的对照表</h2>
 *
 * <pre>
 *   槽位                 RWG 用的是             rtgc 用的是
 *   oceanShallowSnow     baseOceanCold          minecraft:frozen_ocean（适配，见下）
 *   oceanShallowCold     baseOceanCold          rtgc:shallow_cold_ocean   ← 本类 shallow=true
 *   oceanShallowHot      baseOceanHot           rtgc:shallow_hot_ocean    ← 本类 shallow=true
 *   oceanShallowWet      baseOceanWet           rtgc:shallow_wet_ocean    ← 本类 shallow=true
 *   oceanDeepSnow        baseOceanCold          rtgc:deep_ice_ocean       ← 本类 shallow=false
 *   oceanDeepCold        BiomeGenBase.deepOcean minecraft:deep_ocean      （照抄，见 BiomeInit）
 *   oceanDeepHot         baseOceanHot           rtgc:deep_hot_ocean       ← 本类 shallow=false
 *   oceanDeepWet         baseOceanWet           rtgc:deep_wet_ocean       ← 本类 shallow=false
 * </pre>
 *
 * 也就是说 RWG 的 {@code baseOceanCold/Hot/Wet} **同时**被用作浅海与深海（同一个 MC 群系、
 * 两个 {@code RealisticBiomeBase} 包装，只有海底高度不同）。rtgc 的对应物就是
 * {@code RtgOceanBiome}——注册号里用 {@code deep}/{@code shallow} 区分，
 * 好让 F3 能一眼看出是哪种海底。
 *
 * <h2>与 RWG 的三处口径差异（都是 rtgc 必须的适配）</h2>
 *
 * <ol>
 *   <li><b>地形</b>：RWG 的海洋地形是 {@code height = shallow ? 52 : 34} 加噪声
 *       （rtgc 已移植为 {@code TerrainBase.terrainOcean}）。本类把它接到 {@code shallow} 上。</li>
 *   <li><b>装饰</b>：RWG 的海洋 {@code rDecorate} 是**空的**、且 {@code decorateBaseBiome = false}，
 *       等于完全不装饰。rtgc 不能照做 —— 原版 {@code Biome.decorate} 里**带着矿物生成**，
 *       关掉会连矿一起没了。故这里与 rtgc 既有的海洋群系保持一致
 *       （{@code DecoCollectionOcean} + 默认的原版装饰）。</li>
 *   <li><b>建冰</b>：RWG 的浅/深海共用 {@code baseOceanCold}（温度 0.5）⇒ RWG 的雪带海里
 *       **不结冰**。rtgc 的雪带深海用 {@code RtgOceanBiome.Kind.ICE}（温度 0.0，会结冰），
 *       因为 rtgc 的槽位是**按群系温度/降雨自动归类**的（见 {@code RtgBiomeCategorizer}），
 *       要占住 SNOW 槽位就必须是真正的严寒群系。这是口径差异，不是遗漏。</li>
 * </ol>
 */
public class RealisticBiomeRtgOcean extends RealisticBiomeBase {

    /** RWG 的 {@code shallow} 标志：{@code true} = 浅海海底 y≈52，{@code false} = 深海海底 y≈34。 */
    private final boolean shallow;

    public RealisticBiomeRtgOcean(final Biome biome, final boolean shallow) {

        super(biome);
        this.shallow = shallow;
    }

    @Override
    public void initConfig() {
        this.getConfig().SURFACE_WATER_LAKE_MULT.set(0.0f);
        this.getConfig().ALLOW_RIVERS.set(false);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
        this.getConfig().addProperty(this.getConfig().ALLOW_SPONGE).set(true);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_BLOCK).set("");
    }

    @Override
    public TerrainBase initTerrain() {
        return new TerrainRtgOcean(this.shallow);
    }

    @Override
    public SurfaceBase initSurface() {
        // RWG `RealisticBiomeOcean.rReplace`：shallow ? sand : gravel（6 格厚）
        return new SurfaceOcean(this.getConfig(), this.shallow);
    }

    @Override
    public void initDecos() {
        this.addDecoCollection(new DecoCollectionOcean(this.getConfig()));
    }

    public static class TerrainRtgOcean extends TerrainBase {

        private final boolean shallow;

        public TerrainRtgOcean(final boolean shallow) {
            this.shallow = shallow;
        }

        @Override
        public float generateNoise(final RTGWorld rtgWorld, final int x, final int y,
                                   final float border, final float river) {
            // RWG `RealisticBiomeOcean.rNoise`：height = shallow ? 52f : 34f 加两层噪声
            return terrainOcean(x, y, rtgWorld, this.shallow);
        }
    }
}
