package Sdf1_game;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ThreadLocalRandom;

public class TreasureSpawnTask extends BukkitRunnable {

    private final Main plugin;
    private final TreasureManager manager;
    private int offlineCount = 0;
    private boolean paused = false;

    public TreasureSpawnTask(Main plugin,
                             TreasureManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public void run() {
        int online =
                Bukkit.getOnlinePlayers().size();

        // 无人在线
        if (online == 0) {
            offlineCount++;
            if (offlineCount == 1) {
                plugin.getLogger().info(
                        "[寻宝] 无人在线，60秒后暂停");
            }
            if (offlineCount >= 12 && !paused) {
                paused = true;
                manager.removeAllChests();
                plugin.getLogger().info(
                        "[寻宝] ★暂停+收回");
            }
            return;
        }

        if (paused) {
            paused = false;
            plugin.getLogger().info(
                    "[寻宝] ★玩家上线");
        }

        offlineCount = 0;
        long now = System.currentTimeMillis();

        for (TreasureConfig tc
                : manager.getConfigs().values()) {

            // ★ 首次生成
            if (tc.lastSpawnTime == 0) {
                manager.spawnChest(tc);
                tc.lastSpawnTime = now;
                // ★ 通知所有玩家
                broadcastSpawn(tc);
                continue;
            }

            // 正常计时
            long elapsed = now - tc.lastSpawnTime;
            long need = tc.spawnInterval * 1000L;
            if (elapsed < need) continue;

            int roll = ThreadLocalRandom.current()
                    .nextInt(1, 101);
            if (roll > tc.spawnChance) {
                tc.lastSpawnTime = now;
                continue;
            }

            manager.spawnChest(tc);
            tc.lastSpawnTime = now;
            // ★ 通知所有玩家
            broadcastSpawn(tc);
            plugin.getLogger().info(
                    "[寻宝] ★生成: " + tc.name);
        }
    }
    private void broadcastSpawn(TreasureConfig tc) {
        for (org.bukkit.entity.Player p
                : Bukkit.getOnlinePlayers()) {
            p.sendMessage("§6§l[寻宝] §e"
                    + tc.name + " §a区域刷新了宝箱！");
        }
        plugin.getLogger().info(
                "[寻宝] " + tc.name
                        + " 区域刷新了宝箱！");
    }

}
