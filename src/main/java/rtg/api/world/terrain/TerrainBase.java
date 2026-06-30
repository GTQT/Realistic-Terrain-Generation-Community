package rtg.api.world.terrain;

import net.minecraft.block.BlockSnow;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkPrimer;
import rtg.api.util.noise.CellularNoise;
import rtg.api.util.noise.ISimplexData2D;
import rtg.api.util.noise.SimplexData2D;
import rtg.api.util.noise.SimplexNoise;
import rtg.api.world.RTGWorld;
import rtg.api.world.terrain.heighteffect.VariableRuggednessEffect;


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

    public static float above(float limited, float limit) {

        if (limited > limit) {
            return limited - limit;
        }
        return 0f;
    }

    public static float unsignedPower(float number, float power) {

        if (number > 0) {
            return (float) Math.pow(number, power);
        }
        //(else)
        return (-1f) * (float) Math.pow((-1f) * number, power);
    }

    public static float hills(float x, float y, float hillStrength, RTGWorld rtgWorld) {

        SimplexNoise simplex0 = rtgWorld.simplexInstance(0);
        SimplexNoise simplex2 = rtgWorld.simplexInstance(2);

        float m = simplex0.noise2f(x * INV_150, y * INV_150);
        m = blendedHillHeight(m, 0.2f);

        float sm = simplex2.noise2f(x * INV_55, y * INV_55);// there are artifacts if this is close to a multiple of 16
        sm = blendedHillHeight(sm, 0.2f);
        //sm = sm*0.8f;
        sm *= sm * m;
        m += sm * 0.33333333f; // 1/3

        return m * hillStrength;
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

    public static float mountainCap(float m) {
        // heights can "blow through the ceiling" so pull more extreme values down a bit
        // Relaxed from original RTG — allows higher peaks for grand terrain (inspired by RWG)
        if (m > 140) {
            m = 140 + (m - 140f) * 0.8f;
            if (m > 200) {
                m = 200 + (m - 200f) * 0.65f;
            }
        }
        return m;
    }

    public static float riverized(float height, float river) {

        if (height < 62.45f) {
            return height;
        }
        // experimental adjustment to make riverbanks more varied
        float heightAdjust = (height - 62.45f) * 0.1f + .6f; // /10
        river = bayesianAdjustment(river, heightAdjust);
        return 62.45f + (height - 62.45f) * river;
    }

    public static float terrainBeach(int x, int y, RTGWorld rtgWorld, float river, float baseHeight) {
        return riverized(baseHeight + TerrainBase.groundNoise(x, y, 4f, rtgWorld), river);
    }

    public static float terrainBryce(int x, int y, RTGWorld rtgWorld, float river, float height) {

        SimplexNoise simplex = rtgWorld.simplexInstance(0);
        float sn = simplex.noise2f(x * 0.5f, y * 0.5f) * 0.5f + 0.5f;
        sn += simplex.noise2f(x, y) * 0.2f + 0.2f;
        sn += simplex.noise2f(x * 0.25f, y * 0.25f) * 4f + 4f;
        sn += simplex.noise2f(x * 0.125f, y * 0.125f) * 2f + 2f;
        float n = height / sn * 2;
        n += simplex.noise2f(x * INV_64, y * INV_64) * 4f;
        n = (sn < 6) ? n : 0f;
        return riverized(getTerrainBase() + n, river);
    }

    public static float terrainCanyon(int x, int y, RTGWorld rtgWorld, float river, float[] height, float border, float strength, int heightLength, boolean booRiver) {
        SimplexNoise simplex = rtgWorld.simplexInstance(0);
        float r = simplex.noise2f(x * INV_100, y * INV_100) * 50f * river;
        r = r < -7.4f ? -7.4f : Math.min(r, 7.4f);
        float b = (17f + r) * river;

        float hn = simplex.noise2f(x * INV_12, y * INV_12) * 0.5f;
        float sb = 0f;
        if (b > 0f) {
            sb = Math.min(b, 7f);
            sb = hn * sb * river;
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
                    bn *= bn * 0.22222222f; // /4.5
                }
            }
        } else if (b < 5f) {
            bn = (simplex.noise2f(x * INV_7, y * INV_7) * 1.3f + simplex.noise2f(x * INV_15, y * INV_15) * 2f) * (5f - b) * 0.2f;
        }

        b += cTotal - bn;

        return getTerrainBase(river) + b;
    }

    public static float terrainFlatLakes(int x, int y, RTGWorld rtgWorld, float river, float baseHeight) {
        float ruggedNoise = rtgWorld.simplexInstance(1).noise2f(
                x * VariableRuggednessEffect.INV_STANDARD_RUGGEDNESS_WAVELENGTH,
                y * VariableRuggednessEffect.INV_STANDARD_RUGGEDNESS_WAVELENGTH
        );

        ruggedNoise = blendedHillHeight(ruggedNoise);
        float h = groundNoise(x, y, 2f * (ruggedNoise + 1f), rtgWorld);// ground noise
        return riverized(baseHeight + h, river);
    }

    public static float terrainForest(int x, int y, RTGWorld rtgWorld, float river, float baseHeight) {

        SimplexNoise simplex = rtgWorld.simplexInstance(0);

        double h = simplex.noise2d(x * INV_100, y * INV_100) * 8d;
        h += simplex.noise2d(x * INV_30, y * INV_30) * 4d;
        h += simplex.noise2d(x * INV_15, y * INV_15) * 2d;
        h += simplex.noise2d(x * INV_7, y * INV_7);

        return riverized(baseHeight + 20f + (float) h, river);
    }

    public static float terrainGrasslandFlats(int x, int y, RTGWorld rtgWorld, float river, float mPitch, float baseHeight) {

        SimplexNoise simplex = rtgWorld.simplexInstance(0);
        float h = simplex.noise2f(x * INV_100, y * INV_100) * 7;
        h += simplex.noise2f(x * INV_20, y * INV_20) * 2;

        float m = simplex.noise2f(x * INV_180, y * INV_180) * 35f * river;
        m *= m / mPitch;

        float sm = blendedHillHeight(simplex.noise2f(x * INV_30, y * INV_30)) * 8f;
        float mDiv20 = m * 0.05f; // /20
        sm *= Math.min(mDiv20, 3.75f);
        m += sm;

        return riverized(baseHeight + h + m, river);
    }

    public static float terrainGrasslandHills(int x, int y, RTGWorld rtgWorld, float river, float vWidth, float vHeight, float hWidth, float hHeight, float bHeight) {

        float invVWidth = 1f / vWidth;
        float invHWidth = 1f / hWidth;

        SimplexNoise simplex0 = rtgWorld.simplexInstance(0);
        SimplexNoise simplex1 = rtgWorld.simplexInstance(1);

        float h = simplex0.noise2f(x * invVWidth, y * invVWidth);
        h = blendedHillHeight(h, 0.3f);

        float m = simplex1.noise2f(x * invHWidth, y * invHWidth);
        m = blendedHillHeight(m, 0.3f) * h;
        m *= m;

        h *= vHeight * river;
        m *= hHeight * river;

        h += TerrainBase.groundNoise(x, y, 4f, rtgWorld);

        return riverized(bHeight + h, river) + m;
    }

    public static float terrainGrasslandMountains(int x, int y, RTGWorld rtgWorld, float river, float hFactor, float mFactor, float baseHeight) {

        SimplexNoise simplex0 = rtgWorld.simplexInstance(0);
        float h = simplex0.noise2f(x * INV_100, y * INV_100) * hFactor;
        h += simplex0.noise2f(x * INV_20, y * INV_20) * 2;

        float m = simplex0.noise2f(x * INV_230, y * INV_230) * mFactor * river;
        m *= m * 0.028571429f; // /35
        m = m > 90f ? 90f + (m - 90f) * 0.6f : m;

        float c = rtgWorld.simplexInstance(4).noise3f(x * INV_30, y * INV_30, 1f) * (m * 0.30f);

        float sm = simplex0.noise2f(x * INV_30, y * INV_30) * 8f + simplex0.noise2f(x * INV_8, y * INV_8);
        float mDiv20 = m * 0.05f; // /20
        sm *= Math.min(mDiv20, 2.5f);
        m += sm;

        m += c;

        return riverized(baseHeight + h + m, river);
    }

    public static float terrainHighland(float x, float y, RTGWorld rtgWorld, float river, float start, float width, float height, float baseAdjust) {

        float invWidth = 1f / width;
        float h = rtgWorld.simplexInstance(0).noise2f(x * invWidth, y * invWidth) * height * river; //-140 to 140
        h = h < start ? start + ((h - start) * 0.22222222f) : h; // /4.5

        if (h < 0f) {
            h = 0;//0 to 140
        }
        if (h > 0f) {
            float st = Math.min(h * 1.5f, 15f);// 0 to 15
            h += rtgWorld.simplexInstance(4).noise3f(x * INV_70, y * INV_70, 1f) * st;// 0 to 155
            h = h * river;
        }

        h += blendedHillHeight(rtgWorld.simplexInstance(0).noise2f(x * INV_20, y * INV_20), 0f) * 4f;
        h += blendedHillHeight(rtgWorld.simplexInstance(0).noise2f(x * INV_12, y * INV_12), 0f) * 2f;
        h += blendedHillHeight(rtgWorld.simplexInstance(0).noise2f(x * INV_5, y * INV_5), 0f);

        if (h < 0) {
            h = h * 0.5f; // /2
        }

        if (h < -3) {
            h = (h + 3f) * 0.5f - 3f; // /2
        }

        return getTerrainBase(river) + (h + baseAdjust) * river;
    }

    public static float terrainLonelyMountain(int x, int y, RTGWorld rtgWorld, float river, float strength, float width, float terrainHeight) {

        float invWidth = 1f / width;
        SimplexNoise simplex0 = rtgWorld.simplexInstance(0);
        float h = blendedHillHeight(simplex0.noise2f(x * INV_20, y * INV_20), 0) * 3;
        h += blendedHillHeight(simplex0.noise2f(x * INV_7, y * INV_7), 0) * 1.3f;

        float m = simplex0.noise2f(x * invWidth, y * invWidth) * strength * river;
        m *= m * 0.028571429f; // /35
        m = m > 70f ? 70f + (m - 70f) * 0.4f : m; // /2.5

        float st = m * 0.7f;
        st = Math.min(st, 20f);
        float c = rtgWorld.simplexInstance(4).noise3f(x * INV_30, y * INV_30, 1f) * (5f + st);

        float sm = simplex0.noise2f(x * INV_30, y * INV_30) * 8f + simplex0.noise2f(x * INV_8, y * INV_8);
        float mPlus10Div20 = (m + 10f) * 0.05f; // /20
        sm *= Math.min(mPlus10Div20, 2.5f);
        m += sm;

        m += c;

        // the parameters can "blow through the ceiling" so pull more extreme values down a bit
        // this should allow a height parameter up to about 120
        if (m > 120) {
            m = 120f + (m - 120f) * 0.85f;
            if (m > 160) {
                m = 160f + (m - 160f) * 0.85f;
            }
        }
        return riverized(terrainHeight + h + m, river);
    }

    public static float terrainMarsh(int x, int y, RTGWorld rtgWorld, float baseHeight, float river) {

        SimplexNoise simplex = rtgWorld.simplexInstance(0);
        float h = simplex.noise2f(x * INV_130, y * INV_130) * 20f;

        h += simplex.noise2f(x * INV_12, y * INV_12) * 2f;
        h += simplex.noise2f(x * INV_18, y * INV_18) * 4f;

        h = h < 8f ? 0f : h - 8f;

        if (h == 0f) {
            h += simplex.noise2f(x * INV_20, y * INV_20) + simplex.noise2f(x * INV_5, y * INV_5);
            h *= 2f;
        }

        return riverized(baseHeight + h, river);
    }

    public static float terrainOcean(int x, int y, RTGWorld rtgWorld, float river, float averageFloor) {

        SimplexNoise simplex = rtgWorld.simplexInstance(0);
        float h = simplex.noise2f(x * INV_300, y * INV_300) * 8f * river;
        //h = h > 3f ? 3f : h;
        h += simplex.noise2f(x * INV_50, y * INV_50) * 2f;
        h += simplex.noise2f(x * INV_15, y * INV_15);

        float floNoise = averageFloor + h;
        floNoise = Math.max(floNoise, minimumOceanFloor);

        return floNoise;
    }

    public static float terrainOceanCanyon(int x, int y, RTGWorld rtgWorld, float river, float[] height, float border, float strength, int heightLength, boolean booRiver) {
        SimplexNoise simplex = rtgWorld.simplexInstance(0);
        river *= 1.3f;
        river = Math.min(river, 1f);
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
                    bn *= bn * 0.22222222f; // /4.5
                }
            }
        } else if (b < 5f) {
            bn = (simplex.noise2f(x * INV_7, y * INV_7) * 1.3f + simplex.noise2f(x * INV_15, y * INV_15) * 2f) * (5f - b) * 0.2f;
        }

        b += cTotal - bn;

        float floNoise = 30f + b;
        floNoise = Math.max(floNoise, minimumOceanFloor);

        return floNoise;
    }

    public static float terrainPlains(int x, int y, RTGWorld rtgWorld, float river, float stPitch, float stFactor, float hPitch, float hDivisor, float baseHeight) {

        float invStPitch = 1f / stPitch;
        float invHPitch = 1f / hPitch;
        float invHDivisor = 1f / hDivisor;

        SimplexNoise simplex = rtgWorld.simplexInstance(0);
        float floNoise;
        float st = (simplex.noise2f(x * invStPitch, y * invStPitch) + 0.38f) * stFactor * river;
        st = Math.max(st, 0.2f);

        float h = simplex.noise2f(x * invHPitch, y * invHPitch) * st * 2f;
        h = h > 0f ? -h : h;
        h += st;
        h *= h * invHDivisor;
        h += st;

        floNoise = riverized(baseHeight + h, river);
        return floNoise;
    }

    public static float terrainPlateau(float x, float y, RTGWorld rtgWorld, float river, float[] height, float border, float strength, int heightLength, float selectorWaveLength, boolean isM) {

        float invSelectorWaveLength = 1f / selectorWaveLength;
        SimplexNoise simplex = rtgWorld.simplexInstance(0);
        river = Math.min(river, 1f);
        float border2 = border * 4 - 2.5f;
        border2 = border2 > 1f ? 1f : Math.max(border2, 0f);
        float b = simplex.noise2f(x * INV_40, y * INV_40) * 1.5f;

        float sn = simplex.noise2f(x * invSelectorWaveLength, y * invSelectorWaveLength) * 0.5f + 0.5f;
        sn *= border2;
        sn *= river;
        sn += simplex.noise2f(x * 0.25f, y * 0.25f) * 0.01f + 0.01f;
        sn += simplex.noise2f(x * 0.5f, y * 0.5f) * 0.01f + 0.01f;
        float n, hn, stepUp;
        for (int i = 0; i < heightLength; i += 2) {
            n = (sn - height[i + 1]) / (1 - height[i + 1]);
            n = n * strength;
            n = (n < 0f) ? 0f : Math.min(n, 1f);
            hn = height[i] * 0.5f * ((sn * 2f) - 0.4f);
            hn = (hn < 0) ? 0f : hn;
            stepUp = 0f;
            if (sn > height[i + 1]) {
                stepUp += (height[i] * n);
                if (isM) {
                    stepUp += simplex.noise2f(x * INV_20, y * INV_20) * 3f * n;
                    stepUp += simplex.noise2f(x * INV_12, y * INV_12) * 2f * n;
                    stepUp += simplex.noise2f(x * INV_5, y * INV_5) * 1f * n;
                }
            }
            if (i == 0 && stepUp < hn) {
                b += hn;
            }
            stepUp = (stepUp < 0) ? 0f : stepUp;
            b += stepUp;
        }
        if (isM) {
            b += simplex.noise2f(x * INV_12, y * INV_12) * sn;
        }
        //Counteracts smoothing
        b /= border;

        return riverized(getTerrainBase(), river) + b;
    }

    public static float terrainPolar(float x, float y, RTGWorld rtgWorld, float river, float stPitch, float stFactor, float hPitch, float hDivisor, float baseHeight) {

        float invStPitch = 1f / stPitch;
        float invHPitch = 1f / hPitch;
        float invHDivisor = 1f / hDivisor;

        SimplexNoise simplex = rtgWorld.simplexInstance(0);
        float floNoise;
        float st = (simplex.noise2f(x * invStPitch, y * invStPitch) + 0.38f) * stFactor * river;
        st = Math.max(st, 0.1f);

        float h = simplex.noise2f(x * invHPitch, y * invHPitch) * st * 2f;
        h = h > 0f ? -h : h;
        h += st;
        h *= h * invHDivisor;
        h += st;

        floNoise = riverized(baseHeight + h, river);
        return floNoise;
    }

    public static float terrainRollingHills(int x, int y, RTGWorld rtgWorld, float river, float hillStrength, float addedHeight, float groundNoiseAmplitudeHills, float lift) {

        float groundNoise = groundNoise(x, y, groundNoiseAmplitudeHills, rtgWorld);
        float m = hills(x, y, hillStrength, rtgWorld);
        float floNoise = addedHeight + groundNoise + m;
        return riverized(floNoise + lift, river);
    }

    public static float terrainRollingHills(int x, int y, RTGWorld rtgWorld, float river, float hillStrength, float groundNoiseAmplitudeHills, float baseHeight) {

        float groundNoise = groundNoise(x, y, groundNoiseAmplitudeHills, rtgWorld);
        float m = hills(x, y, hillStrength, rtgWorld);
        float floNoise = groundNoise + m;
        return riverized(floNoise + baseHeight, river);
    }

    public static float terrainVolcano(int x, int y, RTGWorld rtgWorld, float border, float baseHeight) {

        SimplexNoise simplex = rtgWorld.simplexInstance(0);
        CellularNoise cellularNoise = rtgWorld.cellularInstance(0);

        float st = 15f - (float) (cellularNoise.eval2D(x * 0.002f, y * 0.002f).getShortestDistance() * 42d) + (simplex.noise2f(x * INV_30, y * INV_30) * 2f);

        float h = Math.max(st, 0f);
        h = Math.max(h, 0f);
        h += (h * 0.4f) * ((h * 0.4f) * 2f);

        if (h > 10f) {
            float d2 = Math.min((h - 10f) * 0.66666667f, 30f); // /1.5
            h += (float) (cellularNoise.eval2D(x * 0.04f, y * 0.04f).getShortestDistance() * d2); // /25
        }

        h += simplex.noise2f(x * INV_18, y * INV_18) * 3;
        h += simplex.noise2f(x * INV_8, y * INV_8) * 2;

        return baseHeight + h * border;
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
                float d2 = Math.min((h - 35f) * 0.66666667f, 30f);  // /1.5
                h += (float) cell.eval2D(x * 0.04f, y * 0.04f).getShortestDistance() * d2;  // /25
            }
        }

        h += simplex.noise2f(x * INV_28, y * INV_28) * 4;
        h += simplex.noise2f(x * INV_18, y * INV_18) * 2;
        h += simplex.noise2f(x * INV_8, y * INV_8) * 2;

        return mountainCap(h + 67f);
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
                float d2 = Math.min((h - 35f) * 0.66666667f, 30f);
                h += (float) cell.eval2D(x * 0.04f, y * 0.04f).getShortestDistance() * d2;
            }
        }

        if (h > 2f) {
            float d = Math.min((h - 2f) * 0.5f, 4f);
            h += simplex.noise2f(x * INV_28, y * INV_28) * d;
            h += simplex.noise2f(x * INV_18, y * INV_18) * (d * 0.5f);
            h += simplex.noise2f(x * INV_8, y * INV_8) * (d * 0.5f);
        }

        return mountainCap(h + 67f);
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
        float c = (float) cell.eval2D(x * INV_30, y * INV_30).getShortestDistance() * (5f + st);

        float sm = simplex0.noise2f(x * INV_30, y * INV_30) * 8f + simplex0.noise2f(x * INV_8, y * INV_8);
        sm *= Math.min((m + 10f) * 0.05f, 2.5f);  // /20
        m += sm + c;

        // Lake basin carving — subtracts depth to create valleys
        float invLakeWidth = 1f / lakeWidth;
        float l = simplex0.noise2f(x * invLakeWidth, y * invLakeWidth) * lakeDepth;
        l *= l * 0.04f;  // /25
        l = Math.max(l, -8f);

        return mountainCap(terrainHeight + h + m - l);
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

        float c = (float) cell.eval2D(x * INV_30, y * INV_30).getShortestDistance() * (m * 0.30f);

        float sm = simplex0.noise2f(x * INV_30, y * INV_30) * 8f + simplex0.noise2f(x * INV_8, y * INV_8);
        sm *= Math.min(m * 0.05f, 2.5f);  // /20
        m += sm + c;

        float l = simplex0.noise2f(x * INV_260, y * INV_260) * 38f;
        l *= l * 0.04f;  // /25
        l = Math.max(l, -8f);

        return mountainCap(68f + h + m - l);
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

        float cm = (float) cell.eval2D(x * 0.04f, y * 0.04f).getShortestDistance() * 12f;  // /25
        cm *= Math.min(m * 0.05f, 3.75f);  // /20
        m += cm;

        float l = simplex0.noise2f(x / lakeWidth, y / lakeWidth) * lakeHeight;
        l *= l * 0.04f;  // /25
        l = Math.max(l, 8f);

        h += simplex0.noise2f(x * INV_12, y * INV_12) * 3f;
        h += simplex0.noise2f(x * INV_5, y * INV_5) * 1.5f;

        return mountainCap(baseHeight + h + m - l);
    }

    /**
     * RWG original — mountain spikes for snowy/alpine terrain.
     * Creates extremely jagged peaks.
     */
    public static float terrainMountainSpikes(int x, int y, RTGWorld rtgWorld, float river) {

        SimplexNoise simplex = rtgWorld.simplexInstance(0);
        CellularNoise cell = rtgWorld.cellularInstance(0);

        float b = (12f + (simplex.noise2f(x * INV_300, y * INV_300) * 6f));
        float h = (float) cell.eval2D(x * 0.005f, y * 0.005f).getShortestDistance() * b * river;  // /200
        h *= h * 1.5f;
        h = Math.min(h, 155f);

        if (h > 2f) {
            float d = Math.min((h - 2f) * 0.5f, 8f);
            h += simplex.noise2f(x * INV_30, y * INV_30) * d;
            h += simplex.noise2f(x * INV_50, y * INV_50) * d * 0.5f;

            if (h > 35f) {
                float d2 = Math.min((h - 35f) * 0.66666667f, 30f);  // /1.5
                h += (float) cell.eval2D(x * 0.04f, y * 0.04f).getShortestDistance() * d2;  // /25
            }
        }

        h += simplex.noise2f(x * INV_18, y * INV_18) * 3;
        h += simplex.noise2f(x * INV_8, y * INV_8) * 2;

        return mountainCap(45f + h + (b * 2));
    }

    // ====================================================================
    // END RWG-GRAND TERRAIN FUNCTIONS
    // ====================================================================

    public static float getRiverStrength(final BlockPos blockPos, final RTGWorld rtgWorld) {
        return getRiverStrength(blockPos, rtgWorld, SimplexData2D.newDisk());
    }

    public static float getRiverStrength(final BlockPos blockPos, final RTGWorld rtgWorld, final ISimplexData2D jitterData) {

        final int worldX = blockPos.getX();
        final int worldZ = blockPos.getZ();
        double pX = worldX;
        double pZ = worldZ;

        //New river curve function. No longer creates worldwide curve correlations along cardinal axes.
        rtgWorld.simplexInstance(1).multiEval2D(worldX * 0.004166667f, worldZ * 0.004166667f, jitterData); // /240
        pX += jitterData.getDeltaX() * rtgWorld.getRiverLargeBendSize();
        pZ += jitterData.getDeltaY() * rtgWorld.getRiverLargeBendSize();

        rtgWorld.simplexInstance(2).multiEval2D(worldX * 0.0125f, worldZ * 0.0125f, jitterData); // /80
        pX += jitterData.getDeltaX() * rtgWorld.getRiverSmallBendSize();
        pZ += jitterData.getDeltaY() * rtgWorld.getRiverSmallBendSize();

        double riverSeparation = rtgWorld.getRiverSeparation();
        pX /= riverSeparation;
        pZ /= riverSeparation;

        //New cellular noise.
        double riverFactor = rtgWorld.cellularInstance(0).eval2D(pX, pZ).interiorValue();

        // the output is a curved function of relative distance from the center, so adjust to make it flatter
        riverFactor = bayesianAdjustment((float) riverFactor, 0.5f);
        double riverValleyLevel = rtgWorld.getRiverValleyLevel();
        if (riverFactor > riverValleyLevel) {
            return 0;
        }// no river effect
        return (float) (riverFactor / riverValleyLevel - 1d);
    }

    public static float calcCliff(int x, int z, float[] noise, float river) {
        float cliff = 0f;

        // this is to solve a chronic problem where the edges of rivers are "cliffs"
        // Algorithm - in both x and z directions look for the *lowest* number in both x and z directions
        // Then return the higher of those two.
        int index = x * 16 + z;
        float currentNoise = noise[index];
        if (currentNoise < 64.5f && currentNoise > 61.5f) {
            // near water level
            if (river + RTGWorld.ACTUAL_RIVER_PROPORTION > 0.97f) {
                //near river (here near 1 means river)
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

    public static float bayesianAdjustment(float probability, float multiplier) {
        // returns the original probability adjusted for the multiplier to the confidence ratio
        // useful for computationally cheap remappings within [0,1]
        if (probability >= 1) {
            return probability;
        }
        if (probability <= 0) {
            return probability;
        }
        float oneMinusProbability = 1f - probability;
        float newConfidence = probability * multiplier / oneMinusProbability;
        return newConfidence / (1f + newConfidence);
    }

    public abstract float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river);
}