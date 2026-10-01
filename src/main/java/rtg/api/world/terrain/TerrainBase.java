package rtg.api.world.terrain;

import net.minecraft.block.BlockSnow;
import net.minecraft.init.Blocks;
import net.minecraft.world.chunk.ChunkPrimer;
import rtg.api.util.noise.CellularNoise;
import rtg.api.util.noise.SimplexNoise;
import rtg.api.util.noise.SpacedCellularNoise;
import rtg.api.util.noise.VoronoiResult;
import rtg.api.world.RTGWorld;
import rtg.api.world.WaterLevel;


@SuppressWarnings("WeakerAccess")
public abstract class TerrainBase {

    private static final float minimumOceanFloor = 20.01f; // The lowest Y coord an ocean floor is allowed to be.
    private static final float minimumDuneHeight = 21f; // The strength factor to which the dune height config option is added.
    // Pre-calculated inverse values for common divisors
    private static final float INV_49 = 1f / 49f;
    private static final float INV_23 = 1f / 23f;
    private static final float INV_11 = 1f / 11f;
    private static final float INV_150 = 1f / 150f;
    private static final float INV_55 = 1f / 55f;
    private static final float INV_100 = 1f / 100f;
    private static final float INV_300 = 1f / 300f;
    private static final float INV_50 = 1f / 50f;
    private static final float INV_15 = 1f / 15f;
    private static final float INV_30 = 1f / 30f;
    private static final float INV_20 = 1f / 20f;
    private static final float INV_7 = 1f / 7f;
    private static final float INV_5 = 1f / 5f;
    private static final float INV_12 = 1f / 12f;
    private static final float INV_18 = 1f / 18f;
    private static final float INV_8 = 1f / 8f;
    private static final float INV_40 = 1f / 40f;
    private static final float INV_25 = 1f / 25f;
    private static final float INV_70 = 1f / 70f;
    private static final float INV_230 = 1f / 230f;
    private static final float INV_180 = 1f / 180f;
    private static final float INV_130 = 1f / 130f;
    private static final float INV_64 = 1f / 64f;
    private static final float INV_240 = 1f / 240f;
    private static final float INV_80 = 1f / 80f;
    // Additional inverses for RWG-grand terrain functions
    private static final float INV_35 = 1f / 35f;
    private static final float INV_60 = 1f / 60f;
    private static final float INV_28 = 1f / 28f;
    private static final float INV_14 = 1f / 14f;
    private static final float INV_260 = 1f / 260f;
    private static final float INV_32 = 1f / 32f;
    private static final float INV_200 = 1f / 200f;
    private static final float INV_120 = 1f / 120f;
    // Pre-calculated constants
    private static final float BLENDED_HILL_NORMALIZATION = 1f / 0.45f;
    private static final float BLENDED_HILL_OFFSET = 4.5f;
    protected final float minDuneHeight; // The strength factor to which the dune height config option is added.
    protected final float groundNoiseAmplitudeHills;
    protected final float groundVariation;
    protected final float rollingHillsMaxHeight;
    protected float base; // added as most terrains have this;
    protected float groundNoise;

    public TerrainBase() {

        this(68f);// default to marginally above sea level;
    }

    public TerrainBase(float base) {

        this.base = base;
        this.minDuneHeight = minimumDuneHeight;
        this.groundVariation = 2f;
        this.groundNoise = this.base;
        this.groundNoiseAmplitudeHills = 6f;
        this.rollingHillsMaxHeight = 80f;
    }

    public static float blendedHillHeight(float simplex) {
        // this takes a simplex supposed to vary from -1 to 1
        // and produces an output which varies from 0 to 1 non-linearly
        // with the value of 0 mapped to about 0.15 and smooth transition
        // the purpose is to make hills above plains without significant deadvalleys
        float result = simplex + 1;
        result = result * result * result + 10;
        result = (float) Math.pow(result, .33333333333333);
        result = result * BLENDED_HILL_NORMALIZATION;
        result = result - BLENDED_HILL_OFFSET;
        return result;
    }

    public static float blendedHillHeight(float simplex, float turnAt) {
        // like blendedHillHeight, but the effect of zero occurs at the turnAt parameter instead
        float oneMinusTurnAt = 1f - turnAt;
        float adjusted = (1f - (1f - simplex) / oneMinusTurnAt);
        return blendedHillHeight(adjusted);
    }

    public static float groundNoise(int x, int y, float amplitude, RTGWorld rtgWorld) {

        SimplexNoise simplex0 = rtgWorld.simplexInstance(0);
        SimplexNoise simplex1 = rtgWorld.simplexInstance(1);
        SimplexNoise simplex2 = rtgWorld.simplexInstance(2);

        float h = blendedHillHeight(simplex0.noise2f(x * INV_49, y * INV_49), 0.2f) * amplitude;
        h += blendedHillHeight(simplex1.noise2f(x * INV_23, y * INV_23), 0.2f) * amplitude * 0.5f; // /2
        h += blendedHillHeight(simplex2.noise2f(x * INV_11, y * INV_11), 0.2f) * amplitude * 0.25f; // /4
        return h;
    }

    public static float groundNoise(float x, float y, float amplitude, RTGWorld rtgWorld) {

        SimplexNoise simplex0 = rtgWorld.simplexInstance(0);
        SimplexNoise simplex1 = rtgWorld.simplexInstance(1);
        SimplexNoise simplex2 = rtgWorld.simplexInstance(2);

        float h = blendedHillHeight(simplex0.noise2f(x * INV_49, y * INV_49), 0.2f) * amplitude;
        h += blendedHillHeight(simplex1.noise2f(x * INV_23, y * INV_23), 0.2f) * amplitude * 0.5f; // /2
        h += blendedHillHeight(simplex2.noise2f(x * INV_11, y * INV_11), 0.2f) * amplitude * 0.25f; // /4
        return h;
    }

    public static float getTerrainBase() {

        return 68f;
    }

    public static float getTerrainBase(float river) {

        return 62f + 6f * river;
    }

