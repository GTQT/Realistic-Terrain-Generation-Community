package rtg.world.gen;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.chunk.ChunkPrimer;

import rtg.world.biome.RtgBiomeLayout;
import rtg.world.biome.RtgLayoutAccess;


/**
 * 地下河（山间河洞）：山体内部沿河网开凿的水道与交汇洞厅。
 *
 * <p><b>本类现在是 RWG {@code ChunkGeneratorRealistic.carveMountainChainRivers()} 的逐行移植</b>，
 * 不再是"rtgc 自己的近似"。对照源码：
 * <pre>
 *   RWG                                                rtgc
 *   mountainWeight &lt;= 0.10f → continue               {@link #HOST_MIN}
 *   mountainHost = smoothstep((w - .10) / .40)        同名同式
 *   tunnelStrength = tunnel * mountainHost            同名同式
 *   tunnelCurve = sqrt(tunnelStrength * (2 - s))      同名同式（圆帽收口）
 *   floor = min(62, floor(riverBedHeight) + 1)        同名同式（{@link ChunkLandscape#riverBedHeight}）
 *   tunnelFloor = min(floor, 62 - round(curve * 4))   {@link #FLOOR_DROP}
 *   ceiling = 62 + round(curve * 11)                  {@link #ROOF_RISE}
 *   chamber: 63 / 23 / 42 / min(surface-10)           {@link #CHAMBER_BASE_Y} …
 *   y &lt;= 62 ? water : air                             {@link #fillChannel}
 *   天窗 0.70 / 0.15                                   {@link #SKYLIGHT_*}
 * </pre>
 *
 * <p>判定链（逐列，任一关不过即跳过）：
 * <pre>
 *   ⓪ {@code landscape.river > 0}     在河网带内（性能前置；隧道带 ⊂ 河网带，故等价）
 *   ① {@code tunnel > 0 || junction > 0}   在隧道带 / 交汇盘内
 *   ② {@code mountainChainRiverHost > 0.10}  **上方是山地链**（RWG 的唯一山体判据）
 *   ③ 断面：洞心恒 {@value #CENTER_Y}，上下按山体权重渐隐；洞厅另加扩张
 *   ④ {@code y <= }62 {@code ? 水 : 空气}；洞厅够强时向上开天窗
 * </pre>
 *
 * <h2>② 为什么只看 {@code mountainChainRiverHost}</h2>
 * 这是本轮"照抄检修"的关键。此前 rtgc 在这里加了一层自造的"岩层厚度"门控：
 * 用同区块 3×3 采样点的干高度均值减去满断面洞顶，够厚就开凿。**海拔高 ≠ 山**：
 * 高原、丘陵、山坳、台地都能满足"洞顶上有 12 格岩层"，于是河网经过这些地形时也被凿成
 * 暗河——正是用户报的"不是山的地方也生成暗河"。RWG 没有这层判据，它只认山地链
 * （{@code RealisticBiomeMountainChain} 在混合权重里的占比，加上链邻域影响 ——
 * RWG 是 ±48 格，rtgc 早在上一轮按用户"两山交接处暗河过多"的反馈收到 ±32，非本轮改动）。
 * 已删除岩层门控，恢复 RWG 的单一阈值。
 *
 * <h2>过渡为什么不再生硬</h2>
 * RWG 的收口是两件事叠加的，缺一不可：
 * <ol>
 *   <li><b>地表高度先保下来</b>：{@code ChunkGeneratorRTG} 按
 *       {@code mountainChainWeight} 做 {@code fade}（0.35 / 0.65），山体内部的河道雕刻被
 *       抵消，地表留成山；山外仍照常雕刻成河。rtgc 早已照抄这段，未改。</li>
 *   <li><b>断面按同一个权重渐隐</b>：{@code tunnelCurve = sqrt(s(2-s))} 在
 *       {@code s→0} 时只以 {@code sqrt(2s)} 收窄，比线性的 {@code s} 慢得多；再乘
 *       {@code mountainHost} 的 smoothstep ⇒ 洞口是一段**圆帽**，而不是"整断面突然消失"。
 *       此前 rtgc 写成 {@code sqrt(tunnel) * mountainHost}，在带边缘 {@code sqrt(tunnel)}
 *       跳变很大，正是"分界生硬"的算术来源。</li>
 * </ol>
 *
 * <p>⚠ 调用位置必须在 {@code replaceBiomeBlocks} **之后**（rtgc 既有约束）：地表替换按
 * {@code depth} 计数涂刷，先开凿会让隧道里的空气重置 {@code depth}，把隧道底刷成草/沙。
 * RWG 是反过来的（先开凿再替换），但它的隧道底在水下、看不见；rtgc 保留自己的顺序，
 * 视觉上更干净。这是本类**唯一**与 RWG 的顺序差异，已在此注明。
 *
 * <p>⚠ {@code riverBedHeight} 在 RWG 里是"河道雕刻后、fade 前"的高度。rtgc 的
 * {@code ChunkLandscape} 过去没有这个字段（用干高度近似），本轮补上；缺失时回退到已雕刻
 * 地表（只会让洞底停在 58–62，不会挖穿）。
 */
