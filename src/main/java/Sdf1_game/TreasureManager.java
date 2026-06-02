package Sdf1_game;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class TreasureManager {

    private final Main plugin;
    private final Map<String, TreasureConfig> configs
            = new LinkedHashMap<>();
    private final Map<Location, String> activeChests
            = new LinkedHashMap<>();
    public int minX, maxX;
    public int minZ, maxZ;
    public int minY = 64;  // 最低高度
    public int maxY = 80;  // 最高高度

    public TreasureManager(Main plugin) {
        this.plugin = plugin;

    }

    // ========== 加载 ==========

    public void loadAll() {
        configs.clear();
        File dir = new File(
                plugin.getDataFolder(), "寻宝");
        dir.mkdirs();

        File[] files = dir.listFiles(
                (d, n) -> n.endsWith(".txt")
                        && !n.equals("配置说明.txt"));
        if (files == null) return;

        for (File f : files) {
            try {
                TreasureConfig tc = parseConfig(f);
                if (tc != null) {
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


    private TreasureConfig parseConfig(File f)
            throws IOException {
        BufferedReader br = new BufferedReader(
                new InputStreamReader(
                        new FileInputStream(f),
                        StandardCharsets.UTF_8));

        TreasureConfig tc = new TreasureConfig(
                f.getName().replace(".txt", ""));

        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()
                    || line.startsWith("#")) continue;
            int ci = line.indexOf(':');
            if (ci < 0) continue;
            String k = line.substring(0, ci).trim();
            String v = line.substring(ci + 1).trim();

            switch (k) {
                case "区域名":
                    tc.name = v; break;
                case "世界":
                    tc.world = v; break;
                case "坐标":
                    // ★ 兼容中文逗号
                    String coordStr = v
                            .replace("，", ",");
                    String[] pp = coordStr.split(",");
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
                case "最多宝箱":
                    tc.maxChests =
                            Integer.parseInt(v);
                    break;
                case "检查间隔":
                    tc.spawnInterval = parseTime(v);
                    break;
                case "生成概率":
                    tc.spawnChance =
                            Integer.parseInt(v);
                    break;
                case "随机高度":
                    tc.randomY = v.equals("是")
                            || v.equals("true");
                    break;
                case "奖励":
                    TreasureReward r =
                            parseReward(v);
                    if (r != null)
                        tc.rewards.add(r);
                    break;
                case "高度":
                    // 格式: minY,maxY
                    String[] yp1 = v.split(",");
                    if (yp1.length == 2) {
                        int y1 = Integer.parseInt(
                                yp1[0].trim());
                        int y2 = Integer.parseInt(
                                yp1[1].trim());
                        tc.minY = Math.min(y1, y2);
                        tc.maxY = Math.max(y1, y2);
                    }
                    break;

            }
        }
        br.close();
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
    // ★ 粒子边框任务
    private BukkitTask borderTask;
    private boolean borderShowing = false;
    private String borderRegionName;

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
                "[寻宝] 解析奖励行: " + line);

        if (line.startsWith("贴标:")) {
            TreasureReward r = parseCustomItem(
                    line.substring("贴标:".length()));
            plugin.getLogger().info(
                    "[寻宝] → 贴标结果: "
                            + (r != null ? "OK" : "NULL"));
            return r;
        }

        // ★ 统一分隔符：中文逗号→英文逗号
        String normalized = line
                .replace("，", ",")
                .replace("。", ".")
                .replace("：", ":")
                .replace("；", ";");

        String[] p = normalized.split(",");
        if (p.length < 2) {
            plugin.getLogger().warning(
                    "[寻宝] ★参数不足: "
                            + p.length + "个");
            return null;
        }

        // ★ 去首尾空格 + 转大写
        String type = p[0].trim().toUpperCase();

        // 债券
        if (type.equals("债券")) {
            int amt = Integer.parseInt(p[1].trim());
            int w = p.length > 2
                    ? Integer.parseInt(p[2].trim())
                    : 10;
            TreasureReward r = new TreasureReward(
                    TreasureReward.Type.BOND,
                    null, 1, amt, null, w,
                    "§6" + amt + "债券");
            plugin.getLogger().info(
                    "[寻宝] → 债券: " + amt
                            + " 权重=" + w);
            return r;
        }

        // 命令
        if (type.equals("命令")) {
            String cmd = p[1].trim();
            int w = p.length > 2
                    ? Integer.parseInt(p[2].trim())
                    : 10;
            TreasureReward r = new TreasureReward(
                    TreasureReward.Type.COMMAND,
                    null, 1, 0, cmd, w,
                    "§a命令奖励");
            plugin.getLogger().info(
                    "[寻宝] → 命令: " + cmd);
            return r;
        }

        // ★ 物品名转大写
        String matName = type;
        int amt = Integer.parseInt(p[1].trim());
        int w = p.length > 2
                ? Integer.parseInt(p[2].trim())
                : 10;

        Material m = Material.matchMaterial(matName);
        if (m == null) {
            plugin.getLogger().warning(
                    "[寻宝] ★物品识别失败: "
                            + matName);
        } else {
            plugin.getLogger().info(
                    "[寻宝] → 物品: " + m.name()
                            + " x" + amt
                            + " 权重=" + w);
        }

        String dn = m != null
                ? "§f" + m.name() : "§f" + matName;
        return new TreasureReward(
                TreasureReward.Type.ITEM,
                matName, amt, 0, null, w, dn);
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
        if (getChestCount(tc.name) >= tc.maxChests)
            return false;

        World w = plugin.getServer()
                .getWorld(tc.world);
        if (w == null) return false;

        normalize(tc);

        int x = ThreadLocalRandom.current()
                .nextInt(tc.minX, tc.maxX + 1);
        int z = ThreadLocalRandom.current()
                .nextInt(tc.minZ, tc.maxZ + 1);

        // ★ 在Y范围内找可放置的位置
        int y = findPlaceableY(w, x, z,
                tc.minY, tc.maxY);
        if (y < 0) {
            plugin.getLogger().warning(
                    "[寻宝] 找不到位置: [" + x
                            + "," + z + "] Y范围="
                            + tc.minY + "~"
                            + tc.maxY);
            return false;
        }

        Location loc = new Location(w, x, y, z);
        loc.getBlock().setType(Material.CHEST);
        activeChests.put(loc, tc.name);

        plugin.getLogger().info(
                "[寻宝] 宝箱:"
                        + " " + tc.name
                        + " [" + x + "," + y
                        + "," + z + "]"
                        + " X=" + tc.minX
                        + "~" + tc.maxX
                        + " Z=" + tc.minZ
                        + "~" + tc.maxZ
                        + " Y=" + tc.minY
                        + "~" + tc.maxY);

        Bukkit.broadcastMessage(
                "§e§l[寻宝]§r §7" + tc.name
                        + " §e区域刷新了宝箱！");

        return true;
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
