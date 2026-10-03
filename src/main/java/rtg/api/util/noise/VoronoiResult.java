package rtg.api.util.noise;

import java.awt.geom.Point2D;


/**
 * @author Zeno410
 */
public class VoronoiResult {

    private static final double UNSET = 32000000.0;
    private static final double SQRT_2 = 1.4142135623730950488;

    private double shortestDistance = UNSET;
    private double nextDistance = UNSET;
    private double thirdDistance = UNSET;
    private double closestX = UNSET;
    private double closestZ = UNSET;

    public final double getShortestDistance() {
        return this.shortestDistance;
    }

    public final double getNextDistance() {
        return this.nextDistance;
    }

    public final double getThirdDistance() {
        return this.thirdDistance;
    }

    // returns 0 in the middle of a cell and 1 on the border;
    public final double borderValue() {
        return shortestDistance / nextDistance;
    }

    // returns 1 in the middle of a cell and 0 on the border;
    public final double interiorValue() {
        return (nextDistance - shortestDistance) / nextDistance;
    }

    /**
     * 到最近 Voronoi **单元边界**的距离，单位与传入的（已缩放的）采样坐标一致。
     * <p>
     * 推导：设点到边界的距离为 {@code t}，则到最近与次近站点距离之差 {@code d2 - d1 ≈ 2t}，
     * 故 {@code t = (d2 - d1) / 2}。注意本类内部存的是**平方距离**，因此这里先开方。
     * <p>
     * 用途：WP-3 的地下河隧道——河网即 Voronoi 单元边界，隧道理应沿边界延伸。
     *
     * @since 1.0.8
     */
    public final double borderDistance() {
        return (Math.sqrt(nextDistance) - Math.sqrt(shortestDistance)) * 0.5d;
    }

    /**
     * 到最近 Voronoi **顶点**（三条单元边界交汇处）的距离，单位同上。
     * <p>
     * 推导与 {@link #borderDistance()} 同理，取第三近站点：{@code u = (d3 - d1) / 2}。
     * 顶点处三站等距，故该值为 0。
     * <p>
     * 用途：WP-3 的河网交汇洞厅——交汇点即 Voronoi 顶点。
     * <p>
     * 注意：本类原先只跟踪最近与次近站点，为支持本方法新增了第三近站点（见 {@link #evaluate}）。
     *
     * @since 1.0.8
     */
    public final double vertexDistance() {
        return (Math.sqrt(thirdDistance) - Math.sqrt(shortestDistance)) * 0.5d;
    }

    /**
     * RWG {@code CellNoise.noise(x, z, 1D)}（{@code useDistance = true}）的**等价量**（C-5）。
     * <p>
     * <b>为什么需要它</b>：{@code TerrainBase} 里 7 处 RWG-GRAND 地形函数是 RWG 原版的**逐行对译**，
     * 唯独 cell 项把 RWG 的 {@code cell.noise(x / 25D, y / 25D, 1D)} 写成了
     * {@code cell.eval2D(x * 0.04f, y * 0.04f).getShortestDistance()}。
     * 传入坐标相同（都是 {@code /25}），但两者返回的**量纲完全不同**：
     * </p>
     * <table>
     *   <tr><th></th><th>RWG {@code CellNoise}</th><th>rtgc {@code SpacedCellularNoise}</th></tr>
     *   <tr><td>每单位方格的点数</td><td>1（抖动格点）</td><td>25</td></tr>
     *   <tr><td>输入坐标缩放</td><td>1（原样）</td><td>÷ {@code COORDINATE_SCALE} = 5</td></tr>
     *   <tr><td>Voronoi 单元尺寸（传入坐标下）</td><td>≈ 1</td><td>≈ 1</td></tr>
     *   <tr><td>返回</td><td>{@code sqrt(d1²) / √2}（**线性**）</td><td>{@code d1²}（**平方**）</td></tr>
     * </table>
     * <p>
     * 单元尺寸在**传入坐标**下两者一致，所以特征尺度本来就是对的；差的只有幅值：
     * 点数密 25 倍 ⇒ 线性距离是 RWG 的 1/5，而 {@code getShortestDistance()} 又是平方，
     * 于是实测幅值只有 RWG 的约 <b>1/35</b>（{@code E[d1²] ≈ 0.01} 对 {@code E[d1/√2] ≈ 0.354}）。
     * 这使 rtgc 的山脊/河谷细结构基本消失。
     * </p>
     * <p>
     * 换算即：先开方拿回线性距离，乘回 {@code COORDINATE_SCALE}（补点数密度带来的 1/5），
     * 再按 RWG 的 {@code / √2} 归一。
     * </p>
     * <p>
     * 离线校验见 {@code CellularNoiseCalibration}（{@code gradlew calibrateCellularNoise}）：
     * 它把 RWG 的 {@code CellNoise} 原样移植进预览工具，并逐点比对两者的均值/极值。
     * </p>
     *
     * @since 1.0.8
     */
    public final double rwgCellDistance() {
        return Math.sqrt(shortestDistance) * SpacedCellularNoise.COORDINATE_SCALE / SQRT_2;
    }

    // returns a point on the vector from closestX, closestZ to the passed point but at the specified length
    public final Point2D.Float toLength(Point2D.Float toMap, float radius) {
        double distance = toMap.distance(this.closestX, this.closestZ);
        double xDist = toMap.getX() - this.closestX;
        double zDist = toMap.getY() - this.closestZ;
        xDist *= radius / distance;
        zDist *= radius / distance;
        return new Point2D.Float((float) (this.closestX + xDist), (float) (this.closestZ + zDist));
    }

    void evaluate(Point2D.Double[] points, double x, double z) {
        for (Point2D.Double point : points) {
            double distance = point.distanceSq(x, z);
            if (distance < this.shortestDistance) {
                this.thirdDistance = this.nextDistance;
                this.nextDistance = this.shortestDistance;
                this.shortestDistance = distance;
                this.closestX = point.getX();
                this.closestZ = point.getY();
            }
            else if (distance < this.nextDistance) {
                this.thirdDistance = this.nextDistance;
                this.nextDistance = distance;
            }
            else if (distance < this.thirdDistance) {
                this.thirdDistance = distance;
            }
        }
    }
}
