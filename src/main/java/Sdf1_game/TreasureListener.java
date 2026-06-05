package Sdf1_game;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

public class TreasureListener implements Listener {

    private final Main plugin;
    private final TreasureManager manager;

    public TreasureListener(Main plugin,
                            TreasureManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }
    // ★ 玩家攻击玩家时计数
    @org.bukkit.event.EventHandler
    public void onDamage(
            org.bukkit.event.entity
                    .EntityDamageByEntityEvent e) {

        if (!(e.getDamager()
                instanceof org.bukkit.entity.Player))
            return;
        if (!(e.getEntity()
                instanceof org.bukkit.entity.Player))
            return;

        org.bukkit.entity.Player attacker =
                (org.bukkit.entity.Player)
                        e.getDamager();
        ItemStack hand =
                attacker.getInventory()
                        .getItemInMainHand();

        if (hand == null) return;
        if (!TreasureInventory
                .isCustomItem(hand)) return;

        String[] mark =
                TreasureInventory.parseMark(hand);
        if (mark == null) return;

        String owner = mark[0];
        String region = mark[1];
        String rName = mark[2];
        int maxUse =
                Integer.parseInt(mark[3]);
        int useCount =
                Integer.parseInt(mark[4]) + 1;

        // ★ 只有攻击上限>0的物品才计数
        if (maxUse <= 0) return;

        // ★ 检查归属
        if (!owner.equalsIgnoreCase(
                attacker.getName())) return;

        plugin.getLogger().info(
                "[寻宝] ★攻击: "
                        + attacker.getName()
                        + " " + rName
                        + " " + useCount
                        + "/" + maxUse);

        // ★ 更新lore里的次数
        ItemMeta meta = hand.getItemMeta();
        if (meta != null && meta.hasLore()) {
            List<Component> lore = meta.lore();
            for (int i = 0;
                 i < lore.size(); i++) {
                String line =
                        net.kyori.adventure.text
                                .serializer.legacy
                                .LegacyComponentSerializer
                                .legacySection()
                                .serialize(lore.get(i));
                if (line.contains("§0§kCUSTOM")) {
                    String newMark =
                            "§0§kCUSTOM|"
                                    + owner + "|"
                                    + region + "|"
                                    + rName + "|"
                                    + maxUse + "|"
                                    + useCount;
                    lore.set(i,
                            Component.text(newMark));
                    break;
                }
            }
            meta.lore(lore);
            hand.setItemMeta(meta);
        }

        // ★ 打印给玩家看
        attacker.sendMessage(
                "§e[寻宝] " + rName
                        + " 攻击次数: "
                        + useCount + "/" + maxUse);

        // 达到上限 → 回收
        if (useCount >= maxUse) {
            attacker.getInventory()
                    .setItemInMainHand(null);
            plugin.getTreasureManager()
                    .removeClaim(owner, rName,
                            region);
            attacker.sendMessage(
                    "§c[寻宝] " + rName
                            + " 攻击次数用完，已回收");
            attacker.getWorld().dropItemNaturally(
                    attacker.getLocation(),
                    new ItemStack(Material.AIR));
        }
    }


