package Sdf1_game;

import java.util.concurrent.ThreadLocalRandom;

public class TreasureReward {

    public enum Type {
        BOND, COMMAND, ITEM
    }

    private Type type;
    private String materialName;
    private int amount;
    private int bondAmount;
    private int bondMin;
    private int bondMax;
    private String command;
    private int weight;
    private String displayName;
    private int durationSec;
    private String enchant;
    private String rewardLore;
    private int attackUsesLimit;

    public TreasureReward(Type type,
                          String materialName,
                          int amount,
                          int bondAmount,
                          String command,
                          int weight,
                          String displayName,
                          int durationSec,
                          String enchant,
                          String lore,
                          int attackUsesLimit) {
        this.type = type;
        this.materialName = materialName;
        this.amount = amount;
        this.bondAmount = bondAmount;
        this.bondMin = bondAmount;
        this.bondMax = bondAmount;
        this.command = command;
        this.weight = weight;
        this.displayName = displayName;
        this.durationSec = durationSec;
        this.enchant = enchant;
        this.rewardLore = lore;
        this.attackUsesLimit = attackUsesLimit;
    }

    public TreasureReward(Type type,
                          String mat, int amt,
                          int bond, String cmd,
                          int w, String name) {
        this(type, mat, amt, bond, cmd, w, name,
                0, null, null, 0);
    }

    public TreasureReward(Type type,
                          String mat, int amt,
                          int bond, String cmd,
                          int w, String name,
                          int dur, String ench,
                          String lore) {
        this(type, mat, amt, bond, cmd, w, name,
                dur, ench, lore, 0);
    }

    public Type getType() { return type; }
    public String getMaterialName() { return materialName; }
    public int getAmount() { return amount; }
    public int getBondAmount() { return bondAmount; }
    public int getBondMin() { return bondMin; }
    public int getBondMax() { return bondMax; }
    public String getCommand() { return command; }
    public int getWeight() { return weight; }
    public String getDisplayName() { return displayName; }
    public int getDurationSec() { return durationSec; }
    public String getEnchant() { return enchant; }
    public String getLore() { return rewardLore; }
    public int getAttackUsesLimit() { return attackUsesLimit; }

    public void setBondRange(int min, int max) {
        this.bondMin = min;
        this.bondMax = max;
        this.bondAmount = min;
    }

    public int rollBondAmount() {
        if (bondMax <= bondMin) return bondMin;
        return ThreadLocalRandom.current()
                .nextInt(bondMin, bondMax + 1);
    }

    public static String formatTime(int s) {
        if (s <= 0) return "永久";
        int h = s / 3600;
        int m = (s % 3600) / 60;
        int sec = s % 60;
        if (h > 0) return h + "小时" + m + "分钟";
        if (m > 0) return m + "分钟" + sec + "秒";
        return sec + "秒";
    }

    public static int parseDuration(String input) {
        if (input == null || input.isEmpty()) return 0;
        String s = input.trim().toLowerCase();
        if (s.matches("\\d+")) return Integer.parseInt(s);
        if (s.contains("小时") || s.contains("时")) {
            return Integer.parseInt(s.replaceAll("[^\\d]", "")) * 3600;
        }
        if (s.contains("分钟") || s.contains("分")) {
            return Integer.parseInt(s.replaceAll("[^\\d]", "")) * 60;
        }
        if (s.contains("秒")) {
            return Integer.parseInt(s.replaceAll("[^\\d]", ""));
        }
        if (s.contains("天")) {
            return Integer.parseInt(s.replaceAll("[^\\d]", "")) * 86400;
        }
        try { return Integer.parseInt(s); }
        catch (Exception e) { return 0; }
    }
}