public final class UndergroundRiver {

    /** 洞心高度，同时也是水面高度（RWG 写死 62）。水面平在它上面 ⇒ 洞里与外面同一个平面，划船进得去。 */
    public static final int CENTER_Y = 62;

    /** RWG 的隧道门控下限（{@code mountainWeight <= 0.10f → continue}）。 */
    private static final float HOST_MIN = 0.10f;
    /** RWG 的山体权重 smoothstep 宽度（{@code 0.40}）。 */
    private static final float HOST_WIDTH = 0.40f;

    /** RWG 洞顶起伏（{@code 62 + round(curve * 11)}）。 */
    private static final int ROOF_RISE = 11;
    /** RWG 洞底下探（{@code 62 - round(curve * 4)}）。 */
    private static final int FLOOR_DROP = 4;
    /** RWG 的河床上限（{@code min(62, floor(riverBedHeight) + 1)}）。 */
    private static final int RIVER_FLOOR_CAP = 62;

    /** RWG 洞厅基准高度与上下扩张量（{@code 63 / 23 / 42}）。 */
    private static final int CHAMBER_BASE_Y = 63;
    private static final int CHAMBER_FLOOR_SPREAD = 23;
    private static final int CHAMBER_ROOF_SPREAD = 42;
    /** RWG 洞厅顶与地表之间保留的岩层（{@code surface - 10}）。 */
    private static final int ROOF_CLEARANCE = 10;

    /** RWG 洞厅/天窗的"地表够不够高"：基准与 smoothstep 宽度（{@code 76 / 24}，用**已雕刻地表**）。 */
    private static final float MIN_SURFACE = 76f;
    private static final float OVERHEAD_WIDTH = 24f;
    /** RWG 天窗门槛与收口坡度（{@code 0.70 / 0.15}）。 */
    private static final float SKYLIGHT_MIN_CHAMBER = 0.70f;
    private static final float SKYLIGHT_TAPER = 0.15f;
    /** RWG 天窗的下界（{@code max(ceiling + 1, 63)}）。 */
    private static final int SKYLIGHT_BOTTOM = 63;

    private static final IBlockState WATER = Blocks.WATER.getDefaultState();
    private static final IBlockState AIR = Blocks.AIR.getDefaultState();

    private UndergroundRiver() {}

