package Sdf1_game;

import java.util.ArrayList;
import java.util.List;

public class TreasureConfig {

    public String name = "未命名";
    public String world = "world";
    public int minX, maxX;
    public int minZ, maxZ;
    public int minY = 64;
    public int maxY = 80;
    public int maxChests = 3;
    public int spawnInterval = 300;
    public int spawnChance = 100;
    public boolean randomY = true;
    public List<TreasureReward> rewards =
            new ArrayList<>();

    public TreasureConfig(String name) {
        this.name = name;
    }

    public int getTotalWeight() {
        int w = 0;
        for (TreasureReward r : rewards) {
            w += r.getWeight();
        }
        return w;
    }

    public String getBoundsStr() {
        return "[" + minX + "," + minZ + "] ~ ["
                + maxX + "," + maxZ + "]";
    }
}
