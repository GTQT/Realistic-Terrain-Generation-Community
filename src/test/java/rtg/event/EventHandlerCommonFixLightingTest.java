package rtg.event;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P0 fixLighting 的纯扫描逻辑测试。
 *
 * <p>这里刻意不碰真实 Chunk/World：测试只验证三条优化没有改变扫描语义：
 * 空 section 整段跳过、同一空气格只检查一次、跳过空段后 lastAir 仍落在段底。
 * 真正的 setBlockState/checkLight 行为由游戏内 profiler 数据验证。
 */
class EventHandlerCommonFixLightingTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    void openColumnSkipsAllEmptySections() {
        FakeColumn column = new FakeColumn();
        RecordingSink sink = new RecordingSink(15, 15);

        EventHandlerCommon.FixLightingStats stats = new EventHandlerCommon.FixLightingStats();
        EventHandlerCommon.scanFixLightingColumn(column, 0, 0, sink, stats);

        assertEquals(13, stats.sectionSkips, "sections 3..15 are empty");
        assertEquals(0, stats.checks);
        assertEquals(0, stats.fixes);
    }

    @Test
    void repeatedOpaqueBlocksUnderTheSameAirAreCheckedOnce() {
        FakeColumn column = new FakeColumn();
        for (int y = 90; y <= 100; y++) {
            column.set(y, Blocks.STONE.getDefaultState());
        }
        RecordingSink sink = new RecordingSink(0, 15);

        EventHandlerCommon.FixLightingStats stats = new EventHandlerCommon.FixLightingStats();
        EventHandlerCommon.scanFixLightingColumn(column, 0, 0, sink, stats);

        assertEquals(1, stats.checks, "one air cell above a run of opaque blocks needs one check");
        assertEquals(1, stats.fixes);
        assertEquals(10, stats.repeatSkips, "the other ten opaque blocks reuse the same air cell");
    }

    @Test
    void emptySectionKeepsLastAirAtItsBase() {
        FakeColumn column = new FakeColumn();
        column.set(63, Blocks.STONE.getDefaultState());
        RecordingSink sink = new RecordingSink(0, 15);

        EventHandlerCommon.FixLightingStats stats = new EventHandlerCommon.FixLightingStats();
        EventHandlerCommon.scanFixLightingColumn(column, 0, 0, sink, stats);

        assertEquals(1, stats.checks);
        assertEquals(64, sink.lastCheckedY,
                "section 4 is empty, so the air cell above the block at y=63 is y=64");
        assertEquals(12, stats.sectionSkips);
    }

    private static final class FakeColumn implements EventHandlerCommon.FixLightingColumn {

        private final IBlockState[] states = new IBlockState[256];

        FakeColumn() {
            Arrays.fill(states, Blocks.AIR.getDefaultState());
        }

        void set(int y, IBlockState state) {
            states[y] = state;
        }

        @Override
        public IBlockState get(int y) {
            return states[y];
        }

        @Override
        public boolean isEmptySection(int sectionY) {
            for (int y = sectionY; y < sectionY + 16; y++) {
                if (states[y].getBlock() != Blocks.AIR) return false;
            }
            return true;
        }
    }

    private static final class RecordingSink implements EventHandlerCommon.FixLightingSink {

        private final int light;
        private final int adjacentLight;
        int lastCheckedY = -1;

        RecordingSink(int light, int adjacentLight) {
            this.light = light;
            this.adjacentLight = adjacentLight;
        }

        @Override
        public int skyLight(int x, int y, int z) {
            lastCheckedY = y;
            return light;
        }

        @Override
        public int adjacentLight(int x, int y, int z) {
            return adjacentLight;
        }

        @Override
        public void fix(int x, int y, int z) {
        }
    }
}
