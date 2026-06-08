package Sdf1_game;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class TreasureInventory {

    // ★ 奖励索引标记（写在lore里用于精确匹配）
    private static final String LORE_INDEX_PREFIX =
            "§0§k";
    // ★ 唯一的标记前缀
    private static final String MARK = "§0§k";


    public static class GuiHolder
            implements InventoryHolder {
        private final TreasureConfig config;
        private final Player player;
        private Inventory inv;



        public GuiHolder(TreasureConfig config,
                         Player player) {
            this.config = config;
            this.player = player;
        }

        public TreasureConfig getConfig() {
            return config;
        }

        public Player getPlayer() {
            return player;
        }

        public void setInventory(Inventory inv) {
            this.inv = inv;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }


    public static void openTreasure(
            Player player,
            TreasureConfig config,
            Location chestLoc) {
        Main plugin = (Main) Bukkit
                .getPluginManager()
                .getPlugin("Sdf1_game");

        // ★ 复制奖励列表，避免共享引用
        List<TreasureReward> rewardsCopy =
                new ArrayList<>(config.rewards);

        // 保底债券
        int bondAmount = 0;
        if (config.guaranteeBond) {
            for (TreasureReward r : rewardsCopy) {
                if (r.getType()
                        == TreasureReward.Type.BOND) {
                    bondAmount = r.rollBondAmount();
                    break;
                }
            }
        }

        // 随机非债券
        List<TreasureReward> others =
                new ArrayList<>();
        for (TreasureReward r : rewardsCopy) {
            if (r.getType()
                    != TreasureReward.Type.BOND) {
                others.add(r);
            }
        }

        List<TreasureReward> rolled =
                new ArrayList<>();
        int count = ThreadLocalRandom.current()
                .nextInt(
                        config.randomMin,
                        config.randomMax + 1);

        int totalW = 0;
        for (TreasureReward r : others)
            totalW += r.getWeight();

        if (totalW > 0 && !others.isEmpty()) {
            List<TreasureReward> pool =
                    new ArrayList<>(others);
            for (int i = 0;
                 i < count && !pool.isEmpty(); i++) {
                int roll = ThreadLocalRandom
                        .current().nextInt(totalW);
                int acc = 0;
                TreasureReward pick = null;
                for (TreasureReward r : pool) {
                    acc += r.getWeight();
                    if (roll < acc) {
                        pick = r;
                        break;
                    }
                }
                if (pick == null)
                    pick = pool.get(
                            pool.size() - 1);
                rolled.add(pick);
                pool.remove(pick);
                totalW = 0;
                for (TreasureReward r : pool)
                    totalW += r.getWeight();
            }
        }
//开GUI
        showTreasureGUI(player, config, chestLoc,
                bondAmount, rolled, false);
    }


    /** 刷新后的开箱：债券进随机池，保底3个 */
    public static void openRefreshed(
            Player player,
            TreasureConfig config,
            Location chestLoc) {
        Main plugin = (Main) Bukkit
                .getPluginManager()
                .getPlugin("Sdf1_game");

        // ★ 复制奖励列表
        List<TreasureReward> all =
                new ArrayList<>(config.rewards);

        List<TreasureReward> rolled =
                new ArrayList<>();
        int count = ThreadLocalRandom.current()
                .nextInt(
                        config.refreshMinRewards,
                        config.randomMax + 1);

        int totalW = 0;
        for (TreasureReward r : all)
            totalW += r.getWeight();

        if (totalW > 0 && !all.isEmpty()) {
            List<TreasureReward> pool =
                    new ArrayList<>(all);
            for (int i = 0;
                 i < count && !pool.isEmpty(); i++) {
                int roll = ThreadLocalRandom
                        .current().nextInt(totalW);
                int acc = 0;
                TreasureReward pick = null;
                for (TreasureReward r : pool) {
                    acc += r.getWeight();
                    if (roll < acc) {
                        pick = r;
                        break;
                    }
                }
                if (pick == null)
                    pick = pool.get(
                            pool.size() - 1);
                rolled.add(pick);
                pool.remove(pick);
                totalW = 0;
                for (TreasureReward r : pool)
                    totalW += r.getWeight();
            }
        }
//开GUI
        showTreasureGUI(player, config, chestLoc,
                0, rolled, true);
    }

    // ★ 寻宝GUI点击处理（最高优先级）
    @org.bukkit.event.EventHandler(priority =
            org.bukkit.event.EventPriority.HIGHEST,
            ignoreCancelled = true)
    public void onTreasureGUI(
            org.bukkit.event.inventory
                    .InventoryClickEvent e) {
        // ★ 精确匹配标题
        String title = e.getView().getTitle();
        if (!title.contains("寻宝结果")) {
            return;
        }


        // ★ 非玩家背包的槽位都拦截
        int slot = e.getRawSlot();
        if (slot < 0) return;
        e.setCancelled(true);

        // ★ 只处理54格的寻宝GUI
        if (e.getInventory().getSize() != 54) return;

        org.bukkit.entity.Player player =
                (org.bukkit.entity.Player)
                        e.getWhoClicked();
        Main plugin = (Main) org.bukkit.Bukkit
                .getPluginManager()
                .getPlugin("Sdf1_game");
        if (plugin == null) return;

        // ★ 刷新按钮（49格）
        if (slot == 49) {
            Sdf1_game.TreasureManager tm =
                    plugin.getTreasureManager();
            Sdf1_game.TreasureData td =
                    tm.getPlayerTreasureData(
                            player.getName());
            if (td == null) return;
            if (td.isRefreshed) return;

            int cost = td.config.refreshBondCost;
            Sdf1_game.BondBridge bb =
                    plugin.getBondBridge();
            int current = 0;
            if (bb != null && bb.isHooked()) {
                current = bb.getBonds(
                        player.getName());
            }

            if (current < cost) {
                player.sendMessage(
                        "§c[寻宝] 债券不足，需要"
                                + cost + "张，当前"
                                + current + "张");
                return;
            }

            // 扣债券
            if (bb != null && bb.isHooked()) {
                bb.addBonds(player.getName(),
                        -cost, "寻宝刷新");
            }

            // 标记已刷新
            tm.markRefreshed(td.chestLoc);

            // 关闭旧GUI
            player.closeInventory();

            // 刷新后重新开箱（债券进随机池）
            Sdf1_game.TreasureInventory
                    .openRefreshed(player,
                            td.config, td.chestLoc);

            player.sendMessage(
                    "§e[寻宝] 已刷新！消耗"
                            + cost + "债券");
            return;
        }

        // ★ 确认领取按钮（50格）
        if (slot == 50) {
            Sdf1_game.TreasureManager tm =
                    plugin.getTreasureManager();
            Sdf1_game.TreasureData td =
                    tm.getPlayerTreasureData(
                            player.getName());
            if (td == null) return;

            player.closeInventory();

            Sdf1_game.TreasureInventory
                    .giveRewards(player,
                            td.config,
                            td.bondAmount,
                            td.rolled);

            tm.clearPlayerTreasureData(
                    player.getName());
            return;
        }

        // ★ 其他槽位都不处理（纯展示）
    }


    // ★ isCustomItem
    public static boolean isCustomItem(
            ItemStack item) {
        if (item == null) return false;
        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        List<Component> lore = meta.lore();
        if (lore == null) return false;
        for (Component lc : lore) {
            String line =
                    net.kyori.adventure.text
                            .serializer.legacy
                            .LegacyComponentSerializer
                            .legacySection()
                            .serialize(lc);
            // ★ 同时检查两种标记格式
            if (line.contains("CUSTOM")
                    || line.contains("§0§k")) {
                return true;
            }
        }
        return false;
    }
    // ===== buildCustomItem =====
    private static ItemStack buildCustomItem(
            TreasureReward r,
            String playerName,
            String regionName,
            Main plugin) {
        Material mat = Material.matchMaterial(
                r.getMaterialName());
        if (mat == null) {
            mat = Material.PAPER;
        }
        ItemStack item =
                new ItemStack(mat, r.getAmount());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Component.text(
                "§b" + r.getDisplayName()));
        // 附魔
        String enchStr = r.getEnchant();
        if (enchStr != null
                && !enchStr.isEmpty()) {
            String[] pairs = enchStr.split(";");
            for (String pair : pairs) {
                String[] ep = pair.split(",");
                if (ep.length != 2) continue;
                String id = ep[0].trim()
                        .toLowerCase();
                int lvl = 1;
                try {
                    lvl = Integer.parseInt(
                            ep[1].trim());
                } catch (Exception ignore) {
                }

                plugin.getLogger().info(
                        "[寻宝] ★★★尝试附魔: id="
                                + id + " lvl=" + lvl);

                Enchantment ench =
                        Enchantment.getByKey(
                                NamespacedKey
                                        .minecraft(id));

                plugin.getLogger().info(
                        "[寻宝] ★★★getByKey结果: "
                                + ench);

                if (ench == null) {
                    ench = Enchantment.getByName(
                            id.toUpperCase());
                    plugin.getLogger().info(
                            "[寻宝] ★★★getByName结果: "
                                    + ench);
                }

                if (ench != null) {
                    meta.removeEnchant(ench);
                    meta.addEnchant(ench, lvl, true);
                    int actual = meta
                            .getEnchantLevel(ench);
                    plugin.getLogger().info(
                            "[寻宝] ★★★附魔成功: "
                                    + id + " 期望="
                                    + lvl + " 实际="
                                    + actual);
                } else {
                    plugin.getLogger().warning(
                            "[寻宝] ★★★附魔失败: "
                                    + id
                                    + " 两种方式都null");
                }
            }
        }


        // ★ 计算到期时间
        long expireTime = 0;
        if (r.getDurationSec() > 0) {
            expireTime = System.currentTimeMillis()
                    + (long) r.getDurationSec() * 1000;
        }

        // ★ 标记格式: CUSTOM|玩家|区域|名称|上限|次数|到期时间戳
        String tag = "§0§k" + "CUSTOM|"
                + playerName + "|"
                + regionName + "|"
                + r.getDisplayName() + "|"
                + r.getAttackUsesLimit() + "|"
                + "0|"
                + expireTime;

        List<Component> lore = new ArrayList<>();
        if (r.getDurationSec() > 0) {
            lore.add(Component.text("§7时限: "
                    + TreasureReward.formatTime(
                    r.getDurationSec())));
            lore.add(Component.text(
                    "§c§o到期自动收回"));
        }
        if (r.getAttackUsesLimit() > 0) {
            lore.add(Component.text("§c攻击上限: "
                    + r.getAttackUsesLimit() + "次"));
        }
        lore.add(Component.text(tag));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;

    }

    public static void giveRewards(
            Player player,
            TreasureConfig config,
            int bondAmount,
            List<TreasureReward> rolled) {
        Main plugin = (Main) Bukkit
                .getPluginManager()
                .getPlugin("Sdf1_game");
        BondBridge bb = plugin.getBondBridge();
        TreasureManager tm =
                plugin.getTreasureManager();
        StringBuilder msg = new StringBuilder();
        msg.append("§b§l═══ 寻宝结果 ═══\n");

        // 债券
        if (bondAmount > 0) {
            int oldBond = 0;
            if (bb != null && bb.isHooked()) {
                oldBond = bb.getBonds(
                        player.getName());
                bb.addBonds(player.getName(),
                        bondAmount, "寻宝奖励");
            }
            int newBond = oldBond + bondAmount;
            msg.append("§6保底债券: §e")
                    .append(oldBond)
                    .append(" §7→§a ")
                    .append(newBond)
                    .append(" §6(+" + bondAmount + ")\n");
        }

        for (TreasureReward r : rolled) {
            if (r.getType()
                    == TreasureReward.Type.ITEM) {

                // 限时物品领取检查
                if (r.getDurationSec() > 0
                        && tm.isClaimed(
                        player.getName(),
                        r.getDisplayName(),
                        config.name)) {
                    msg.append("§c")
                            .append(r.getDisplayName())
                            .append(" §7已领过\n");
                    continue;
                }

                Material m = resolveMaterial(
                        r.getMaterialName());
                if (m == null) m = Material.PAPER;
                ItemStack give =
                        new ItemStack(m,
                                r.getAmount());
                ItemMeta im = give.getItemMeta();
                if (im != null) {
                    // 名称
                    im.displayName(Component.text(
                            r.getDisplayName()));

                    // lore
                    List<Component> lore =
                            new ArrayList<>();
                    if (r.getDurationSec() > 0) {
                        lore.add(Component.text(
                                "§7时限: "
                                        + TreasureReward
                                        .formatTime(
                                                r.getDurationSec())));
                        lore.add(Component.text(
                                "§c§o到期自动收回"));
                    }
                    if (r.getAttackUsesLimit() > 0) {
                        lore.add(Component.text(
                                "§c攻击上限: "
                                        + r.getAttackUsesLimit()
                                        + "次"));
                    }
                    // ★ 限时物品 CUSTOM 标记
                    if (r.getDurationSec() > 0
                            || r.getAttackUsesLimit()
                            > 0) {
                        lore.add(Component.text(
                                "§0§kCUSTOM|"
                                        + player.getName()
                                        + "|"
                                        + config.name
                                        + "|"
                                        + r.getDisplayName()
                                        + "|"
                                        + r.getAttackUsesLimit()
                                        + "|0|"
                                        + (System.currentTimeMillis()
                                        + (long) r
                                        .getDurationSec()
                                        * 1000)));
                    }
                    im.lore(lore);

                    // ★★★ 附魔 ★★★
                    String enchStr = r.getEnchant();
                    if (enchStr != null
                            && !enchStr.isEmpty()) {
                        String[] pairs =
                                enchStr.split(";");
                        for (String pair : pairs) {
                            String[] ep =
                                    pair.split(",");
                            if (ep.length != 2)
                                continue;
                            String id = ep[0].trim()
                                    .toLowerCase();
                            int lvl = 1;
                            try {
                                lvl = Integer.parseInt(
                                        ep[1].trim());
                            } catch (Exception ignore) {
                            }

                            Enchantment ench =
                                    Enchantment
                                            .getByKey(
                                                    NamespacedKey
                                                            .minecraft(
                                                                    id));
                            if (ench == null) {
                                ench = Enchantment
                                        .getByName(
                                                id.toUpperCase());
                            }

                            if (ench != null) {
                                im.removeEnchant(ench);
                                im.addEnchant(ench,
                                        lvl, true);
                                plugin.getLogger().info(
                                        "[寻宝] ★★附魔: "
                                                + id + "="
                                                + lvl);
                            } else {
                                plugin.getLogger()
                                        .warning(
                                                "[寻宝] ★★附魔null: "
                                                        + id);
                            }
                        }
                    }
                }

                // ★ 设置meta
                give.setItemMeta(im);

                // ★★★ 只发放一次 ★★★
                player.getInventory()
                        .addItem(give);

                // ★ 限时/限次物品记录领取限制（有时限 或 有攻击次数 都要记录）
                if (r.getDurationSec() > 0
                        || r.getAttackUsesLimit() > 0) {
                    tm.claim(player.getName(),
                            r.getDisplayName(),
                            config.name);
                    tm.addClaim(player.getName(),
                            r.getDisplayName(),
                            config.name);
                }

                msg.append("§a✓ ")
                        .append(r.getDisplayName())
                        .append("§7×")
                        .append(r.getAmount())
                        .append("\n");

            } else if (r.getType()
                    == TreasureReward.Type.COMMAND) {
                String cmd = r.getCommand()
                        .replace("{player}",
                                player.getName());
                Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(),
                        cmd);
                msg.append("§a命令已执行\n");
            }
        }

        msg.append("§b§l══════════════");
        player.sendMessage(msg.toString());
        msg.append("§a§l\n欢迎游玩草原探险服务器");
        msg.append("§a§l服务器ip：mc2.ypshidifu.cn\n端口号30679，Java免输入端口，基岩版输端口30679");
    }

    private static Material resolveMaterial(
            String name) {
        if (name == null) return Material.PAPER;

        // 方式1: valueOf（最可靠）
        try {
            return Material.valueOf(
                    name.toUpperCase());
        } catch (Exception ignore) {
        }

        // 方式2: matchMaterial
        Material m = Material.matchMaterial(name);
        if (m != null) return m;

        // 方式3: 中文映射
        String[][] map = {
                {"旋风棒", "BREEZE_ROD"},
                {"旋风棍", "BREEZE_ROD"},
                {"烈焰棒", "BLAZE_ROD"},
                {"钻石", "DIAMOND"},
                {"金锭", "GOLD_INGOT"},
                {"铁锭", "IRON_INGOT"},
                {"面包", "BREAD"},
                {"木棍", "STICK"},
        };
        for (String[] pair : map) {
            if (name.contains(pair[0])) {
                try {
                    return Material.valueOf(pair[1]);
                } catch (Exception ignore) {
                }
            }
        }

        return Material.PAPER;
    }



    // ★ parseMark
    public static String[] parseMark(
            ItemStack item) {
        if (!isCustomItem(item)) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        List<Component> lore = meta.lore();
        if (lore == null) return null;
        for (Component lc : lore) {
            String line =
                    net.kyori.adventure.text
                            .serializer.legacy
                            .LegacyComponentSerializer
                            .legacySection()
                            .serialize(lc);
            if (line.contains("§0§kCUSTOM")) {
                int idx = line.indexOf("§0§kCUSTOM");
                String data =
                        line.substring(idx + 3);
                String[] p = data.split("\\|");
                // ★ 至少7个字段
                if (p.length >= 7) {
                    return new String[]{
                            p[1], // 玩家
                            p[2], // 区域
                            p[3], // 名称
                            p[4], // 上限
                            p[5], // 次数
                            p[6]  // 到期时间戳
                    };
                }
                // 兼容旧格式（6个字段）
                if (p.length >= 6) {
                    return new String[]{
                            p[1], p[2], p[3],
                            p[4], p[5], "0"
                    };
                }
            }
        }
        return null;
    }

    // ★ getMarkName
    private static String getMarkName(
            ItemStack item) {
        String[] m = parseMark(item);
        return m != null ? m[2] : "";
    }




    private static void expiryTimer(
            Player player,
            TreasureManager tm,
            int dur,
            String rName,
            String pName,
            String regionName) {
        // 收回物品
        for (int si = 0;
             si < player.getInventory().getSize();
             si++) {
            ItemStack siItem =
                    player.getInventory()
                            .getItem(si);
            if (siItem != null
                    && isCustomItem(siItem)
                    && getMarkName(siItem)
                    .equals(rName)) {
                player.getInventory()
                        .setItem(si, null);
                break;
            }
        }
        // 清记录
        tm.removeClaim(pName, rName, regionName);
        if (player.isOnline()) {
            player.sendMessage(
                    "§c[寻宝] " + rName
                            + " 已到期收回");
        }
    }



    /**
     * 构建GUI显示物品（含索引标记用于匹配）
     */
    private static ItemStack buildDisplayItem(
            TreasureReward r, int index) {
        switch (r.getType()) {
            case BOND: {
                ItemStack item = new ItemStack(
                        Material.GOLD_NUGGET, 1);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.displayName(Component.text(
                            "§6" + r.getBondAmount()
                                    + " 债券"));
                    List<Component> lore =
                            new ArrayList<>();
                    lore.add(Component.text(
                            LORE_INDEX_PREFIX + index
                                    + ",BOND,"
                                    + r.getBondAmount()));
                    lore.add(Component.text(
                            "§7点击领取"));
                    meta.lore(lore);
                    item.setItemMeta(meta);
                }
                return item;
            }
            case COMMAND: {
                ItemStack item = new ItemStack(
                        Material.PAPER, 1);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.displayName(Component.text(
                            "§a命令奖励"));
                    List<Component> lore =
                            new ArrayList<>();
                    lore.add(Component.text(
                            LORE_INDEX_PREFIX + index
                                    + ",CMD"));
                    lore.add(Component.text(
                            "§7点击领取"));
                    meta.lore(lore);
                    item.setItemMeta(meta);
                }
                return item;
            }
            default: {
                Material mat =
                        Material.matchMaterial(
                                r.getMaterialName());
                if (mat == null) mat = Material.PAPER;
                ItemStack item =
                        new ItemStack(mat, 1);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    // ★ 用中文名
                    String displayName = r
                            .getDisplayName();
                    if (displayName == null
                            || displayName.isEmpty()
                            || displayName.startsWith(
                            "§f")) {
                        displayName =
                                TreasureManager
                                        .getItemDisplayName(
                                                r.getMaterialName());
                    }

                    String amountStr = r.getAmount() > 1
                            ? " x" + r.getAmount()
                            : "";
                    meta.displayName(Component.text(
                            displayName + amountStr));

                    List<Component> lore =
                            new ArrayList<>();
                    lore.add(Component.text(
                            LORE_INDEX_PREFIX + index
                                    + ",ITEM,"
                                    + r.getMaterialName()
                                    + ","
                                    + r.getAmount()));
                    lore.add(Component.text(
                            "§7点击领取"));
                    meta.lore(lore);
                    item.setItemMeta(meta);
                }
                return item;
            }
        }
    }


    // ===== 点击处理 =====

    /** GUI点击处理 */
    public static boolean handleClick(
            InventoryClickEvent event,
            BondBridge bb,
            Main plugin) {
        if (event.getView().getTitle()
                .contains("寻宝结果")) {
            event.setCancelled(true);

            int slot = event.getRawSlot();
            Player player =
                    (Player) event.getWhoClicked();
            TreasureManager tm =
                    plugin.getTreasureManager();
            TreasureData td =
                    tm.getPlayerTreasureData(
                            player.getName());
            if (td == null) return true;

            // ★ 刷新按钮（第49格）
            if (slot == 49 && !td.isRefreshed) {
                // 检查债券
                int cost = td.config
                        .refreshBondCost;
                int current = 0;
                if (bb != null && bb.isHooked()) {
                    current = bb.getBonds(
                            player.getName());
                }
                if (current < cost) {
                    player.sendMessage(
                            "§c[寻宝] 债券不足，需要"
                                    + cost + "张");
                    return true;
                }

                // 扣债券
                if (bb != null && bb.isHooked()) {
                    bb.addBonds(player.getName(),
                            -cost, "寻宝刷新");
                }

                // ★ 标记已刷新
                tm.markRefreshed(td.chestLoc);

                // ★ 刷新后开箱（债券进随机池）
                openRefreshed(player, td.config,
                        td.chestLoc);

                player.sendMessage(
                        "§e[寻宝] 已刷新！消耗"
                                + cost + "债券");
                return true;
            }

            // ★ 确认领取按钮（第50格）
            if (slot == 50) {
                player.closeInventory();
                giveRewards(player, td.config,
                        td.bondAmount,
                        td.rolled);
                tm.clearPlayerTreasureData(
                        player.getName());
                return true;
            }

            return true;
        }
        return false;
    }



    /** 显示寻宝结果GUI */
    private static void showTreasureGUI(
            Player player,
            TreasureConfig config,
            Location chestLoc,
            int bondAmount,
            List<TreasureReward> rolled,
            boolean isRefreshed) {

        Main plugin = (Main) Bukkit
                .getPluginManager()
                .getPlugin("Sdf1_game");

        Inventory inv = Bukkit.createInventory(
                null, 54,
                "§b§l═══ 寻宝结果 ═══");

        int slot = 0;

        // ★ 债券（如果有保底）
        if (bondAmount > 0) {
            ItemStack bondItem =
                    new ItemStack(Material.PAPER);
            ItemMeta bm = bondItem.getItemMeta();
            if (bm != null) {
                bm.displayName(Component.text(
                        "§6保底债券 §ex" + bondAmount));
                List<Component> bl = new ArrayList<>();
                bl.add(Component.text(
                        "§7开箱后自动到账"));
                bm.lore(bl);
                bondItem.setItemMeta(bm);
            }
            inv.setItem(slot, bondItem);
            slot++;
        }

        // ★ 奖励物品
        for (TreasureReward r : rolled) {
            if (slot >= 45) break;

            Material mat = Material.PAPER;

            if (r.getType()
                    == TreasureReward.Type.BOND) {
                mat = Material.PAPER;
            } else if (r.getMaterialName() != null) {
                // 方式1: matchMaterial
                mat = Material.matchMaterial(
                        r.getMaterialName());
                // 方式2: valueOf
                if (mat == null) {
                    try {
                        mat = Material.valueOf(
                                r.getMaterialName());
                    } catch (Exception ignored) {
                    }
                }
                // 方式3: 大写再试
                if (mat == null) {
                    try {
                        mat = Material.valueOf(
                                r.getMaterialName()
                                        .toUpperCase());
                    } catch (Exception ignored) {
                    }
                }
                if (mat == null) {
                    plugin.getLogger().warning(
                            "[寻宝] ★材质未知: "
                                    + r.getMaterialName());
                    mat = Material.PAPER;
                }
            }
            ItemStack item =
                    new ItemStack(mat,
                            r.getAmount());
            ItemMeta im = item.getItemMeta();
            if (im != null) {
                im.displayName(Component.text(
                        r.getDisplayName()));
                List<Component> il =
                        new ArrayList<>();
                if (r.getDurationSec() > 0) {
                    il.add(Component.text(
                            "§7时限: "
                                    + TreasureReward
                                    .formatTime(
                                            r.getDurationSec())));
                }
                if (r.getAttackUsesLimit() > 0) {
                    il.add(Component.text(
                            "§7攻击上限: "
                                    + r.getAttackUsesLimit()
                                    + "次"));
                }
                im.lore(il);
                item.setItemMeta(im);
            }
            inv.setItem(slot, item);
            slot++;
        }

        // ★ 刷新按钮（第50格）
        if (!isRefreshed) {
            ItemStack refreshBtn =
                    new ItemStack(
                            Material.ENCHANTED_GOLDEN_APPLE);
            ItemMeta rb = refreshBtn.getItemMeta();
            if (rb != null) {
                rb.displayName(Component.text(
                        "§e§l刷新奖励 §c(150债券)"));
                List<Component> rl =
                        new ArrayList<>();
                rl.add(Component.text(
                        "§7刷新后取消保底"));
                rl.add(Component.text(
                        "§7债券也加入随机池"));
                rl.add(Component.text(
                        "§7保底3个以上奖励"));
                rl.add(Component.text(
                        "§c每个宝箱只能刷新1次"));
                rb.lore(rl);
                refreshBtn.setItemMeta(rb);
            }
            inv.setItem(49, refreshBtn);
        } else {
            // ★ 已刷新标记
            ItemStack doneBtn =
                    new ItemStack(
                            Material.RED_STAINED_GLASS_PANE);
            ItemMeta db = doneBtn.getItemMeta();
            if (db != null) {
                db.displayName(Component.text(
                        "§c已刷新（不可再刷）"));
                doneBtn.setItemMeta(db);
            }
            inv.setItem(49, doneBtn);
        }

        // ★ 确认领取按钮（第51格）
        ItemStack confirmBtn =
                new ItemStack(Material.CHEST);
        ItemMeta cb = confirmBtn.getItemMeta();
        if (cb != null) {
            cb.displayName(Component.text(
                    "§a§l确认领取"));
            List<Component> cl =
                    new ArrayList<>();
            cl.add(Component.text(
                    "§7点击领取所有奖励"));
            cb.lore(cl);
            confirmBtn.setItemMeta(cb);
        }
        inv.setItem(50, confirmBtn);

        // ★ 装饰边框
        ItemStack filler =
                new ItemStack(
                        Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        if (fm != null) {
            fm.displayName(Component.text(""));
            filler.setItemMeta(fm);
        }
        for (int i = 45; i < 54; i++) {
            if (inv.getItem(i) == null)
                inv.setItem(i, filler);
        }

        // ★ 保存数据到玩家的临时Map
        plugin.getTreasureManager()
                .setPlayerTreasureData(
                        player.getName(),
                        new TreasureData(
                                config, chestLoc,
                                bondAmount,
                                rolled,
                                isRefreshed));

        player.openInventory(inv);
    }

    // ===== 权重随机 =====

    private static List<TreasureReward> rollRewards(
            TreasureConfig config, int count) {
        List<TreasureReward> pool =
                new ArrayList<>(config.rewards);
        List<TreasureReward> result =
                new ArrayList<>();
        int total = config.getTotalWeight();

        // ★ 日志
        Bukkit.getLogger().info(
                "[寻宝GUI] 抽奖: 奖励池="
                        + pool.size()
                        + " 总权重=" + total);

        if (total <= 0 || pool.isEmpty()) {
            Bukkit.getLogger().warning(
                    "[寻宝GUI] ★奖励池为空或总权重=0!");
            return result;
        }

        for (int i = 0;
             i < count && !pool.isEmpty(); i++) {
            int roll = ThreadLocalRandom.current()
                    .nextInt(total);
            int acc = 0;
            TreasureReward picked = null;
            for (TreasureReward r : pool) {
                acc += r.getWeight();
                if (roll < acc) {
                    picked = r;
                    break;
                }
            }
            if (picked == null)
                picked = pool.get(pool.size() - 1);

            // ★ 日志
            Bukkit.getLogger().info(
                    "[寻宝GUI] 抽中[" + i + "]: "
                            + picked.getType()
                            + " " + picked.getMaterialName()
                            + " x" + picked.getAmount()
                            + " 权重=" + picked.getWeight());

            result.add(picked);
            pool.remove(picked);
            total = 0;
            for (TreasureReward r : pool)
                total += r.getWeight();
        }
        return result;
    }
}