    /**
     * 峡谷阶地（RWG {@code rwg/terrain/TerrainCanyon.java}）。
     * <p>
     * 调用者：{@code RealisticBiomeBOPCrag}（BOP 的 crag 即 RWG 的 canyon 对应群系，
     * RWG 侧见 {@code SupportBOP.java:157-165}：{@code new TerrainCanyon(true, 35f, 160f, 60f, 40f, 69f)}）。
     * <p>
     * <b>本方法曾是一份"有偏差的死副本"</b>：唯一会用到峡谷的群系把 RWG 原逻辑**整段内联**在
     * {@code RealisticBiomeBOPCrag.TerrainBOPCrag} 里，而这里的静态版本有 0 调用者，且与内联版
     * 有三处不一致（见下）。现已把本方法改为**与 RWG 原文逐行一致**，并让 BOPCrag 调用它，
     * 从而消除重复代码、同时保证 BOPCrag 的世界生成结果**不变**。
     * <p>
     * 修正的三处偏差：
     * <ol>
     *   <li>缺少 {@code river *= 1.3f} 及其 {@code min(river, 1f)} 钳制；</li>
     *   <li>误给 {@code r} 与 {@code sb} 额外乘了 {@code river}（RWG 原文只对 {@code (17+r)} 乘）；</li>
     *   <li>返回 {@code getTerrainBase(river) + b}（= 62 + 6·river）而非 RWG 的常量基高参数
     *       {@code base + b}（RWG 传 69）。低流速处相差可达 7 格。</li>
     * </ol>
     *
     * @param height     阶地高度表，形如 {@code {5, 0.5, 12.5, 0.5, 18, 0.5}}（高度, 平台厚度, …）
     * @param strength   阶地抬升倍率（RWG 默认 35）
     * @param baseHeight 基准高度（RWG 默认 69；不再是由 river 推导的值）
     */
    public static float terrainCanyon(int x, int y, RTGWorld rtgWorld, float river, float[] height, float border, float strength, int heightLength, boolean booRiver, float baseHeight) {
        SimplexNoise simplex = rtgWorld.simplexInstance(0);

        // RWG: river *= 1.3f; river = river > 1f ? 1f : river;
        river *= 1.3f;
        river = river > 1f ? 1f : river;

        float r = simplex.noise2f(x * INV_100, y * INV_100) * 50f;
        r = r < -7.4f ? -7.4f : Math.min(r, 7.4f);
        float b = (17f + r) * river;

        float hn = simplex.noise2f(x * INV_12, y * INV_12) * 0.5f;
        float sb = 0f;
        if (b > 0f) {
            sb = Math.min(b, 7f);
            sb = hn * sb;
        }
        b += sb;

        float cTotal = 0f;
        float cTemp;

        for (int i = 0; i < heightLength; i += 2) {
            cTemp = 0;
            if (b > height[i] && border > 0.6f + (height[i] * 0.015f) + hn * 0.2f) {
                cTemp = b > height[i] + height[i + 1] ? height[i + 1] : b - height[i];
                cTemp *= strength;
            }
            cTotal += cTemp;
        }

        float bn = 0f;
        if (booRiver) {
            if (b < 5f) {
                bn = 5f - b;
                for (int i = 0; i < 3; i++) {
                    bn *= bn / 4.5f;
                }
            }
        } else if (b < 5f) {
            bn = (simplex.noise2f(x * INV_7, y * INV_7) * 1.3f + simplex.noise2f(x * INV_15, y * INV_15) * 2f) * (5f - b) * 0.2f;
        }

        b += cTotal - bn;

        return baseHeight + b;
    }

    /**
     * 平原（RWG {@code TerrainGrasslandFlats.java} 的逐行移植）。
     * <p>
     * <b>无参数</b>：RWG 原版就是硬编码 {@code m *= m / 40f} 与 {@code return 68f + h + m - l}。
     * rtgc 移植时多出了 {@code mPitch} / {@code baseHeight} 两个形参 —— 已去掉。
     * 留着形参就等于"允许用非 RWG 的值调用"，那正是偏离的入口。
     */
    public static float terrainGrasslandFlats(int x, int y, RTGWorld rtgWorld, float river) {

        final SimplexNoise simplex = rtgWorld.simplexInstance(0);

        float h = simplex.noise2f(x / 100f, y / 100f) * 7;
        h += simplex.noise2f(x / 20f, y / 20f) * 2;

        float m = simplex.noise2f(x / 180f, y / 180f) * 70f * river;
        m *= m / 40f;

        float sm = simplex.noise2f(x / 30f, y / 30f) * 8f;
        sm *= m / 20f > 3.75f ? 3.75f : m / 20f;
        m += sm;

        float l = simplex.noise2f(x / 260f, y / 260f) * 38f;
        l *= l / 25f;
        l = l < -8f ? -8f : l;

        return 68f + h + m - l;
    }

    /**
     * 高地（RWG {@code rwg/terrain/TerrainHighland.java} 的**忠实**移植）。
     * <p>
     * 参数与 RWG 构造参数一一对应（<b>注意顺序</b>）：
     * {@code TerrainHighland(hillStart, landHeight, baseHeight, hillWidth)}
     * → {@code terrainHighland(x, y, rtgWorld, river, hillStart, landHeight, baseHeight, hillWidth)}。
     * <p>
     * Team-RTG 移植时曾另有一个改动版 {@code terrainHighlandLegacy}（多一层 {@code * river}、基高改为
     * {@code getTerrainBase(river)}、参数顺序也不同）。原先按它的语义调参的 8 个群系已全部迁到本方法，
     * 该改动版因此**已删除**——现在全仓只有本方法这一个高地实现。
     *
     * @param hillStart  山丘起始高度（RWG {@code hillStart}）
     * @param landHeight 山丘幅度（RWG {@code landHeight}）
     * @param baseHeight 基准高度（RWG {@code baseHeight}，返回值即 {@code baseHeight + h}）
     * @param hillWidth  山丘波长（RWG {@code hillWidth}）
     */
    public static float terrainHighland(float x, float y, RTGWorld rtgWorld, float river,
                                        float hillStart, float landHeight, float baseHeight, float hillWidth) {
        return terrainHighland(x, y, rtgWorld, river, hillStart, landHeight, baseHeight, hillWidth, 1f);
    }

    /**
     * 同上，带 RWG 的第 5 个构造参数 {@code flatDetailStrength}（默认 1f）。
     * <p>
     * 值越小，平坦区域的细节噪声越弱 —— RWG 的 {@code grove} 群系用 {@code .3f}。
     */
    public static float terrainHighland(float x, float y, RTGWorld rtgWorld, float river,
                                        float hillStart, float landHeight, float baseHeight, float hillWidth,
                                        float flatDetailStrength) {
        final SimplexNoise simplex = rtgWorld.simplexInstance(0);

        float h = simplex.noise2f(x / hillWidth, y / hillWidth) * landHeight * river;
        h = h < hillStart ? hillStart + ((h - hillStart) / 4.5f) : h;

        if (h > 0f) {
            final float st = Math.min(h * 1.5f, 15f);
            // RWG: cell.noise(x / 70D, y / 70D, 1D) —— 用 cellDistance 做 RWG 等价的线性距离换算（C-5）
            h += cellDistance(rtgWorld.cellularInstance(0).eval2D(x / 70D, y / 70D)) * st;
        }

        final float hillDetail = Math.max(0f, Math.min(1f, (h - hillStart) / 15f));
        final float detailStrength = flatDetailStrength + (1f - flatDetailStrength) * hillDetail;
        h += simplex.noise2f(x * INV_20, y * INV_20) * 5f * detailStrength;
        h += simplex.noise2f(x * INV_12, y * INV_12) * 3f * detailStrength;
        h += simplex.noise2f(x * INV_5, y * INV_5) * 1.5f * detailStrength;

        return baseHeight + h;
    }

