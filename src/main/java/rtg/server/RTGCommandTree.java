package rtg.server;

import net.minecraft.block.material.Material;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.chunk.Chunk;

import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.server.command.CommandTreeBase;

import rtg.RTG;
import rtg.RTGConfig;
import rtg.api.util.Logger;
import rtg.api.world.RTGWorld;
import rtg.api.world.gen.RTGChunkGenSettings;
import rtg.world.biome.RtgBiomeLayout;
import rtg.world.biome.RtgLayoutAccess;

public final class RTGCommandTree extends CommandTreeBase
{
    private static final int    ACCESS_ALL           = 0; // everyone
    private static final int    ACCESS_ADMIN         = 4; // only admins
    private static final String LANGKEY_BASE         = RTG.MODID+".command";
    private static final String LANGKEY_PREFIX_ERROR = LANGKEY_BASE +".prefix.error";
    private static final Style  STYLE_ERROR          = new Style().setColor(TextFormatting.RED);
    private static final Style  STYLE_GOLD           = new Style().setColor(TextFormatting.GOLD);
    private static final String CMD_ROOT             = "rtg";

    public RTGCommandTree() {
        Logger.debug("Created /{} command", CMD_ROOT);
        this.addSubcommand(new CommandGetWhereAmI(this.getName()));
        this.addSubcommand(new CommandProbe());
        this.addSubcommand(new CommandTunnels());
    }
    @Override public int getRequiredPermissionLevel() { return ACCESS_ALL; }
    @Override public String getName() { return CMD_ROOT; }

    /** 供各子命令输出一行文本的共用辅助。 */
    private static void line(ICommandSender sender, String format, Object... args) {
        sender.sendMessage(new TextComponentString(String.format(format, args)));
    }
    @Override public String getUsage(ICommandSender sender) {
        final ITextComponent ret = new TextComponentString("");
        getSubCommands().forEach(cmd -> {
            if (cmd instanceof CommandTreeBase) {
                ((CommandTreeBase)cmd).getSubCommands().forEach(rcmd -> ret.appendText("\n").appendText(rcmd.getUsage(sender)));
            } else {
                ret.appendText("\n").appendText(cmd.getUsage(sender));
            }
        });
        return ret.getFormattedText();
    }

    static final class CommandGetWhereAmI extends CommandBase
    {
        private static final String NAME = "whereami";
        private static final String LANGKEY_ERROR = LANGKEY_BASE + "." + NAME + ".error";
        private final String parentName;
        CommandGetWhereAmI(String parentName) { this.parentName = parentName; }
        @Override public int getRequiredPermissionLevel() { return ACCESS_ADMIN; }
        @Override public String getName() { return NAME; }
        @Override public String getUsage(ICommandSender sender) {
            return new TextComponentString("/").appendSibling(new TextComponentString(this.parentName+" "+this.getName())).getFormattedText();
        }
        @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
            final EntityPlayerMP player;
            if (sender instanceof EntityPlayerMP) {
                player = (EntityPlayerMP)sender;
            } else {
                sender.sendMessage(new TextComponentString("  ")
                    .appendSibling(new TextComponentTranslation(LANGKEY_PREFIX_ERROR).setStyle(STYLE_ERROR))
                    .appendText(": ")
                    .appendSibling(new TextComponentTranslation(LANGKEY_ERROR).setStyle(STYLE_GOLD))
                );
                return;
            }

            final BlockPos      pos      = player.getPosition();
            final World         world    = player.getEntityWorld();
            final Chunk         chunk    = world.getChunk(pos);
            final BiomeProvider provider = world.getBiomeProvider();

            final Biome chunkBiome    = chunk.getBiome(pos, provider);
            final Biome providerBiome = provider.getBiome(pos);

