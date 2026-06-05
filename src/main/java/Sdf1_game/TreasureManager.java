package Sdf1_game;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;

import static Sdf1_game.TreasureInventory.isCustomItem;
import static Sdf1_game.TreasureInventory.parseMark;


public class TreasureManager {

    private static final String CONFIG_SUFFIX =
            ".sdf1.txt";


    private final Main plugin;
    private final Map<String, TreasureConfig>
            configs = new LinkedHashMap<>();
    private final Map<Location, String>
            activeChests = new LinkedHashMap<>();
    private static final String MARKER = "SDF1";
    private final Set<String> pendingReclaims =
            new HashSet<>();

    public Set<String> getPendingReclaims() {
        return pendingReclaims;
    }


    // ★ 领取记录
    private final File claimFile;
    private final Set<String> claimedSet =
            new HashSet<>();

    // ★ 边框
    private BukkitTask borderTask;
    private boolean borderShowing = false;
    private String borderRegionName;



    public TreasureManager(Main plugin) {
        this.plugin = plugin;
        // ★ 必须在构造器里初始化
        this.claimFile = new File(
                plugin.getDataFolder(),
                "寻宝/领取限制.txt");
        claimFile.getParentFile().mkdirs();
        loadClaims();
        // 构造器里加（最后一行）：
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                for (org.bukkit.entity.Player p :
                        plugin.getServer()
                                .getOnlinePlayers()) {
                    reclaimPlayerItems(p.getName());
                }
            }
        }.runTaskTimer(plugin, 600L, 600L);

    }

    /** 加载领取记录 */
    private void loadClaims() {
        claimedSet.clear();
        if (!claimFile.exists()) return;
        try {
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(
                            new FileInputStream(claimFile),
                            StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()
                        && !line.startsWith("#")) {
                    claimedSet.add(line);
                }
            }
            br.close();
        } catch (IOException e) {
            plugin.getLogger().warning(
                    "[寻宝] 加载领取记录失败");
        }
    }
    public void reload() {
        forceCleanClaims();
        for (org.bukkit.entity.Player p :
                plugin.getServer()
                        .getOnlinePlayers()) {
            reclaimAllTreasureItems(p);
        }
        removeAllChests();
        configs.clear();
        activeChests.clear();
        loadAll();
        plugin.getLogger().info(
                "[寻宝] ★重载完成，"
                        + configs.size() + " 个区域");
    }

    /** 回收玩家身上所有寻宝物品（重载/关闭时用） */
    public void reclaimAllTreasureItems(
            org.bukkit.entity.Player player) {
        if (player == null) return;
        int removed = 0;
        for (int i = 0;
             i < player.getInventory().getSize(); i++) {
            ItemStack item =
                    player.getInventory().getItem(i);
            if (item == null) continue;
            if (!TreasureInventory
                    .isCustomItem(item)) continue;
            player.getInventory().setItem(i, null);
            removed++;
        }
        if (removed > 0) {
            player.sendMessage(
                    "§c[寻宝] 插件重载，"
                            + removed
                            + "件寻宝物品已回收");
        }
    }

    public void migrateOldFiles() {
        File dir = new File(
                plugin.getDataFolder(), "寻宝");
        dir.mkdirs();

        File[] files = dir.listFiles(
                (d, n) -> n.endsWith(".txt")
                        && !n.endsWith(CONFIG_SUFFIX));

        if (files == null) return;

        // ★ 黑名单
        java.util.Set<String> blacklist =
                new java.util.HashSet<>();
        blacklist.add("领取限制");
        blacklist.add("配置说明");
        blacklist.add("设置");

        for (File f : files) {
            String baseName = f.getName()
                    .replace(".txt", "");

            // ★ 黑名单跳过
            if (blacklist.contains(baseName)) {
                continue;
            }

            try {
                BufferedReader br =
                        new BufferedReader(
                                new InputStreamReader(
                                        new FileInputStream(f),
                                        StandardCharsets.UTF_8));
                String first = br.readLine();
                br.close();

                if (first != null
                        && first.trim()
                        .startsWith("区域名")) {
                    String newName = baseName
                            + CONFIG_SUFFIX;
                    File newFile = new File(
                            f.getParent(), newName);
                    if (f.renameTo(newFile)) {
                        plugin.getLogger().info(
                                "[寻宝] 迁移: "
                                        + f.getName()
                                        + " → "
                                        + newName);
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }


    // ========== 加载 ==========

    public void loadAll() {
        configs.clear();
        File dir = new File(
                plugin.getDataFolder(), "寻宝");
        dir.mkdirs();

        File[] files = dir.listFiles(
                (d, n) -> n.endsWith(CONFIG_SUFFIX));

        if (files == null) return;

        // ★ 黑名单
        java.util.List<String> skip =
                java.util.Arrays.asList(
                        "领取限制",
                        "配置说明",
                        "设置");
        for (File f : files) {
            try {
                TreasureConfig tc = parseConfig(f);
                if (tc != null) {
                    configs.put(tc.name, tc);
                    loadSettings(tc);
                    configs.put(tc.name, tc);
                  plugin.getLogger().info(
                            "[寻宝] ★加载: " + tc.name
                                    + " 奖励数量="
                                    + tc.rewards.size());
                    // ★ 打印每条奖励
                 for (int i = 0;
                         i < tc.rewards.size(); i++) {
                        TreasureReward r =
                                tc.rewards.get(i);
                        plugin.getLogger().info(
                                "[寻宝]   奖励["
                                        + i + "]: "
                                        + r.getType()
                                        + " "
                                        + r.getMaterialName()
                                        + " x"
                                        + r.getAmount()
                                        + " 权重="
                                        + r.getWeight()
                                        + " 名="
                                        + r.getDisplayName());
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning(
                        "[寻宝] 失败: " + f.getName()
                                + " - " + e.getMessage());
                e.printStackTrace();
            }
        }
        plugin.getLogger().info(
                "[寻宝] 共" + configs.size()
                        + "个区域");
    }

    /** 扫描玩家背包，回收不属于自己的物品 + 攻击次数用完的物品 */
    public void reclaimPlayerItems(
            String playerName) {
        org.bukkit.entity.Player player =
                plugin.getServer()
                        .getPlayerExact(playerName);
        if (player == null) return;

        int removed = 0;
        for (int i = 0;
             i < player.getInventory()
                     .getSize(); i++) {
            ItemStack item =
                    player.getInventory()
                            .getItem(i);
            if (item == null) continue;
            if (!TreasureInventory
                    .isCustomItem(item)) continue;

            String[] mark =
                    TreasureInventory
                            .parseMark(item);
            if (mark == null) continue;

            // mark[0]=原始玩家, mark[1]=区域,
            // mark[2]=名称, mark[3]=上限, mark[4]=次数
            String owner = mark[0];
            int maxUse =
                    Integer.parseInt(mark[3]);
            int useCount =
                    Integer.parseInt(mark[4]);

            boolean notMine = !owner
                    .equalsIgnoreCase(playerName);
            boolean usedUp =
                    maxUse > 0
                            && useCount >= maxUse;

            if (notMine || usedUp) {
                player.getInventory()
                        .setItem(i, null);
                removeClaim(owner, mark[2],
                        mark[1]);
                removed++;
                if (notMine) {
                    player.sendMessage(
                            "§c[寻宝] " + mark[2]
                                    + " 不属于你，已回收");
                }
            }
        }

        if (removed > 0) {
            plugin.getLogger().info(
                    "[寻宝] 回收: "
                            + playerName
                            + " " + removed + "件");
        }
    }


    /** ★ 统一标点：中文→英文 */
    private String normalizePunctuation(String s) {
        return s
                .replace("：", ":")
                .replace("；", ";")
                .replace("，", ",")
                .replace("。", ".")
                .replace("（", "(")
                .replace("）", ")")
                .replace("【", "[")
                .replace("】", "]")
                .replace("！", "!")
                .replace("？", "?")
                .replace("～", "~")
                .replace("、", ",")
                .replace("＝", "=")
                .replace("＋", "+")
                .replace("－", "-")
                .replace("　", " ")
                .replace("\uFEFF", "");
    }


    private TreasureConfig parseConfig(File f)
            throws IOException {

        TreasureConfig tc = new TreasureConfig(
                f.getName().replace(".txt", "")
                        .replace(CONFIG_SUFFIX, ""));

        java.util.List<String> lines =
                new java.util.ArrayList<>();

        BufferedReader br = new BufferedReader(
                new InputStreamReader(
                        new FileInputStream(f),
                        StandardCharsets.UTF_8));
        String raw;
        while ((raw = br.readLine()) != null) {
            lines.add(raw);
        }
        br.close();

        plugin.getLogger().info(
                "[寻宝] 文件共 " + lines.size()
                        + " 行: " + f.getName());

        for (int idx = 0;
             idx < lines.size(); idx++) {

            String rawLine = lines.get(idx);
            int lineNum = idx + 1;

            // ★ 打印原始行
            plugin.getLogger().info(
                    "[寻宝] 行" + lineNum
                            + ": [" + rawLine + "]");

            String line = rawLine.trim();
            if (line.isEmpty()
                    || line.startsWith("#")) {
                plugin.getLogger().info(
                        "[寻宝] → 跳过");
                continue;
            }

            // ★ 统一标点
            line = normalizePunctuation(line);

            int ci = line.indexOf(':');
            if (ci < 0) {
                plugin.getLogger().warning(
                        "[寻宝] ★无冒号: " + line);
                continue;
            }

            String k = line.substring(0, ci).trim();
            String v = line.substring(ci + 1).trim();

            plugin.getLogger().info(
                    "[寻宝] key=[" + k
                            + "] val=[" + v + "]");

            switch (k) {
                case "区域名":
                    tc.name = v;
                    break;
                case "世界":
                    tc.world = v;
                    break;
                case "坐标":
                    String[] pp = v.split(",");
                    if (pp.length == 4) {
                        int a = Integer.parseInt(
                                pp[0].trim());
                        int b = Integer.parseInt(
                                pp[1].trim());
                        int c = Integer.parseInt(
                                pp[2].trim());
                        int d = Integer.parseInt(
                                pp[3].trim());
                        tc.minX = Math.min(a, c);
                        tc.maxX = Math.max(a, c);
                        tc.minZ = Math.min(b, d);
                        tc.maxZ = Math.max(b, d);
                    }
                    break;
                case "高度":
                    String[] yp = v.split(",");
                    if (yp.length == 2) {
                        tc.minY = Math.min(
                                Integer.parseInt(
                                        yp[0].trim()),
                                Integer.parseInt(
                                        yp[1].trim()));
                        tc.maxY = Math.max(
                                Integer.parseInt(
                                        yp[0].trim()),
                                Integer.parseInt(
                                        yp[1].trim()));
                    }
                    break;
                case "最多宝箱":
                    tc.maxChests =
                            Integer.parseInt(v.trim());
                    break;
                case "检查间隔":
                    tc.spawnInterval =
                            parseTime(v);
                    break;
                case "生成概率":
                    tc.spawnChance =
                            Integer.parseInt(v.trim());
                    break;
                case "随机高度":
                    tc.randomY = v.equals("是")
                            || v.equals("true");
                    break;
                case "奖励":
                    try {
                        TreasureReward rr =
                                parseReward(v);
                        if (rr != null) {
                            tc.rewards.add(rr);
                            int idx1 =
                                    tc.rewards.size() - 1;
                            // ★ 记录中文名
                            tc.rewardNames.put(idx,
                                    rr.getDisplayName());
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning(
                                "[寻宝] 奖励异常: "
                                        + e.getMessage());
                    }
                    break;

                default:
                    plugin.getLogger().info(
                            "[寻宝] 未知key: ["
                                    + k + "]");
                    break;
            }
        }

        plugin.getLogger().info(
                "[寻宝] 解析完成: "
                        + tc.name
                        + " 奖励=" + tc.rewards.size());
        return tc;
    }

    private int parseTime(String s) {
        s = s.trim().toLowerCase();
        if (s.matches("\\d+"))
            return Integer.parseInt(s);
        if (s.endsWith("分钟") || s.endsWith("m")) {
            return Integer.parseInt(
                    s.replaceAll("[^\\d]", "")) * 60;
        }
        if (s.endsWith("小时") || s.endsWith("h")) {
            return Integer.parseInt(
                    s.replaceAll("[^\\d]", "")) * 3600;
        }
        if (s.endsWith("秒") || s.endsWith("s")) {
            return Integer.parseInt(
                    s.replaceAll("[^\\d]", ""));
        }
        try { return Integer.parseInt(s); }
        catch (Exception e) { return 300; }
    }


    /** 显示已保存区域的粒子边框 */
    public boolean showRegionBorder(String name) {
        TreasureConfig tc = configs.get(name);
        if (tc == null) return false;

        World w = plugin.getServer()
                .getWorld(tc.world);
        if (w == null) return false;

        hideRegionBorder();
        normalize(tc);

        borderShowing = true;
        borderRegionName = name;

        final int minX = tc.minX;
        final int maxX = tc.maxX;
        final int minZ = tc.minZ;
        final int maxZ = tc.maxZ;

        plugin.getLogger().info(
                "[寻宝] 显示边框: " + name
                        + " X=" + minX + "~" + maxX
                        + " Z=" + minZ + "~" + maxZ);

        borderTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!borderShowing) return;

                List<Location> points =
                        new ArrayList<>();

                for (int x = minX; x <= maxX; x++) {
                    points.add(getP(w, x, minZ));
                    points.add(getP(w, x, maxZ));
                }
                for (int z = minZ + 1; z < maxZ; z++) {
                    points.add(getP(w, minX, z));
                    points.add(getP(w, maxX, z));
                }

                for (Player p : w.getPlayers()) {
                    int px = p.getLocation()
                            .getBlockX();
                    int pz = p.getLocation()
                            .getBlockZ();
                    if (px >= minX - 50
                            && px <= maxX + 50
                            && pz >= minZ - 50
                            && pz <= maxZ + 50) {
                        for (Location loc : points) {
                            p.spawnParticle(
                                    Particle.FLAME,
                                    loc, 1, 0, 0, 0, 0);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);

        return true;
    }

    public void removeAllChests() {
        int count = 0;
        java.util.Iterator<Location> it =
                activeChests.keySet().iterator();
        while (it.hasNext()) {
            Location loc = it.next();
            it.remove();
            if (loc.getWorld() == null) continue;
            if (loc.getBlock().getType()
                    == Material.CHEST) {
                loc.getBlock()
                        .setType(Material.AIR);
                count++;
            }
        }
        if (count > 0) {
            plugin.getLogger().info(
                    "[寻宝] 收回 " + count + " 个");
        }
        // ★ 不清除领取记录
    }

    public void forceCleanClaims() {
        // ★ 暴力方案：直接删文件再重建
        if (claimFile.exists()) {
            boolean deleted = claimFile.delete();
            plugin.getLogger().info(
                    "[寻宝] ★删除领取记录: "
                            + deleted);
        }
        try {
            claimFile.getParentFile().mkdirs();
            claimFile.createNewFile();
            plugin.getLogger().info(
                    "[寻宝] ★重建领取记录完成");
        } catch (IOException e) {
            plugin.getLogger().warning(
                    "[寻宝] ★重建失败: "
                            + e.getMessage());
        }
        claimedSet.clear();
    }


    // ===== NBT 标记宝箱 =====

    /** 生成宝箱时写入NBT标记 */
    private void tagChest(Location loc,
                          String regionName) {
        org.bukkit.block.Block block =
                loc.getBlock();
        if (!(block.getState()
                instanceof org.bukkit.block.TileState))
            return;
        org.bukkit.block.TileState state =
                (org.bukkit.block.TileState)
                        block.getState();
        org.bukkit.persistence
                .PersistentDataContainer pdc =
                state.getPersistentDataContainer();
        org.bukkit.NamespacedKey kMarker =
                new org.bukkit.NamespacedKey(
                        plugin, "sdf1_marker");
        org.bukkit.NamespacedKey kTime =
                new org.bukkit.NamespacedKey(
                        plugin, "sdf1_time");
        org.bukkit.NamespacedKey kRegion =
                new org.bukkit.NamespacedKey(
                        plugin, "sdf1_region");
        pdc.set(kMarker,
                org.bukkit.persistence
                        .PersistentDataType.BOOLEAN,
                true);
        pdc.set(kTime,
                org.bukkit.persistence
                        .PersistentDataType.LONG,
                System.currentTimeMillis());
        pdc.set(kRegion,
                org.bukkit.persistence
                        .PersistentDataType.STRING,
                regionName);
        state.update(true);
    }

    /** 检查箱子是否是我们的 */
    private boolean isOurChest(Location loc) {
        org.bukkit.block.Block block =
                loc.getBlock();
        if (!(block.getState()
                instanceof org.bukkit.block.TileState))
            return false;
        org.bukkit.block.TileState state =
                (org.bukkit.block.TileState)
                        block.getState();
        org.bukkit.persistence
                .PersistentDataContainer pdc =
                state.getPersistentDataContainer();
        org.bukkit.NamespacedKey kMarker =
                new org.bukkit.NamespacedKey(
                        plugin, "sdf1_marker");
        return pdc.has(kMarker,
                org.bukkit.persistence
                        .PersistentDataType.BOOLEAN);
    }

    /** 读取箱子的区域名 */
    private String getChestRegion(Location loc) {
        org.bukkit.block.Block block =
                loc.getBlock();
        if (!(block.getState()
                instanceof org.bukkit.block.TileState))
            return null;
        org.bukkit.block.TileState state =
                (org.bukkit.block.TileState)
                        block.getState();
        org.bukkit.persistence
                .PersistentDataContainer pdc =
                state.getPersistentDataContainer();
        org.bukkit.NamespacedKey kRegion =
                new org.bukkit.NamespacedKey(
                        plugin, "sdf1_region");
        return pdc.getOrDefault(kRegion,
                org.bukkit.persistence
                        .PersistentDataType.STRING,
                "");
    }

    /** 读取箱子生成时间 */
    private long getChestTime(Location loc) {
        org.bukkit.block.Block block =
                loc.getBlock();
        if (!(block.getState()
                instanceof org.bukkit.block.TileState))
            return 0;
        org.bukkit.block.TileState state =
                (org.bukkit.block.TileState)
                        block.getState();
        org.bukkit.persistence
                .PersistentDataContainer pdc =
                state.getPersistentDataContainer();
        org.bukkit.NamespacedKey kTime =
                new org.bukkit.NamespacedKey(
                        plugin, "sdf1_time");
        return pdc.getOrDefault(kTime,
                org.bukkit.persistence
                        .PersistentDataType.LONG,
                0L);
    }

    // ★ 1. parseChineseReward
    private TreasureReward parseChineseReward(
            String line) {
        try {
            return parseChineseRewardInternal(line);
        } catch (Exception e) {
            plugin.getLogger().warning(
                    "[寻宝] ★中文解析异常: " + line
                            + " 错误: " + e.getMessage());
            return null;
        }
    }
    private TreasureReward parseChineseRewardInternal(
            String line) {
        plugin.getLogger().info(
                "[寻宝] 中文解析: " + line);

        // 1. 提取物品名
        String itemName = line
                .replace("给予玩家", "")
                .replace("给玩家", "")
                .replace("给予", "")
                .replace("给", "")
                .trim();

        String cleanName = itemName
                .replaceAll(
                        "[一二三四五六七八九十\\d]+[个根把件套只]",
                        "")
                .trim();

        // 物品匹配
        String[][] items = {
                {"旋风棒", "BREEZE_ROD"},
                {"烈焰棒", "BLAZE_ROD"},
                {"附魔金苹果", "ENCHANTED_GOLDEN_APPLE"},
                {"金苹果", "GOLDEN_APPLE"},
                {"下界合金剑", "NETHERITE_SWORD"},
                {"下界合金镐", "NETHERITE_PICKAXE"},
                {"下界合金锭", "NETHERITE_INGOT"},
                {"钻石剑", "DIAMOND_SWORD"},
                {"钻石镐", "DIAMOND_PICKAXE"},
                {"钻石斧", "DIAMOND_AXE"},
                {"钻石甲", "DIAMOND_CHESTPLATE"},
                {"钻石靴子", "DIAMOND_BOOTS"},
                {"钻石护腿", "DIAMOND_LEGGINGS"},
                {"钻石头盔", "DIAMOND_HELMET"},
                {"末影珍珠", "ENDER_PEARL"},
                {"绿宝石", "EMERALD"},
                {"金锭", "GOLD_INGOT"},
                {"铁剑", "IRON_SWORD"},
                {"铁锭", "IRON_INGOT"},
                {"钻石", "DIAMOND"},
                {"木棍", "STICK"},
                {"木剑", "WOODEN_SWORD"},
                {"弓", "BOW"},
                {"箭", "ARROW"},
                {"盾牌", "SHIELD"},
                {"面包", "BREAD"},
                {"不死图腾", "TOTEM_OF_UNDYING"},
                {"煤炭", "COAL"},
                {"红石", "REDSTONE"}
        };

        String matName = null;
        for (String[] pair : items) {
            if (cleanName.contains(pair[0])) {
                matName = pair[1];
                break;
            }
        }
        if (matName == null) {
            plugin.getLogger().warning(
                    "[寻宝] ★无法识别: " + line);
            return null;
        }
        matName = matName.toUpperCase();



        // 2. 数量
        int amount = 1;
        java.util.regex.Matcher am =
                java.util.regex.Pattern
                        .compile(
                                "[一二三四五六七八九十\\d]+[个根把件套只]")
                        .matcher(itemName);
        if (am.find()) {
            String n = am.group()
                    .replaceAll(
                            "[个根把件套只]", "");
            amount = parseChineseNumber(n);
        }

        // 3. ★ 泛解析附魔（全行扫描）
        String[] enchDefs = {
                "水下速掘", "aqua_affinity",
                "节肢杀手", "bane_of_arthropods",
                "爆炸保护", "blast_protection",
                "破甲", "breach",
                "引雷", "channeling",
                "绑定诅咒", "binding_curse",
                "消失诅咒", "vanishing_curse",
                "致密", "density",
                "深海探索者", "depth_strider",
                "效率", "efficiency",
                "掉落缓冲", "feather_falling",
                "火焰附加", "fire_aspect",
                "火焰保护", "fire_protection",
                "火矢", "flame",
                "时运", "fortune",
                "冰霜行者", "frost_walker",
                "穿刺", "impaling",
                "无限", "infinity",
                "击退", "knockback",
                "抢夺", "looting",
                "忠诚", "loyalty",
                "海之眷顾", "luck_of_the_sea",
                "突进", "lure",
                "经验修补", "mending",
                "多重射击", "multishot",
                "穿透", "piercing",
                "力量", "power",
                "弹射物保护", "projectile_protection",
                "保护", "protection",
                "冲击", "punch",
                "快速装填", "quick_charge",
                "水下呼吸", "respiration",
                "激流", "riptide",
                "锋利", "sharpness",
                "精准采集", "silk_touch",
                "亡灵杀手", "smite",
                "灵魂疾行", "soul_speed",
                "横扫之刃", "sweeping_edge",
                "迅捷潜行", "swift_sneak",
                "荆棘", "thorns",
                "耐久", "unbreaking",
                "风暴", "wind_burst",
                "风爆", "wind_burst",

        };

        List<String> enchIds = new ArrayList<>();
        List<Integer> enchLvls = new ArrayList<>();

        // ★ 用"与"分割多个附魔
        String[] segments = line
                .replace("与", "|")
                .replace("、", "|")
                .replace(",", "|")
                .replace(";", "|")
                .split("\\|");

        for (String seg : segments) {
            String segTrim = seg.trim();

            // ★ 先用全行匹配所有附魔
            for (int i = 0; i < enchDefs.length;
                 i += 2) {
                String cn = enchDefs[i];
                String id = enchDefs[i + 1];

                if (!line.contains(cn)) continue;

                int lvl = 0;

                // 方式1: "附魔火焰附加,等级255"
                // → "等级255"在附魔名之后的文本中
                java.util.regex.Matcher m1 =
                        java.util.regex.Pattern
                                .compile(
                                        cn + "[，,]?\\s*等级?\\s*[：:]?\\s*(\\d+)")
                                .matcher(line);
                if (m1.find()) {
                    lvl = Integer.parseInt(
                            m1.group(1));
                }

                // 方式2: "附魔击退255" 紧跟数字
                if (lvl <= 0) {
                    java.util.regex.Matcher m2 =
                            java.util.regex.Pattern
                                    .compile(
                                            cn + "\\s*(\\d+)")
                                    .matcher(line);
                    if (m2.find()) {
                        lvl = Integer.parseInt(
                                m2.group(1));
                    }
                }

                // 方式3: "255级火焰附加"
                if (lvl <= 0) {
                    java.util.regex.Matcher m3 =
                            java.util.regex.Pattern
                                    .compile(
                                            "(\\d+)\\s*级?"
                                                    + cn)
                                    .matcher(line);
                    if (m3.find()) {
                        lvl = Integer.parseInt(
                                m3.group(1));
                    }
                }

                // 方式4: "锋利255与风暴3" 分割后
                // 在整个line中找该附魔名后最近的数字
                if (lvl <= 0) {
                    int pos = line.indexOf(cn);
                    if (pos >= 0) {
                        String after =
                                line.substring(
                                        pos + cn.length());
                        java.util.regex.Matcher m4 =
                                java.util.regex.Pattern
                                        .compile(
                                                "\\D*(\\d+)")
                                        .matcher(after);
                        if (m4.find()) {
                            int val = Integer.parseInt(
                                    m4.group(1));
                            if (val > 0 && val <= 255) {
                                lvl = val;
                            }
                        }
                    }
                }

                if (lvl <= 0) lvl = 1;
                enchIds.add(id);
                enchLvls.add(lvl);
            }
        }


            // ★ 额外安全网：直接全行扫数字
        //   如果上面没匹配到但全行有附魔名+数字
        if (enchIds.isEmpty()) {
            for (int i = 0; i < enchDefs.length;
                 i += 2) {
                String cn = enchDefs[i];
                String id = enchDefs[i + 1];
                if (!line.contains(cn)) continue;

                java.util.regex.Matcher m =
                        java.util.regex.Pattern
                                .compile(
                                        "(\\d+)")
                                .matcher(line);
                int lvl = 0;
                while (m.find()) {
                    int val = Integer.parseInt(
                            m.group(1));
                    if (val > 0 && val <= 255) {
                        lvl = val;
                        break;
                    }
                }
                if (lvl <= 0) lvl = 1;
                enchIds.add(id);
                enchLvls.add(lvl);
            }
        }

        StringBuilder enchSB = new StringBuilder();
        for (int i = 0; i < enchIds.size(); i++) {
            if (i > 0) enchSB.append(";");
            enchSB.append(enchIds.get(i))
                    .append(",")
                    .append(enchLvls.get(i));
        }
        String enchant = enchSB.length() > 0
                ? enchSB.toString() : null;

        // 4. 时长
        int duration = 0;
        java.util.regex.Matcher dm =
                java.util.regex.Pattern
                        .compile(
                                "时长[：:]?\\s*([^,，。]+)")
                        .matcher(line);
        if (dm.find()) {
            duration = TreasureReward
                    .parseDuration(
                            dm.group(1).trim());
        }

        // 5. 攻击次数
        int attackLimit = 0;
        java.util.regex.Matcher atm =
                java.util.regex.Pattern
                        .compile(
                                "攻击[^：:]*[：:]?\\s*(\\d+)")
                        .matcher(line);
        if (atm.find()) {
            attackLimit = Integer.parseInt(
                    atm.group(1));
        }

        plugin.getLogger().info(
                "[寻宝] ★结果: "
                        + matName + " x" + amount
                        + " 附魔=" + enchant
                        + " 时长=" + duration
                        + " 攻击=" + attackLimit);

        return new TreasureReward(
                TreasureReward.Type.ITEM,
                matName, amount, 0, null, 10,
                "§b" + cleanName,
                duration, enchant, null,
                attackLimit);
    }

    // ★ 2. isClaimed
    public boolean isClaimed(String player,
                             String itemName,
                             String regionName) {
        return claimedSet.contains(
                player + "|" + itemName
                        + "|" + regionName);
    }

    // ★ 3. addClaim
    public void addClaim(String player,
                         String itemName,
                         String regionName) {
        String key = player + "|" + itemName
                + "|" + regionName;
        if (claimedSet.contains(key)) return;
        claimedSet.add(key);
        try {
            OutputStreamWriter w =
                    new OutputStreamWriter(
                            new FileOutputStream(
                                    claimFile, true),
                            StandardCharsets.UTF_8);
            w.write(key + "\n");
            w.close();
        } catch (IOException e) {
            plugin.getLogger().warning(
                    "[寻宝] 写入记录失败");
        }
    }

    // ★ 4. removeClaim
    public void removeClaim(String player,
                            String itemName,
                            String regionName) {
        String key = player + "|" + itemName
                + "|" + regionName;
        claimedSet.remove(key);
        rewriteClaimsFile();
    }

    // ★ 5. rewriteClaimsFile
    private void rewriteClaimsFile() {
        try {
            OutputStreamWriter w =
                    new OutputStreamWriter(
                            new FileOutputStream(
                                    claimFile),
                            StandardCharsets.UTF_8);
            for (String e : claimedSet)
                w.write(e + "\n");
            w.close();
        } catch (IOException ignored) {
        }
    }

    private int parseChineseNumber(String s) {
        s = s.trim();
        switch (s) {
            case "一": case "1": return 1;
            case "二": case "两": case "2": return 2;
            case "三": case "3": return 3;
            case "四": case "4": return 4;
            case "五": case "5": return 5;
            case "六": case "6": return 6;
            case "七": case "7": return 7;
            case "八": case "8": return 8;
            case "九": case "9": return 9;
            case "十": case "10": return 10;
            default:
                try { return Integer.parseInt(s); }
                catch (Exception e) { return 1; }
        }
    }

    private String matchChineseEnchant(String name) {
        name = name.trim();
        switch (name) {
            case "击退": return "KNOCKBACK";
            case "锋利": return "SHARPNESS";
            case "火焰附加": return "FIRE_ASPECT";
            case "抢夺": return "LOOTING";
            case "效率": return "EFFICIENCY";
            case "时运": return "FORTUNE";
            case "耐久": return "UNBREAKING";
            case "经验修补": return "MENDING";
            case "保护": return "PROTECTION";
            case "力量": return "POWER";
            case "火矢": return "FLAME";
            case "无限": return "INFINITY";
            case "忠诚": return "LOYALTY";
            default: return name.toUpperCase().replace(" ", "_");
        }
    }

    // ★ 只在玩家攻击玩家时计数
    // ★ 只拦截自定义物品放入容器
    @org.bukkit.event.EventHandler(priority =
            org.bukkit.event.EventPriority.HIGH)
    public void onClick(
            org.bukkit.event.inventory
                    .InventoryClickEvent e) {
        if (e.getWhoClicked()
                instanceof org.bukkit.entity.Player) {
            org.bukkit.entity.Player p =
                    (org.bukkit.entity.Player)
                            e.getWhoClicked();

            // ★ 检查光标上的物品
            if (e.getCursor() != null
                    && TreasureInventory
                    .isCustomItem(
                            e.getCursor())) {
                String[] mark =
                        TreasureInventory.parseMark(
                                e.getCursor());
                if (mark != null
                        && !mark[0]
                        .equalsIgnoreCase(
                                p.getName())) {
                    // 不是本人的→立即回收
                    e.setCancelled(true);
                    e.getCursor().setAmount(0);
                    p.sendMessage(
                            "§c[寻宝] 该物品不属于你");
                    return;
                }
                // ★ 光标有自定义物品+目标是容器→拦截
                Inventory top = e.getView()
                        .getTopInventory();
                InventoryType type = top.getType();
                if (type != InventoryType.PLAYER
                        && type != InventoryType.CRAFTING
                        && type != InventoryType.CREATIVE) {
                    e.setCancelled(true);
                    p.sendMessage(
                            "§c[寻宝] 该物品不可放入容器");
                    return;
                }
            }

            // ★ 检查被点击槽位的物品
            ItemStack clicked = e.getCurrentItem();
            if (clicked != null
                    && TreasureInventory
                    .isCustomItem(clicked)) {
                String[] mark =
                        TreasureInventory.parseMark(
                                clicked);
                if (mark != null
                        && !mark[0]
                        .equalsIgnoreCase(
                                p.getName())) {
                    // 槽位物品不属于本人→回收
                    e.setCancelled(true);
                    e.getCurrentItem().setAmount(0);
                    p.sendMessage(
                            "§c[寻宝] 该物品不属于你");
                }
            }
        }
    }

    // ★ 拖拽拦截
    @org.bukkit.event.EventHandler(priority =
            org.bukkit.event.EventPriority.HIGH)
    public void onDrag(
            org.bukkit.event.inventory
                    .InventoryDragEvent e) {
        if (!(e.getWhoClicked()
                instanceof org.bukkit.entity.Player))
            return;
        org.bukkit.entity.Player p =
                (org.bukkit.entity.Player)
                        e.getWhoClicked();
        for (ItemStack item :
                e.getNewItems().values()) {
            if (TreasureInventory
                    .isCustomItem(item)) {
                InventoryType type = e.getView()
                        .getTopInventory()
                        .getType();
                if (type != InventoryType.PLAYER
                        && type != InventoryType.CRAFTING) {
                    e.setCancelled(true);
                    p.sendMessage(
                            "§c[寻宝] 该物品不可放入容器");
                    return;
                }
            }
        }
    }

    // ★ 潜影盒/容器右键拦截
    @org.bukkit.event.EventHandler(priority =
            org.bukkit.event.EventPriority.HIGH)
    public void onInteract(
            org.bukkit.event.player
                    .PlayerInteractEvent e) {
        if (e.getItem() == null) return;
        if (!TreasureInventory
                .isCustomItem(e.getItem())) return;
        if (!e.getAction().name()
                .contains("RIGHT_CLICK")) return;
        if (e.getClickedBlock() == null) return;
        String btype = e.getClickedBlock()
                .getType().name();
        if (btype.contains("SHULKER")
                || btype.contains("CHEST")
                || btype.equals("BARREL")
                || btype.equals("ENDER_CHEST")
                || btype.equals("HOPPER")
                || btype.equals("DROPPER")
                || btype.equals("DISPENSER")) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(
                    "§c[寻宝] 该物品不可放入容器");
        }
    }

    // ★ 铁砧拦截
    @org.bukkit.event.EventHandler(priority =
            org.bukkit.event.EventPriority.HIGH)
    public void onAnvil(
            org.bukkit.event.inventory
                    .PrepareAnvilEvent e) {
        ItemStack first =
                e.getInventory().getItem(0);
        if (first != null
                && TreasureInventory
                .isCustomItem(first)) {
            e.setResult(null);
        }
    }

    /** 隐藏边框 */
    public boolean hideRegionBorder() {
        if (borderTask != null) {
            borderTask.cancel();
            borderTask = null;
        }
        borderShowing = false;
        borderRegionName = null;
        return true;
    }

    /** 按名字隐藏（兼容旧调用） */
    public boolean hideRegionBorder(String name) {
        if (name == null
                || name.equals(borderRegionName)) {
            return hideRegionBorder();
        }
        return false;
    }

    /** 检查边框是否在显示 */
    public boolean isBorderVisible(String name) {
        return borderShowing
                && name.equals(borderRegionName);
    }

    private Location getP(World w, int x, int z) {
        int y = w.getHighestBlockYAt(x, z) + 2;
        return new Location(w, x + 0.5, y, z + 0.5);
    }

    /**
     * 格式:
     *   DIAMOND,1,10
     *   债券,50,10
     *   命令,give {player} diamond 1,5
     *   贴标:STICK,1,60,KNOCKBACK,2,鸡腿棒,10
     *         物品,数量,权重,附魔,等级,显示名,时长秒
     */
    private TreasureReward parseReward(String line) {
        plugin.getLogger().info(
                "[寻宝] ★parseReward收到: ["
                        + line + "]");
        line = normalizePunctuation(line);
        plugin.getLogger().info(
                "[寻宝] ★parseReward归一化: ["
                        + line + "]");
        line = normalizePunctuation(line);
        if (line.charAt(0) == '\uFEFF')
            line = line.substring(1);
        line = line.replace("，", ",")
                .replace("：", ":");

        if (line.startsWith("贴标:")) {
            return parseCustomItem(
                    line.substring("贴标:".length()));
        }
        plugin.getLogger().info(
                "[寻宝] 解析奖励: " + line);

        if (line.startsWith("贴标:")) {
            return parseCustomItem(
                    line.substring("贴标:".length()));
        }

        if (line.contains("给予")
                || line.contains("附魔")
                || line.contains("时长")) {
            plugin.getLogger().info(
                    "[寻宝] → 走中文分支");
            return parseChineseReward(line);
        }

        String[] p = line.split(",");
        if (p.length < 2) return null;
        String type = p[0].trim().toUpperCase();

        // ★ 债券
        if (type.equals("债券")) {
            String amtStr = p[1].trim();
            int w = p.length > 2
                    ? Integer.parseInt(p[2].trim())
                    : 10;
            int bMin, bMax;
            if (amtStr.contains("~")) {
                String[] r = amtStr.split("~");
                bMin = Integer.parseInt(r[0].trim());
                bMax = Integer.parseInt(r[1].trim());
            } else if (amtStr.contains("-")) {
                String[] r = amtStr.split("-");
                bMin = Integer.parseInt(r[0].trim());
                bMax = Integer.parseInt(r[1].trim());
            } else {
                bMin = Integer.parseInt(amtStr);
                bMax = bMin;
            }
            TreasureReward tr = new TreasureReward(
                    TreasureReward.Type.BOND,
                    null, 1, bMin, null, w,
                    "§6" + bMin + "~" + bMax + "债券");
            tr.setBondRange(bMin, bMax);
            return tr;
        }

        // ★ 命令
        if (type.equals("命令")) {
            String cmd = p[1].trim();
            int w = p.length > 2
                    ? Integer.parseInt(p[2].trim())
                    : 10;
            return new TreasureReward(
                    TreasureReward.Type.COMMAND,
                    null, 1, 0, cmd, w,
                    "§a命令奖励");
        }

        // ★ 物品
        int amt = Integer.parseInt(p[1].trim());
        int w = p.length > 2
                ? Integer.parseInt(p[2].trim())
                : 10;
//打好包了，直接上传测试即可
        // ★ 中文名映射
        String[][] cnNames = {
                {"DIAMOND", "钻石"},
                {"GOLD_INGOT", "金锭"},
                {"IRON_INGOT", "铁锭"},
                {"EMERALD", "绿宝石"},
                {"COAL", "煤炭"},
                {"REDSTONE", "红石"},
                {"DIAMOND_SWORD", "钻石剑"},
                {"DIAMOND_PICKAXE", "钻石镐"},
                {"DIAMOND_AXE", "钻石斧"},
                {"DIAMOND_CHESTPLATE", "钻石甲"},
                {"DIAMOND_BOOTS", "钻石靴子"},
                {"DIAMOND_LEGGINGS", "钻石护腿"},
                {"DIAMOND_HELMET", "钻石头盔"},
                {"NETHERITE_SWORD", "下界合金剑"},
                {"NETHERITE_PICKAXE", "下界合金镐"},
                {"NETHERITE_INGOT", "下界合金锭"},
                {"BLAZE_ROD", "烈焰棒"},
                {"BREEZE_ROD", "旋风棒"},
                {"ENDER_PEARL", "末影珍珠"},
                {"GOLDEN_APPLE", "金苹果"},
                {"ENCHANTED_GOLDEN_APPLE",
                        "附魔金苹果"},
                {"TOTEM_OF_UNDYING", "不死图腾"},
                {"BOW", "弓"},
                {"ARROW", "箭"},
                {"SHIELD", "盾牌"},
                {"BREAD", "面包"},
                {"STICK", "木棍"},
                {"IRON_SWORD", "铁剑"},
                {"WOODEN_SWORD", "木剑"},
        };

        String cnName = type;
        for (String[] pair : cnNames) {
            if (pair[0].equals(type)) {
                cnName = pair[1];
                break;
            }
        }

        String displayName =
                "§f" + cnName + " x" + amt;

        return new TreasureReward(
                TreasureReward.Type.ITEM,
                type, amt, 0, null, w, displayName);
    }


    public void loadSettings(TreasureConfig tc) {

        File f = new File(
                plugin.getDataFolder(),
                "寻宝/设置.txt");
        if (!f.exists()) {
            File dir = new File(
                    plugin.getDataFolder(),
                    "寻宝");
            FileGenerator
                    .ensureTreasureConfig(dir);
        }
        if (!f.exists()) return;
        try (BufferedReader br =
                     new BufferedReader(
                             new InputStreamReader(
                                     new FileInputStream(f),
                                     StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()
                        || line.startsWith("#"))
                    continue;
                line = normalizePunctuation(line);
                int ci = line.indexOf(':');
                if (ci < 0) continue;
                String k = line.substring(0, ci)
                        .trim();
                String v = line.substring(ci + 1)
                        .trim();
                switch (k) {
                    case "保底债券":
                        tc.guaranteeBond =
                                v.equals("是")
                                        || v.equals("true");
                        break;
                    case "随机数量":
                        String[] rr =
                                v.split("[~\\-]");
                        if (rr.length == 2) {
                            tc.randomMin =
                                    Integer.parseInt(
                                            rr[0].trim());
                            tc.randomMax =
                                    Integer.parseInt(
                                            rr[1].trim());
                        }
                        break;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning(
                    "[寻宝] 设置加载失败: "
                            + e.getMessage());
        }
        plugin.getLogger().info(
                "[寻宝] 设置: 保底="
                        + tc.guaranteeBond
                        + " 随机=" + tc.randomMin
                        + "~" + tc.randomMax);
    }

    /**
     * 贴标物品解析
     * 格式: 物品ID,数量,权重,附魔ID,附魔等级,显示名,时长秒
     * 例: STICK,1,60,KNOCKBACK,2,鸡腿棒,3600
     */
    private TreasureReward parseCustomItem(String line) {
        String[] p = line.split(",");
        if (p.length < 6) return null;

        String matName = p[0].trim();
        int amt = Integer.parseInt(p[1].trim());
        int weight = Integer.parseInt(p[2].trim());
        String enchId = p[3].trim();
        int enchLvl = Integer.parseInt(p[4].trim());
        String displayName = p[5].trim();
        int duration = p.length >= 7
                ? parseTime(p[6].trim()) : 3600;

        Material mat =
                Material.matchMaterial(matName);
        if (mat == null) mat = Material.STICK;

        String enchant = enchId + "," + enchLvl;
        String lore = "§7" + displayName;

        return new TreasureReward(
                TreasureReward.Type.ITEM,
                matName, amt, 0, null, weight,
                "§b" + displayName,
                duration, enchant, lore);
    }


    // ========== 保存区域 ==========

    public boolean saveRegion(String name,
                              String world,
                              int x1, int z1,
                              int x2, int z2,
                              int minY, int maxY) {
        int a = Math.min(x1, x2);
        int b = Math.max(x1, x2);
        x1 = a; x2 = b;
        a = Math.min(z1, z2);
        b = Math.max(z1, z2);
        z1 = a; z2 = b;

        File dir = new File(
                plugin.getDataFolder(), "寻宝");
        dir.mkdirs();
        File f = new File(dir, name + ".txt");

        List<String> lines = new ArrayList<>();
        lines.add("区域名: " + name);
        lines.add("世界: " + world);
        lines.add("坐标: " + x1 + "," + z1
                + "," + x2 + "," + z2);
        lines.add("高度: " + minY + "," + maxY);
        lines.add("最多宝箱: 3");
        lines.add("检查间隔: 5分钟");
        lines.add("生成概率: 100");
        lines.add("奖励: DIAMOND,1,10");
        lines.add("奖励: GOLD_INGOT,3,20");
        lines.add("奖励: 债券,50,25");

        try {
            OutputStreamWriter w =
                    new OutputStreamWriter(
                            new FileOutputStream(f),
                            StandardCharsets.UTF_8);
            for (String l : lines)
                w.write(l + "\n");
            w.close();
        } catch (IOException e) {
            return false;
        }
        loadAll();
        return true;
    }


    /** 标准化坐标：确保 minX <= maxX, minZ <= maxZ */
    private void normalize(TreasureConfig tc) {
        int a = Math.min(tc.minX, tc.maxX);
        int b = Math.max(tc.minX, tc.maxX);
        tc.minX = a;
        tc.maxX = b;
        a = Math.min(tc.minZ, tc.maxZ);
        b = Math.max(tc.minZ, tc.maxZ);
        tc.minZ = a;
        tc.maxZ = b;
    }


    public boolean removeRegion(String name) {
        File f = new File(plugin.getDataFolder(),
                "寻宝/" + name + ".txt");
        boolean ok = f.exists() && f.delete();
        loadAll();
        return ok;
    }
    // ★ expand: 四周各扩N格
    public boolean expandRegion(String name, int n) {
        TreasureConfig tc = configs.get(name);
        if (tc == null) return false;
        tc.minX -= n;
        tc.minZ -= n;
        tc.maxX += n;
        tc.maxZ += n;
        rewriteFile(tc);
        return true;
    }

    // ★ return: 四周各缩N格
    public boolean returnRegion(String name, int n) {
        TreasureConfig tc = configs.get(name);
        if (tc == null) return false;
        if (tc.maxX - tc.minX <= n * 2
                || tc.maxZ - tc.minZ <= n * 2) {
            return false;
        }
        tc.minX += n;
        tc.minZ += n;
        tc.maxX -= n;
        tc.maxZ -= n;
        rewriteFile(tc);
        return true;
    }

    private void rewriteFile(TreasureConfig tc) {
        File f = new File(plugin.getDataFolder(),
                "寻宝/" + tc.name + ".txt");
        List<String> lines = new ArrayList<>();
        lines.add("区域名: " + tc.name);
        lines.add("世界: " + tc.world);
        lines.add("坐标: " + tc.minX + ","
                + tc.minZ + "," + tc.maxX
                + "," + tc.maxZ);
        lines.add("高度: " + tc.minY + ","
                + tc.maxY);
        lines.add("最多宝箱: " + tc.maxChests);
        lines.add("检查间隔: " + tc.spawnInterval);
        lines.add("生成概率: " + tc.spawnChance);
        lines.add("随机高度: "
                + (tc.randomY ? "是" : "否"));
        for (TreasureReward r : tc.rewards) {
            switch (r.getType()) {
                case BOND:
                    lines.add("奖励: 债券,"
                            + r.getBondAmount()
                            + "," + r.getWeight());
                    break;
                case COMMAND:
                    lines.add("奖励: 命令,"
                            + r.getCommand()
                            + "," + r.getWeight());
                    break;
                case ITEM:
                    lines.add("奖励: "
                            + r.getMaterialName()
                            + "," + r.getAmount()
                            + "," + r.getWeight());
                    break;
            }
        }
        try {
            OutputStreamWriter w =
                    new OutputStreamWriter(
                            new FileOutputStream(f),
                            StandardCharsets.UTF_8);
            for (String l : lines)
                w.write(l + "\n");
            w.close();
        } catch (IOException ignored) {
        }
    }

    // ========== 宝箱操作 ==========

    public int getChestCount(String name) {
        int c = 0;
        for (String v : activeChests.values()) {
            if (v.equals(name)) c++;
        }
        return c;
    }

    public boolean spawnChest(TreasureConfig tc) {
        World w = plugin.getServer()
                .getWorld(tc.world);
        if (w == null) return false;

        normalize(tc);

        // 统计现有宝箱
        java.util.List<Location> existing =
                new ArrayList<>();
        for (java.util.Map.Entry<Location, String> e
                : activeChests.entrySet()) {
            if (e.getValue().equals(tc.name)
                    && e.getKey().getWorld() != null
                    && e.getKey().getWorld().getName()
                    .equals(tc.world)) {
                existing.add(e.getKey());
            }
        }

        // 超过上限→销毁最早的
        while (existing.size() >= tc.maxChests) {
            Location oldest = existing.remove(0);
            if (oldest.getBlock().getType()
                    == Material.CHEST) {
                oldest.getBlock()
                        .setType(Material.AIR);
            }
            activeChests.remove(oldest);
        }

        // ★ 50次尝试找合法位置
        for (int attempt = 0;
             attempt < 50; attempt++) {
            int x = ThreadLocalRandom.current()
                    .nextInt(tc.minX, tc.maxX + 1);
            int z = ThreadLocalRandom.current()
                    .nextInt(tc.minZ, tc.maxZ + 1);
            int y = ThreadLocalRandom.current()
                    .nextInt(tc.minY, tc.maxY + 1);

            Location loc =
                    new Location(w, x, y, z);
            Block block = loc.getBlock();
            Block above = loc.getBlock()
                    .getRelative(
                            org.bukkit.block.BlockFace.UP);

            // ★ 条件1: 当前位置不是箱子
            if (block.getType() == Material.CHEST)
                continue;

            // ★ 条件2: 上方1格是空气（开箱空间）
            if (above.getType() != Material.AIR)
                continue;

            // ★ 放置箱子
            block.setType(Material.CHEST);
            tagChest(loc, tc.name);
            activeChests.put(loc, tc.name);

            plugin.getLogger().info(
                    "[寻宝] ★生成: " + tc.name
                            + " [" + x + ","
                            + y + "," + z + "]");

            if (borderShowing
                    && borderRegionName != null
                    && borderRegionName
                    .equals(tc.name)) {
                showRegionBorder(tc.name);
            }
            return true;
        }

        plugin.getLogger().warning(
                "[寻宝] ★生成失败: " + tc.name);
        return false;
    }


    /** 清除带NBT标记但不在记录中的残留宝箱 */
    public void cleanResidualChests() {
        int total = 0;
        for (TreasureConfig tc
                : configs.values()) {
            World w = plugin.getServer()
                    .getWorld(tc.world);
            if (w == null) continue;
            normalize(tc);
            for (int x = tc.minX;
                 x <= tc.maxX; x++) {
                for (int z = tc.minZ;
                     z <= tc.maxZ; z++) {
                    for (int y = tc.minY;
                         y <= tc.maxY + 2; y++) {
                        Location loc =
                                new Location(
                                        w, x, y, z);
                        if (loc.getBlock().getType()
                                != Material.CHEST)
                            continue;
                        // ★ 有NBT标记→是我们的箱子
                        if (!isOurChest(loc))
                            continue;
                        // ★ 不在记录中→残留
                        if (!activeChests
                                .containsKey(loc)) {
                            String region =
                                    getChestRegion(loc);
                            long time =
                                    getChestTime(loc);
                            loc.getBlock().setType(
                                    Material.AIR);
                            total++;
                            plugin.getLogger().info(
                                    "[寻宝] 清除残留: ["
                                            + x + ","
                                            + y + ","
                                            + z + "] 区域="
                                            + region);
                        }
                    }
                }
            }
        }
        if (total > 0) {
            plugin.getLogger().info(
                    "[寻宝] ★清除残留: "
                            + total + " 个");
        }
    }


    /**
     * 在Y范围内找可放箱子的位置
     * 从Y范围底部向上找：
     * 找到实心方块 → 箱子放它上面
     * 上方有2格空气（箱子+开箱空间）→ 可用
     */
    private int findPlaceableY(World w, int x, int z,
                               int minY, int maxY) {
        List<Integer> validYs = new ArrayList<>();

        // 第一轮：找实心方块上放箱子
        for (int y = minY; y <= maxY; y++) {
            Block ground = w.getBlockAt(x, y, z);
            Block chest = w.getBlockAt(x, y + 1, z);
            Block above = w.getBlockAt(x, y + 2, z);

            if (ground.getType().isSolid()
                    && !chest.getType().isSolid()
                    && above.getType() == Material.AIR) {
                validYs.add(y + 1);
            }
        }

        // 第二轮兜底：找空气+上方空气
        if (validYs.isEmpty()) {
            for (int y = minY; y <= maxY; y++) {
                Block here = w.getBlockAt(x, y, z);
                Block above2 = w.getBlockAt(
                        x, y + 1, z);
                if (here.getType() == Material.AIR
                        && above2.getType()
                        == Material.AIR) {
                    validYs.add(y);
                }
            }
        }

        if (validYs.isEmpty()) return -1;

        return validYs.get(
                ThreadLocalRandom.current()
                        .nextInt(validYs.size()));
    }

    // ★ 物品中文名映射
    private static final Map<String, String> ITEM_NAMES
            = new HashMap<>();
    static {
        ITEM_NAMES.put("DIAMOND", "§b钻石");
        ITEM_NAMES.put("DIAMOND_ORE", "§b钻石矿石");
        ITEM_NAMES.put("GOLD_INGOT", "§6金锭");
        ITEM_NAMES.put("GOLD_NUGGET", "§6金粒");
        ITEM_NAMES.put("IRON_INGOT", "§f铁锭");
        ITEM_NAMES.put("EMERALD", "§a绿宝石");
        ITEM_NAMES.put("LAPIS_LAZULI", "§9青金石");
        ITEM_NAMES.put("REDSTONE", "§4红石");
        ITEM_NAMES.put("COAL", "§8煤炭");
        ITEM_NAMES.put("NETHERITE_INGOT", "§8下界合金锭");
        ITEM_NAMES.put("NETHERITE_SCRAP", "§8下界合金碎片");
        ITEM_NAMES.put("DIAMOND_SWORD", "§b钻石剑");
        ITEM_NAMES.put("DIAMOND_PICKAXE", "§b钻石镐");
        ITEM_NAMES.put("DIAMOND_AXE", "§b钻石斧");
        ITEM_NAMES.put("DIAMOND_SHOVEL", "§b钻石铲");
        ITEM_NAMES.put("DIAMOND_HELMET", "§b钻石头盔");
        ITEM_NAMES.put("DIAMOND_CHESTPLATE", "§b钻石胸甲");
        ITEM_NAMES.put("DIAMOND_LEGGINGS", "§b钻石护腿");
        ITEM_NAMES.put("DIAMOND_BOOTS", "§b钻石靴子");
        ITEM_NAMES.put("GOLDEN_SWORD", "§6金剑");
        ITEM_NAMES.put("GOLDEN_APPLE", "§6金苹果");
        ITEM_NAMES.put("ENCHANTED_GOLDEN_APPLE",
                "§6附魔金苹果");
        ITEM_NAMES.put("STICK", "§a木棍");
        ITEM_NAMES.put("BREAD", "§6面包");
        ITEM_NAMES.put("COOKED_BEEF", "§6熟牛排");
        ITEM_NAMES.put("COOKED_PORKCHOP", "§6熟猪排");
        ITEM_NAMES.put("COOKED_CHICKEN", "§6熟鸡肉");
        ITEM_NAMES.put("ARROW", "§f箭矢");
        ITEM_NAMES.put("ENDER_PEARL", "§5末影珍珠");
        ITEM_NAMES.put("BLAZE_ROD", "§6烈焰棒");
        ITEM_NAMES.put("GHAST_TEAR", "§f恶魂之泪");
        ITEM_NAMES.put("SHULKER_SHELL", "§5潜影壳");
        ITEM_NAMES.put("EXPERIENCE_BOTTLE",
                "§a经验瓶");
        ITEM_NAMES.put("TOTEM_OF_UNDYING",
                "§6不死图腾");
        ITEM_NAMES.put("ELYTRA", "§8鞘翅");
        ITEM_NAMES.put("TRIDENT", "§b三叉戟");
        ITEM_NAMES.put("BEACON", "§f信标");
    }

    /** 获取物品中文显示名 */
    public static String getItemDisplayName(
            String materialName) {
        String upper = materialName.toUpperCase();
        if (ITEM_NAMES.containsKey(upper)) {
            return ITEM_NAMES.get(upper);
        }
        // 转为可读名：DIAMOND_ORE → Diamond Ore
        String readable = materialName
                .replace("_", " ")
                .toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (String word : readable.split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character
                        .toUpperCase(word.charAt(0)));
                sb.append(word.substring(1));
                sb.append(" ");
            }
        }
        return "§f" + sb.toString().trim();
    }

    // ★ 区域边框存储
    private final Map<String,
            Map<Location, Material>> regionBorders
            = new HashMap<>();


    private void setBorderBlock(World w, int x,
                                int z,
                                Map<Location,
                                        Material> blocks) {
        int y = w.getHighestBlockYAt(x, z) + 1;
        Location loc = new Location(w, x, y, z);
        Block block = w.getBlockAt(loc);
        // 记录原方块
        if (!blocks.containsKey(loc)) {
            blocks.put(loc, block.getType());
        }
        // 放置荧石
        block.setType(Material.GLOWSTONE);
    }



    public boolean isTreasure(Location loc) {
        return activeChests.containsKey(loc);
    }

    public void removeChest(Location loc) {
        activeChests.remove(loc);
    }

    public Map<String, TreasureConfig> getConfigs() {
        return configs;
    }

    public Map<Location, String> getActiveChests() {
        return activeChests;
    }

    public List<String> getRegionNames() {
        return new ArrayList<>(configs.keySet());
    }
}
