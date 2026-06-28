package Sdf1_game;

public class BondBridge {

    private Object bondManager;
    private Object sdf1Plugin;
    private boolean hooked = false;

    public boolean hook(Main plugin) {
        try {
            // 使用 Class.forName 避免 JVM 直接加载 Sdf1_login.Main.class
            // 如果 Main 中有 Vault import，直接 getClassLoader().loadClass 会导致 NoClassDefFoundError
            Class<?> mainClass = Class.forName("Sdf1_login.Main");
            sdf1Plugin = mainClass.getField("INSTANCE").get(null);
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning(
                    "[快问快答] 未找到Sdf1_login类，债券奖励不可用");
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning(
                    "[快问快答] 获取Sdf1_login实例失败: " + e.getMessage());
            return false;
        }

        if (sdf1Plugin == null) {
            plugin.getLogger().warning(
                    "[快问快答] Sdf1_login 实例为空");
            return false;
        }

        try {
            bondManager = findManager();
            if (bondManager == null) {
                plugin.getLogger().warning(
                        "[快问快答] 找不到BondManager");
                return false;
            }
            hooked = true;
            plugin.getLogger().info(
                    "[快问快答] 债券桥接成功");
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning(
                    "[快问快答] 桥接失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 查询玩家当前债券余额
     * 使用反射避免触发 Vault class loading
     */
    public int getBonds(String playerName) {
        if (!isHooked() || sdf1Plugin == null) return 0;
        try {
            Class<?> mainClass = Class.forName("Sdf1_login.Main");
            java.lang.reflect.Method getBM = mainClass.getMethod("getBondManager");
            Object bm = getBM.invoke(sdf1Plugin);

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
     * 完全使用反射,避免触发 Main.class 的 Vault 依赖
     */
    private Object findManager() throws Exception {
        Class<?> mainClass = Class.forName("Sdf1_login.Main");

        // 策略1: getBondManager() 方法
        try {
            java.lang.reflect.Method m = mainClass.getMethod("getBondManager");
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