    /**
     * 开凿一个区块的地下河。
     *
     * @param landscape 本区块地形（读 {@code noise/river/mountainChainRiverHost/riverBedHeight}；
     *                  写 {@code riverCaveCeiling} 供装饰期 {@link RiverCaveVines} 用）
     */
    public static void carve(final ChunkPrimer primer, final int chunkX, final int chunkZ,
                             final ChunkLandscape landscape) {

        final RtgBiomeLayout layout = RtgLayoutAccess.current();
        if (layout == null) {
            return;                                     // 布局未就绪：本区块不凿（fail-soft）
        }
        final int baseX = chunkX * 16;
        final int baseZ = chunkZ * 16;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                carveColumn(primer, layout, landscape, x, z, baseX + x, baseZ + z);
            }
        }
    }

    private static void carveColumn(final ChunkPrimer primer, final RtgBiomeLayout layout,
                                    final ChunkLandscape landscape, final int x, final int z,
                                    final int worldX, final int worldZ) {

        final int k = x * 16 + z;
        if (landscape.river[k] <= 0f) {                 // ⓪ 河网带内
            return;
        }
        final float tunnel = layout.getRiverTunnelStrength(worldX, worldZ);
        final float junction = layout.getRiverJunctionStrength(worldX, worldZ);
        if (tunnel <= 0f && junction <= 0f) {           // ① 隧道带 / 交汇盘
            return;
        }
        final float mountainWeight = landscape.mountainChainRiverHost[k];
        if (mountainWeight <= HOST_MIN) {               // ② RWG 的唯一山体判据
            return;
        }
        final float mountainHost = smoothstep((mountainWeight - HOST_MIN) / HOST_WIDTH);

        // RWG:596-617 —— 断面。
        final int surface = Math.min(255, (int) landscape.noise[k]);
        final float tunnelStrength = tunnel * mountainHost;
        // RWG:602 —— 圆帽收口。注意**不是** sqrt(tunnel) * mountainHost。
        final float tunnelCurve = (float) Math.sqrt(Math.max(0f, tunnelStrength * (2f - tunnelStrength)));
        final float bed = landscape.riverBedHeight[k] > 0f ? landscape.riverBedHeight[k] : landscape.noise[k];
        final int normalRiverFloor = Math.min(RIVER_FLOOR_CAP, (int) Math.floor(bed) + 1);
        int floor = Math.min(normalRiverFloor, CENTER_Y - Math.round(tunnelCurve * FLOOR_DROP));
        int ceiling = CENTER_Y + Math.round(tunnelCurve * ROOF_RISE);

        // RWG:609-617 —— 洞厅。顺序照抄：基准写死 63，`min(surface-10)` 在 `max()` 之前。
        final float overheadHost = smoothstep((surface - MIN_SURFACE) / OVERHEAD_WIDTH);
        final float chamberStrength = junction * mountainHost * overheadHost;
        if (chamberStrength > 0f) {
            final float chamberCurve = (float) Math.sqrt(chamberStrength);
            floor = Math.min(floor, CHAMBER_BASE_Y - Math.round(chamberCurve * CHAMBER_FLOOR_SPREAD));
            ceiling = Math.max(ceiling, CHAMBER_BASE_Y + Math.round(chamberCurve * CHAMBER_ROOF_SPREAD));
            ceiling = Math.min(ceiling, surface - ROOF_CLEARANCE);
            ceiling = Math.max(ceiling, CENTER_Y + Math.round(tunnelCurve * ROOF_RISE));
        }

        fillChannel(primer, x, z, floor, ceiling);      // ④
        landscape.riverCaveCeiling[k] = ceiling;        // 供装饰期挂洞穴藤蔓

        if (chamberStrength > SKYLIGHT_MIN_CHAMBER && surface > ceiling) {
            openSkylight(primer, x, z, surface, ceiling, chamberStrength);
        }
    }

    /** ④ 灌水：{@code y <= 62 ? 水 : 空气}（RWG:640-651）。 */
    private static void fillChannel(final ChunkPrimer primer, final int x, final int z,
                                    final int floor, final int ceiling) {
        final int top = Math.min(255, ceiling);
        for (int y = Math.max(1, floor); y <= top; y++) {
            primer.setBlockState(x, y, z, y <= CENTER_Y ? WATER : AIR);
        }
    }

    /** 天窗：洞厅足够强时自洞顶向上打通，与地表连通（RWG:621-630）。 */
    private static void openSkylight(final ChunkPrimer primer, final int x, final int z,
                                     final int surface, final int ceiling, final float chamberStrength) {
        final int bottom = Math.max(ceiling + 1, SKYLIGHT_BOTTOM);
        final float span = Math.max(1, surface - bottom);
        for (int y = bottom; y <= surface; y++) {
            if (chamberStrength >= SKYLIGHT_MIN_CHAMBER + (y - bottom) / span * SKYLIGHT_TAPER) {
                primer.setBlockState(x, y, z, AIR);
            }
        }
    }

    /**
     * 该列的**门控权重**（{@code 0} = 不开凿），供 {@code /rtg probe} 与开凿共用。
     *
     * <p>与 RWG 的 {@code smoothstep((mountainChainRiverHost - 0.10) / 0.40)} 完全一致；
     * 返回 0 表示这一列连"山地链"这一关都过不了。
     */
    public static float mountainHostAt(final ChunkLandscape landscape, final int k) {
        final float host = landscape.mountainChainRiverHost[k];
        return host <= HOST_MIN ? 0f : smoothstep((host - HOST_MIN) / HOST_WIDTH);
    }

    /** RWG 的 smoothstep（0–1 门控权重）。 */
    private static float smoothstep(final float value) {
        if (value <= 0f) {
            return 0f;
        }
        if (value >= 1f) {
            return 1f;
        }
        return value * value * (3f - 2f * value);
    }
}
