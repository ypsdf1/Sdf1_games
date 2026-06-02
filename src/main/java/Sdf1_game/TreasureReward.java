package Sdf1_game;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class TreasureReward {

    public enum Type {
        ITEM, BOND, COMMAND
    }

    private final Type type;
    private final String materialName;
    private final int amount;
    private final int bondAmount;
    private final String command;
    private final int weight;
    private final String displayName;
    private final int durationSec;
    private final String enchant;
    private final String rewardLore;

    public TreasureReward(Type type,
                          String materialName,
                          int amount,
                          int bondAmount,
                          String command,
                          int weight,
                          String displayName,
                          int durationSec,
                          String enchant,
                          String rewardLore) {
        this.type = type;
        this.materialName = materialName;
        this.amount = Math.max(1, amount);
        this.bondAmount = bondAmount;
        this.command = command;
        this.weight = weight;
        this.displayName = displayName;
        this.durationSec = durationSec;
        this.enchant = enchant;
        this.rewardLore = rewardLore;
    }

    public TreasureReward(Type type,
                          String materialName,
                          int amount,
                          int bondAmount,
                          String command,
                          int weight,
                          String displayName) {
        this(type, materialName, amount, bondAmount,
                command, weight, displayName,
                0, null, null);
    }

    public Type getType() { return type; }
    public String getMaterialName() { return materialName; }
    public int getAmount() { return amount; }
    public int getBondAmount() { return bondAmount; }
    public String getCommand() { return command; }
    public int getWeight() { return weight; }
    public String getDisplayName() { return displayName; }
    public int getDurationSec() { return durationSec; }
    public String getEnchant() { return enchant; }

    public ItemStack toItemStack() {
        Material mat = Material.PAPER;
        if (materialName != null
                && !materialName.isEmpty()) {
            Material found =
                    Material.matchMaterial(materialName);
            if (found != null) mat = found;
        }

        ItemStack item = new ItemStack(mat, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(
                    Component.text(displayName));

            List<Component> lore = new ArrayList<>();
            if (durationSec > 0) {
                lore.add(Component.text(
                        "§7时限: " + formatTime(durationSec)));
            }
            if (rewardLore != null
                    && !rewardLore.isEmpty()) {
                lore.add(Component.text(rewardLore));
            }
            lore.add(Component.text("§7点击领取"));
            meta.lore(lore);

            applyEnchant(meta, enchant);

            item.setItemMeta(meta);
        }
        return item;
    }

    /** ★ 安全附魔：用 NamespacedKey */
    static void applyEnchant(ItemMeta meta,
                             String enchant) {
        if (enchant == null || enchant.isEmpty())
            return;
        String[] ep = enchant.split(",");
        if (ep.length != 2) return;
        try {
            // 支持 "KNOCKBACK" 或 "minecraft:knockback"
            String key = ep[0].trim();
            if (!key.contains(":")) {
                key = "minecraft:" + key.toLowerCase();
            }
            NamespacedKey nk =
                    NamespacedKey.fromString(key);
            if (nk == null) return;
            Enchantment ench =
                    Enchantment.getByKey(nk);
            if (ench == null) return;
            int lvl = Integer.parseInt(ep[1].trim());
            meta.addEnchant(ench, lvl, true);
            meta.addItemFlags(
                    ItemFlag.HIDE_ENCHANTS);
        } catch (Exception ignored) {
        }
    }

    static String formatTime(int sec) {
        if (sec >= 3600) {
            int h = sec / 3600;
            int m = (sec % 3600) / 60;
            return m > 0 ? h + "小时" + m + "分钟"
                    : h + "小时";
        }
        int m = sec / 60;
        int s = sec % 60;
        return s > 0 ? m + "分钟" + s + "秒"
                : m + "分钟";
    }
}
