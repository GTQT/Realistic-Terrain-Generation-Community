package rtg.api.world.gen.feature.tree.rtg;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A5' 的位精确扫描测试。这里不启动 World，只验证 {@code scanColumn} 的语义：
 * 第一段遇空段必须停（不能跳过），第二段才允许整段跳过。
 */
class SkylightColumnScanTest {

    private static final int HEIGHT = 50;

    @BeforeAll
    static void bootstrapMinecraftRegistry() {
        if (!Bootstrap.isRegistered()) {
            Bootstrap.register();
        }
    }

    @Test
    void emptyColumnSkipsAllSectionsWithoutStateReads() {
        final FakeColumn column = new FakeColumn();
        final SkylightTracker.ColumnScan out = new SkylightTracker.ColumnScan();

        SkylightTracker.scanColumn(column, 0, HEIGHT, out);

        assertEquals(0, out.reads, "a fully empty column should not read any block state");
        assertEquals(4, out.emptySections,
                "sections 0..3 are empty; section 3 still overlaps y=48..49, so it is skipped as one unit");
        assertEquals(0, out.obstruction);
        assertFalse(out.trunk);
    }

    @Test
    void firstLoopMustStopAtEmptySectionInsteadOfJumpingToLogAbove() {
        final FakeColumn column = new FakeColumn();
        for (int y = 0; y < 16; y++) {
            column.set(y, Blocks.STONE.getDefaultState());
        }
        column.set(32, Blocks.LOG.getDefaultState());

        final SkylightTracker.ColumnScan out = new SkylightTracker.ColumnScan();
        SkylightTracker.scanColumn(column, 0, HEIGHT, out);

        assertFalse(out.trunk, "the first loop must break at the empty section, not skip to the log at y=32");
        assertEquals(255, out.obstruction, "the log is still opaque when reached by the second loop");
        assertEquals(1, out.emptySections);
    }

    @Test
    void secondLoopSkipsEmptySectionsAndStopsAtOpaqueBlock() {
        final FakeColumn column = new FakeColumn();
        column.set(0, Blocks.STONE.getDefaultState());
        column.set(1, Blocks.STONE.getDefaultState());
        column.set(33, Blocks.STONE.getDefaultState());

        final SkylightTracker.ColumnScan out = new SkylightTracker.ColumnScan();
        SkylightTracker.scanColumn(column, 0, HEIGHT, out);

        assertEquals(255, out.obstruction);
        assertEquals(1, out.emptySections, "the empty section 1 should be skipped as one unit");
        assertEquals(19, out.reads,
                "3 reads in section 0 (two stones + first air), 14 air + 1 air + 1 stone in section 2");
    }

    @Test
    void waterIsIgnoredButWebStillAddsOpacity() {
        final FakeColumn column = new FakeColumn();
        column.set(0, Blocks.WATER.getDefaultState());
        column.set(1, Blocks.WEB.getDefaultState());

        final SkylightTracker.ColumnScan out = new SkylightTracker.ColumnScan();
        SkylightTracker.scanColumn(column, 0, HEIGHT, out);

        assertEquals(1, out.obstruction,
                "water counts as air; one web contributes lightOpacity 1, two would be 255");
        assertFalse(out.trunk);
    }

    @Test
    void logInTheBottomOpaqueRunMarksTrunk() {
        final FakeColumn column = new FakeColumn();
        for (int y = 0; y <= 5; y++) {
            column.set(y, Blocks.STONE.getDefaultState());
        }
        column.set(6, Blocks.LOG.getDefaultState());

        final SkylightTracker.ColumnScan out = new SkylightTracker.ColumnScan();
        SkylightTracker.scanColumn(column, 0, HEIGHT, out);

        assertTrue(out.trunk);
        assertEquals(255, out.obstruction);
    }

    @Test
    void trunkDistanceIsRelativeToTreeBase() {
        final FakeColumn column = new FakeColumn();
        column.set(64, Blocks.STONE.getDefaultState());
        column.set(65, Blocks.LOG.getDefaultState());

        final SkylightTracker.ColumnScan out = new SkylightTracker.ColumnScan();
        SkylightTracker.scanColumn(column, 64, HEIGHT, out);

        assertFalse(out.trunk,
                "a log at baseY+1 is in the first six blocks and must not be treated as a trunk");
    }

    @Test
    void checkLightingSkipsEmptySectionsAndChecksOnlyFirstOpaqueInARun() {
        final FakeColumn column = new FakeColumn();
        column.set(80, Blocks.STONE.getDefaultState());
        column.set(79, Blocks.STONE.getDefaultState());
        column.set(64, Blocks.WEB.getDefaultState());

        final List<Integer> hits = new ArrayList<>();
        final int checks = SkylightTracker.scanCheckLightingColumn(
                column, 0, 0, 90, 62, (x, y, z) -> hits.add(y));

        assertEquals(1, checks, "two adjacent opaque blocks must produce only one checkLight");
        assertEquals(Arrays.asList(80), hits,
                "only the first block of the opaque run is checked; the second is already in shadow");
    }

    @Test
    void checkLightingResetsLookingAfterSkippedEmptySection() {
        final FakeColumn column = new FakeColumn();
        column.set(100, Blocks.STONE.getDefaultState());
        column.set(64, Blocks.STONE.getDefaultState());

        final List<Integer> hits = new ArrayList<>();
        final int checks = SkylightTracker.scanCheckLightingColumn(
                column, 0, 0, 100, 62, (x, y, z) -> hits.add(y));

        assertEquals(2, checks);
        assertEquals(Arrays.asList(100, 64), hits,
                "an empty section must reset looking, so the lower opaque block is checked again");
    }

    @Test
    void checkLightingSkipsWholeEmptySection() {
        final FakeColumn column = new FakeColumn();

        final List<Integer> hits = new ArrayList<>();
        final int checks = SkylightTracker.scanCheckLightingColumn(
                column, 0, 0, 100, 62, (x, y, z) -> hits.add(y));

        assertEquals(0, checks);
        assertTrue(hits.isEmpty());
    }

    private static final class FakeColumn implements SkylightTracker.ColumnAccess {

        private final IBlockState[] states = new IBlockState[256];
        private final boolean[] emptySections = new boolean[16];

        FakeColumn() {
            Arrays.fill(states, Blocks.AIR.getDefaultState());
            Arrays.fill(emptySections, true);
        }

        void set(int y, IBlockState state) {
            states[y] = state;
            emptySections[y >> 4] = false;
        }

        @Override
        public IBlockState get(int y) {
            return states[y];
        }

        @Override
        public boolean isEmptySection(int y) {
            return emptySections[y >> 4];
        }

        @Override
        public int sectionTop(int y) {
            return ((y >> 4) + 1) << 4;
        }
    }
}