    /**
     * 沼泽（RWG {@code rwg/terrain/TerrainMarsh.java} 的逐行移植）。
     * <p>
     * 本函数此前有 <b>3 处数值偏离</b>，把沼泽压得几乎全平（起伏只剩 RWG 的约 1/3），现已拨回：
     * <ul>
     *   <li>主噪声幅度 {@code 20f} → RWG 的 {@code 30f}（TerrainMarsh.java:13）</li>
     *   <li>死区阈值 {@code 8f} → RWG 的 {@code 4f}（:18）</li>
     *   <li>平坦分支多出的 {@code h *= 2f} → 删除（RWG :21-22 无此乘子）</li>
     * </ul>
     * RWG 本体是 {@code return 62f + h;}（不走任何河道后处理）。此处保留 {@code baseHeight}
     * 形参以便跟随可配置水位（F-41）。
     *
     * <p><b>⚠ 调用方必须传 {@link WaterLevel#waterSurfaceTop()}（默认 63 ⇒ 62，与 RWG 逐位相同），
     * 而不是 {@code riverSurface()}</b>。RWG 的 62 就是它的**水面顶**
     * （{@code ChunkGeneratorRealistic.generateTerrain:292}：{@code if (k < 63) blocks[p] = Blocks.water}
     * ⇒ 水占 {@code y ≤ 62}），而 {@code riverSurface()} 是 {@code seaLevel - 1.5} = 61.5，
     * 会让整个沼泽族**整体低 0.5 格**。
     * 此前 10 个调用点全部传错，已于 1.0.18 全部改正。
     *
     * <p>原先外层的 {@code riverized(...)} 包裹（RTG 时代遗产）已随 WP-5 删除。
     */
    public static float terrainMarsh(int x, int y, RTGWorld rtgWorld, float baseHeight, float river) {

        final SimplexNoise simplex = rtgWorld.simplexInstance(0);
        float h = simplex.noise2f(x * INV_130, y * INV_130) * 30f;

        h += simplex.noise2f(x * INV_12, y * INV_12) * 2f;
        h += simplex.noise2f(x * INV_18, y * INV_18) * 4f;

        h = h < 4f ? 0f : h - 4f;

        if (h == 0f) {
            h += simplex.noise2f(x * INV_20, y * INV_20) + simplex.noise2f(x * INV_5, y * INV_5);
        }

        return baseHeight + h;
    }

    /**
     * {@link #terrainPolar(int, int, RTGWorld, float)} 的**参数化**版本 —— 这是 rtgc 的偏离。
     *
     * <p>RWG 的 {@code TerrainPolar} 没有参数；这里把它的 5 个字面量
     * （{@code 160f / 35f / 60f / 50f / 70f}）提成形参，以便
     * {@code RealisticBiomeVanillaDesert} 传入自己的沙丘参数。
     *
     * <p>其余算式与 RWG 逐字一致；{@code st} 的下限也已与忠实重载统一为 <b>0.2f</b>
     *（此前泛化版写的是 0.1f，两版不一致）。
     */
    public static float terrainPolar(float x, float y, RTGWorld rtgWorld, float river,
                                     float stPitch, float stFactor, float hPitch, float hDivisor,
                                     float baseHeight) {

        float invStPitch = 1f / stPitch;
        float invHPitch = 1f / hPitch;
        float invHDivisor = 1f / hDivisor;

        SimplexNoise simplex = rtgWorld.simplexInstance(0);
        float floNoise;
        float st = (simplex.noise2f(x * invStPitch, y * invStPitch) + 0.38f) * stFactor * river;
        // A2：RWG 是 0.2f。rtgc 原先写 0.1f —— 这个下限直接决定沙丘脊线的厚度。
        st = Math.max(st, 0.2f);

        float h = simplex.noise2f(x * invHPitch, y * invHPitch) * st * 2f;
        h = h > 0f ? -h : h;
        h += st;
        h *= h * invHDivisor;
        h += st;

        floNoise = baseHeight + h;
        return floNoise;
    }

    // ====================================================================
    // RWG-GRAND TERRAIN FUNCTIONS
    // Ported from Realistic World Gen (ted80) — produces dramatic,
    // high-contrast terrain with sharp peaks and deep valleys.
    // ====================================================================

    /**
     * RWG original — produces sharp isolated mountain peaks up to ~217.
     * Uses quadratic height amplification (h²/32) for dramatic contrast.
     */
    public static float terrainMountain(int x, int y, RTGWorld rtgWorld, float river) {

        SimplexNoise simplex = rtgWorld.simplexInstance(0);
        CellularNoise cell = rtgWorld.cellularInstance(0);

        float h = simplex.noise2f(x * INV_300, y * INV_300) * 135f * river;
        h *= h * INV_32;  // quadratic amplification — small values collapse, large values explode
        h = Math.min(h, 150f);

        if (h > 10f) {
            float d = Math.min((h - 10f) * 0.5f, 8f);  // /2
            h += simplex.noise2f(x * INV_35, y * INV_35) * d;
            h += simplex.noise2f(x * INV_60, y * INV_60) * d * 0.5f;
            if (h > 35f) {
                float d2 = Math.min((h - 35f) / 1.5f, 30f);  // /1.5
                h += cellDistance(cell.eval2D(x / 25D, y / 25D)) * d2;  // /25
            }
        }

        h += simplex.noise2f(x * INV_28, y * INV_28) * 4;
        h += simplex.noise2f(x * INV_18, y * INV_18) * 2;
        h += simplex.noise2f(x * INV_8, y * INV_8) * 2;

        return h + 67f;
    }