    // ★ 禁止丢弃寻宝物品
    @org.bukkit.event.EventHandler
    public void onDrop(
            org.bukkit.event.player
                    .PlayerDropItemEvent e) {
        if (TreasureInventory
                .isCustomItem(
                        e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(
                    "§c[寻宝] 寻宝物品不可丢弃");
        }
    }
    // ★ 死亡时回收寻宝物品（不掉落）
    @org.bukkit.event.EventHandler
    public void onDeath(
            org.bukkit.event.entity
                    .PlayerDeathEvent e) {
        org.bukkit.entity.Player player =
                e.getEntity();
        List<ItemStack> drops = e.getDrops();

        // ★ 检查背包里所有物品
        ItemStack[] contents =
                player.getInventory().getContents();
        for (int i = 0;
             i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null) continue;
            if (!TreasureInventory
                    .isCustomItem(item)) continue;

            String[] mark =
                    TreasureInventory.parseMark(item);
            if (mark != null) {
                // 从掉落列表移除
                drops.remove(item);
                // 清除记录
                plugin.getTreasureManager()
                        .removeClaim(mark[0],
                                mark[2], mark[1]);
            }
        }
        // 清空背包里所有寻宝物品
        for (int i = 0;
             i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null) continue;
            if (TreasureInventory
                    .isCustomItem(item)) {
                player.getInventory()
                        .setItem(i, null);
            }
        }
    }
    // ★ 物品掉落到地上时检测（丢弃后立即清除）
    @org.bukkit.event.EventHandler
    public void onItemSpawn(
            org.bukkit.event.entity
                    .ItemSpawnEvent e) {
        ItemStack item =
                e.getEntity().getItemStack();
        if (!TreasureInventory
                .isCustomItem(item)) return;

        // ★ 延迟1秒后检查，如果还在地上就销毁
        //    （已捡起的不会被销毁）
        final org.bukkit.Location loc =
                e.getLocation();
        final String itemKey =
                item.getType().name()
                        + "_" + item.getAmount();
        org.bukkit.Bukkit.getScheduler()
                .runTaskLater(plugin, () -> {
                    for (org.bukkit.entity.Entity ent :
                            loc.getChunk()
                                    .getEntities()) {
                        if (ent instanceof
                                org.bukkit.entity.Item) {
                            org.bukkit.entity.Item di =
                                    (org.bukkit.entity.Item)
                                            ent;
                            if (TreasureInventory
                                    .isCustomItem(
                                            di.getItemStack())) {
                                String[] mark =
                                        TreasureInventory
                                                .parseMark(
                                                        di.getItemStack());
                                if (mark != null) {
                                    // 清除领取记录
                                    plugin
                                            .getTreasureManager()
                                            .removeClaim(
                                                    mark[0],
                                                    mark[2],
                                                    mark[1]);
                                }
                                di.remove();
                                break;
                            }
                        }
                    }
                }, 20L);
    }

    // ===== 圈地工具选点 =====

    @EventHandler
    public void onToolClick(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        // 只处理主手
        if (e.getHand() != EquipmentSlot.HAND)
            return;

        if (!TreasureRegion.isTool(
                p.getInventory().getItemInMainHand()))
            return;

        Block b = e.getClickedBlock();
        if (b == null) return;

        e.setCancelled(true);
        Location loc = b.getLocation();
        TreasureRegion tr =
                TreasureRegion.get(p.getName());

        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            // A点
            tr.setPos1(loc);
            int ex = tr.getExpand();
            p.sendMessage("§a§l[圈地] §7A点已设置: §e"
                    + loc.getBlockX() + ","
                    + loc.getBlockY() + ","
                    + loc.getBlockZ());
            p.sendMessage("§7扩展范围: §e"
                    + ex + "§7格");
            p.sendMessage("§7输入 §e/寻宝 expand <数字> "
                    + "§7调整范围");
            p.sendMessage("§7输入 §e/寻宝 区域名 "
                    + "§7命名并保存");

        } else if (e.getAction()
                == Action.RIGHT_CLICK_BLOCK) {
            tr.setPos2(loc);
            p.sendMessage("§a§l[圈地] §7B点已设置: §e"
                    + loc.getBlockX() + ","
                    + loc.getBlockY() + ","
                    + loc.getBlockZ());
            // ★ 设置B点后自动显示边框
            tr.showBorder();
            showPreview(p, tr);
        }
    }

        // ===== 右键寻宝宝箱 =====

    @EventHandler
    public void onChestInteract(
            PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        if (e.getHand() != EquipmentSlot.HAND) return;

        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.CHEST)
            return;
        Location loc = b.getLocation();
        if (!manager.isTreasure(loc)) return;

        Player p = e.getPlayer();
        e.setCancelled(true);

        String regionName = null;
        for (Map.Entry<Location, String> en
                : manager.getActiveChests().entrySet()) {
            if (en.getKey().equals(loc)) {
                regionName = en.getValue();
                break;
            }
        }

        b.setType(Material.AIR);
        manager.removeChest(loc);

        if (regionName != null) {
            TreasureConfig tc =
                    manager.getConfigs().get(regionName);
            if (tc != null) {
                TreasureInventory.open(p, tc);
            }
        }
    }
    // ★ 玩家下线时标记待回收
    @org.bukkit.event.EventHandler
    public void onQuit(
            org.bukkit.event.player.PlayerQuitEvent e) {
        String name = e.getPlayer().getName();
        // 标记该玩家有物品需要在上线时回收
        plugin.getTreasureManager()
                .getPendingReclaims().add(name);
    }


    // ===== 阻止破坏宝箱 =====

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (e.getBlock().getType() != Material.CHEST)
            return;
        if (manager.isTreasure(
                e.getBlock().getLocation())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(
                    "§c宝箱不能破坏");
        }
    }

    // ===== GUI点击 =====

    @EventHandler
    public void onInventoryClick(
            InventoryClickEvent e) {
        if (e.getInventory().getHolder()
                instanceof TreasureInventory.GuiHolder) {
            TreasureInventory.handleClick(
                    e, plugin.getBondBridge(), plugin);
        }
    }

    @org.bukkit.event.EventHandler(priority =
            org.bukkit.event.EventPriority.HIGH)
    public void onClick(
            org.bukkit.event.inventory
                    .InventoryClickEvent e) {
        if (e.getCursor() == null) return;
        if (!TreasureInventory
                .isCustomItem(e.getCursor()))
            return;
        String type = e.getView()
                .getTopInventory().getType().name();
        if (!type.equals("PLAYER")
                && !type.equals("CRAFTING")
                && !type.equals("CREATIVE")) {
            e.setCancelled(true);
            if (e.getWhoClicked()
                    instanceof org.bukkit.entity.Player) {
                ((org.bukkit.entity.Player)
                        e.getWhoClicked())
                        .sendMessage(
                                "§c[寻宝] 不可放入容器");
            }
        }
    }

    @org.bukkit.event.EventHandler(priority =
            org.bukkit.event.EventPriority.HIGH)
    public void onDrag(
            org.bukkit.event.inventory
                    .InventoryDragEvent e) {
        for (ItemStack item :
                e.getNewItems().values()) {
            if (TreasureInventory
                    .isCustomItem(item)) {
                String type = e.getView()
                        .getTopInventory()
                        .getType().name();
                if (!type.equals("PLAYER")
                        && !type.equals("CRAFTING")) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

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
        String b = e.getClickedBlock()
                .getType().name();
        if (b.contains("SHULKER")
                || b.contains("CHEST")
                || b.equals("BARREL")
                || b.equals("ENDER_CHEST")
                || b.equals("HOPPER")
                || b.equals("DROPPER")
                || b.equals("DISPENSER")) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(
                    "§c[寻宝] 不可放入容器");
        }
    }

    // ★ 上线时扫描背包回收
    @org.bukkit.event.EventHandler
    public void onJoin(
            org.bukkit.event.player
                    .PlayerJoinEvent e) {
        plugin.getTreasureManager()
                .reclaimPlayerItems(
                        e.getPlayer().getName());
    }

    // ★ 手持寻宝物品切换槽位时检查归属
    @org.bukkit.event.EventHandler
    public void onHeld(
            org.bukkit.event.player
                    .PlayerItemHeldEvent e) {
        ItemStack item =
                e.getPlayer().getInventory()
                        .getItem(
                                e.getNewSlot());
        if (item == null) return;
        if (!TreasureInventory
                .isCustomItem(item)) return;

        String[] mark =
                TreasureInventory.parseMark(item);
        if (mark == null) return;

        if (!mark[0].equalsIgnoreCase(
                e.getPlayer().getName())) {
            // 不是本人的→立即回收
            e.getPlayer().getInventory()
                    .setItem(e.getNewSlot(), null);
            plugin.getTreasureManager()
                    .removeClaim(mark[0], mark[2],
                            mark[1]);
            e.getPlayer().sendMessage(
                    "§c[寻宝] " + mark[2]
                            + " 不属于你，已回收");
        }
    }

    // ===== 预览区域范围 =====

    private void showPreview(Player p,
                             TreasureRegion tr) {
        Location a = tr.getPos1();
        Location b = tr.getPos2();
        if (a == null || b == null) {
            p.sendMessage("§c请先设置A点");
            return;
        }

        int ex = tr.getExpand();
        int minX = Math.min(a.getBlockX(),
                b.getBlockX()) - ex;
        int maxX = Math.max(a.getBlockX(),
                b.getBlockX()) + ex;
        int minZ = Math.min(a.getBlockZ(),
                b.getBlockZ()) - ex;
        int maxZ = Math.max(a.getBlockZ(),
                b.getBlockZ()) + ex;

        p.sendMessage("§e§l[圈地] 区域预览:");
        p.sendMessage("§7世界: §e"
                + a.getWorld().getName());
        p.sendMessage("§7范围: §e["
                + minX + "," + minZ + "] ~ ["
                + maxX + "," + maxZ + "]");
        p.sendMessage("§7大小: §e"
                + (maxX - minX + 1) + " x "
                + (maxZ - minZ + 1));
        p.sendMessage("§7扩展: §e" + ex + "格");
        p.sendMessage("§7输入 §e/寻宝 expand <数字> "
                + "§7调整");
        p.sendMessage("§7输入 §e/寻宝 区域名 "
                + "§7保存");
    }
}
