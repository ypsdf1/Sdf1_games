package Sdf1_game;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
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


    public static void open(Player player,
                            TreasureConfig config) {
        Main plugin = (Main) Bukkit
                .getPluginManager()
                .getPlugin("Sdf1_game");

        // 保底债券
        int bondAmount = 0;
        if (config.guaranteeBond) {
            for (TreasureReward r : config.rewards) {
                if (r.getType()
                        == TreasureReward.Type.BOND) {
                    bondAmount = r.rollBondAmount();
                    break;
                }
            }
        }

        // 随机非债券奖励
        List<TreasureReward> others =
                new ArrayList<>();
        for (TreasureReward r : config.rewards) {
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
            // ★ 显示GUI让玩家确认
            GuiHolder gh = new GuiHolder(
                    config, player);
            Inventory inv = Bukkit.createInventory(
                    gh, 27,
                    "§6§l═══ 寻宝 ═══ " + config.name);

            // 奖励预览
            List<TreasureReward> preview =
                    new ArrayList<>();
            preview.add(new TreasureReward(
                    TreasureReward.Type.BOND, null,
                    1, bondAmount, null, 0,
                    "§6债券+" + bondAmount));
            preview.addAll(rolled);

            for (int i = 0;
                 i < preview.size() && i < 9; i++) {
                TreasureReward r = preview.get(i);
                Material mat;
                if (r.getType()
                        == TreasureReward.Type.BOND) {
                    mat = Material.PAPER;
                } else {
                    mat = Material.matchMaterial(
                            r.getMaterialName());
                    if (mat == null)
                        mat = Material.PAPER;
                }
                ItemStack slot =
                        new ItemStack(mat,
                                r.getAmount());
                ItemMeta sm = slot.getItemMeta();
                if (sm != null) {
                    sm.displayName(
                            Component.text(
                                    r.getDisplayName()));
                    slot.setItemMeta(sm);
                }
                inv.setItem(i, slot);
            }

            // 装饰边框
            ItemStack filler = new ItemStack(
                    Material.BLACK_STAINED_GLASS_PANE);
            ItemMeta fm = filler.getItemMeta();
            if (fm != null) {
                fm.displayName(Component.text(""));
                filler.setItemMeta(fm);
            }
            for (int i = 0; i < 27; i++) {
                if (inv.getItem(i) == null)
                    inv.setItem(i, filler);
            }

            gh.setInventory(inv);
            player.openInventory(inv);

        }

        // ★ 直接发放+延迟显示结果
        final int fBond = bondAmount;
        final List<TreasureReward> fRolled =
                rolled;
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> giveRewards(player, config,
                        fBond, fRolled), 40L);
    }

    // ★ isCustomItem
    public static boolean isCustomItem(
            ItemStack item) {
        if (item == null) return false;
        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        if (!meta.hasLore()) return false;
        List<Component> lore = meta.lore();
        if (lore == null) return false;
        for (Component lc : lore) {
            String line = net.kyori.adventure.text
                    .serializer.legacy
                    .LegacyComponentSerializer
                    .legacySection().serialize(lc);
            if (line.contains(MARK + "CUSTOM"))
                return true;
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
                Enchantment ench =
                        Enchantment.getByKey(
                                NamespacedKey
                                        .minecraft(id));
                if (ench != null) {
                    // ★ 每个附魔单独打，不共享meta
                    meta.addEnchant(ench, lvl, true);
                }
            }
            // ★ 所有附魔加完后一次性设置
            item.setItemMeta(meta);

        }


        String tag = "§0§kCUSTOM|"
                + playerName + "|"
                + regionName + "|"
                + r.getDisplayName() + "|"
                + r.getAttackUsesLimit() + "|0";
        List<Component> lore = new ArrayList<>();
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
        lore.add(Component.text(tag));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ===== giveRewards =====
    private static void giveRewards(
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
        msg.append("§6§l═══ 寻宝结果 ═══\n");
        // ★ 控制台打印开箱结果
        StringBuilder log = new StringBuilder();
        log.append("[寻宝开箱] ");
        log.append(player.getName());
        log.append(" | 区域=" + config.name);

        // 债券
        if (bondAmount > 0) {
            int oldBond = 0;
            if (bb != null && bb.isHooked()) {
                // ★ 先查原始余额
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

        msg.append("§6债券: §e+")
                    .append(bondAmount)
                    .append(" 张\n");
        }
        for (TreasureReward r : rolled) {
            if (r.getType()
                    == TreasureReward.Type.ITEM) {
                if (r.getDurationSec() > 0
                        && tm.isClaimed(
                        player.getName(),
                        r.getDisplayName(),
                        config.name)) {
                    msg.append("§c")
                            .append(r.getDisplayName())
                            .append(" 已领过\n");
                    continue;
                }
                ItemStack give;
                if (r.getDurationSec() > 0
                        || r.getAttackUsesLimit()
                        > 0) {
                    give = buildCustomItem(r,
                            player.getName(),
                            config.name, plugin);
                    if (r.getDurationSec() > 0) {
                        tm.addClaim(
                                player.getName(),
                                r.getDisplayName(),
                                config.name);
                    }
                } else {
                    Material m =
                            Material.matchMaterial(
                                    r.getMaterialName());
                    if (m == null) {
                        m = Material.PAPER;
                    }
                    give = new ItemStack(m,
                            r.getAmount());
                }
                player.getInventory()
                        .addItem(give);
                msg.append("§f")
                        .append(r.getDisplayName())
                        .append("\n");
                if (r.getDurationSec() > 0) {
                    final int dur =
                            r.getDurationSec();
                    final String rn =
                            r.getDisplayName();
                    final String pn =
                            player.getName();
                    final String rc =
                            config.name;
                    Bukkit.getScheduler()
                            .runTaskLater(plugin,
                                    () -> {
                                        for (int i = 0;
                                             i < player
                                                     .getInventory()
                                                     .getSize();
                                             i++) {
                                            ItemStack it =
                                                    player
                                                            .getInventory()
                                                            .getItem(i);
                                            if (it != null
                                                    && isCustomItem(
                                                    it)
                                                    && getMarkName(
                                                    it)
                                                    .equals(
                                                            rn)) {
                                                player
                                                        .getInventory()
                                                        .setItem(
                                                                i,
                                                                null);
                                                break;
                                            }
                                        }
                                        tm.removeClaim(
                                                pn, rn, rc);
                                        if (player
                                                .isOnline()) {
                                            player
                                                    .sendMessage(
                                                            "§c[寻宝] "
                                                                    + rn
                                                                    + " 已到期收回");
                                        }
                                    },
                                    dur * 20L);
                }
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
        msg.append("§6§l══════════════");
        // ★ 打印到控制台
        plugin.getLogger().info(log.toString());

        player.sendMessage(msg.toString());
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
            String line = net.kyori.adventure.text
                    .serializer.legacy
                    .LegacyComponentSerializer
                    .legacySection().serialize(lc);
            if (line.contains(MARK + "CUSTOM")) {
                String data = line.substring(
                        line.indexOf(MARK + "CUSTOM")
                                + MARK.length());
                String[] p = data.split("\\|");
                if (p.length >= 6)
                    return new String[]{
                            p[1], p[2], p[3],
                            p[4], p[5]};
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

    public static boolean handleClick(
            InventoryClickEvent event,
            BondBridge bb,
            Main plugin) {
        InventoryHolder holder =
                event.getInventory().getHolder();
        if (!(holder instanceof GuiHolder))
            return false;

        event.setCancelled(true);
        if (event.getRawSlot() < 0
                || event.getRawSlot() >= 27)
            return true;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return true;
        if (clicked.getType()
                == Material.BLACK_STAINED_GLASS_PANE)
            return true;

        GuiHolder gh = (GuiHolder) holder;
        Player player = gh.getPlayer();
        TreasureConfig config = gh.getConfig();

        // ★ 从lore中解析索引标记
        ItemMeta im = clicked.getItemMeta();
        if (im == null || !im.hasLore()) return true;

        int rewardIndex = -1;
        for (Component lc : im.lore()) {
            String line = net.kyori.adventure.text
                    .serializer.legacy
                    .LegacyComponentSerializer
                    .legacySection().serialize(lc);
            if (line.startsWith(LORE_INDEX_PREFIX)) {
                // 格式: §0§k0,BOND,50
                String data = line.substring(
                        LORE_INDEX_PREFIX.length());
                String[] parts = data.split(",");
                try {
                    rewardIndex =
                            Integer.parseInt(parts[0]);
                } catch (Exception ignored) {
                }
                break;
            }
        }

        if (rewardIndex < 0
                || rewardIndex >= config.rewards.size())
            return true;

        TreasureReward matched =
                config.rewards.get(rewardIndex);

        player.closeInventory();
        open(player, config);


        // 领取后删除GUI物品
        event.getInventory()
                .setItem(event.getRawSlot(), null);

        player.sendMessage("§a§l[寻宝]§r 领取了 §6"
                + matched.getDisplayName() + "§r！");
        return true;
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