    /**
     * RWG original — mountain river variant with gentler low-elevation treatment.
     * Peaks up to ~217, lower slopes smoothed by river influence.
     */
    public static float terrainMountainRiver(int x, int y, RTGWorld rtgWorld, float river) {

        SimplexNoise simplex = rtgWorld.simplexInstance(0);
        CellularNoise cell = rtgWorld.cellularInstance(0);

        float h = simplex.noise2f(x * INV_300, y * INV_300) * 135f * river;
        h *= h * INV_32;
        h = Math.min(h, 150f);

        if (h < 10f) {
            h += simplex.noise2f(x * INV_14, y * INV_14) * (10f - h) * 0.2f;
        }

        if (h > 10f) {
            float d = Math.min((h - 10f) * 0.5f, 8f);
            h += simplex.noise2f(x * INV_35, y * INV_35) * d;
            h += simplex.noise2f(x * INV_60, y * INV_60) * d * 0.5f;
            if (h > 35f) {
                float d2 = Math.min((h - 35f) / 1.5f, 30f);
                h += cellDistance(cell.eval2D(x / 25D, y / 25D)) * d2;
            }
        }

        if (h > 2f) {
            float d = Math.min((h - 2f) * 0.5f, 4f);
            h += simplex.noise2f(x * INV_28, y * INV_28) * d;
            h += simplex.noise2f(x * INV_18, y * INV_18) * (d * 0.5f);
            h += simplex.noise2f(x * INV_8, y * INV_8) * (d * 0.5f);
        }

        return h + 67f;
    }

    /**
     * RWG original — hilly mountains with lake basin erosion.
     * Creates dramatic peaks (up to ~250+) with deep carved valleys.
     * Default params from RWG JungleHills: width=230, strength=120, lakeDepth=50
     */
    public static float terrainHilly(int x, int y, RTGWorld rtgWorld, float river,
                                      float width, float strength, float lakeDepth,
                                      float lakeWidth, float terrainHeight) {

        SimplexNoise simplex0 = rtgWorld.simplexInstance(0);
        CellularNoise cell = rtgWorld.cellularInstance(0);

        float h = simplex0.noise2f(x * INV_20, y * INV_20) * 2;
        h += simplex0.noise2f(x * INV_7, y * INV_7) * 0.8f;

        float invWidth = 1f / width;
        float m = simplex0.noise2f(x * invWidth, y * invWidth) * strength * river;
        m *= m * INV_35;  // m²/35
        m = m > 70f ? 70f + (m - 70f) * 0.4f : m;  // /2.5

        float st = Math.min(m * 0.7f, 20f);
        float c = cellDistance(cell.eval2D(x / 30f, y / 30f)) * (5f + st);

        float sm = simplex0.noise2f(x * INV_30, y * INV_30) * 8f + simplex0.noise2f(x * INV_8, y * INV_8);
        sm *= Math.min((m + 10f) * 0.05f, 2.5f);  // /20
        m += sm + c;

        // Lake basin carving — subtracts depth to create valleys
        float invLakeWidth = 1f / lakeWidth;
        float l = simplex0.noise2f(x * invLakeWidth, y * invLakeWidth) * lakeDepth;
        l *= l * 0.04f;  // /25
        l = Math.max(l, -8f);

        return terrainHeight + h + m - l;
    }

    /**
     * RWG original — grassland mountains with rolling peaks and lake basins.
     * Peaks up to ~200, creates wide mountain ranges.
     */
    public static float terrainGrasslandMountains(int x, int y, RTGWorld rtgWorld, float river) {

        SimplexNoise simplex0 = rtgWorld.simplexInstance(0);
        CellularNoise cell = rtgWorld.cellularInstance(0);

        float h = simplex0.noise2f(x * INV_100, y * INV_100) * 7;
        h += simplex0.noise2f(x * INV_20, y * INV_20) * 2;

        float m = simplex0.noise2f(x * INV_230, y * INV_230) * 120f * river;
        m *= m * INV_35;
        m = m > 70f ? 70f + (m - 70f) * 0.4f : m;  // /2.5

        float c = cellDistance(cell.eval2D(x / 30f, y / 30f)) * (m * 0.30f);

        float sm = simplex0.noise2f(x * INV_30, y * INV_30) * 8f + simplex0.noise2f(x * INV_8, y * INV_8);
        sm *= Math.min(m * 0.05f, 2.5f);  // /20
        m += sm + c;

        float l = simplex0.noise2f(x * INV_260, y * INV_260) * 38f;
        l *= l * 0.04f;  // /25
        l = Math.max(l, -8f);

        return 68f + h + m - l;
    }

    /**
     * RWG original — grassland hills with configurable parameters.
     * Gentler than terrainHilly, produces rolling terrain.
     */
    public static float terrainGrasslandHills(int x, int y, RTGWorld rtgWorld, float river,
                                               float hillHeight, float hillWidth,
                                               float varHeight, float varWidth,
                                               float lakeHeight, float lakeWidth,
                                               float baseHeight) {

        SimplexNoise simplex0 = rtgWorld.simplexInstance(0);
        CellularNoise cell = rtgWorld.cellularInstance(0);

        float h = simplex0.noise2f(x / varWidth, y / varWidth) * varHeight * river;
        h += simplex0.noise2f(x * INV_20, y * INV_20) * 2;

        float m = simplex0.noise2f(x / hillWidth, y / hillWidth) * hillHeight * river;
        m *= m * 0.025f;  // /40

        float sm = simplex0.noise2f(x * INV_30, y * INV_30) * 8f;
        sm *= Math.min(m * 0.05f, 3.75f);  // /20
        m += sm;

        float cm = cellDistance(cell.eval2D(x / 25D, y / 25D)) * 12f;  // /25
        cm *= Math.min(m * 0.05f, 3.75f);  // /20
        m += cm;

        float l = simplex0.noise2f(x / lakeWidth, y / lakeWidth) * lakeHeight;
        l *= l * 0.04f;  // /25
        l = Math.max(l, 8f);

        h += simplex0.noise2f(x * INV_12, y * INV_12) * 3f;
        h += simplex0.noise2f(x * INV_5, y * INV_5) * 1.5f;

        return baseHeight + h + m - l;
    }

