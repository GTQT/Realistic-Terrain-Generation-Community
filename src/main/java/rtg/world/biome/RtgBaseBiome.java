package rtg.world.biome;

import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityOcelot;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;


/**
 * RWG 自己注册的那套 {@code BaseBiome*}（{@code rwg2/biomes/base/}）的等价物。
 *
 * <p>RWG 的每个 {@code BaseBiomeX} 都只是 {@code extends BiomeGenBase} 后设：
 * 温度/降雨、显示名、动物、以及个别群系的颜色/禁雨。它的 top/filler 方块**从不设置**
 * （1.7.10 默认就是草/土），地表由 {@code Surface*} 在 {@code rReplace} 里刷。
 * 本类把这一层合并成一个带 {@link Kind} 的通用类，和 {@link RtgOceanBiome} 的做法一致。
 *
 * <p>颜色：RWG 的 {@code BaseBiomeHotPlains/HotForest} 用
 * {@code ColorizerGrass.getGrassColor(1f, 0f)} / {@code ColorizerFoliage.getFoliageColor(0.8f, 0.2f)}，
 * 这两个是 1.7.10 的客户端函数，1.12 不能在世界生成线程调用。这里改用原版稀树草原的
 * 干草色（{@code 0xBFB755} / {@code 0xAEA42A}）作为等价近似，并在此注明。
 */
public class RtgBaseBiome extends Biome {

    /** RWG {@code BaseBiomes.load()} 里那 18 个陆地基底中 A 组会用到的种类。 */
    public enum Kind {
        /** {@code BaseBiomeSnowForest}：0.0 / 0.1，Wolf 8,4,4 */
        SNOW_FOREST,
        /** {@code BaseBiomeSnowDesert}：0.0 / 0.1，无动物 */
        SNOW_DESERT,
        /** {@code BaseBiomeColdForest}：0.5 / 0.4，Wolf 8,1,2 */
        COLD_FOREST,
        /** {@code BaseBiomeColdPlains}：0.2 / 0.2，Wolf 5,2,3 + Horse 5,2,3 */
        COLD_PLAINS,
        /** {@code BaseBiomeTemperateForest}：0.8 / 0.6，Wolf 8,1,2 */
        TEMPERATE_FOREST,
        /** {@code BaseBiomePlains}：0.6 / 0.4，Horse 3,2,3 */
        PLAINS,
        /** {@code BaseBiomeHotPlains}：0.9 / 0.1，禁雨，Horse 5,2,3，干草色 */
        HOT_PLAINS,
        /** {@code BaseBiomeHotForest}：0.8 / 0.2，禁雨，干草色 */
        HOT_FOREST,
        /** {@code BaseBiomeHotDesert}：1.0 / 0.0，禁雨，无动物 */
        HOT_DESERT,
        /** {@code BaseBiomeRedwood}：0.7 / 0.6，Wolf 8,1,2 + Horse 3,2,3 */
        REDWOOD,
        /** {@code BaseBiomeJungle}：0.9 / 0.9，Ocelot 2,1,1 */
        JUNGLE,
        /** {@code BaseBiomeRiver(subID 5)} 的 Oasis：0.9 / 0.9，Ocelot */
        OASIS,
        /** {@code BaseBiomeTropicalIsland}：0.8 / 0.8 */
        TROPICAL_ISLAND,
        /** {@code BaseBiomeJungleMesa}：0.9 / 1.0，水色 65326，草/叶色 5762404，Ocelot */
        JUNGLE_MESA
    }

    private final Kind kind;

