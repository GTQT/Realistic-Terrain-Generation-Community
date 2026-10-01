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
        // `/rtg tunnels` 已按用户要求移除（1.0.33 同版本追加）：定位地下河改回"顺着山里的河找"。
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
                line(sender, "[tunnel] 链权重=%.3f 链宿主=%.3f 山体门控=%.3f 干高度=%.0f 洞顶=%d",
                        t[0], t[1], t[5], t[4], (int) t[2]);
                if (!cached) {
                    line(sender, "  ⚠ 该区块的 layout 已被 LRU 缓存淘汰，此处是**重算**结果："
                            + "门控数值可信，洞顶不可信（重算不会开凿）。");
                } else if (t[2] > 0f) {
                    line(sender, "  ✅ 该列**确实开凿过**地下河隧道/洞厅，洞顶 y=%d。", (int) t[2]);
                } else {
                    line(sender, "  ✗ 该列**没有**被开凿：%s",
                            t[5] <= 0.10f
                                    ? "山体门控不足 ⇒ 既不是山地链、附近干高度也没到约 73（暗河只在山里出现）"
                                    : "山体门控够，但不在河网边界/顶点上（隧道沿河网走）");
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


}