    /**
     * RWG original — mountain spikes for snowy/alpine terrain.
     * Creates extremely jagged peaks.
     */
    public static float terrainMountainSpikes(int x, int y, RTGWorld rtgWorld, float river) {

        SimplexNoise simplex = rtgWorld.simplexInstance(0);
        CellularNoise cell = rtgWorld.cellularInstance(0);

        float b = (12f + (simplex.noise2f(x * INV_300, y * INV_300) * 6f));
        float h = cellDistance(cell.eval2D(x / 200D, y / 200D)) * b * river;  // /200
        h *= h * 1.5f;
        h = Math.min(h, 155f);

        if (h > 2f) {
            float d = Math.min((h - 2f) * 0.5f, 8f);
            h += simplex.noise2f(x * INV_30, y * INV_30) * d;
            h += simplex.noise2f(x * INV_50, y * INV_50) * d * 0.5f;

            if (h > 35f) {
                float d2 = Math.min((h - 35f) / 1.5f, 30f);  // /1.5
                h += cellDistance(cell.eval2D(x / 25D, y / 25D)) * d2;  // /25
            }
        }

        h += simplex.noise2f(x * INV_18, y * INV_18) * 3;
        h += simplex.noise2f(x * INV_8, y * INV_8) * 2;

        return 45f + h + (b * 2);
    }

    // ====================================================================
    // RWG 有、rtgc 原先**缺失**的 4 个地形函数（逐行移植；源：rwg/terrain/*.java）
    //
    // 写法说明：这里刻意用 RWG 的 `x / w` 直接除法，而不用本文件其它地方的 INV_ 常量。
    //   ① 与源逐字一致；② 避开 `x * (1f/w)` 与 `x / w` 之间 ≤1 ulp 的差异
    //   （审计已确认该差异不是问题，但照抄就该照抄）；
    //   ③ cell 项同样用 RWG 的 `x / 25D` 双精度写法，而非 `x * 0.04f`。
    // ====================================================================

    /**
     * 平坦湖地（RWG {@code TerrainFlatLakes.java} 的逐行移植）。
     * <p>
     * <b>名字冲突已解决</b>：rtgc 原先那个 RTG 时代的 {@code terrainFlatLakes} 已**删除**；
     * 这个名字现在专归 RWG 的函数。
     * <p>
     * rtgc 原先的 {@code terrainOcean} 用的就是本函数的本体，但基高取自调用方参数、
     * 且多一个 {@code minimumOceanFloor} 钳制 —— 两者都已按 RWG 拨回原样
     *（RWG：{@code return 62f + h}，无钳制）。
     */
    public static float terrainFlatLakes(int x, int y, RTGWorld rtgWorld, float river) {

        final SimplexNoise simplex = rtgWorld.simplexInstance(0);

        float h = simplex.noise2f(x / 300f, y / 300f) * 40f * river;
        h = h > 3f ? 3f : h;
        h += simplex.noise2f(x / 50f, y / 50f) * (12f - h) * 0.4f;
        h += simplex.noise2f(x / 15f, y / 15f) * (12f - h) * 0.15f;

        return 62f + h;
    }

    /**
     * 海洋底（RWG {@code biomes/realistic/ocean/RealisticBiomeOcean.rNoise} 的逐行移植）。
     *
     * <p>RWG 的六个海洋槽位（{@code Support.oceanDeep*} / {@code oceanShallow*}）
     * **全部**是 {@code RealisticBiomeOcean} 实例，它们的 {@code rNoise} 就是：
     * <pre>
     *   float height = shallow ? 52f : 34f;
     *   return height + perlin.noise2(x / 220f, y / 220f) * 4f
     *                 + perlin.noise2(x / 55f, y / 55f) * 1.5f;
     * </pre>
     * 即**浅海海底 y≈52、深海 y≈34**（海平面 63 ⇒ 水深约 11 / 29 格）。
     *
     * <p><b>⚠ rtgc 此前把海洋群系接到了 {@link #terrainFlatLakes}（{@code 62f + h}）上 ——
     * 那是 RWG 给**陆地**用的地形</b>（RWG 里它的使用者是
     * {@code SnowLakes} / {@code TaigaPlains} / {@code TundraPlains}）。
     * 后果：全图 **51% 的海洋只有 1 格水**。
     *
     * <p>另外，BOP 的 {@code kelp_forest} / {@code coral_reef} 在 RWG 里是被
     * {@code RealisticBiomeBOPOcean} 包过之后才放进海洋槽位的（{@code SupportBOP.java:39-48}），
     * 而 rtgc 原先直接把它们当海洋群系，于是 {@code kelp_forest} 带着**陆地版的山地地形**
     * （{@code terrainSwampMountain}）出现在海里 —— 实测表现为"陆地上 F3 显示海洋群系"。
     * 现在这两个也走本函数。
     *
     * @param shallow {@code true} = 浅海（y≈52）；{@code false} = 深海（y≈34）
     */
    public static float terrainOcean(int x, int y, RTGWorld rtgWorld, boolean shallow) {

        final SimplexNoise simplex = rtgWorld.simplexInstance(0);

        final float height = shallow ? 52f : 34f;

        return height + simplex.noise2f(x / 220f, y / 220f) * 4f
                + simplex.noise2f(x / 55f, y / 55f) * 1.5f;
    }

    /**
     * 极地 / 沙丘（RWG {@code TerrainPolar.java} 的逐行移植，**常量写死在函数里**）。
     * <p>
     * rtgc 另有一个 9 参的 {@code terrainPolar}，那是把 RWG 的常量外提成参数的泛化版
     *（供沙漠沙丘复用），属 RTG 时代的改造。本重载才是 RWG 原样的那个：
     * 160 / 35 / 60 / 50，下限 {@code 0.2f}（泛化版此前写的是 {@code 0.1f}），返回 {@code 70f + h}。
     */
    public static float terrainPolar(int x, int y, RTGWorld rtgWorld, float river) {

        final SimplexNoise simplex = rtgWorld.simplexInstance(0);

        float st = (simplex.noise2f(x / 160f, y / 160f) + 0.38f) * 35f * river;
        st = st < 0.2f ? 0.2f : st;

        float h = simplex.noise2f(x / 60f, y / 60f) * st * 2f;
        h = h > 0f ? -h : h;
        h += st;
        h *= h / 50f;
        h += st;

        return 70f + h;
    }

