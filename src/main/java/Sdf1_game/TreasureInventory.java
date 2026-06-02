package Sdf1_game;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
        GuiHolder holder =
                new GuiHolder(config, player);

        Inventory inv = Bukkit.createInventory(
                holder, 27,
                "§6§l寻宝宝箱 - " + config.name);

        holder.setInventory(inv);

        List<TreasureReward> selected =
                rollRewards(config, 6);

        int[] slots = {10, 12, 14, 16, 22, 24};
        for (int i = 0;
             i < selected.size()
                     && i < slots.length; i++) {
            TreasureReward r = selected.get(i);
            inv.setItem(slots[i],
                    buildDisplayItem(r, i));
        }

        // 边框
        ItemStack glass = new ItemStack(
                Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta gm = glass.getItemMeta();
        if (gm != null) {
            gm.displayName(Component.text(" "));
            glass.setItemMeta(gm);
        }
        for (int i = 0; i < 27; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, glass);
            }
        }

        player.openInventory(inv);
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

        giveReward(player, matched, bb, plugin);

        // 领取后删除GUI物品
        event.getInventory()
                .setItem(event.getRawSlot(), null);

        player.sendMessage("§a§l[寻宝]§r 领取了 §6"
                + matched.getDisplayName() + "§r！");
        return true;
    }

    // ===== 发放奖励 =====

    private static void giveReward(Player player,
                                   TreasureReward r,
                                   BondBridge bb,
                                   Main plugin) {
        switch (r.getType()) {
            case BOND:
                // ★ 直接发债券
                if (bb != null && bb.isHooked()) {
                    bb.addBonds(player.getName(),
                            r.getBondAmount(),
                            "寻宝奖励");
                }
                player.sendMessage("§e获得了 §6"
                        + r.getBondAmount()
                        + " §e债券");
                break;

            case COMMAND:
                String cmd = r.getCommand()
                        .replace("{player}",
                                player.getName());
                Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(), cmd);
                player.sendMessage(
                        "§e执行了命令奖励");
                break;

            case ITEM:
                // ★ 给原版物品：不设自定义名称
                Material mat =
                        Material.matchMaterial(
                                r.getMaterialName());
                if (mat == null) mat = Material.PAPER;
                ItemStack give =
                        new ItemStack(mat,
                                r.getAmount());

                if (r.getDurationSec() > 0) {
                    // ★ 限时贴标物品
                    ItemMeta meta =
                            give.getItemMeta();
                    if (meta != null) {
                        meta.displayName(
                                Component.text(
                                        "§b" + r.getDisplayName()));
                        List<Component> lore =
                                new ArrayList<>();
                        lore.add(Component.text(
                                "§7时限: "
                                        + TreasureReward
                                        .formatTime(
                                                r.getDurationSec())));
                        lore.add(Component.text(
                                "§c§o到期自动收回"));
                        meta.lore(lore);
                        TreasureReward.applyEnchant(
                                meta, r.getEnchant());
                        give.setItemMeta(meta);
                    }
                    player.getInventory()
                            .addItem(give);

                    // 定时收回
                    final ItemStack ref =
                            give.clone();
                    final int dur =
                            r.getDurationSec();
                    final String rName =
                            r.getDisplayName();
                    Bukkit.getScheduler()
                            .runTaskLater(plugin,
                                    () -> {
                                        player.getInventory()
                                                .remove(ref);
                                        if (player.isOnline()) {
                                            player.sendMessage(
                                                    "§c[寻宝] " + rName
                                                            + " §7已到期，自动收回");
                                        }
                                    },
                                    dur * 20L);
                } else {
                    // ★ 普通物品：纯原版，无自定义名称
                    player.getInventory()
                            .addItem(give);
                }

                player.sendMessage("§e获得了 §f"
                        + mat.name()
                        + " §ex" + r.getAmount());
                break;
        }
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