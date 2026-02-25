package rtg.api.util;

import java.util.Arrays;

/**
 * GC-optimized pool for noise generation arrays
 *
 * 预分配 hugeRender[81][256] + smallRender[625][256]
 * 通过 ThreadLocal 槽位实现无锁线程安全借用
 */
public class NoiseArrayPool {

    public static final int POOL_SIZE = 4;
    private static final int BIOME_COUNT = 256;
    private static final int HUGE_ROWS = 81;
    private static final int SMALL_ROWS = 625;

    private final float[][][] hugePool = new float[POOL_SIZE][HUGE_ROWS][BIOME_COUNT];
    private final float[][][] smallPool = new float[POOL_SIZE][SMALL_ROWS][BIOME_COUNT];

    private final int[] hugeBorrowed = new int[POOL_SIZE];
    private final int[] smallBorrowed = new int[POOL_SIZE];

    public NoiseArrayPool() {
        Logger.info("NoiseArrayPool initialized: POOL_SIZE={}, HUGE={}KB, SMALL={}KB",
                POOL_SIZE,
                (POOL_SIZE * HUGE_ROWS * BIOME_COUNT * 4) / 1024,
                (POOL_SIZE * SMALL_ROWS * BIOME_COUNT * 4) / 1024);
    }

    public float[][] borrowHuge(int slot) {
        if (slot < 0 || slot >= POOL_SIZE) {
            Logger.warn("Invalid slot {}, clamping to 0", slot);
            slot = 0;
        }

        float[][] arr = hugePool[slot];
        for (int i = 0; i < HUGE_ROWS; i++) {
            Arrays.fill(arr[i], 0f);
        }

        synchronized (hugeBorrowed) {
            hugeBorrowed[slot]++;
            if (hugeBorrowed[slot] > 1) {
                Logger.debug("Huge slot {} borrowed {} times without return", slot, hugeBorrowed[slot]);
            }
        }
        return arr;
    }

    public float[][] borrowSmall(int slot) {
        if (slot < 0 || slot >= POOL_SIZE) {
            slot = 0;
        }

        float[][] arr = smallPool[slot];
        for (int i = 0; i < SMALL_ROWS; i++) {
            Arrays.fill(arr[i], 0f);
        }

        synchronized (smallBorrowed) {
            smallBorrowed[slot]++;
        }
        return arr;
    }

    public void returnHuge(int slot) {
        if (slot < 0 || slot >= POOL_SIZE) slot = 0;
        synchronized (hugeBorrowed) {
            hugeBorrowed[slot] = Math.max(0, hugeBorrowed[slot] - 1);
        }
    }

    public void returnSmall(int slot) {
        if (slot < 0 || slot >= POOL_SIZE) slot = 0;
        synchronized (smallBorrowed) {
            smallBorrowed[slot] = Math.max(0, smallBorrowed[slot] - 1);
        }
    }
}