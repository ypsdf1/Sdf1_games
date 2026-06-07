package Sdf1_game;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;


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

    // ★ 开箱设置
    public boolean guaranteeBond = true;
    public int bondMin = 1;
    public int bondMax = 3;
    public int randomMin = 1;
    public int randomMax = 3;
    public long lastSpawnTime = 0;
    // ★ 中文名映射：奖励索引→显示名
    public java.util.Map<Integer, String>
            rewardNames = new java.util.LinkedHashMap<>();
    // ★ 刷新相关
    public int refreshBondCost = 150;
    public int refreshMinRewards = 3;
    public int refreshTimes = 1;


    public TreasureConfig(String name) {
        this.name = name;
    }

    public int getTotalWeight() {
        int w = 0;
        for (TreasureReward r : rewards)
            w += r.getWeight();
        return w;
    }
    public static void ensureTreasureConfig(
            File dir) {
        dir.mkdirs();
        File f = new File(dir, "设置.txt");
        if (f.exists()) return;
        try {
            OutputStreamWriter w =
                    new OutputStreamWriter(
                            new FileOutputStream(f),
                            StandardCharsets.UTF_8);
            w.write("# 寻宝全局设置\n");
            w.write("# 保底债券: 是/否\n");
            w.write("保底债券: 是\n");
            w.write("# 随机奖励数量范围\n");
            w.write("随机数量: 1~3\n");
            w.close();
        } catch (IOException ignored) {
        }
    }

}
