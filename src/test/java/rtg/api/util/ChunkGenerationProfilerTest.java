package rtg.api.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M0 的计数器/归因口径测试。重点是两件事：
 * <ul>
 *   <li>profiler 关闭时计数器必须完全不动（正常游玩零开销）；</li>
 *   <li>子阶段（weights / PreEvent / PostEvent）不能再次计入 chunkTotal，
 *       否则慢区块百分比会被重复计算。</li>
 * </ul>
 */
class ChunkGenerationProfilerTest {

    @BeforeEach
    void setUp() {
        ChunkGenerationProfiler.setEnabled(false);
        ChunkGenerationProfiler.reset();
        ChunkGenerationProfiler.setSlowThresholdMs(20L);
    }

    @AfterEach
    void tearDown() {
        ChunkGenerationProfiler.setEnabled(false);
        ChunkGenerationProfiler.reset();
    }

    @Test
    void countersOnlyMoveWhenEnabled() {
        ChunkGenerationProfiler.count(ChunkGenerationProfiler.Counter.OPAQUE_LOG_WRITES);
        assertEquals(0L, ChunkGenerationProfiler.getCounter(
                ChunkGenerationProfiler.Counter.OPAQUE_LOG_WRITES));

        ChunkGenerationProfiler.setEnabled(true);
        ChunkGenerationProfiler.count(ChunkGenerationProfiler.Counter.OPAQUE_LOG_WRITES);
        ChunkGenerationProfiler.add(ChunkGenerationProfiler.Counter.OPAQUE_LOG_WRITES, 2L);
        assertEquals(3L, ChunkGenerationProfiler.getCounter(
                ChunkGenerationProfiler.Counter.OPAQUE_LOG_WRITES));
    }

    @Test
    void sessionDecorationAttributionAppearsInReport() {
        ChunkGenerationProfiler.setEnabled(true);
        ChunkGenerationProfiler.beginChunk(0, 0);
        ChunkGenerationProfiler.recordDeco("TreeA", 1_000_000L);
        assertEquals(1_000_000L, ChunkGenerationProfiler.getSessionDecoNs("TreeA"));

        ChunkGenerationProfiler.endChunk(0, 0);
        assertTrue(ChunkGenerationProfiler.getReport().contains("TreeA"),
                "session-level decoration attribution should be in the report");
    }

    @Test
    void subPhaseDoesNotCountTowardChunkTotal() {
        ChunkGenerationProfiler.setEnabled(true);
        ChunkGenerationProfiler.setSlowThresholdMs(1L);
        ChunkGenerationProfiler.beginChunk(1, 2);
        // 5ms 的 weights 子阶段：它属于 Pop: decoration，不能再单独进 chunkTotal。
        ChunkGenerationProfiler.end(ChunkGenerationProfiler.Category.POP_WEIGHTS,
                System.nanoTime() - 5_000_000L);
        ChunkGenerationProfiler.endChunk(1, 2);
        assertEquals(0L, ChunkGenerationProfiler.getSlowChunks(),
                "a sub-phase alone must not make the chunk slow");
    }
}
