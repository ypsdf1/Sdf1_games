package Sdf1_game;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
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


    // ★ 丢弃拦截
    @org.bukkit.event.EventHandler
    public void onDrop(
            org.bukkit.event.player
                    .PlayerDropItemEvent e) {
        if (TreasureInventory.isCustomItem(
                e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(
                    "§c[寻宝] 寻宝物品不可丢弃");
        }
    }
    // ★ 死亡清除
    @org.bukkit.event.EventHandler
    public void onDeath(
            org.bukkit.event.entity
                    .PlayerDeathEvent e) {
        org.bukkit.entity.Player player =
                e.getEntity();
        for (int i = 0;
             i < player.getInventory()
                     .getSize(); i++) {
            ItemStack item =
                    player.getInventory()
                            .getItem(i);
            if (item == null) continue;
            if (!TreasureInventory
                    .isCustomItem(item)) continue;
            e.getDrops().remove(item);
            player.getInventory()
                    .setItem(i, null);
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
                TreasureInventory.openTreasure(
                        p, tc, loc);


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

    // ★ 寻宝GUI点击处理
    @org.bukkit.event.EventHandler(priority =
            org.bukkit.event.EventPriority.HIGHEST,
            ignoreCancelled = true)
    public void onTreasureGUI(
            org.bukkit.event.inventory
                    .InventoryClickEvent e) {
        String title = e.getView().getTitle();
        if (!title.contains("寻宝结果")) return;

        int slot = e.getRawSlot();
        if (slot < 0) return;
        e.setCancelled(true);
        if (e.getInventory().getSize() != 54) return;

        org.bukkit.entity.Player player =
                (org.bukkit.entity.Player)
                        e.getWhoClicked();
        Main plugin = (Main) org.bukkit.Bukkit
                .getPluginManager()
                .getPlugin("Sdf1_game");
        if (plugin == null) return;

        TreasureManager tm =
                plugin.getTreasureManager();
        TreasureData td =
                tm.getPlayerTreasureData(
                        player.getName());
        if (td == null) return;

        // ★ 刷新按钮（49格）
        if (slot == 49 && !td.isRefreshed) {
            int cost = td.config.refreshBondCost;
            BondBridge bb =
                    plugin.getBondBridge();
            int current = 0;
            if (bb != null && bb.isHooked()) {
                current = bb.getBonds(
                        player.getName());
            }
            if (current < cost) {
                player.sendMessage(
                        "§c[寻宝] 债券不足，需要"
                                + cost
                                + "张，当前"
                                + current + "张");
                return;
            }
            if (bb != null && bb.isHooked()) {
                bb.addBonds(player.getName(),
                        -cost, "寻宝刷新");
            }
            tm.markRefreshed(td.chestLoc);
            player.closeInventory();
            TreasureInventory.openRefreshed(
                    player, td.config,
                    td.chestLoc);
            player.sendMessage(
                    "§e[寻宝] 已刷新！消耗"
                            + cost + "债券");
            return;
        }

        // ★ 确认领取按钮（50格）
        // 确认领取（50格）
        if (slot == 50) {
            player.closeInventory();
            // ★ 只调用一次
            TreasureInventory.giveRewards(
                    player, td.config,
                    td.bondAmount, td.rolled);
            tm.clearPlayerTreasureData(
                    player.getName());
            return;
        }

        // ★ 其他槽位全部取消（只展示）
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
            org.bukkit.event.EventPriority.HIGHEST)
    public void onInvClick(
            org.bukkit.event.inventory
                    .InventoryClickEvent e) {
        if (e.getWhoClicked() == null) return;
        org.bukkit.entity.Player p =
                (org.bukkit.entity.Player)
                        e.getWhoClicked();
        Main plugin = (Main) Bukkit
                .getPluginManager()
                .getPlugin("Sdf1_game");
        String title = e.getView().getTitle();

        // ========== 1. 寻宝GUI ==========
        if (title.contains("寻宝结果")) {
            e.setCancelled(true);
            int slot = e.getRawSlot();
            if (slot < 0) return;
            if (e.getInventory().getSize() != 54)
                return;
            handleTreasureGUI(p, slot, e);
            return;
        }

        // ========== 2. 检测自定义物品 ==========
        ItemStack checkItem = null;

        // 光标有物品（普通点击）
        if (e.getCursor() != null
                && e.getCursor().getType()
                != Material.AIR) {
            checkItem = e.getCursor();
        }

        // shift-click
        if (checkItem == null
                && e.isShiftClick()
                && e.getCurrentItem() != null
                && e.getCurrentItem().getType()
                != Material.AIR) {
            checkItem = e.getCurrentItem();
        }

        // ========== 3. 非自定义物品直接放行 ==========
        if (checkItem == null) return;
        if (!TreasureInventory
                .isCustomItem(checkItem)) return;

        // ========== 4. 确认是容器 ==========
        InventoryType topType = e.getView()
                .getTopInventory().getType();
        // 只放行玩家背包、创造模式背包和玩家合成背包，其他所有容器都拦截
        if (topType == InventoryType.PLAYER
                || topType == InventoryType.CREATIVE
                || topType == InventoryType.CRAFTING) {
            return;
        }

        // ========== 5. 自定义物品+容器 → 拦截 ==========
        e.setCancelled(true);

        plugin.getLogger().info(
                "[寻宝] ★★★拦截: " + p.getName()
                        + " 物品=" + checkItem.getType()
                        + " 容器=" + topType);

        // 非本人→直接没收
        String[] mark = TreasureInventory
                .parseMark(checkItem);
        if (mark != null
                && !mark[0].equalsIgnoreCase(
                p.getName())) {
            checkItem.setAmount(0);
            p.getOpenInventory().setCursor(null);
            p.sendMessage(
                    "§c[寻宝] 该物品不属于你，已没收");
            return;
        }

        // ★ 事不过三
        boolean confiscate = plugin
                .getTreasureManager()
                .onContainerBlock(p.getName());

        if (confiscate) {
            // 第3次：没收
            String itemName = mark != null
                    ? mark[2] : "?";
            String region = mark != null
                    ? mark[1] : "?";

            checkItem.setAmount(0);
            p.getOpenInventory().setCursor(null);

            plugin.getTreasureManager()
                    .unclaim(p.getName(),
                            itemName, region);

            p.sendMessage(
                    "§c[寻宝] 多次违规，"
                            + itemName + " 已被没收！");
        } else {
            // 第1-2次：退回
            if (p.getOpenInventory()
                    .getCursor() != null
                    && p.getOpenInventory()
                    .getCursor().getType()
                    != Material.AIR) {
                ItemStack cur = p.getOpenInventory()
                        .getCursor();
                p.getOpenInventory().setCursor(null);
                p.getInventory().addItem(cur);
            }
            int count = plugin
                    .getTreasureManager()
                    .getContainerBlockCount(
                            p.getName());
            p.sendMessage(
                    "§c[寻宝] 不可放入容器！"
                            + "第" + count + "次警告（"
                            + (3 - count)
                            + "次后没收）");
        }
    }

    // ★ 寻宝GUI处理
    private void handleTreasureGUI(
            org.bukkit.entity.Player player,
            int slot,
            org.bukkit.event.inventory
                    .InventoryClickEvent e) {
        Main plugin = (Main) org.bukkit.Bukkit
                .getPluginManager()
                .getPlugin("Sdf1_game");
        if (plugin == null) return;
        TreasureManager tm =
                plugin.getTreasureManager();
        TreasureData td =
                tm.getPlayerTreasureData(
                        player.getName());
        if (td == null) return;

        // 刷新按钮（49格）
        if (slot == 49 && !td.isRefreshed) {
            int cost = td.config.refreshBondCost;
            BondBridge bb =
                    plugin.getBondBridge();
            int current = 0;
            if (bb != null && bb.isHooked()) {
                current = bb.getBonds(
                        player.getName());
            }
            if (current < cost) {
                player.sendMessage(
                        "§c[寻宝] 债券不足，需要"
                                + cost + "张");
                return;
            }
            if (bb != null && bb.isHooked()) {
                bb.addBonds(player.getName(),
                        -cost, "寻宝刷新");
            }
            tm.markRefreshed(td.chestLoc);
            player.closeInventory();
            TreasureInventory.openRefreshed(
                    player, td.config,
                    td.chestLoc);
            player.sendMessage(
                    "§e[寻宝] 已刷新！消耗"
                            + cost + "债券");
            return;
        }

        // 确认领取（50格）
        if (slot == 50) {
            player.closeInventory();
            TreasureInventory.giveRewards(
                    player, td.config,
                    td.bondAmount, td.rolled);
            tm.clearPlayerTreasureData(
                    player.getName());
            return;
        }
    }

    // ★ 拖拽拦截
    @org.bukkit.event.EventHandler(priority =
            org.bukkit.event.EventPriority.HIGHEST)
    public void onDrag(
            org.bukkit.event.inventory
                    .InventoryDragEvent e) {
        String title = e.getView().getTitle();

        // ★ 垃圾站拖拽拦截
        if (title.contains("垃圾回收站")) {
            for (ItemStack item :
                    e.getNewItems().values()) {
                if (item != null
                        && TreasureInventory
                        .isCustomItem(item)) {
                    e.setCancelled(true);
                    Main plugin = (Main) Bukkit
                            .getPluginManager()
                            .getPlugin("Sdf1_game");
                    if (plugin != null) {
                        plugin.getTreasureManager()
                                .onContainerBlock(
                                        e.getWhoClicked()
                                                .getName());
                    }
                    e.getWhoClicked().sendMessage(
                            "§c[寻宝] 不可拖入垃圾站");
                    return;
                }
            }
            return;
        }

        // ★ 通用容器拖拽拦截
        InventoryType type = e.getView()
                .getTopInventory().getType();
        // 只放行玩家背包和创造模式背包，其他所有容器都拦截
        if (type != InventoryType.PLAYER
                && type != InventoryType.CREATIVE) {
            for (ItemStack item :
                    e.getNewItems().values()) {
                if (item != null
                        && TreasureInventory
                        .isCustomItem(item)) {
                    e.setCancelled(true);
                    e.getWhoClicked().sendMessage(
                            "§c[寻宝] 不可放入容器");
                    return;
                }
            }
        }
    }


    // ★ 铁砧拦截
    @org.bukkit.event.EventHandler(priority =
            org.bukkit.event.EventPriority.HIGHEST)
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


    // ★ 右键容器拦截（HIGHEST 优先级）
    @org.bukkit.event.EventHandler(priority =
            org.bukkit.event.EventPriority.HIGHEST)
    public void onInteract(
            org.bukkit.event.player
                    .PlayerInteractEvent e) {
        // ★ 双重获取物品：e.getItem() + 主手后备
        ItemStack held = e.getItem();
        if (held == null || held.getType() == org.bukkit.Material.AIR) {
            held = e.getPlayer().getInventory()
                    .getItemInMainHand();
        }
        if (held == null || held.getType() == org.bukkit.Material.AIR) return;
        if (!TreasureInventory
                .isCustomItem(held)) return;
        if (!e.getAction().name()
                .contains("RIGHT_CLICK")) return;
        if (e.getClickedBlock() == null) return;
        String bt = e.getClickedBlock()
                .getType().name();
        if (bt.contains("SHULKER")
                || bt.contains("CHEST")
                || bt.equals("BARREL")
                || bt.equals("ENDER_CHEST")
                || bt.equals("HOPPER")
                || bt.equals("DROPPER")
                || bt.equals("DISPENSER")
                || bt.equals("DECORATED_POT")
                || bt.contains("SHELF")) {
            e.setCancelled(true);
            e.setUseItemInHand(
                    org.bukkit.event.Event.Result.DENY);
            e.getPlayer().sendMessage(
                    "§c[寻宝] 不可放入容器");
        }
    }
    // ★ 方块放置拦截 - 防止手持宝箱物品放置方块
    @org.bukkit.event.EventHandler(priority =
            org.bukkit.event.EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent e) {
        ItemStack held = e.getItemInHand();
        if (held == null || held.getType() == Material.AIR) return;
        if (!TreasureInventory.isCustomItem(held)) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(
                "§c[寻宝] 不可使用此物品放置方块");
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

    // ★ 传送门拦截 - 物品通过末地传送门/下界传送门时自动销毁
    @org.bukkit.event.EventHandler(priority =
            org.bukkit.event.EventPriority.HIGHEST)
    public void onPortal(org.bukkit.event.player.PlayerPortalEvent e) {
        Player p = e.getPlayer();
        // 检查玩家背包中的宝箱物品
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null
                    && TreasureInventory.isCustomItem(item)) {
                // 销毁宝箱物品
                item.setAmount(0);
                p.sendMessage("§c[寻宝] 宝箱物品在传送时已自动销毁");
            }
        }
        // 检查副手
        ItemStack offhand = p.getInventory().getItemInOffHand();
        if (offhand != null
                && TreasureInventory.isCustomItem(offhand)) {
            offhand.setAmount(0);
            p.sendMessage("§c[寻宝] 宝箱物品在传送时已自动销毁");
        }
    }

    // ★ 实体传送拦截 - 驴箱子、运输船等实体传送
    @org.bukkit.event.EventHandler(priority =
            org.bukkit.event.EventPriority.HIGHEST)
    public void onEntityPortal(org.bukkit.event.entity.EntityPortalEvent e) {
        // 检查实体是否携带宝箱物品（如驴箱子、运输船）
        if (e.getEntity() instanceof org.bukkit.entity.AnimalTamer) {
            // 这里可以添加更多实体类型的检查
        }
        
        // 检查驴箱子、骡子、羊驼等可骑乘实体
        if (e.getEntity() instanceof org.bukkit.entity.AbstractHorse) {
            org.bukkit.entity.AbstractHorse horse = (org.bukkit.entity.AbstractHorse) e.getEntity();
            // 检查马鞍和装备
            ItemStack saddle = horse.getInventory().getItem(0);
            if (saddle != null && TreasureInventory.isCustomItem(saddle)) {
                saddle.setAmount(0);
                // 通知附近的玩家
                for (Player p : horse.getWorld().getPlayers()) {
                    if (p.getLocation().distance(horse.getLocation()) < 32) {
                        p.sendMessage("§c[寻宝] 驴箱子中的宝箱物品在传送时已自动销毁");
                    }
                }
            }
        }
        
        // 检查运输船（如果有相关API）
        // 注意：Bukkit API可能不直接支持运输船的物品检查
    }

    // ★ 漏斗拦截 - 防止宝箱物品通过漏斗传输
    @org.bukkit.event.EventHandler(priority =
            org.bukkit.event.EventPriority.HIGHEST)
    public void onInventoryMoveItem(org.bukkit.event.inventory.InventoryMoveItemEvent e) {
        ItemStack item = e.getItem();
        if (item != null && TreasureInventory.isCustomItem(item)) {
            // 检查目标容器类型
            InventoryType destinationType = e.getDestination().getType();
            // 只放行玩家背包，其他所有容器都拦截
            if (destinationType != InventoryType.PLAYER
                    && destinationType != InventoryType.CREATIVE) {
                e.setCancelled(true);
                // 将物品弹出到世界
                Location loc = e.getSource().getLocation();
                if (loc != null) {
                    loc.getWorld().dropItemNaturally(loc, item.clone());
                    item.setAmount(0);
                }
            }
        }
    }

    // ★ 展示框拦截 - 防止宝箱物品放入展示框
    @org.bukkit.event.EventHandler(priority =
            org.bukkit.event.EventPriority.HIGHEST)
    public void onPlayerInteractEntity(org.bukkit.event.player.PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof org.bukkit.entity.ItemFrame)) return;
        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item != null && TreasureInventory.isCustomItem(item)) {
            e.setCancelled(true);
            p.sendMessage("§c[寻宝] 宝箱物品不可放入展示框");
        }
    }

    // ★ 打开容器时检查 - 防止宝箱物品通过其他方式进入容器
    @org.bukkit.event.EventHandler(priority =
            org.bukkit.event.EventPriority.HIGHEST)
    public void onInventoryOpen(org.bukkit.event.inventory.InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        Player p = (Player) e.getPlayer();
        InventoryType type = e.getInventory().getType();
        
        // 只检查容器类型，不检查玩家背包
        if (type == InventoryType.PLAYER
                || type == InventoryType.CREATIVE) {
            return;
        }
        
        // 检查容器中是否已有宝箱物品
        for (ItemStack item : e.getInventory().getContents()) {
            if (item != null && TreasureInventory.isCustomItem(item)) {
                // 移除宝箱物品并弹出到世界
                Location loc = e.getInventory().getLocation();
                if (loc != null) {
                    loc.getWorld().dropItemNaturally(loc, item.clone());
                }
                item.setAmount(0);
                p.sendMessage("§c[寻宝] 容器中的宝箱物品已被清除");
            }
        }
    }
}
