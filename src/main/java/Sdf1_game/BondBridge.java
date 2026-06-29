package Sdf1_game;

import org.bukkit.Bukkit;

public class BondBridge {

    private Object bondManager;
    private Object sdf1Plugin;
    private boolean hooked = false;

    public boolean hook(Main plugin) {
        // Sdf1_login 已经加载完毕，直接通过 Bukkit 获取实例
        // （Sdf1_login.Main 的 Vault import 不会在这里触发 class loading，
        //   因为我们只调用 getMethod 反射，不会解析 Main 的字段签名）
        try {
            org.bukkit.plugin.Plugin pl = Bukkit.getPluginManager().getPlugin("Sdf1_login");
            if (pl == null || !pl.isEnabled()) {
                plugin.getLogger().warning("[快问快答] 未找到Sdf1_login，债券奖励不可用");
                return false;
            }
            sdf1Plugin = pl;
        } catch (Exception e) {
            plugin.getLogger().warning("[快问快答] 获取Sdf1_login实例失败: " + e.getMessage());
            return false;
        }

        try {
            bondManager = findManager();
            if (bondManager == null) {
                plugin.getLogger().warning("[快问快答] 找不到BondManager");
                return false;
            }
            hooked = true;
            plugin.getLogger().info("[快问快答] 债券桥接成功");
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[快问快答] 桥接失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 查询玩家当前债券余额
     */
    public int getBonds(String playerName) {
        if (!isHooked() || sdf1Plugin == null) return 0;
        try {
            java.lang.reflect.Method getBM = sdf1Plugin.getClass().getMethod("getBonds");
            Object bm = getBM.invoke(sdf1Plugin);
            if (bm == null) return 0;

            java.lang.reflect.Method getBondsM = bm.getClass()
                    .getMethod("getBonds", String.class);
            Object result = getBondsM.invoke(bm, playerName);
            return (int) result;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * findManager: 尝试多种方式获取 BondManager 实例
     */
    private Object findManager() throws Exception {
        Class<?> mainClass = sdf1Plugin.getClass();

        // 策略1: getBondManager() 方法
        try {
            java.lang.reflect.Method m = mainClass.getMethod("getBonds");
            Object r = m.invoke(sdf1Plugin);
            if (r != null) return r;
        } catch (NoSuchMethodException ignored) {
        }

        // 策略2: 字段 bondManager
        try {
            java.lang.reflect.Field f = mainClass.getDeclaredField("bondManager");
            f.setAccessible(true);
            Object r = f.get(sdf1Plugin);
            if (r != null) return r;
        } catch (NoSuchFieldException ignored) {
        }

        // 策略3: 遍历字段按类型名查找
        try {
            for (java.lang.reflect.Field f : mainClass.getDeclaredFields()) {
                if (f.getType().getSimpleName().equals("BondManager")) {
                    f.setAccessible(true);
                    Object r = f.get(sdf1Plugin);
                    if (r != null) return r;
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    public boolean addBonds(String player, int amount, String reason) {
        if (!hooked || bondManager == null) return false;
        try {
            java.lang.reflect.Method m = bondManager.getClass()
                    .getMethod("addBonds",
                            String.class, int.class,
                            String.class, String.class,
                            String.class, String.class);
            int r = (int) m.invoke(bondManager,
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