            player.sendMessage(new TextComponentString(String.format("Biome @ %s:%s, Chunk: %s, BiomeProvider: %s",
                pos.getX(), pos.getZ(), chunkBiome.getRegistryName(), providerBiome.getRegistryName())));
        }
    }

    /**
     * {@code /rtg probe [x z]} —— **机械验收入口**。
     * <p>
     * 打印指定坐标处地形管线的全部关键数值，并对可判定的项直接给出 {@code PASS}/{@code FAIL}。
     * 目的是让 WP-1（大陆场）、WP-3（隧道）、WP-6（配置）的验收变成"读数字"而不是"看地形像不像"。
     * <p>
     * 用法：站在待测位置执行 {@code /rtg probe}，或指定坐标 {@code /rtg probe 1200 -800}。
     */
    static final class CommandProbe extends CommandBase
    {
        private static final String NAME = "probe";

        @Override public int getRequiredPermissionLevel() { return ACCESS_ALL; }
        @Override public String getName() { return NAME; }
        @Override public String getUsage(ICommandSender sender) {
            return new TextComponentString("/rtg probe [x z]").getFormattedText();
        }

        @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
            final World world = sender.getEntityWorld();
            final BlockPos pos;
            if (args.length >= 2) {
                try {
                    pos = new BlockPos(CommandBase.parseInt(args[0]), 0, CommandBase.parseInt(args[1]));
                } catch (Exception ex) {
                    line(sender, "[rtg] 坐标解析失败，用法：/rtg probe [x z]");
                    return;
                }
            } else if (sender instanceof EntityPlayerMP) {
                pos = ((EntityPlayerMP) sender).getPosition();
            } else {
                line(sender, "[rtg] probe 需由玩家执行，或提供 <x> <z>");
                return;
            }

            final int x = pos.getX();
            final int z = pos.getZ();
            final RTGWorld rtgWorld;
            final RTGChunkGenSettings settings;
            try {
                rtgWorld = RTGWorld.getInstance(world);
                settings = rtgWorld.getGeneratorSettings();
            } catch (Throwable t) {
                line(sender, "[rtg] 无法取得 RTGWorld（该维度可能未启用 RTG）：%s", t);
                return;
            }

            line(sender, "=== /rtg probe @ %d,%d (dim %d) ===", x, z, world.provider.getDimension());
            line(sender, "[provider] %s", world.getBiomeProvider().getClass().getSimpleName());

            // ---- WP-6：配置是否生效 ----
            line(sender, "[config] seaLevel=%d bedrockLayers=%d riverSizeFactor=%.2f",
                    settings.seaLevel, settings.bedrockLayers,
                    RTGConfig.riverSizeFactor());

            // ---- 实际地形 ----
            final Chunk chunk = world.getChunk(pos);
            final int surface = chunk.getHeightValue(x & 15, z & 15);
            line(sender, "[terrain] heightmapY=%d seaLevel=%d -> %s", surface, settings.seaLevel,
                    surface <= settings.seaLevel ? "UNDER_WATER" : "ABOVE_WATER");

            // ---- 群系 ----
            final Biome biome = chunk.getBiome(pos, world.getBiomeProvider());
            final boolean biomeOcean = BiomeDictionary.hasType(biome, BiomeDictionary.Type.OCEAN);
            final Biome providerBiome = world.getBiomeProvider().getBiome(pos);
            line(sender, "[biome] 区块内=%s oceanTagged=%s | provider=%s", biome.getRegistryName(), biomeOcean,
                    providerBiome.getRegistryName());
            if (biome != providerBiome) {
                line(sender, "  ↑ 两者不同是**正常的**：BiomeAnalyzer 的群系修复会在生成期改写区块群系数组");
            }

            // ---- 河网 / 隧道 / 洞厅 ----
            // D1：原先走 TerrainBase 里与布局重复的旧河道族；现统一走布局（与生成器同源）。
            final RtgBiomeLayout probeLayout = RtgLayoutAccess.current();
            if (probeLayout == null) {
                line(sender, "[river] 布局未就绪（未进入 RTG 世界？）");
            } else {
                final float river = probeLayout.getRiverStrength(x, z);
                final float tunnel = probeLayout.getRiverTunnelStrength(x, z);
                final float junction = probeLayout.getRiverJunctionStrength(x, z);
                line(sender, "[river] strength=%.3f tunnel=%.3f junction=%.3f", river, tunnel, junction);
                line(sender, "[CHECK tunnel] 隧道/洞厅的硬门控是**山地链宿主** "
                        + "mountainChainRiverHost > 0.10（RWG:586），不是地表高度。");
                line(sender, "  本列 tunnel/junction 非零只说明落在河网边界/顶点附近；"
                        + "是否真的开凿还取决于它是不是山地链。实际地表=%d", surface);
            }

            // ---- 真·判定：该列到底有没有被开凿（读生成器里那份 ChunkLandscape）----
            final net.minecraft.world.chunk.IChunkProvider chunkProvider = world.getChunkProvider();
            final net.minecraft.world.gen.IChunkGenerator generator =
                    chunkProvider instanceof net.minecraft.world.gen.ChunkProviderServer
                            ? ((net.minecraft.world.gen.ChunkProviderServer) chunkProvider).chunkGenerator
                            : null;
            if (generator instanceof rtg.world.gen.ChunkGeneratorRTG) {
                final float[] t = ((rtg.world.gen.ChunkGeneratorRTG) generator)
                        .probeTunnelColumn(world.getBiomeProvider(), x, z);
                final boolean cached = t[3] > 0.5f;
                line(sender, "[tunnel] 链权重=%.3f 链宿主=%.3f 洞顶=%d",
                        t[0], t[1], (int) t[2]);
                if (!cached) {
                    line(sender, "  ⚠ 该区块的 layout 已被 LRU 缓存淘汰，此处是**重算**结果："
                            + "链权重/链宿主可信，洞顶不可信（重算不会开凿）。");
                } else if (t[2] > 0f) {
                    line(sender, "  ✅ 该列**确实开凿过**地下河隧道/洞厅，洞顶 y=%d。", (int) t[2]);
                } else {
                    line(sender, "  ✗ 该列**没有**被开凿：%s",
                            t[1] <= 0.10f
                                    ? "链宿主不足 ⇒ 这里不是山地链（地下河只在山地链内出现）"
                                    : "链宿主够，但不在河网边界/顶点上（隧道沿河网走）");
                }
            } else {
                line(sender, "[tunnel] 该维度的区块生成器不是 ChunkGeneratorRTG，无法读取隧道门控。");
            }

            // ---- 实际方块抽检 ----
            int bedrockLayers = 0;
            while (bedrockLayers < 16
                    && chunk.getBlockState(new BlockPos(x, bedrockLayers, z)).getBlock() == Blocks.BEDROCK) {
                bedrockLayers++;
            }
            line(sender, "[CHECK bedrock] 实际连续基岩层=%d (配置=%d；上层为概率生成，故 1 <= 实际 <= 配置)",
                    bedrockLayers, settings.bedrockLayers);

            int undergroundWater = 0;
            for (int y = 1; y < settings.seaLevel - 1; y++) {
                if (chunk.getBlockState(new BlockPos(x, y, z)).getMaterial() == Material.WATER) {
                    undergroundWater++;
                }
            }
            line(sender, "[blocks] 海平面以下水方块数=%d %s", undergroundWater,
                    undergroundWater > 0 ? "(提示：可能来自地下河隧道、洞穴水体或含水层，需结合 tunnel 数值判断)"
                            : "(该列海平面以下无水)");
        }
    }

    /**
     * {@code /rtg tunnels [半径]} —— **地下河/洞厅定位器**。
     *
     * <p>存在的理由：隧道只在**山地链**内的河网边界/顶点开凿，且洞顶被压在地表以下 ≥10 格，
     * 所以"走进世界找一条地下河"基本靠运气，验收只能靠肉眼扫 —— 那是本项目反复吃过亏的方式。
     * 本命令改为**直接查已生成区块的 layout 缓存**，把附近**真的被开凿过**的列报出来，
     * 玩家照着坐标往下挖即可。
     *
     * <p><b>只读</b>：只读 {@code landscapeCache} 里**已有**的条目，不新建、不生成、不污染缓存。
     * 因此它是"对已生成区域的观测"，没走到的地方不会出现（会在输出里说明）。
     *
     * <p>用法：{@code /rtg tunnels} 或 {@code /rtg tunnels 12}（单位：区块，默认 8，上限 24）。
     */
    static final class CommandTunnels extends CommandBase
    {
        private static final String NAME = "tunnels";

        @Override public int getRequiredPermissionLevel() { return ACCESS_ALL; }
        @Override public String getName() { return NAME; }
        @Override public String getUsage(ICommandSender sender) {
            return new TextComponentString("/rtg tunnels [半径(区块, 默认8)]").getFormattedText();
        }

        @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
            final World world = sender.getEntityWorld();
            final BlockPos pos;
            if (sender instanceof EntityPlayerMP) {
                pos = ((EntityPlayerMP) sender).getPosition();
            } else if (args.length >= 2) {
                try {
                    pos = new BlockPos(CommandBase.parseInt(args[0]), 0, CommandBase.parseInt(args[1]));
                } catch (Exception ex) {
                    line(sender, "[rtg] 坐标解析失败，用法：/rtg tunnels [半径]");
                    return;
                }
            } else {
                line(sender, "[rtg] tunnels 需由玩家执行（它按你的位置扫附近缓存）");
                return;
            }

            int radius = 8;
            if (args.length >= 1) {
                try {
                    radius = Math.max(1, Math.min(24, CommandBase.parseInt(args[0])));
                } catch (Exception ex) {
                    line(sender, "[rtg] 半径解析失败，用默认 8 区块");
                }
            }

            final net.minecraft.world.chunk.IChunkProvider cp = world.getChunkProvider();
            final net.minecraft.world.gen.IChunkGenerator generator =
                    cp instanceof net.minecraft.world.gen.ChunkProviderServer
                            ? ((net.minecraft.world.gen.ChunkProviderServer) cp).chunkGenerator
                            : null;
            if (!(generator instanceof rtg.world.gen.ChunkGeneratorRTG)) {
                line(sender, "[rtg] 该维度的区块生成器不是 ChunkGeneratorRTG，无法读取隧道数据。");
                return;
            }
            final rtg.world.gen.ChunkGeneratorRTG gen = (rtg.world.gen.ChunkGeneratorRTG) generator;

            final int centerCX = pos.getX() >> 4;
            final int centerCZ = pos.getZ() >> 4;
            int cachedChunks = 0;
            int carvedColumns = 0;
            // 只留最近的前几个（按切比雪夫距离排序）
            final java.util.List<int[]> hits = new java.util.ArrayList<>();   // {dist, x, z, ceiling}

            for (int dcx = -radius; dcx <= radius; dcx++) {
                for (int dcz = -radius; dcz <= radius; dcz++) {
                    final rtg.world.gen.ChunkLandscape ls = gen.cachedLandscape(centerCX + dcx, centerCZ + dcz);
                    if (ls == null) {
                        continue;                        // 未生成/已被 LRU 淘汰：跳过（只读，不新建）
                    }
                    cachedChunks++;
                    for (int k = 0; k < 256; k++) {
                        final int ceiling = ls.riverCaveCeiling[k];
                        if (ceiling <= 0) {
                            continue;                    // 该列没被开凿
                        }
                        carvedColumns++;
                        final int lx = k >> 4;
                        final int lz = k & 15;
                        final int wx = (centerCX + dcx) * 16 + lx;
                        final int wz = (centerCZ + dcz) * 16 + lz;
                        hits.add(new int[] {
                                Math.max(Math.abs(dcx), Math.abs(dcz)),
                                wx, wz, ceiling,
                                Math.max(Math.abs(wx - pos.getX()), Math.abs(wz - pos.getZ())) });
                    }
                }
            }

            line(sender, "=== /rtg tunnels @ %d,%d  半径 %d 区块 ===", pos.getX(), pos.getZ(), radius);
            line(sender, "[cache] 半径内已缓存的区块 %d 个（未生成的区块不在其中 —— 先在附近走走再执行）",
                    cachedChunks);
            line(sender, "[result] 被开凿过的列：%d 个", carvedColumns);

            if (carvedColumns == 0) {
                line(sender, "  ✗ 半径内没有一条地下河/洞厅。两种可能：");
                line(sender, "    ① 这里不是**山地链**（隧道是链专有地貌：极端气候交界带，约占陆地 11%%）；");
                line(sender, "    ② 是山地链但还没走到隧道带上（隧道沿**河网边界**走、洞厅在**河网交汇点**）。");
                line(sender, "    建议：往气候交界的大山方向走，让区块生成，再执行一次。");
                return;
            }

            hits.sort((a, b) -> a[4] != b[4] ? Integer.compare(a[4], b[4]) : a[0] - b[0]);
            line(sender, "[最近的地下河/洞厅（挖掘目标）]");
            final int shown = Math.min(8, hits.size());
            for (int i = 0; i < shown; i++) {
                final int[] h = hits.get(i);
                line(sender, "  %d) x=%d z=%d 洞顶 y=%d  直线距离 %d 格%s",
                        i + 1, h[1], h[2], h[3], h[4],
                        h[4] < 40 ? " ← 就在脚下附近" : "");
            }
            line(sender, "  挖法：站在目标点往下挖到 **洞顶 y** 就是了；洞顶以下是气道/暗河（%s）。",
                    "下层是水、上层是空气，与 RWG 一致");
        }
    }

}
