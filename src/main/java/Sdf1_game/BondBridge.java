package Sdf1_game;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class BondBridge {

    private Object bondManager;
    private Method addBondsMethod;
    private boolean hooked = false;

    public boolean hook(Main plugin) {
        Plugin sdf1 = Bukkit.getPluginManager()
                .getPlugin("Sdf1_login");

        if (sdf1 == null || !sdf1.isEnabled()) {
            plugin.getLogger().warning(
                    "[快问快答] 未找到Sdf1_login，"
                            + "债券奖励不可用");
            return false;
        }

        try {
            bondManager = findManager(sdf1);
            if (bondManager == null) {
                plugin.getLogger().warning(
                        "[快问快答] 找不到BondManager");
                return false;
            }

            addBondsMethod =
                    bondManager.getClass()
                            .getMethod("addBonds",
                                    String.class,
                                    int.class,
                                    String.class,
                                    String.class,
                                    String.class,
                                    String.class);

            hooked = true;
            plugin.getLogger().info(
                    "[快问快答] 债券桥接成功");
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning(
                    "[快问快答] 桥接失败: "
                            + e.getMessage());
            return false;
        }
    }
    /** 查询玩家当前债券余额 */
    public int getBonds(String playerName) {
        if (!isHooked()) return 0;
        try {
            org.bukkit.plugin.Plugin plug =
                    org.bukkit.Bukkit
                            .getPluginManager()
                            .getPlugin("Sdf1_login");
            if (plug == null) return 0;

            Object bondManager =
                    plug.getClass()
                            .getMethod("getBondManager")
                            .invoke(plug);

            Object result =
                    bondManager.getClass()
                            .getMethod("getBonds",
                                    String.class)
                            .invoke(bondManager,
                                    playerName);

            return (int) result;
        } catch (Exception e) {
            return 0;
        }
    }


    private Object findManager(Plugin sdf1) {
        Class<?> c = sdf1.getClass();

        // 策略1: getBondManager()
        try {
            Method m = c.getMethod("getBondManager");
            Object r = m.invoke(sdf1);
            if (r != null) return r;
        } catch (Exception ignored) {
        }

        // 策略2: 字段 bondManager
        try {
            Field f = c.getDeclaredField(
                    "bondManager");
            f.setAccessible(true);
            Object r = f.get(sdf1);
            if (r != null) return r;
        } catch (Exception ignored) {
        }

        // 策略3: 遍历字段按类型名查找
        try {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType().getSimpleName()
                        .equals("BondManager")) {
                    f.setAccessible(true);
                    Object r = f.get(sdf1);
                    if (r != null) return r;
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    public boolean addBonds(String player,
                            int amount,
                            String reason) {
        if (!hooked) return false;
        try {
            int r = (int) addBondsMethod.invoke(
                    bondManager,
                    player, amount,
                    "quiz_reward", "",
                    "快问快答", reason);
            return r >= 0;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isHooked() {
        return hooked;
    }
}
