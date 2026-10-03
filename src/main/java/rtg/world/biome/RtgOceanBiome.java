package rtg.world.biome;

import net.minecraft.entity.passive.EntityOcelot;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.world.biome.Biome;


/**
 * RWG {@code rwg/biomes/base/BaseBiomeOcean.java} 的等价物 —— RWG **自己注册**的那几个海洋生物群系。
 *
 * <h2>为什么 rtgc 需要它们（D5）</h2>
 *
 * RWG 给它自己的四个气候各写了海洋群系（{@code BaseBiomes.java:22-27}）：
 *
 * <pre>
 *   baseOceanIce       (subID 0)  0.0 / 0.1   Wolf 8,4,4
 *   baseOceanCold      (subID 1)  0.5 / 0.4   Wolf 8,1,2
 *   baseOceanTemperate (subID 2)  0.8 / 0.6   —
 *   baseOceanHot       (subID 3)  0.8 / 0.2   禁雨
 *   baseOceanWet       (subID 4)  0.9 / 0.9   Ocelot 2,1,1
 *   baseOceanOasis     (subID 5)  0.9 / 0.9   —
 * </pre>
 *
 * 而 rtgc 此前**只有 1 个深海**（{@code VanillaDeepOcean}）、浅海则被 BOP 的
 * {@code kelp_forest} 靠字典标签抢占了三个槽位 ⇒ "全世界的深海/浅海长得一模一样"。
 *
 * <p>现在本类注册了 **6 个**群系，对应 RWG 槽位表里它自己那一半：
 * {@code deep_ice_ocean} / {@code deep_hot_ocean} / {@code deep_wet_ocean}（深海）与
 * {@code shallow_cold_ocean} / {@code shallow_hot_ocean} / {@code shallow_wet_ocean}（浅海）。
 * 另外两格仍用原版（SNOW 浅海 = {@code frozen_ocean}、COLD 深海 = {@code deep_ocean}，
 * 后者是海底神殿的硬要求）；BOP 的 {@code kelp_forest} / {@code coral_reef}
 * **不占槽位**，只接 RWG 的两个 patch 钩子（{@code SupportBOP.java:39-47}）。
 * 完整对照表见 {@link rtg.world.biome.realistic.rtg.RealisticBiomeRtgOcean}。
 *
 * <p>MC 1.12.2 自己没有更多深海（`OCEAN`/`DEEP_OCEAN`/`FROZEN_OCEAN`，`frozen_deep_ocean`
 * 是 1.13 才有的），BOP 1.12.2 也没有深海变体 ⇒ **唯一的补齐办法就是把 RWG 这几个群系抄过来**
 * （它们是 RWG 的源码定义，不是发明）。本类就是那份定义的移植：温度/降雨/动物/禁雨**逐条照抄**。
 *
 * <p>⚠ 与 RWG 的一处差异：RWG 的 {@code BaseBiomeOcean} 没有设置 top/filler 方块，
 * 它的海底由 {@code RealisticBiomeOcean.rReplace} 自己刷成沙/砾石
 * （rtgc 侧对应 {@code rtg.api.world.surface.SurfaceOcean}）。这里沿用同一做法。
 */
public class RtgOceanBiome extends Biome {

    /** RWG {@code BaseBiomeOcean} 的 subID。我们只用到其中 4 个（0/1/3/4）。 */
    public enum Kind {
        /** subID 0：冰 */
        ICE,
        /** subID 1：冷 */
        COLD,
        /** subID 2：温带（RWG 定义了但槽位里没用到，留着以便以后对齐） */
        TEMPERATE,
        /** subID 3：热（禁雨） */
        HOT,
        /** subID 4：湿 */
        WET
    }

    /**
     * @param kind        RWG 的 subID → 温度/降雨/动物
     * @param displayName 显示名（F3 与语言文件都读它；RWG 用的是 "Hot Ocean" 这样的字面量）
     */
    public RtgOceanBiome(final Kind kind, final String displayName) {

        super(properties(kind, displayName));

        switch (kind) {
            case ICE:
                this.spawnableCreatureList.add(new SpawnListEntry(EntityWolf.class, 8, 4, 4));
                break;
            case COLD:
                this.spawnableCreatureList.add(new SpawnListEntry(EntityWolf.class, 8, 1, 2));
                break;
            case WET:
                this.spawnableMonsterList.add(new SpawnListEntry(EntityOcelot.class, 2, 1, 1));
                break;
            default:
                break;      // TEMPERATE / HOT 没有额外动物
        }
    }

    /** 温度/降雨逐条照抄 RWG {@code BaseBiomeOcean:12-34}。 */
    private static BiomeProperties properties(final Kind kind, final String displayName) {

        final BiomeProperties p = new BiomeProperties(displayName);

        switch (kind) {
            case ICE:
                // RWG: setTemperatureRainfall(0.0f, 0.1f) —— 0.0 会让水面自然结冰
                p.setTemperature(0.0f).setRainfall(0.1f).setSnowEnabled();
                break;
            case COLD:
                p.setTemperature(0.5f).setRainfall(0.4f);
                break;
            case TEMPERATE:
                p.setTemperature(0.8f).setRainfall(0.6f);
                break;
            case HOT:
                p.setTemperature(0.8f).setRainfall(0.2f).setRainDisabled();
                break;
            case WET:
                p.setTemperature(0.9f).setRainfall(0.9f);
                break;
        }
        return p;
    }
}
