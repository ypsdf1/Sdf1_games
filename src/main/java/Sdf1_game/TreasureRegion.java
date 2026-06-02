package Sdf1_game;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class TreasureRegion {

    public enum State {
        NONE, POS1_SET
    }

    private static final Map<String, TreasureRegion>
            players = new HashMap<>();

    private State state = State.NONE;
    private Location pos1;
    private Location pos2;
    private int expandAmount = 5;
    private String regionName;

    // ★ 粒子边框任务
    private BukkitTask particleTask;
    private boolean borderVisible = false;

    private static final Material TOOL =
            Material.WOODEN_AXE;

    public static TreasureRegion get(String name) {
        return players.computeIfAbsent(
                name, k -> new TreasureRegion());
    }

    public State getState() { return state; }
    public Location getPos1() { return pos1; }
    public Location getPos2() { return pos2; }
    public int getExpand() { return expandAmount; }
    public boolean isBorderVisible() {
        return borderVisible;
    }

    public void setPos1(Location loc) {
        this.pos1 = loc.clone();
        this.state = State.POS1_SET;
    }

    public void setPos2(Location loc) {
        this.pos2 = loc.clone();
    }

    public void setExpand(int val) {
        this.expandAmount = val;
    }

    public void reset() {
        hideBorder();
        this.state = State.NONE;
        this.pos1 = null;
        this.pos2 = null;
        this.expandAmount = 5;
        this.regionName = null;
    }

    /** 计算最终区域范围 */
    public int[] getFinalBounds() {
        if (pos1 == null || pos2 == null)
            return null;
        int minX = Math.min(pos1.getBlockX(),
                pos2.getBlockX()) - expandAmount;
        int maxX = Math.max(pos1.getBlockX(),
                pos2.getBlockX()) + expandAmount;
        int minZ = Math.min(pos1.getBlockZ(),
                pos2.getBlockZ()) - expandAmount;
        int maxZ = Math.max(pos1.getBlockZ(),
                pos2.getBlockZ()) + expandAmount;
        return new int[]{minX, minZ, maxX, maxZ};
    }

    // ★ 粒子边框：显示
    public void showBorder() {
        if (pos1 == null || pos2 == null) return;
        hideBorder();

        World w = pos1.getWorld();
        if (w == null) return;

        int[] b = getFinalBounds();
        if (b == null) return;

        final int minX = b[0], minZ = b[1];
        final int maxX = b[2], maxZ = b[3];

        borderVisible = true;

        particleTask = new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                tick++;
                // 每4tick画一次（频率控制）
                if (tick % 4 != 0) return;

                // 收集所有边框点
                List<Location> points =
                        new ArrayList<>();

                // X轴两条边
                for (int x = minX; x <= maxX; x++) {
                    points.add(getParticleLoc(
                            w, x, minZ));
                    points.add(getParticleLoc(
                            w, x, maxZ));
                }
                // Z轴两条边（不重复角）
                for (int z = minZ + 1; z < maxZ; z++) {
                    points.add(getParticleLoc(
                            w, minX, z));
                    points.add(getParticleLoc(
                            w, maxX, z));
                }

                // 向范围内每个玩家发送粒子
                for (Player p : w.getPlayers()) {
                    int px = p.getLocation()
                            .getBlockX();
                    int pz = p.getLocation()
                            .getBlockZ();
                    // 只给区域附近50格内的玩家
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
        }.runTaskTimer(
                org.bukkit.Bukkit.getPluginManager()
                        .getPlugin("Sdf1_game"),
                0L, 20L);
    }

    private Location getParticleLoc(World w,
                                    int x, int z) {
        int y = w.getHighestBlockYAt(x, z) + 2;
        return new Location(w, x + 0.5, y,
                z + 0.5);
    }

    // ★ 粒子边框：隐藏
    public void hideBorder() {
        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }
        borderVisible = false;
    }

    // ===== 工具 =====

    public static void giveTool(Player p) {
        ItemStack axe = new ItemStack(TOOL);
        ItemMeta meta = axe.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(
                    "§6§l圈地工具 §7- 左键A点右键B点"));
            meta.lore(Arrays.asList(
                    Component.text("§7左键方块 = A点"),
                    Component.text("§7右键方块 = B点")));
            axe.setItemMeta(meta);
        }
        p.getInventory().addItem(axe);
    }

    public static boolean isTool(ItemStack item) {
        if (item == null) return false;
        if (item.getType() != TOOL) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.hasDisplayName()
                && meta.getDisplayName().toString()
                .contains("圈地工具");
    }
}
