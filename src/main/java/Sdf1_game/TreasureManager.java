package Sdf1_game;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class TreasureManager {

    private static final String CONFIG_SUFFIX =
            ".sdf1.txt";

    // ===== 模糊物品匹配 =====
    private final java.util.Map<String, String>
            ITEM_CN = new java.util.HashMap<>();

    private void initItemMap() {
        ITEM_CN.put("旋风棒", "BREEZE_ROD");
        ITEM_CN.put("旋风棍", "BREEZE_ROD");
        ITEM_CN.put("旋风杖", "BREEZE_ROD");
        ITEM_CN.put("烈焰棒", "BLAZE_ROD");
        ITEM_CN.put("烈焰棍", "BLAZE_ROD");
        ITEM_CN.put("金苹果", "GOLDEN_APPLE");
        ITEM_CN.put("附魔金苹果",
                "ENCHANTED_GOLDEN_APPLE");
        ITEM_CN.put("下界合金剑",
                "NETHERITE_SWORD");
        ITEM_CN.put("下界合金镐",
                "NETHERITE_PICKAXE");
        ITEM_CN.put("下界合金锭",
                "NETHERITE_INGOT");
        ITEM_CN.put("钻石剑", "DIAMOND_SWORD");
        ITEM_CN.put("钻石镐",
                "DIAMOND_PICKAXE");
        ITEM_CN.put("钻石斧", "DIAMOND_AXE");
        ITEM_CN.put("钻石甲",
                "DIAMOND_CHESTPLATE");
        ITEM_CN.put("钻石靴子",
                "DIAMOND_BOOTS");
        ITEM_CN.put("钻石护腿",
                "DIAMOND_LEGGINGS");
        ITEM_CN.put("钻石头盔",
                "DIAMOND_HELMET");
        ITEM_CN.put("末影珍珠",
                "ENDER_PEARL");
        ITEM_CN.put("绿宝石", "EMERALD");
        ITEM_CN.put("金锭", "GOLD_INGOT");
        ITEM_CN.put("铁剑", "IRON_SWORD");
        ITEM_CN.put("铁镐", "IRON_PICKAXE");
        ITEM_CN.put("铁锭", "IRON_INGOT");
        ITEM_CN.put("钻石", "DIAMOND");
        ITEM_CN.put("木棍", "STICK");
        ITEM_CN.put("面包", "BREAD");
        ITEM_CN.put("弓", "BOW");
        ITEM_CN.put("箭", "ARROW");
        ITEM_CN.put("盾牌", "SHIELD");
        ITEM_CN.put("不死图腾",
                "TOTEM_OF_UNDYING");
        ITEM_CN.put("煤炭", "COAL");
        ITEM_CN.put("红石", "REDSTONE");
        ITEM_CN.put("附魔书",
                "ENCHANTED_BOOK");
    }

    // ===== 领取限制 =====
    private final java.util.Map<String, Boolean>
            claimDb = new java.util.HashMap<>();
    private File claimFile;

    // ===== 容器拦截计数 =====
    private final java.util.Map<String, Integer>
            containerBlockCount =
            new java.util.HashMap<>();


    private final Main plugin;
    private final Map<String, TreasureConfig>
            configs = new LinkedHashMap<>();
    private final Map<Location, String>
            activeChests = new LinkedHashMap<>();
    private static final String MARKER = "SDF1";
    private final Set<String> pendingReclaims =
            new HashSet<>();
    private final java.util.Map<String, String>
            ITEM_CN_TO_ID = new java.util.HashMap<>();


    public Set<String> getPendingReclaims() {
        return pendingReclaims;
    }


    // ★ 领取记录
    private final Set<String> claimedSet =
            new HashSet<>();

    // ★ 边框
    private BukkitTask borderTask;
    private boolean borderShowing = false;
    private String borderRegionName;
    // ★ 容器拦截计数: 玩家名 → 次数

    /** 容器拦截：第1-2次警告，第3次没收 */
    public boolean onContainerBlock(
            String playerName) {
        int count = containerBlockCount
                .getOrDefault(playerName, 0) + 1;
        containerBlockCount.put(playerName, count);

        plugin.getLogger().info(
                "[寻宝] ★★★onContainerBlock: "
                        + playerName
                        + " 次数=" + count);

        if (count < 3) {
            return false;
        } else {
            // ★ 重置计数
            containerBlockCount.put(playerName, 0);
            return true;
        }
    }

    /** 重置容器拦截计数 */
    public void resetContainerBlock(
            String playerName) {
        containerBlockCount.remove(playerName);
    }


    public TreasureManager(Main plugin) {
        this.plugin = plugin;
        // ★ 必须在构造器里初始化
        this.claimFile = new File(
                plugin.getDataFolder(),
                "寻宝/领取限制.txt");
        claimFile.getParentFile().mkdirs();
        loadClaims();
        // 构造器里加（最后一行）：
        // ★ 每2秒扫描（全场景覆盖）
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                for (org.bukkit.entity.Player p :
                        plugin.getServer()
                                .getOnlinePlayers()) {
                    reclaimPlayerItems(p.getName());
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // 1秒
        initItemMap();
        loadClaims();


    }

    /** 模糊匹配物品名→材质ID */
    private String fuzzyMatchItem(String input) {
        if (input == null || input.isEmpty())
            return null;

        // 精确匹配
        for (java.util.Map.Entry<String, String> e
                : ITEM_CN.entrySet()) {
            if (input.equals(e.getKey()))
                return e.getValue();
        }

        // 包含匹配
        String found = null;
        for (java.util.Map.Entry<String, String> e
                : ITEM_CN.entrySet()) {
            if (input.contains(e.getKey())
                    || e.getKey().contains(input)) {
                if (found != null) return null;
                found = e.getValue();
            }
        }
        if (found != null) return found;

        // 前缀匹配
        if (input.length() >= 2) {
            String p = input.substring(0, 2);
            found = null;
            for (java.util.Map.Entry<String, String> e
                    : ITEM_CN.entrySet()) {
                String k = e.getKey();
                if (k.length() >= 2
                        && k.substring(0, 2)
                        .equals(p)) {
                    if (found != null) return null;
                    found = e.getValue();
                }
            }
            if (found != null) return found;
        }

        return null;
    }


    /** 加载领取记录 */
    private void loadClaims() {
        claimedSet.clear();
        claimDb.clear();
        if (!claimFile.exists()) return;
        try {
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(
                            new FileInputStream(claimFile),
                            StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#"))
                    continue;
                if (line.contains("|")) {
                    // claimedSet 格式: 玩家|物品|区域
                    claimedSet.add(line);
                } else if (line.contains(".")) {
                    // claimDb 格式: 玩家.物品.区域
                    claimDb.put(line, true);
                }
            }
            br.close();
        } catch (IOException e) {
            plugin.getLogger().warning(
                    "[寻宝] 加载领取记录失败");
        }
    }
    public void reload() {
        // ★ 先销毁领取记录文件并清空所有内存
        forceCleanClaims();

        // 清除玩家背包里的自定义物品
        for (org.bukkit.entity.Player p :
                plugin.getServer()
                        .getOnlinePlayers()) {
            reclaimPlayerItems(p.getName());
        }

        removeAllChests();
        configs.clear();
        activeChests.clear();
        refreshedChests.clear();
        loadAll();

        plugin.getLogger().info(
                "[寻宝] ★重载完成，领取记录已清空，"
                        + configs.size() + " 个区域");
    }


    // ★ 玩家寻宝临时数据
    private final Map<String, TreasureData>
            playerTreasureData = new HashMap<>();

    public void setPlayerTreasureData(
            String name, TreasureData data) {
        playerTreasureData.put(name, data);
    }

    public TreasureData getPlayerTreasureData(
            String name) {
        return playerTreasureData.get(name);
    }

    public void clearPlayerTreasureData(
            String name) {
        playerTreasureData.remove(name);
    }

    //  已刷新的宝箱坐标记录
    private final Set<String> refreshedChests =
            new HashSet<>();

    /** 标记宝箱已刷新 */
    public void markRefreshed(Location loc) {
        refreshedChests.add(locToString(loc));
    }

    /** 检查宝箱是否已刷新 */
    public boolean isRefreshed(Location loc) {
        return refreshedChests.contains(
                locToString(loc));
    }

    /** 清除刷新记录（重载/关闭时） */
    public void clearRefreshed() {
        refreshedChests.clear();
    }

    private String locToString(Location loc) {
        return loc.getWorld().getName()
                + "," + loc.getBlockX()
                + "," + loc.getBlockY()
                + "," + loc.getBlockZ();
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
        activeChests.clear();
        File dir = new File(
                plugin.getDataFolder(), "寻宝");
        dir.mkdirs();

        // ★ 第一步：自动迁移旧.txt文件
        migrateOldFiles(dir);

        // 第二步：读取.sdf1.txt文件
        File[] files = dir.listFiles(
                (d, n) -> n.endsWith(CONFIG_SUFFIX));

        if (files == null) return;

        for (File f : files) {
            try {
                TreasureConfig tc = parseConfig(f);
                if (tc != null && tc != null) {
                    configs.put(tc.name, tc);
                    plugin.getLogger().info(
                            "[寻宝] ★加载: "
                                    + tc.name
                                    + " 奖励数量="
                                    + tc.rewards.size());
              /*      for (int i = 0;
                         i < tc.rewards.size(); i++) {
                        TreasureReward r =
                                tc.rewards.get(i);
                        plugin.getLogger().info(
                                "[寻宝]   奖励[" + i
                                        + "]: "
                                        + r.getType()
                                        + " "
                                        + r.getMaterialName()
                                        + " x"
                                        + r.getAmount()
                                        + " 权重="
                                        + r.getWeight()
                                        + " 名="
                                        + r.getDisplayName());
                    }*/
                    loadSettings(tc);
                }
            } catch (Exception e) {
                plugin.getLogger().warning(
                        "[寻宝] 加载失败: "
                                + f.getName()
                                + " "
                                + e.getMessage());
            }
        }

        plugin.getLogger().info(
                "[寻宝] 共"
                        + configs.size() + "个区域");
    }

    /** ★ 自动迁移旧.txt文件 → .sdf1.txt */
    private void migrateOldFiles(File dir) {
        File[] oldFiles = dir.listFiles(
                (d, name) -> name.endsWith(".txt")
                        && !name.endsWith(CONFIG_SUFFIX));

        if (oldFiles == null) return;

        // ★ 黑名单：这些文件不处理
        java.util.Set<String> blacklist =
                new java.util.HashSet<>();
        blacklist.add("领取限制");
        blacklist.add("配置说明");
        blacklist.add("设置");

        for (File f : oldFiles) {
            String baseName = f.getName()
                    .replace(".txt", "");

            // 黑名单跳过
            if (blacklist.contains(baseName)) continue;

            // 尝试读取第一行，确认是区域文件
            try {
                BufferedReader br =
                        new BufferedReader(
                                new InputStreamReader(
                                        new FileInputStream(f),
                                        StandardCharsets.UTF_8));
                String first = br.readLine();
                br.close();

                // 第一行包含"区域名"才迁移
                if (first != null
                        && first.contains("区域名")) {
                    String newName =
                            baseName + CONFIG_SUFFIX;
                    File newFile = new File(
                            f.getParent(), newName);
                    if (f.renameTo(newFile)) {
                        plugin.getLogger().info(
                                "[寻宝] ★迁移: "
                                        + f.getName()
                                        + " → "
                                        + newName);
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    /** ★ 全场景扫描：过期/次数用完/非本人 → 全部回收 */
    /** ★ 全场景扫描回收 */
    public void reclaimPlayerItems(
            String playerName) {
        org.bukkit.entity.Player player =
                plugin.getServer()
                        .getPlayerExact(playerName);
        if (player == null) return;

        long now = System.currentTimeMillis();
        int removed = 0;

        for (int i = 0;
             i < player.getInventory().getSize();
             i++) {
            ItemStack item =
                    player.getInventory()
                            .getItem(i);
            if (item == null) continue;
            if (!TreasureInventory
                    .isCustomItem(item)) continue;

            String[] mark =
                    TreasureInventory
                            .parseMark(item);
            if (mark == null) {
                // ★ 无标记的自定义物品→直接清除
                player.getInventory()
                        .setItem(i, null);
                removed++;
                continue;
            }

            String owner = mark[0];
            String region = mark[1];
            String rName = mark[2];
            int maxUse = Integer.parseInt(mark[3]);
            int useCount =
                    Integer.parseInt(mark[4]);
            long expireTime =
                    Long.parseLong(mark[5]);

            boolean shouldRemove = false;
            String reason = "";

            // 检查1: 非本人
            if (!owner.equalsIgnoreCase(
                    playerName)) {
                shouldRemove = true;
                reason = "非本人持有";
            }

            // ★ 检查2: 无领取记录→视为过期
            // 宝箱物品（区域名含"宝箱"）不需要领取限制
            if (!shouldRemove && !isTreasureItem(playerName, region)) {
                if (!isClaimed(playerName, rName, region)) {
                    shouldRemove = true;
                    reason = "无领取记录（过期/重载）";
                }
            }

            // 检查3: 已过期
            if (!shouldRemove
                    && expireTime > 0
                    && now >= expireTime) {
                shouldRemove = true;
                reason = "已过期";
            }

            // 检查4: 攻击次数用完
            if (!shouldRemove
                    && maxUse > 0
                    && useCount >= maxUse) {
                shouldRemove = true;
                reason = "次数用完";
            }

            if (shouldRemove) {
                player.getInventory()
                        .setItem(i, null);
                unclaim(playerName, rName, region);
                removed++;
                if (!owner.equalsIgnoreCase(
                        playerName)) {
                    player.sendMessage(
                            "§c[寻宝] " + rName
                                    + " 不属于你，已回收");
                } else {
                    player.sendMessage(
                            "§c[寻宝] " + rName
                                    + " " + reason
                                    + "，已回收");
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

    /** 领取限时物品 */
    public void claim(String player,
                      String itemName,
                      String regionName) {
        String key = player + "." + itemName
                + "." + regionName;
        claimDb.put(key, true);
        saveClaims();
    }

    /** 取消领取限制 */
    public void unclaim(String player,
                        String itemName,
                        String regionName) {
        String key = player + "." + itemName
                + "." + regionName;
        claimDb.remove(key);
        saveClaims();
    }

    /** 检查是否已领取 */
    public boolean isClaimed(String player,
                             String itemName,
                             String regionName) {
        // ★ 检查claimDb（限时物品）
        String key = player + "." + itemName + "." + regionName;
        if (claimDb.containsKey(key)) return true;
        
        // ★ 检查claimedSet（寻宝物品/宝箱物品）
        String key2 = player + "|" + itemName + "|" + regionName;
        return claimedSet.contains(key2);
    }

    /** 检查是否是宝箱物品（不检查领取限制） */
    public boolean isTreasureItem(String playerName,
                                  String regionName) {
        // 宝箱物品区域名包含"宝箱"或" Treasure"关键词
        return regionName != null && 
                (regionName.contains("宝箱") || regionName.contains("Treasure"));
    }


    /** ★ 统一标点：中文→英文 */
    private String normalizePunctuation(String s) {
        return s
                .replace("\uff1a", ":")    // ：全角冒号
                .replace("\ufe58", ":")    // ﹕小冒号
                .replace("\ua789", ":")    // ꞉
                .replace("：", ":")        // 冗余保险
                .replace("\uff0c", ",")    // ，全角逗号
                .replace("，", ",")        // 冗余保险
                .replace("\uff1b", ";")    // ；全角分号
                .replace("；", ";")
                .replace("\uff08", "(")    // （
                .replace("（", "(")
                .replace("\uff09", ")")    // ）
                .replace("）", ")")
                .replace("\uff5e", "~")    // ～全角波浪
                .replace("～", "~")
                .replace("\u3001", ",")    // 、顿号
                .replace("、", ",")
                .replace("\uff01", "!")    // ！
                .replace("！", "!")
                .replace("\uff1f", "?")    // ？
                .replace("？", "?")
                .replace("\u3000", " ")    // 全角空格
                .replace("\ufeff", "");    // BOM
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

        for (int lineIdx = 0;
             lineIdx < lines.size(); lineIdx++) {

            String rawLine = lines.get(lineIdx);
            int lineNum = lineIdx + 1;

            String line = rawLine.trim();
            if (line.isEmpty()
                    || line.startsWith("#")) {
                continue;
            }

            // ★ 只转全角冒号为半角
            line = line.replace('\uff1a', ':');

            int ci = line.indexOf(':');
            if (ci < 0) {
                plugin.getLogger().warning(
                        "[寻宝] ★无冒号: " + line);
                continue;
            }

            String k = line.substring(0, ci).trim();
            String v = line.substring(ci + 1).trim();

         /*   plugin.getLogger().info(
                    "[寻宝] key=[" + k
                            + "] val=[" + v + "]");*/

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
                        + " 奖励="
                        + tc.rewards.size());
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
        // ★ 暴力方案：直接删文件再重建，清空所有内存
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
        claimDb.clear();
    }

    /**
     * 清空指定玩家的所有领取记录（限时物品+寻宝物品）
     * @param playerName 玩家名
     * @return 清除的记录数量
     */
    public int clearPlayerAllClaims(String playerName) {
        int count = 0;
        
        // 1. 清除限时物品领取记录 (claimDb) - key格式: 玩家名.物品名.区域名
        Iterator<Map.Entry<String, Boolean>> claimDbIterator = claimDb.entrySet().iterator();
        while (claimDbIterator.hasNext()) {
            Map.Entry<String, Boolean> entry = claimDbIterator.next();
            if (entry.getKey().startsWith(playerName + ".")) {
                claimDbIterator.remove();
                count++;
            }
        }
        
        // 2. 清除寻宝物品领取记录 (claimedSet) - key格式: 玩家名|物品名|区域名
        Iterator<String> claimedSetIterator = claimedSet.iterator();
        while (claimedSetIterator.hasNext()) {
            String key = claimedSetIterator.next();
            if (key.startsWith(playerName + "|")) {
                claimedSetIterator.remove();
                count++;
            }
        }
        
        // 3. 保存更改到文件
        if (count > 0) {
            saveClaims();
            rewriteClaimsFile();
        }
        
        return count;
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

        // ★ 优先给玩家格式
        if (line.contains("给")) {
            return parseGivePlayerItem(line);
        }
        // ★ 模糊匹配
        String matName = fuzzyMatchItem(line);
        if (matName == null) {
            plugin.getLogger().warning(
                    "[寻宝] ★无物品: " + line);
            return null;
        }

        String cnName = "";
        for (java.util.Map.Entry<String, String> e
                : ITEM_CN.entrySet()) {
            if (e.getValue().equals(matName)) {
                cnName = e.getKey();
                break;
            }
        }


        // 数量
        int amount = 1;
        java.util.regex.Matcher am =
                java.util.regex.Pattern
                        .compile(
                                "([一二三四五六七八九十\\d]+)"
                                        + "([个根把件套只])")
                        .matcher(line);
        if (am.find()) {
            amount = parseChineseNumber(
                    am.group(1));
        }
        if (amount <= 1) {
            java.util.regex.Matcher am2 =
                    java.util.regex.Pattern
                            .compile(
                                    "(\\d+)\\s*[个根把件套只]")
                            .matcher(line);
            if (am2.find()) {
                amount = Integer.parseInt(
                        am2.group(1));
            }
        }

        // 附魔
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
                "经验修补", "mending",
                "多重射击", "multishot",
                "穿透", "piercing",
                "力量", "power",
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
        };

        List<String> enchIds = new ArrayList<>();
        List<Integer> enchLvls = new ArrayList<>();

        for (int i = 0;
             i < enchDefs.length; i += 2) {
            String cn = enchDefs[i];
            String id = enchDefs[i + 1];
            if (!line.contains(cn)) continue;

            int lvl = 0;
            java.util.regex.Matcher m1 =
                    java.util.regex.Pattern
                            .compile(cn
                                    + "\\s*[，,]?\\s*等级?\\s*[：:]?\\s*(\\d+)")
                            .matcher(line);
            if (m1.find()) {
                lvl = Integer.parseInt(m1.group(1));
            }
            if (lvl <= 0) {
                java.util.regex.Matcher m2 =
                        java.util.regex.Pattern
                                .compile(cn + "\\s*(\\d+)")
                                .matcher(line);
                if (m2.find()) {
                    lvl = Integer.parseInt(
                            m2.group(1));
                }
            }
            if (lvl <= 0) {
                java.util.regex.Matcher m3 =
                        java.util.regex.Pattern
                                .compile(
                                        "(\\d+)\\s*级?" + cn)
                                .matcher(line);
                if (m3.find()) {
                    lvl = Integer.parseInt(
                            m3.group(1));
                }
            }
            if (lvl <= 0) {
                int pos = line.indexOf(cn);
                if (pos >= 0) {
                    String after = line.substring(
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
            if (!enchIds.contains(id)) {
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

        // 时长
        int duration = 0;
        java.util.regex.Matcher dm =
                java.util.regex.Pattern
                        .compile(
                                "时长[：:]?\\s*(\\d+)")
                        .matcher(line);
        if (dm.find()) {
            int val = Integer.parseInt(dm.group(1));
            duration = val < 100 ? val * 60 : val;
        }

        // 攻击次数（可选）
        int attackLimit = 0;
        java.util.regex.Matcher atm =
                java.util.regex.Pattern
                        .compile(
                                "攻击[^\\d]*[：:]?\\s*(\\d+)")
                        .matcher(line);
        if (atm.find()) {
            attackLimit = Integer.parseInt(
                    atm.group(1));
        }

        // ★ 判断分支
        boolean hasEnch = enchant != null
                && !enchant.isEmpty();

        if (hasEnch || duration > 0
                || attackLimit > 0) {
            return new TreasureReward(
                    TreasureReward.Type.ITEM,
                    matName, amount, 0, null, 10,
                    "§b" + cnName,
                    duration, enchant, null,
                    attackLimit);
        } else {
            return new TreasureReward(
                    TreasureReward.Type.ITEM,
                    matName, amount, 0, null, 10,
                    "§f" + cnName + " x" + amount);
        }
    }

    /** 处理 "给玩家XXX,附魔,等级,时长,攻击" 格式 */
    private TreasureReward parseGivePlayerItem(
            String line) {
        String itemName = line
                .replace("给予玩家", "")
                .replace("给玩家", "")
                .replace("给予", "")
                .replace("给", "")
                .trim();

        // ★ 模糊匹配
        String matName = fuzzyMatchItem(itemName);
        if (matName == null) {
            plugin.getLogger().warning(
                    "[寻宝] ★无法识别: " + line);
            return null;
        }

        String cnName = "";
        for (java.util.Map.Entry<String, String> e
                : ITEM_CN_TO_ID.entrySet()) {
            if (e.getValue().equals(matName)) {
                cnName = e.getKey();
                break;
            }
        }


        String cleanName = itemName
                .replaceAll(
                        "[一二三四五六七八九十\\d]+[个根把件套只]",
                        "")
                .trim();

        String[][] items = {
                {"旋风棒", "BREEZE_ROD"},
                {"烈焰棒", "BLAZE_ROD"},
                {"附魔金苹果", "ENCHANTED_GOLDEN_APPLE"},
                {"金苹果", "GOLDEN_APPLE"},
                {"下界合金剑", "NETHERITE_SWORD"},
                {"下界合金镐", "NETHERITE_PICKAXE"},
                {"钻石剑", "DIAMOND_SWORD"},
                {"钻石镐", "DIAMOND_PICKAXE"},
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
                {"面包", "BREAD"},
                {"不死图腾", "TOTEM_OF_UNDYING"},
                {"煤炭", "COAL"},
                {"红石", "REDSTONE"},
        };

        String matName2 = null;
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

        // 数量
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

        // 附魔泛解析
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
                "突进", "lunge",
                "诱饵", "lure",
                "经验修补", "mending",
                "多重射击", "multishot",
                "穿透", "piercing",
                "力量", "power",
                "弹射物保护",
                "projectile_protection",
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
        List<Integer> enchLvls =
                new ArrayList<>();

        for (int i = 0;
             i < enchDefs.length; i += 2) {
            String cn = enchDefs[i];
            String id = enchDefs[i + 1];
            if (!line.contains(cn)) continue;

            int lvl = 0;

            // "附魔火焰附加,等级255"
            java.util.regex.Matcher m1 =
                    java.util.regex.Pattern
                            .compile(cn
                                    + "[，,]?\\s*等级?\\s*[：:]?\\s*(\\d+)")
                            .matcher(line);
            if (m1.find()) {
                lvl = Integer.parseInt(
                        m1.group(1));
            }

            // "击退255"
            if (lvl <= 0) {
                java.util.regex.Matcher m2 =
                        java.util.regex.Pattern
                                .compile(cn
                                        + "\\s*(\\d+)")
                                .matcher(line);
                if (m2.find()) {
                    lvl = Integer.parseInt(
                            m2.group(1));
                }
            }

            // "255级锋利"
            if (lvl <= 0) {
                java.util.regex.Matcher m3 =
                        java.util.regex.Pattern
                                .compile(
                                        "(\\d+)\\s*级?" + cn)
                                .matcher(line);
                if (m3.find()) {
                    lvl = Integer.parseInt(
                            m3.group(1));
                }
            }

            // 附魔名后最近的数字
            if (lvl <= 0) {
                int pos = line.indexOf(cn);
                if (pos >= 0) {
                    String after = line
                            .substring(pos
                                    + cn.length());
                    java.util.regex.Matcher m4 =
                            java.util.regex.Pattern
                                    .compile(
                                            "\\D*(\\d+)")
                                    .matcher(after);
                    if (m4.find()) {
                        int val =
                                Integer.parseInt(
                                        m4.group(1));
                        if (val > 0 && val <= 255) {
                            lvl = val;
                        }
                    }
                }
            }

            if (lvl <= 0) lvl = 1;

            // 去重
            if (!enchIds.contains(id)) {
                enchIds.add(id);
                enchLvls.add(lvl);
            }
        }

        StringBuilder enchSB =
                new StringBuilder();
        for (int i = 0;
             i < enchIds.size(); i++) {
            if (i > 0) enchSB.append(";");
            enchSB.append(enchIds.get(i))
                    .append(",")
                    .append(enchLvls.get(i));
        }
        String enchant =
                enchSB.length() > 0
                        ? enchSB.toString() : null;

        // 时长
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

        // 攻击次数
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

   /*    plugin.getLogger().info(
                "[寻宝] ★结果: "
                        + matName + " x" + amount
                        + " 附魔=" + enchant
                        + " 时长=" + duration
                        + " 攻击=" + attackLimit);
*/
        return new TreasureReward(
                TreasureReward.Type.ITEM,
                matName, amount, 0, null, 10,
                "§b" + cleanName,
                duration, enchant, null,
                attackLimit);
    }
    /** 获取容器拦截次数 */
    public int getContainerBlockCount(
            String playerName) {
        return containerBlockCount
                .getOrDefault(playerName, 0);
    }

    // ★ 2. isClaimed
    private void saveClaims() {
        if (claimFile == null) claimFile = new File(
                plugin.getDataFolder(), "claims.yml");
        try {
            FileWriter fw = new FileWriter(
                    claimFile);
            // 保存 claimDb（限时物品，用 . 分隔）
            for (String key : claimDb.keySet()) {
                fw.write(key + "\n");
            }
            // 保存 claimedSet（寻宝物品，用 | 分隔）
            for (String key : claimedSet) {
                fw.write(key + "\n");
            }
            fw.close();
        } catch (IOException e) {
            plugin.getLogger().warning(
                    "[寻宝] 保存claims失败: "
                            + e.getMessage());
        }
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
        if (s == null || s.trim().isEmpty()) return 1;
        s = s.trim().toLowerCase();

        // ★ 直接数字
        try { return Integer.parseInt(s); }
        catch (Exception ignore) {}

        // ★ 英文数字
        switch (s) {
            case "one": return 1;
            case "two": case "both": return 2;
            case "three": return 3;
            case "four": return 4;
            case "five": return 5;
            case "six": return 6;
            case "seven": return 7;
            case "eight": return 8;
            case "nine": return 9;
            case "ten": return 10;
            case "eleven": return 11;
            case "twelve": return 12;
            case "thirteen": return 13;
            case "fourteen": return 14;
            case "fifteen": return 15;
            case "sixteen": return 16;
            case "seventeen": return 17;
            case "eighteen": return 18;
            case "nineteen": return 19;
            case "twenty": return 20;
            case "thirty": return 30;
            case "forty": return 40;
            case "fifty": return 50;
            case "sixty": return 60;
            case "seventy": return 70;
            case "eighty": return 80;
            case "ninety": return 90;
            case "hundred": return 100;
        }

        // ★ 罗马数字
        int roman = parseRoman(s);
        if (roman > 0) return roman;

        // ★ 中文数字
        int cn = parseFullChinese(s);
        if (cn > 0) return cn;

        // ★ 兜底：提取字符串中第一个数字
        java.util.regex.Matcher m =
                java.util.regex.Pattern
                        .compile("(\\d+)")
                        .matcher(s);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (Exception ignore) {}
        }

        return 1;
    }

    /** ★ 罗马数字解析 */
    private int parseRoman(String s) {
        s = s.trim().toUpperCase();
        if (s.isEmpty()) return 0;
        // 简单验证：只含IVXLCDM
        if (!s.matches("[IVXLCDM]+")) return 0;

        int result = 0;
        int prev = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            int val;
            switch (s.charAt(i)) {
                case 'I': val = 1; break;
                case 'V': val = 5; break;
                case 'X': val = 10; break;
                case 'L': val = 50; break;
                case 'C': val = 100; break;
                case 'D': val = 500; break;
                case 'M': val = 1000; break;
                default: return 0;
            }
            if (val < prev) {
                result -= val;
            } else {
                result += val;
            }
            prev = val;
        }
        return result > 0 && result <= 9999
                ? result : 0;
    }

    /** ★ 完整中文数字解析 */
    private int parseFullChinese(String s) {
        s = s.trim();

        // ★ 简单中文数字（预定义）
        String[][] cnNums = {
                {"零", "0"}, {"〇", "0"},
                {"一", "1"}, {"壹", "1"},
                {"二", "2"}, {"两", "2"}, {"贰", "2"},
                {"三", "3"}, {"叁", "3"},
                {"四", "4"}, {"肆", "4"},
                {"五", "5"}, {"伍", "5"},
                {"六", "6"}, {"陆", "6"},
                {"七", "7"}, {"柒", "7"},
                {"八", "8"}, {"捌", "8"},
                {"九", "9"}, {"玖", "9"},
                {"十", "10"}, {"拾", "10"},
                {"十一", "11"}, {"十二", "12"},
                {"十三", "13"}, {"十四", "14"},
                {"十五", "15"}, {"十六", "16"},
                {"十七", "17"}, {"十八", "18"},
                {"十九", "19"},
                {"二十", "20"}, {"廿", "20"},
                {"二十一", "21"}, {"二十二", "22"},
                {"二十三", "23"}, {"二十四", "24"},
                {"二十五", "25"}, {"二十六", "26"},
                {"二十七", "27"}, {"二十八", "28"},
                {"二十九", "29"},
                {"三十", "30"}, {"卅", "30"},
                {"四十", "40"}, {"五十", "50"},
                {"六十", "60"}, {"七十", "70"},
                {"八十", "80"}, {"九十", "90"},
                {"一百", "100"}, {"一百", "100"},
                {"一千", "1000"}, {"一万", "10000"},
                {"一亿", "100000000"},
        };
        for (String[] pair : cnNums) {
            if (s.equals(pair[0])) {
                return Integer.parseInt(pair[1]);
            }
        }

        // ★ 复杂中文数字（二十、二百五、三千...）
        int result = 0;
        int current = 0;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int digit = cnCharToDigit(c);
            if (digit >= 0) {
                current = current * 10 + digit;
            } else if (c == '十' || c == '拾') {
                if (current == 0) current = 1;
                result += current * 10;
                current = 0;
            } else if (c == '百' || c == '佰') {
                if (current == 0) current = 1;
                result += current * 100;
                current = 0;
            } else if (c == '千' || c == '仟') {
                if (current == 0) current = 1;
                result += current * 1000;
                current = 0;
            } else if (c == '万' || c == '萬') {
                if (current == 0) current = 1;
                result = (result + current) * 10000;
                current = 0;
            } else if (c == '亿' || c == '億') {
                if (current == 0) current = 1;
                result = (result + current)
                        * 100000000;
                current = 0;
            }
        }
        result += current;

        return result > 0 ? result : 0;
    }


    /** 中文单字→数字 */
    private int cnCharToDigit(char c) {
        switch (c) {
            case '零': case '〇': return 0;
            case '一': case '壹': return 1;
            case '二': case '两': case '贰':
                return 2;
            case '三': case '叁': return 3;
            case '四': case '肆': return 4;
            case '五': case '伍': return 5;
            case '六': case '陆': return 6;
            case '七': case '柒': return 7;
            case '八': case '捌': return 8;
            case '九': case '玖': return 9;
            case '廿': return 2;
            case '卅': return 3;
            default: return -1;
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
            // ★ 检查光标物品（容器放入）
            if (e.getCursor() != null
                    && TreasureInventory
                    .isCustomItem(e.getCursor())) {
                org.bukkit.entity.Player p0 =
                        (org.bukkit.entity.Player)
                                e.getWhoClicked();
                InventoryType topType =
                        e.getView().getTopInventory()
                                .getType();
                if (topType != InventoryType.PLAYER
                        && topType
                        != InventoryType.CRAFTING
                        && topType
                        != InventoryType.CREATIVE) {
                    e.setCancelled(true);
                    // ★ 事不过三
                    boolean confiscate =
                            plugin.getTreasureManager()
                                    .onContainerBlock(
                                            p.getName());
                    if (confiscate) {
                        // 第3次：没收光标物品
                        e.getCursor().setAmount(0);
                        p.sendMessage(
                                "§c[寻宝] 多次违规，"
                                        + "物品已被没收！");
                        plugin.getLogger().info(
                                "[寻宝] ★没收: "
                                        + p.getName());
                    }
                    return;
                }
            }

            // ★ 检查槽位物品（归属+容器）
            ItemStack clicked = e.getCurrentItem();
            if (clicked != null
                    && TreasureInventory
                    .isCustomItem(clicked)) {
                org.bukkit.entity.Player p2 =
                        (org.bukkit.entity.Player)
                                e.getWhoClicked();
                // 非本人→直接没收
                String[] mark =
                        TreasureInventory
                                .parseMark(clicked);
                if (mark != null
                        && !mark[0].equalsIgnoreCase(
                        p.getName())) {
                    e.setCancelled(true);
                    e.getCurrentItem().setAmount(0);
                    p.sendMessage(
                            "§c[寻宝] 该物品不属于你，已没收");
                    return;
                }
                // 目标是容器→事不过三
                InventoryType topType =
                        e.getView().getTopInventory()
                                .getType();
                if (topType != InventoryType.PLAYER
                        && topType
                        != InventoryType.CRAFTING
                        && topType
                        != InventoryType.CREATIVE) {
                    e.setCancelled(true);
                    boolean confiscate =
                            plugin.getTreasureManager()
                                    .onContainerBlock(
                                            p.getName());
                    if (confiscate) {
                        e.getCurrentItem()
                                .setAmount(0);
                        p.sendMessage(
                                "§c[寻宝] 多次违规，"
                                        + "物品已被没收！");
                    }
                }
            }


            // ★ 检查被点击槽位的物品
            ItemStack clicked1 = e.getCurrentItem();
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
        // ★ 只转冒号，不动逗号
        line = line.replace('\uff1a', ':');

    /*    plugin.getLogger().info(
                "[寻宝] 解析奖励: " + line);*/

        if (line.charAt(0) == '\uFEFF')
            line = line.substring(1);

        // 贴标
        if (line.startsWith("贴标:")) {
            return parseCustomItem(
                    line.substring("贴标:".length()));
        }

        String upper = line.toUpperCase();
        // ★ 债券
        if (upper.contains("债券")) {
            String after = line
                    .replaceAll("(?i)债券", "")
                    .trim();

            int bMin = 1;
            int bMax = 1;
            int w = 25;

            if (!after.isEmpty()) {
                String[] parts = after.split(",");
                String rangePart = parts[0].trim();

                if (parts.length >= 2) {
                    try {
                        w = parseChineseNumber(
                                parts[1].trim());
                    } catch (Exception ignore) {
                    }
                }

                // ★ 范围：~ 或 - 分隔
                if (rangePart.contains("~")) {
                    String[] r =
                            rangePart.split("~");
                    bMin = parseChineseNumber(
                            r[0].trim());
                    bMax = parseChineseNumber(
                            r[1].trim());
                } else if (rangePart.contains("-")) {
                    String[] r =
                            rangePart.split("-");
                    bMin = parseChineseNumber(
                            r[0].trim());
                    bMax = parseChineseNumber(
                            r[1].trim());
                } else {
                    bMin = parseChineseNumber(
                            rangePart);
                    bMax = bMin;
                }
            }

            final int fbMin = bMin;
            final int fbMax = bMax;
            final int fw = w;

            TreasureReward tr =
                    new TreasureReward(
                            TreasureReward.Type.BOND,
                            null, 1, fbMin, null, fw,
                            "§6" + fbMin
                                    + "~" + fbMax
                                    + "债券");
            tr.setBondRange(fbMin, fbMax);

            plugin.getLogger().info(
                    "[寻宝] → 债券: "
                            + fbMin + "~" + fbMax
                            + " 权重=" + fw);
            return tr;
        }


        // 命令
        if (upper.contains("命令")) {
            int ci = line.indexOf(',');
            if (ci < 0) ci = line.indexOf(':');
            String cmd = ci > 0
                    ? line.substring(ci + 1).trim()
                    : "";
            int w = 10;
            if (cmd.contains(",")) {
                String[] parts = cmd.split(",");
                cmd = parts[0].trim();
                w = Integer.parseInt(
                        parts[1].trim());
            }
            return new TreasureReward(
                    TreasureReward.Type.COMMAND,
                    null, 1, 0, cmd, w,
                    "§a命令奖励");
        }

        // 中文分支（给玩家/附魔/时长/攻击）
        if (line.contains("给")
                || line.contains("附魔")
                || line.contains("时长")
                || line.contains("攻击")) {
            return parseGivePlayerItem(line);
        }

        // 中文物品简写（面包,64,10）
        String[] commaParts = line.split(",");
        if (commaParts.length >= 2) {
            String firstPart =
                    commaParts[0].trim();
            boolean hasCN = false;
            for (char c : firstPart.toCharArray()) {
                if (c > 0x4E00) {
                    hasCN = true;
                    break;
                }
            }
            if (hasCN) {
                return parseChineseReward(line);
            }
        }

        // 英文格式（DIAMOND,1,10）
        line = line.replace("，", ",");
        String[] p = line.split(",");
        if (p.length < 2) return null;
        String type = p[0].trim().toUpperCase();

        int amt = Integer.parseInt(p[1].trim());
        int w = p.length > 2
                ? Integer.parseInt(p[2].trim())
                : 10;

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
                {"NETHERITE_PICKAXE",
                        "下界合金镐"},
                {"NETHERITE_INGOT",
                        "下界合金锭"},
                {"BLAZE_ROD", "烈焰棒"},
                {"BREEZE_ROD", "旋风棒"},
                {"ENDER_PEARL", "末影珍珠"},
                {"BREAD", "面包"},
                {"STICK", "木棍"},
                {"BOW", "弓"},
                {"ARROW", "箭"},
                {"SHIELD", "盾牌"},
        };

        String cnName = type;
        for (String[] pair : cnNames) {
            if (pair[0].equals(type)) {
                cnName = pair[1];
                break;
            }
        }

        return new TreasureReward(
                TreasureReward.Type.ITEM,
                type, amt, 0, null, w,
                "§f" + cnName + " x" + amt);
    }

    public void saveConfig(TreasureConfig tc) {
        File dir = new File(
                plugin.getDataFolder(), "寻宝");
        dir.mkdirs();
        File f = new File(dir,
                tc.name + ".sdf1.txt");
        try {
            OutputStreamWriter w =
                    new OutputStreamWriter(
                            new FileOutputStream(f),
                            StandardCharsets.UTF_8);
            w.write("区域名: " + tc.name + "\n");
            w.write("世界: " + tc.world + "\n");
            w.write("坐标: " + tc.minX + ","
                    + tc.minZ + ","
                    + tc.maxX + ","
                    + tc.maxZ + "\n");
            w.write("高度: " + tc.minY + ","
                    + tc.maxY + "\n");
            w.write("最多宝箱: " + tc.maxChests + "\n");
            w.write("检查间隔: "
                    + tc.spawnInterval + "秒\n");
            w.write("生成概率: " + tc.spawnChance + "\n");
            w.write("随机高度: "
                    + (tc.randomY ? "是" : "否") + "\n");
            for (TreasureReward r : tc.rewards) {
                w.write("奖励: " + rewardToString(r) + "\n");
            }
            w.close();
        } catch (IOException e) {
            plugin.getLogger().warning(
                    "[寻宝] 保存失败: " + e.getMessage());
        }
    }

    private String rewardToString(TreasureReward r) {
        if (r.getType() == TreasureReward.Type.BOND) {
            return "债券," + r.getBondMin()
                    + "~" + r.getBondMax()
                    + "," + r.getWeight();
        }
        if (r.getType() == TreasureReward.Type.COMMAND) {
            return "命令," + r.getCommand()
                    + "," + r.getWeight();
        }
        String name = r.getMaterialName();
        int amt = r.getAmount();
        int w = r.getWeight();
        return name + "," + amt + "," + w;
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
                    case "刷新费用":
                        tc.refreshBondCost =
                                Integer.parseInt(
                                        v.trim());
                        break;
                    case "刷新最低奖励":
                        tc.refreshMinRewards =
                                Integer.parseInt(
                                        v.trim());
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
                              int minX, int minZ,
                              int maxX, int maxZ,
                              int minY, int maxY) {

        File dir = new File(
                plugin.getDataFolder(), "寻宝");
        dir.mkdirs();

        // ★ 确认后缀是 .sdf1.txt
        File f = new File(dir,
                name + ".sdf1.txt");

        try {
            OutputStreamWriter w =
                    new OutputStreamWriter(
                            new FileOutputStream(f),
                            StandardCharsets.UTF_8);
            w.write("区域名: " + name + "\n");
            w.write("世界: " + world + "\n");
            w.write("坐标: " + minX + ","
                    + minZ + ","
                    + maxX + "," + maxZ + "\n");
            w.write("高度: " + minY + ","
                    + maxY + "\n");
            w.write("最多宝箱: 2\n");
            w.write("检查间隔: 5分钟\n");
            w.write("生成概率: 100\n");
            w.write("奖励: DIAMOND,1,10\n");
            w.close();
        } catch (IOException e) {
            plugin.getLogger().warning(
                    "[寻宝] 保存失败: "
                            + e.getMessage());
            return false;
        }

        // ★ 加入内存map
        TreasureConfig tc = new TreasureConfig(name);
        tc.world = world;
        tc.minX = minX;
        tc.maxX = maxX;
        tc.minZ = minZ;
        tc.maxZ = maxZ;
        tc.minY = minY;
        tc.maxY = maxY;
        configs.put(name, tc);

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

        // ★ 50次尝试
        for (int attempt = 0;
             attempt < 50; attempt++) {
            int x = ThreadLocalRandom.current()
                    .nextInt(tc.minX, tc.maxX + 1);
            int z = ThreadLocalRandom.current()
                    .nextInt(tc.minZ, tc.maxZ + 1);
            int y = ThreadLocalRandom.current()
                    .nextInt(tc.minY, tc.maxY + 1);

            // ★ 智能搜索2格空气
            Location spot = findTwoAirSpot(
                    w, x, y, z, tc);
            if (spot == null) continue;

            Block block = spot.getBlock();
            block.setType(Material.CHEST);
            tagChest(spot, tc.name);
            activeChests.put(spot, tc.name);

            plugin.getLogger().info(
                    "[寻宝] ★生成: " + tc.name
                            + " [" + spot.getBlockX()
                            + "," + spot.getBlockY()
                            + "," + spot.getBlockZ()
                            + "]");

            if (borderShowing
                    && borderRegionName != null
                    && borderRegionName
                    .equals(tc.name)) {
                showRegionBorder(tc.name);
            }
            return true;
        }

        plugin.getLogger().warning(
                "[寻宝] ★生成失败: " + tc.name
                        + " 50次未找到位置");
        return false;
    }

    /** ★ 从指定坐标开始，上下搜索2格空气 */
    private Location findTwoAirSpot(
            World w, int x, int startY, int z,
            TreasureConfig tc) {
        // 先检查原位
        Location origin =
                new Location(w, x, startY, z);
        if (origin.getBlock().getType()
                == Material.AIR
                && origin.getBlock()
                .getRelative(
                        org.bukkit.block.BlockFace.UP)
                .getType() == Material.AIR) {
            return origin;
        }

        // ★ 从原位向下搜索
        for (int y = startY - 1;
             y >= tc.minY; y--) {
            Location loc =
                    new Location(w, x, y, z);
            Block bottom = loc.getBlock();
            Block top = bottom.getRelative(
                    org.bukkit.block.BlockFace.UP);
            if (bottom.getType() == Material.AIR
                    && top.getType()
                    == Material.AIR) {
                return loc;
            }
        }

        // ★ 从原位向上搜索
        for (int y = startY + 1;
             y <= tc.maxY; y++) {
            Location loc =
                    new Location(w, x, y, z);
            Block bottom = loc.getBlock();
            Block top = bottom.getRelative(
                    org.bukkit.block.BlockFace.UP);
            if (bottom.getType() == Material.AIR
                    && top.getType()
                    == Material.AIR) {
                return loc;
            }
        }

        return null;
    }
    /** ★ 公布宝箱坐标 */
    public boolean announceChest() {
        return announceChest(null);
    }

    /** ★ 公布指定区域最旧宝箱 */
    public boolean announceChest(
            String regionName) {
        if (activeChests.isEmpty()) return false;

        Location oldest = null;
        String oldestRegion = null;
        long oldestTime = Long.MAX_VALUE;

        for (java.util.Map.Entry<Location, String> e
                : activeChests.entrySet()) {
            Location loc = e.getKey();
            String region = e.getValue();
            if (loc.getWorld() == null) continue;
            if (loc.getBlock().getType()
                    != Material.CHEST) continue;

            // ★ 指定了区域就只找该区域
            if (regionName != null
                    && !regionName
                    .equalsIgnoreCase(region)) {
                continue;
            }

            long time = getChestTime(loc);
            if (time < oldestTime) {
                oldestTime = time;
                oldest = loc;
                oldestRegion = region;
            }
        }

        if (oldest == null) return false;

        int cx = oldest.getBlockX();
        int cz = oldest.getBlockZ();

        for (org.bukkit.entity.Player p
                : plugin.getServer()
                .getOnlinePlayers()) {
            p.sendMessage("§6§l[寻宝] §e"
                    + (oldestRegion != null
                    ? oldestRegion : "")
                    + " §a宝藏出现在 X:"
                    + cx + " Z:" + cz);
            p.sendMessage("§7快去寻找吧！");
        }

        plugin.getLogger().info(
                "[寻宝] ★公布: " + oldestRegion
                        + " [" + cx + "," + cz + "]");
        return true;
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