    /**
     * 台地 / 恶地（RWG {@code TerrainMesa.java} 的逐行移植）。
     * <p>
     * <b>注意它需要 {@code border}</b>：RWG 在 {@code c2} 那一层用
     * {@code border > 0.95f + hn * 0.09f} 判定「是否在群系边界上」，从而决定台地阶沿
     * 要不要抬 35 倍。这是本函数与其它地形函数最大的不同，调用方必须把
     * {@code rNoise} 收到的 {@code border} 原样传进来。
     *
     * @param border 群系边界权重（0–1），来自 {@code rNoise} 的入参
     */
    public static float terrainMesa(int x, int y, RTGWorld rtgWorld, float border, float river) {

        final SimplexNoise simplex = rtgWorld.simplexInstance(0);

        float b = simplex.noise2f(x / 130f, y / 130f) * 50f * river;
        b *= b / 40f;

        final float hn = simplex.noise2f(x / 12f, y / 12f);

        float sb = 0f;
        if (b > 2f) {
            sb = (b - 2f) / 2f;
            sb = sb < 0f ? 0f : sb > 5.5f ? 5.5f : sb;
            sb = hn * sb;
        }
        b += sb;

        b = b < 0.1f ? 0.1f : b;

        float c1 = 0f;
        if (b > 1f) {
            c1 = b > 5.5f ? 4.5f : b - 1f;
            c1 *= 3;
        }

        float c2 = 0f;
        if (b > 5.5f && border > 0.95f + hn * 0.09f) {
            c2 = b > 6f ? 0.5f : b - 5.5f;
            c2 *= 35;
        }

        float bn = 0f;
        if (b < 7f) {
            final float bnh = 5f - b;
            bn += simplex.noise2f(x / 70f, y / 70f) * (bnh * 0.4f);
            bn += simplex.noise2f(x / 20f, y / 20f) * (bnh * 0.3f);
        }

        float w = simplex.noise2f(x / 80f, y / 80f) * 25f;
        w *= w / 25f;

        b += c1 + c2 + bn - w;

        return 74f + b;
    }

    /**
     * 沙丘（RWG {@code TerrainDunes.java} 的逐行移植）。返回 {@code 70f + h * river}。
     *
     * <p>⚠ 本函数目前**零调用者**（`tools/reachability.ps1` 实测 ext=0 / intra=0）。
     * **不要删**：它是 RWG 的逐行移植（不是 RTG 自造），删掉等于以后还得重抄一遍。
     * 参照 {@code docs/rwg-port-gaps.md} §20：它和忠实的
     * {@code terrainPolar(int,int,RTGWorld,float)} 同属"已移植、待接线"。
     * 当前沙漠走的是 {@code terrainPolar(参数化) + groundNoise(...)}（RTG 的沙丘配方，
     * 见 {@code RealisticBiomeVanillaDesert}），换成它会顺带让"沙丘高度"配置失效。
     */
    public static float terrainDunes(int x, int y, RTGWorld rtgWorld, float river) {

        final SimplexNoise simplex = rtgWorld.simplexInstance(0);

        float st = (simplex.noise2f(x / 160f, y / 160f) + 0.38f) * 35f;
        st = st < 0.2f ? 0.2f : st;

        float h = simplex.noise2f(x / 60f, y / 60f) * st * 2f;
        h = h > 0f ? -h : h;
        h += st;
        h *= h / 50f;
        h += st;

        if (h < 10f) {
            float d = (h - 10f) / 2f;
            d = d > 4f ? 4f : d;
            h += cellDistance(rtgWorld.cellularInstance(0).eval2D(x / 25D, y / 25D)) * d;
            h += simplex.noise2f(x / 30f, y / 30f) * d;
            h += simplex.noise2f(x / 14f, y / 14f) * d * 0.5f;
        }

        return 70f + (h * river);
    }

    /**
     * 小岛基座（RWG {@code TerrainSmallIsland.java} 的逐行移植）。
     * <p>
     * RWG 的注释写的是「a submerged shelf with the same hills as the Cherry Blossom Grove terrain」——
     * 地貌与 {@link #terrainHighland} 同形，但：基高 58（不是 68）、细节项**不乘** {@code detailStrength}、
     * 且末尾有 {@code Math.max(58f, ...)} 的**下限钳制**（保证是"淹没的陆架"而不是坑）。
     * <p>
     * 常量：{@code HILL_START=6f, HILL_HEIGHT=120f, BASE_HEIGHT=58f, HILL_WIDTH=200f}。
     */
    public static float terrainSmallIsland(int x, int y, RTGWorld rtgWorld, float river) {

        final SimplexNoise simplex = rtgWorld.simplexInstance(0);

        float h = simplex.noise2f(x / 200f, y / 200f) * 120f * river;
        h = h < 6f ? 6f + ((h - 6f) / 4.5f) : h;

        if (h > 0f) {
            final float st = h * 1.5f > 15f ? 15f : h * 1.5f;
            h += cellDistance(rtgWorld.cellularInstance(0).eval2D(x / 70D, y / 70D)) * st;
        }

        h += simplex.noise2f(x / 20f, y / 20f) * 5f;
        h += simplex.noise2f(x / 12f, y / 12f) * 3f;
        h += simplex.noise2f(x / 5f, y / 5f) * 1.5f;

        return Math.max(58f, 58f + h);
    }

    /**
     * 小型支撑地形（RWG {@code TerrainSmallSupport.java} 的逐行移植）。
     * <p>
     * 这就是审计里说的「rtgc 的 {@code terrainForest} 的相对应物」：
     * 区别是 RWG 的 {@code +20f} 项**乘了 {@code river}**，而 rtgc 那份漏了。
     */
    public static float terrainSmallSupport(int x, int y, RTGWorld rtgWorld, float river) {

        final SimplexNoise simplex = rtgWorld.simplexInstance(0);

        float h = simplex.noise2f(x / 100f, y / 100f) * 8;
        h += simplex.noise2f(x / 30f, y / 30f) * 4;
        h += simplex.noise2f(x / 15f, y / 15f) * 2;
        h += simplex.noise2f(x / 7f, y / 7f);

        return 70f + (20f * river) + h;
    }

    // ====================================================================
    // RWG 的**海岸 / 岛屿**地形
    //
    // 它们原本不在 rwg/terrain/ 里，而是作为 rNoise 覆写**手写在群系类里**：
    //   coast\RealisticBiomeCoastDunes.java:43-63
    //   coast\RealisticBiomeCoastIce.java:34-56
    //   ocean\RealisticBiomeIslandTundra.java:32-40
    //   ocean\RealisticBiomeIslandTropical.java:77-95
    // 这里逐行提成函数（公式未改）。
    //
    // ⚠ ocean 参数的来源（管线适配，非公式改动）：
    //   RWG 的 rNoise 签名带 `ocean`（= getTerrainOceanValue 的结果），
    //   而 rtgc 的 rNoise 签名没有它（历史原因）。故海岸函数**自行向布局查询**——
    //   取到的正是同一个值 getTerrainOceanValue(getContinentValue(x, z))。
    // ====================================================================

    /** 取该列的 RWG `ocean` 值（0–2）。布局未就绪时返回 1（中性值）。 */
    private static float oceanAt(int x, int z) {
        final rtg.world.biome.RtgBiomeLayout layout = rtg.world.biome.RtgLayoutAccess.current();
        return layout == null ? 1f : rtg.world.biome.RtgBiomeLayout.getTerrainOceanValue(layout.getContinentValue(x, z));
    }

