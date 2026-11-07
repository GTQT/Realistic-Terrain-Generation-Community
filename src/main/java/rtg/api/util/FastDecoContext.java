package rtg.api.util;

/**
 * 标记当前线程正在执行原版/BOP 的 {@code BiomeDecorator#genDecorations}。
 *
 * <p>装饰阶段的方块读写有一个共同特征：目标区块通常已经加载，且此时没有玩家，
 * 不需要把邻居/客户端更新推出去。原版 {@code World#getBlockState} 每次都要做
 * chunk 查找，{@code World#setBlockState} 还要走 {@code markAndNotifyBlock}；
 * 对 BOP 的 4×4×4 扫描、原版 patch/flora 和 RTG 自己的 liquid falls 来说，这些
 * 都是纯开销。
 *
 * <p>与 {@link FastOreContext} 分开：ore 只包 {@code generateOres}，这里包整个
 * {@code genDecorations}。未加载区块仍回退到原版 {@link net.minecraft.world.World}
 * 路径，因此不会改变跨区块边界语义；人口队列负责把由此产生的嵌套 populate 排队。
 *
 * <p>可用 {@code -Drtg.fastDeco=false} 整体回退。
 */
public final class FastDecoContext {

    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();
    private static final boolean ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("rtg.fastDeco", "true"));

    private FastDecoContext() {
    }

    public static void enter() {
        DEPTH.set(depth() + 1);
    }

    public static void exit() {
        int depth = depth() - 1;
        if (depth <= 0) {
            DEPTH.remove();
        } else {
            DEPTH.set(depth);
        }
    }

    public static void reset() {
        DEPTH.remove();
    }

    public static boolean isActive() {
        return ENABLED && depth() > 0;
    }

    private static int depth() {
        Integer depth = DEPTH.get();
        return depth == null ? 0 : depth;
    }
}
