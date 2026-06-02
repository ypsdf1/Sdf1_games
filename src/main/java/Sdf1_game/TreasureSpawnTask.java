package Sdf1_game;

import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ThreadLocalRandom;

public class TreasureSpawnTask extends BukkitRunnable {

    private final Main plugin;
    private final TreasureManager manager;

    public TreasureSpawnTask(Main plugin,
                             TreasureManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public void run() {
        for (TreasureConfig tc
                : manager.getConfigs().values()) {
            int roll = ThreadLocalRandom.current()
                    .nextInt(100);
            if (roll >= tc.spawnChance) continue;
            manager.spawnChest(tc);
        }
    }
}
