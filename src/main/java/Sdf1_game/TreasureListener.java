package Sdf1_game;

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

import java.util.Map;

public class TreasureListener implements Listener {

    private final Main plugin;
    private final TreasureManager manager;

    public TreasureListener(Main plugin,
                            TreasureManager manager) {
        this.plugin = plugin;
        this.manager = manager;
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