    public RtgBaseBiome(final Kind kind, final String displayName) {

        super(properties(kind, displayName));

        this.kind = kind;

        switch (kind) {
            case SNOW_FOREST:
                this.spawnableCreatureList.add(new SpawnListEntry(EntityWolf.class, 8, 4, 4));
                break;
            case COLD_FOREST:
            case TEMPERATE_FOREST:
                this.spawnableCreatureList.add(new SpawnListEntry(EntityWolf.class, 8, 1, 2));
                break;
            case COLD_PLAINS:
                this.spawnableCreatureList.add(new SpawnListEntry(EntityWolf.class, 5, 2, 3));
                this.spawnableCreatureList.add(new SpawnListEntry(EntityHorse.class, 5, 2, 3));
                break;
            case PLAINS:
                this.spawnableCreatureList.add(new SpawnListEntry(EntityHorse.class, 3, 2, 3));
                break;
            case HOT_PLAINS:
                this.spawnableCreatureList.add(new SpawnListEntry(EntityHorse.class, 5, 2, 3));
                break;
            case REDWOOD:
                this.spawnableCreatureList.add(new SpawnListEntry(EntityWolf.class, 8, 1, 2));
                this.spawnableCreatureList.add(new SpawnListEntry(EntityHorse.class, 3, 2, 3));
                break;
            case JUNGLE:
            case OASIS:
            case JUNGLE_MESA:
                this.spawnableMonsterList.add(new SpawnListEntry(EntityOcelot.class, 2, 1, 1));
                break;
            default:
                break;
        }
    }

    /** 温度/降雨/禁雨/颜色逐条照抄 RWG 的 {@code BaseBiome*}。 */
    private static BiomeProperties properties(final Kind kind, final String displayName) {

        final BiomeProperties p = new BiomeProperties(displayName);

        switch (kind) {
            case SNOW_FOREST:
                p.setTemperature(0.0f).setRainfall(0.1f).setSnowEnabled();
                break;
            case SNOW_DESERT:
                p.setTemperature(0.0f).setRainfall(0.1f).setSnowEnabled();
                break;
            case COLD_FOREST:
                p.setTemperature(0.5f).setRainfall(0.4f);
                break;
            case COLD_PLAINS:
                p.setTemperature(0.2f).setRainfall(0.2f);
                break;
            case TEMPERATE_FOREST:
                p.setTemperature(0.8f).setRainfall(0.6f);
                break;
            case PLAINS:
                p.setTemperature(0.6f).setRainfall(0.4f);
                break;
            case HOT_PLAINS:
                p.setTemperature(0.9f).setRainfall(0.1f).setRainDisabled();
                break;
            case HOT_FOREST:
                p.setTemperature(0.8f).setRainfall(0.2f).setRainDisabled();
                break;
            case HOT_DESERT:
                p.setTemperature(1.0f).setRainfall(0.0f).setRainDisabled();
                break;
            case REDWOOD:
                p.setTemperature(0.7f).setRainfall(0.6f);
                break;
            case JUNGLE:
                p.setTemperature(0.9f).setRainfall(0.9f);
                break;
            case OASIS:
                p.setTemperature(0.9f).setRainfall(0.9f);
                break;
            case TROPICAL_ISLAND:
                p.setTemperature(0.8f).setRainfall(0.8f);
                break;
            case JUNGLE_MESA:
                p.setTemperature(0.9f).setRainfall(1.0f).setWaterColor(65326);
                break;
            default:
                break;
        }
        return p;
    }

    /**
     * 干草色（RWG 用 1.7.10 的客户端 {@code ColorizerGrass} 计算，1.12 不能在生成线程调用）。
     * 取原版稀树草原的干草色作为等价近似。
     */
    @Override
    public int getGrassColorAtPos(final BlockPos pos) {
        switch (this.kind) {
            case HOT_PLAINS:
            case HOT_FOREST:
                return 0xBFB755;
            case JUNGLE_MESA:
                return 5762404;
            default:
                return super.getGrassColorAtPos(pos);
        }
    }

    @Override
    public int getFoliageColorAtPos(final BlockPos pos) {
        switch (this.kind) {
            case HOT_PLAINS:
            case HOT_FOREST:
                return 0xAEA42A;
            case JUNGLE_MESA:
                return 5762404;
            default:
                return super.getFoliageColorAtPos(pos);
        }
    }
}
