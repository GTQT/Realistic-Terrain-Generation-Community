/*
 * 洞穴位移场用的 2D Perlin 噪声。
 *
 * 抽自 FastNoise（Copyright (c) 2017 Jordan Peck，MIT License）的
 * `GetNoise(float x, float y)`（NoiseType=Perlin、Interp=Quintic）那一条路径：
 * hash、梯度表、Quintic 插值逐行照抄，保证与上游 WorleyCaves 用的位移场**逐位一致**。
 * 许可全文见仓库根目录 THIRD-PARTY-NOTICES.md。
 *
 * <p>为什么不直接搬 FastNoise（2900 行）：
 * <ol>
 *   <li>上游只调用 `FastFloor` 与 2D Perlin，其余（Simplex/Cellular/Cubic/4D）全用不到；</li>
 *   <li>FastNoise 的 `GradientPerturb*` 引用了 1.12.2 环境里不存在的 `javax.vecmath`
 *       （能加载只是因为那些方法没被调用）。整包搬进来等于给自己埋一颗
 *       `NoClassDefFoundError` 的雷，而这里只需要 60 行。</li>
 * </ol>
 */
package rtg.world.gen.worley;

/**
 * FastNoise 的 2D Perlin 子集（seed + frequency 构造注入）。
 *
 * <p>取值范围约 [-1, 1]，与 FastNoise 一致；`WorleyCavesGenerator` 用它做洞穴的
 * 三轴位移（x/y/z 各一条，靠常量偏移错开）。
 */
public final class WorleyWarpNoise {

    private static final int X_PRIME = 1619;
    private static final int Y_PRIME = 31337;

    private static final float[][] GRAD_2D = {
            {-1, -1}, {1, -1}, {-1, 1}, {1, 1}, {0, -1}, {-1, 0}, {0, 1}, {1, 0},
    };

    private final int seed;
    private final float frequency;

    public WorleyWarpNoise(int seed, float frequency) {
        this.seed = seed;
        this.frequency = frequency;
    }

    /** 等价于 FastNoise#GetNoise(float x, float y)（Perlin + Quintic）。 */
    public float noise(float x, float y) {
        return singlePerlin(seed, x * frequency, y * frequency);
    }

    private static int fastFloor(float f) {
        return (f >= 0 ? (int) f : (int) f - 1);
    }

    private static float lerp(float a, float b, float t) {
        return a + t * (b - a);
    }

    private static float interpQuintic(float t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static float gradCoord2D(int seed, int x, int y, float xd, float yd) {
        int hash = seed;
        hash ^= X_PRIME * x;
        hash ^= Y_PRIME * y;

        hash = hash * hash * hash * 60493;
        hash = (hash >> 13) ^ hash;

        final float[] g = GRAD_2D[hash & 7];
        return xd * g[0] + yd * g[1];
    }

    private static float singlePerlin(int seed, float x, float y) {
        final int x0 = fastFloor(x);
        final int y0 = fastFloor(y);
        final int x1 = x0 + 1;
        final int y1 = y0 + 1;

        final float xs = interpQuintic(x - x0);
        final float ys = interpQuintic(y - y0);

        final float xd0 = x - x0;
        final float yd0 = y - y0;
        final float xd1 = xd0 - 1;
        final float yd1 = yd0 - 1;

        final float xf0 = lerp(gradCoord2D(seed, x0, y0, xd0, yd0), gradCoord2D(seed, x1, y0, xd1, yd0), xs);
        final float xf1 = lerp(gradCoord2D(seed, x0, y1, xd0, yd1), gradCoord2D(seed, x1, y1, xd1, yd1), xs);

        return lerp(xf0, xf1, ys);
    }
}