    /** RWG {@code RealisticBiomeCoastDunes.rNoise} 的逐行移植（沙丘海岸）。 */
    public static float terrainCoastDunes(int x, int y, RTGWorld rtgWorld, float river) {

        final SimplexNoise simplex = rtgWorld.simplexInstance(0);
        final float ocean = oceanAt(x, y);

        river = river > 0.5f ? 1f : river * 2f;

        float h = ocean < 0.5f ? ocean * 18f : 9f;

        if (ocean < 1.9f) {
            // RWG 在此算了一个 st 却**从未使用** —— 原样保留（照抄，不"顺手修好"）
            float st = (1.9f - ocean) * 20f;
            st = st > 1f ? 1f : st;

            h += simplex.noise2f(x / 12f, y / 12f) * 1f;
            h += simplex.noise2f(x / 23f, y / 23f) * 2f;
        }

        if (ocean > 1f) {
            h += simplex.noise2f(x / 15f, y / 15f) * (ocean - 1f) * 8f;
            h += simplex.noise2f(x / 25f, y / 25f) * (ocean - 1f) * 13f;
            h += (ocean - 1f) * 9f;
        }

        return 55f + h;
    }

    /** RWG {@code RealisticBiomeCoastIce.rNoise} 的逐行移植（冰海岸）。 */
    public static float terrainCoastIce(int x, int y, RTGWorld rtgWorld, float river) {

        final SimplexNoise simplex = rtgWorld.simplexInstance(0);
        final float ocean = oceanAt(x, y);

        river = river > 0.5f ? 1f : river * 2f;

        final float start = (simplex.noise2f(x / 90f, y / 90f) * 1f)
                + (simplex.noise2f(x / 40f, y / 40f) * 0.15f)
                + (simplex.noise2f(x / 9f, y / 9f) * 0.07f);

        float h = 0f;
        float c = 0f;
        if (ocean + start > 1.4f) {
            c = ocean + start > 1.5f ? 0.1f : (ocean + start) - 1.4f;
            c *= 250 + simplex.noise2f(x / 50f, y / 50f) * 25f;
        }

        if (ocean < 1.3f) {
            // 同上：RWG 的 st 未被使用，原样保留
            float st = (1.3f - ocean) * 20f;
            st = st > 1f ? 1f : st;

            h += simplex.noise2f(x / 12f, y / 12f);
            h += simplex.noise2f(x / 20f, y / 20f) * 2;
        }

        return 55f + h + (c * river);
    }

    /** RWG {@code RealisticBiomeIslandTundra.rNoise} 的逐行移植（只用 {@code border}）。 */
    public static float terrainIslandTundra(int x, int y, RTGWorld rtgWorld, float border) {

        final SimplexNoise simplex = rtgWorld.simplexInstance(0);

        float h = 0f;
        if (border > 0.9f) {
            h = (border - 0.9f) * (150f + simplex.noise2f(x / 45f, y / 45f) * 50f);
        }

        return 66f + h;
    }

    /** RWG {@code RealisticBiomeIslandTropical.rNoise} 的逐行移植（用 {@code cell} 与 {@code border}）。 */
    public static float terrainIslandTropical(int x, int y, RTGWorld rtgWorld, float border) {

        final SimplexNoise simplex = rtgWorld.simplexInstance(0);
        final CellularNoise cell = rtgWorld.cellularInstance(0);

        float st = 15f - ((cellDistance(cell.eval2D(x / 500D, y / 500D)) * 42f)
                + (simplex.noise2f(x / 30f, y / 30f) * 2f));

        st = st < 0f ? 0f : st;

        float h = st;
        h = h < 0f ? 0f : h;
        h += (h * 0.4f) * ((h * 0.4f) * 2f);

        if (h > 10f) {
            final float d2 = (h - 10f) / 1.5f > 30f ? 30f : (h - 10f) / 1.5f;
            h += cellDistance(cell.eval2D(x / 25D, y / 25D)) * d2;
        }

        h += simplex.noise2f(x / 18f, y / 18f) * 3;
        h += simplex.noise2f(x / 8f, y / 8f) * 2;

        return 55f + h * border;
    }

    // ====================================================================
    // END RWG-GRAND TERRAIN FUNCTIONS
    // ====================================================================

    /**
     * 把 {@link VoronoiResult} 记录的距离换算成**世界格**。
     * {@code VoronoiResult} 的距离位于 cellular 噪声的内部空间，需乘回
     * {@link SpacedCellularNoise#COORDINATE_SCALE}，再乘单元间距。
     *
     * <p>⚠ 本方法目前**零调用者**（隧道实现已改走布局的 `RwgCellNoise`）。保留的理由：
     * {@code src/preview} 的 {@code CellularNoiseCalibration} 用同一套换算**离线复核**
     * 隧道/洞厅的面积覆盖率，这里的公式是那次复核的对照物。
     */
    private static double toWorldBlocks(final double voronoiDistance, final RTGWorld rtgWorld) {
        return voronoiDistance * SpacedCellularNoise.COORDINATE_SCALE * rtgWorld.getRiverSeparation();
    }

    /** 地下河隧道半宽（世界格）。RWG 的隧道全宽约 13 格（{@code border 9/1250}），取同量级。 */
    public static final float TUNNEL_HALF_WIDTH_BLOCKS = 6.5f;

    /** 河网交汇洞厅半径（世界格）。RWG 的 {@code junction 60/1250} 约合 42 格半径。 */
    public static final float JUNCTION_RADIUS_BLOCKS = 42f;

    /**
     * RWG 等价的 cell 距离（C-5）。
     * <p>
     * {@link VoronoiResult#rwgCellDistance()} 即 RWG {@code CellNoise.noise()} 的等价量
     * （RWG 侧 {@code useDistance = true}，返回 {@code √(dx²+dz²)/√2}；已离线逐点验证，均值比 0.9004）。
     * <p>
     * 移植前此处用的是 {@link VoronoiResult#getShortestDistance()}（平方距离，幅值仅约 1/38.7），
     * 该分支已删除。
     */
    public static float cellDistance(final VoronoiResult voronoi) {
        return (float) voronoi.rwgCellDistance();
    }

    /**
     * 沼泽河道（RWG {@code rwg/terrain/TerrainSwampRiver.java} 的逐行移植，无参构造）。
     * <p>
     * 用途：RWG {@code SupportBOP.java} 把 BOP 的 {@code bayou} / {@code lushSwamp} / {@code marsh}
     * 交给它。本仓库原先**没有这个函数**，那三个群系因此退化成了本地噪声。
     */
    public static float terrainSwampRiver(int x, int y, RTGWorld rtgWorld, float river) {
        final SimplexNoise simplex = rtgWorld.simplexInstance(0);

        float h = simplex.noise2f(x * INV_180, y * INV_180) * 40f * river;
        h *= h * INV_35;

        if (h < 1f) {
            h = 1f;
        }
        if (h < 4f) {
            h += (simplex.noise2f(x * INV_50, y * INV_50) + simplex.noise2f(x * INV_15, y * INV_15)) * (4f - h);
        }

        return 60f + h;
    }

    /**
     * 沼泽山地（RWG {@code rwg/terrain/TerrainSwampMountain.java} 的逐行移植）。
     *
     * @param mountainHeight RWG 构造参数 {@code mountainHeight}（BOP 群系用 120f / 135f）
     * @param mountainWidth  RWG 构造参数 {@code mountainWidth}（BOP 群系用 300f）
     */
    public static float terrainSwampMountain(int x, int y, RTGWorld rtgWorld, float river,
                                            float mountainHeight, float mountainWidth) {
        final SimplexNoise simplex = rtgWorld.simplexInstance(0);
        final CellularNoise cell = rtgWorld.cellularInstance(0);

        float h = simplex.noise2f(x / mountainWidth, y / mountainWidth) * mountainHeight * river;
        h *= h * INV_32;
        h = Math.min(h, 150f);

        if (h < 14f) {
            h += simplex.noise2f(x * INV_25, y * INV_25) * (14f - h) * 0.8f;
        }
        if (h < 6f) {
            h = 6f - ((6f - h) * 0.07f)
                    + simplex.noise2f(x * INV_20, y * INV_20) + simplex.noise2f(x * INV_5, y * INV_5);
        }
        if (h > 10f) {
            final float d = Math.min((h - 10f) * 0.5f, 8f);
            h += simplex.noise2f(x * INV_35, y * INV_35) * d;
            h += simplex.noise2f(x * INV_60, y * INV_60) * d * 0.5f;
            if (h > 35f) {
                final float d2 = Math.min((h - 35f) / 1.5f, 30f);
                h += cellDistance(cell.eval2D(x / 25D, y / 25D)) * d2;
            }
        }
        if (h > 2f) {
            final float d = Math.min((h - 2f) * 0.5f, 4f);
            h += simplex.noise2f(x * INV_28, y * INV_28) * d;
            h += simplex.noise2f(x * INV_18, y * INV_18) * (d * 0.5f);
            h += simplex.noise2f(x * INV_8, y * INV_8) * (d * 0.5f);
        }

        return h + 56f;
    }

    /**
     * 沙丘谷地（RWG {@code rwg/terrain/TerrainDuneValley.java} 的逐行移植）。
     *
     * @param valleySize RWG 构造参数 {@code valleySize}（BOP 群系用 300f）
     */
    public static float terrainDuneValley(int x, int y, RTGWorld rtgWorld, float river, float valleySize) {
        final SimplexNoise simplex = rtgWorld.simplexInstance(0);
        final CellularNoise cell = rtgWorld.cellularInstance(0);

        float h = (simplex.noise2f(x / valleySize, y / valleySize) + 0.25f) * 65f * river;
        h = h < 1f ? 1f : h;

        h += cellDistance(cell.eval2D(x / 50D, y / 50D)) * h * 2f; // RWG: cell.noise(x/50, y/50) * h * 2

        h += simplex.noise2f(x * INV_40, y * INV_40) * 8f;
        h += simplex.noise2f(x * INV_14, y * INV_14) * 2f;

        return 70f + h;
    }

    public static float calcCliff(int x, int z, float[] noise, float river) {
        float cliff = 0f;

        // this is to solve a chronic problem where the edges of rivers are "cliffs"
        // Algorithm - in both x and z directions look for the *lowest* number in both x and z directions
        // Then return the higher of those two.
        int index = x * 16 + z;
        float currentNoise = noise[index];
        final WaterLevel waterLevel = WaterLevel.current();
        if (currentNoise < waterLevel.cliffBandHigh() && currentNoise > waterLevel.cliffBandLow()) {
            // near water level
            if (river > 0.85f) {
                //near river center — avoid cliff generation on river banks
                float xUp = 0f;
                float xDown = 0f;
                float zUp = 0f;
                float zDown = 0f;
                if (x > 0) {
                    xDown = Math.abs(currentNoise - noise[(x - 1) * 16 + z]);
                }
                if (z > 0) {
                    zDown = Math.abs(currentNoise - noise[x * 16 + z - 1]);
                }
                if (x < 15) {
                    xUp = Math.abs(currentNoise - noise[(x + 1) * 16 + z]);
                }
                if (z < 15) {
                    zUp = Math.abs(currentNoise - noise[x * 16 + z + 1]);
                }
                float xCliff = Math.min(xUp, xDown);// Again, *minimum* because we are trying to ignore the river edge drop
                float zCliff = Math.min(zDown, zUp);

                return Math.max(xCliff, zCliff);
            }
        }
        if (x > 0) {
            cliff = Math.max(cliff, Math.abs(currentNoise - noise[(x - 1) * 16 + z]));
        }
        if (z > 0) {
            cliff = Math.max(cliff, Math.abs(currentNoise - noise[x * 16 + z - 1]));
        }
        if (x < 15) {
            cliff = Math.max(cliff, Math.abs(currentNoise - noise[(x + 1) * 16 + z]));
        }
        if (z < 15) {
            cliff = Math.max(cliff, Math.abs(currentNoise - noise[x * 16 + z + 1]));
        }
        return cliff;
    }

    public static void calcSnowHeight(int x, int y, int z, ChunkPrimer primer, float[] noise) {
        if (y < 254) {
            int index = x * 16 + z;
            byte h = (byte) ((noise[index] - ((int) noise[index])) * 8);
            if (h > 7) {
                primer.setBlockState(x, y + 2, z, Blocks.SNOW_LAYER.getDefaultState());
                primer.setBlockState(x, y + 1, z, Blocks.SNOW_LAYER.getDefaultState().withProperty(BlockSnow.LAYERS, 7));
            } else if (h > 0) {
                primer.setBlockState(x, y + 1, z, Blocks.SNOW_LAYER.getDefaultState().withProperty(BlockSnow.LAYERS, (int) h));
            }
        }
    }

    public abstract float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river);
}
