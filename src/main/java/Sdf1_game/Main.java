package Sdf1_game;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main extends JavaPlugin
        implements TabCompleter {

    private BondBridge bondBridge;
    private QuizManager quizManager;
    private TreasureManager treasureManager;
    private BukkitTask spawnTask;

    private static final Pattern SPEC_NUM =
            Pattern.compile(
                    "^(选择题|多选题|填空题|问答题)(\\d+)$");

    private static final List<String> TYPES =
            Arrays.asList("选择题", "多选题",
                    "填空题", "问答题");
    @Override
    public void onEnable() {
        File base = new File(getDataFolder(), "快问快答");
        File qDir = new File(base, "题目");
        qDir.mkdirs();
        FileGenerator.ensureAll(base, qDir);

        bondBridge = new BondBridge();
        bondBridge.hook(this);

        // 快问快答
        quizManager = new QuizManager(this, bondBridge);
        quizManager.loadQuestions();
        getServer().getPluginManager()
                .registerEvents(quizManager, this);
        quizManager.startAuto();

        // 寻宝
        // 寻宝系统
        File treasureDir = new File(getDataFolder(), "寻宝");
        treasureDir.mkdirs();
        treasureManager = new TreasureManager(this);
        treasureManager.loadAll();
        getServer().getPluginManager()
                .registerEvents(
                        new TreasureListener(
                                this, treasureManager),
                        this);
        new TreasureSpawnTask(this, treasureManager)
                .runTaskTimer(this, 100L, 100L);


        // ★ 安全注册 Tab 补全
        if (getCommand("quiz") != null) {
            getCommand("quiz")
                    .setTabCompleter(this);
        }
        if (getCommand("出题") != null) {
            getCommand("出题")
                    .setTabCompleter(this);
        }
        if (getCommand("treasure") != null) {
            getCommand("treasure")
                    .setTabCompleter(this);
        }
        if (getCommand("寻宝") != null) {
            getCommand("寻宝")
                    .setTabCompleter(this);
        }

        // ★ 启动时清除残留宝箱
        if (treasureManager != null) {
            treasureManager.cleanResidualChests();
        }
        if (treasureManager != null) {
            treasureManager.forceCleanClaims();
            treasureManager.cleanResidualChests();
        }
        getLogger().info("=== Sdf1_game v1.0 ===");
        getLogger().info("债券桥接: "
                + (bondBridge.isHooked()
                ? "已连接" : "未连接"));
        getLogger().info("\n" +
                " __          __  _                            _                                                                        \n" +
                " \\ \\        / / | |                          | |                                                                       \n" +
                "  \\ \\  /\\  / /__| | ___ ___  _ __ ___   ___  | |_ ___                                                                  \n" +
                "   \\ \\/  \\/ / _ \\ |/ __/ _ \\| '_ ` _ \\ / _ \\ | __/ _ \\                                                                 \n" +
                "    \\  /\\  /  __/ | (_| (_) | | | | | |  __/ | || (_) |                                                                \n" +
                "     \\/  \\/ \\___|_|\\___\\___/|_| |_| |_|\\___|  \\__\\___/              _                                                  \n" +
                "                                             | |                   (_)                                                 \n" +
                "   ___ __ _  ___    _   _ _   _  __ _ _ __   | |_ __ _ _ __   __  ___  __ _ _ __    ___  ___ _ ____   _____ _ __       \n" +
                "  / __/ _` |/ _ \\  | | | | | | |/ _` | '_ \\  | __/ _` | '_ \\  \\ \\/ / |/ _` | '_ \\  / __|/ _ \\ '__\\ \\ / / _ \\ '__|      \n" +
                " | (_| (_| | (_) | | |_| | |_| | (_| | | | | | || (_| | | | |  >  <| | (_| | | | | \\__ \\  __/ |   \\ V /  __/ |         \n" +
                "  \\___\\__,_|\\___/   \\__, |\\__,_|\\__,_|_| |_|  \\__\\__,_|_| |_|_/_/\\_\\_|\\__,_|_| |_| |___/\\___|_|_  _\\_/ \\___|_|         \n" +
                "                     __/ |      (_)     _                 |__ \\                 | |   (_)   | (_)/ _|                  \n" +
                "  ___  ___ _ ____   |___/ _ __   _ _ __(_)  _ __ ___   ___   ) | _   _ _ __  ___| |__  _  __| |_| |_ _   _   ___ _ __  \n" +
                " / __|/ _ \\ '__\\ \\ / / _ \\ '__| | | '_ \\   | '_ ` _ \\ / __| / / | | | | '_ \\/ __| '_ \\| |/ _` | |  _| | | | / __| '_ \\ \n" +
                " \\__ \\  __/ |   \\ V /  __/ |    | | |_) |  | | | | | | (__ / /_ | |_| | |_) \\__ \\ | | | | (_| | | | | |_| || (__| | | |\n" +
                " |___/\\___|_|    \\_/ \\___|_|    |_| .__(_) |_| |_| |_|\\___|____(_)__, | .__/|___/_| |_|_|\\__,_|_|_|  \\__,_(_)___|_| |_|\n" +
                "                                  | |                             __/ | |                                              \n" +
                "                  _       ____   _|_|   ________ ___             |___/|_|                                              \n" +
                "                 | |  _  |___ \\ / _ \\  / /____  / _ \\                                                                  \n" +
                "  _ __   ___  ___| |_(_)   __) | | | |/ /_   / / (_) |                                                                 \n" +
                " | '_ \\ / _ \\/ __| __|    |__ <| | | | '_ \\ / / \\__, |                                                                 \n" +
                " | |_) | (_) \\__ \\ |_ _   ___) | |_| | (_) / /    / /                                                                  \n" +
                " | .__/ \\___/|___/\\__(_) |____/ \\___/ \\___/_/    /_/                                                                   \n" +
                " | |                                                                                                                   \n" +
                " |_|                                                                                                                   ");
    }


    @Override
    public void onDisable() {
        if (quizManager != null) quizManager.stopAuto();
        if (spawnTask != null) spawnTask.cancel();
        // ★ 卸载时清除所有宝箱
        treasureManager.cleanResidualChests();
        treasureManager.removeAllChests();
        treasureManager.forceCleanClaims();
        if (treasureManager != null) {
            treasureManager.forceCleanClaims();
            treasureManager.cleanResidualChests();
        }
        saveConfig();
    getLogger().info("\n" +
            " __          __                             _                                                                           \n" +
            " \\ \\        / /                            | |                                                                          \n" +
            "  \\ \\  /\\  / /__  ___ ___  _ __ ___   ___  | |_ ___                                                                     \n" +
            "   \\ \\/  \\/ / _ \\/ __/ _ \\| '_ ` _ \\ / _ \\ | __/ _ \\                                                                    \n" +
            "    \\  /\\  /  __/ (_| (_) | | | | | |  __/ | || (_) |                                                                   \n" +
            "     \\/  \\/ \\___|\\___\\___/|_| |_| |_|\\___|  \\__\\___/                _                                                   \n" +
            "                                             | |                   (_)                                                  \n" +
            "   ___ __ _  ___    _   _ _   _  __ _ _ __   | |_ __ _ _ __   __  ___  __ _ _ __    ___  ___ _ ____   _____ _ __        \n" +
            "  / __/ _` |/ _ \\  | | | | | | |/ _` | '_ \\  | __/ _` | '_ \\  \\ \\/ / |/ _` | '_ \\  / __|/ _ \\ '__\\ \\ / / _ \\ '__|       \n" +
            " | (_| (_| | (_) | | |_| | |_| | (_| | | | | | || (_| | | | |  >  <| | (_| | | | | \\__ \\  __/ |   \\ V /  __/ |          \n" +
            "  \\___\\__,_|\\___/   \\__, |\\__,_|\\__,_|_| |_|  \\__\\__,_|_| |_|_/_/\\_\\_|\\__,_|_| |_|_|___/\\___|_| _  \\_/ \\___|_|          \n" +
            "                     __/ |    (_)        _                 |__ \\                 | |   (_)   | (_)/ _|                  \n" +
            "  ___  ___ _ ____   |___/ _ __ _ _ __   (_)  _ __ ___   ___   ) | _   _ _ __  ___| |__  _  __| |_| |_ _   _   ___ _ __  \n" +
            " / __|/ _ \\ '__\\ \\ / / _ \\ '__| | '_ \\      | '_ ` _ \\ / __| / / | | | | '_ \\/ __| '_ \\| |/ _` | |  _| | | | / __| '_ \\ \n" +
            " \\__ \\  __/ |   \\ V /  __/ |  | | |_) |  _  | | | | | | (__ / /_ | |_| | |_) \\__ \\ | | | | (_| | | | | |_| || (__| | | |\n" +
            " |___/\\___|_|    \\_/ \\___|_|  |_| .__/  (_) |_| |_| |_|\\___|____(_)__, | .__/|___/_| |_|_|\\__,_|_|_|  \\__,_(_)___|_| |_|\n" +
            "                                | |                                __/ | |                                              \n" +
            "                  _     ____   _|_|   ________ ___                |___/|_|                                              \n" +
            "                 | |  _|___ \\ / _ \\  / /____  / _ \\                                                                     \n" +
            "  _ __   ___  ___| |_(_) __) | | | |/ /_   / / (_) |                                                                    \n" +
            " | '_ \\ / _ \\/ __| __|  |__ <| | | | '_ \\ / / \\__, |                                                                    \n" +
            " | |_) | (_) \\__ \\ |_ _ ___) | |_| | (_) / /    / /                                                                     \n" +
            " | .__/ \\___/|___/\\__(_)____/ \\___/ \\___/_/    /_/                                                                      \n" +
            " | |                                                                                                                    \n" +
            " |_|                                                                                                                    ");
    }

    // ========== 命令 ==========

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {
        String cmd = command.getName();

        // ★ /skip 或 /跳过
        if (cmd.equals("skip") || label.equals("跳过")) {
            if (!sender.hasPermission("sdf1_quiz.admin")) {
                sender.sendMessage("§c无权限");
                return true;
            }
            quizManager.skipQuestion(sender);
            return true;
        }

        // /出题
        if (cmd.equals("quiz")) {
            if (!sender.hasPermission("sdf1_quiz.admin")) {
                sender.sendMessage("§c无权限");
                return true;
            }
            if (args.length == 0) {
                sendQuizHelp(sender);
                return true;
            }
            switch (args[0].toLowerCase()) {
                case "start":
                    handleStart(sender, shiftArgs(args));
                    return true;
                case "skip":
                    quizManager.skipQuestion(sender);
                    return true;
                case "stop":
                    quizManager.stopAuto();
                    sender.sendMessage("§a已停止");
                    return true;
                case "reload":
                    quizManager.reload();
                    sender.sendMessage("§a已重载");
                    return true;
                case "info":
                    sendQuizInfo(sender);
                    return true;
                case "test":
                    sender.sendMessage(bondBridge.isHooked()
                            ? "§a已连接" : "§c未连接");
                    return true;
                default:
                    sendQuizHelp(sender);
                    return true;
            }
        }

        // /出题（中文别名）
        if (cmd.equals("出题") || label.equals("出题")) {
            if (!sender.hasPermission("sdf1_quiz.admin")) {
                sender.sendMessage("§c无权限");
                return true;
            }
            handleStart(sender, args);
            return true;
        }

        // /treasure /寻宝
        if (cmd.equals("treasure") || cmd.equals("寻宝")) {
            if (!sender.hasPermission("sdf1_treasure.admin")) {
                sender.sendMessage("§c无权限");
                return true;
            }
            return handleTreasure(sender, args);
        }

        return false;
    }

    public TreasureManager getTreasureManager() {
        return treasureManager;
    }

    // ========== 快问快答 ==========

    private String[] shiftArgs(String[] a) {
        if (a.length <= 1) return new String[0];
        return Arrays.copyOfRange(a, 1, a.length);
    }

    private void sendQuizHelp(CommandSender s) {
        s.sendMessage("§e§l【快问快答】§r 命令:");
        s.sendMessage("§7/出题 - 全题型随机");
        s.sendMessage("§7/出题 随机 - 同上");
        s.sendMessage("§7/出题 选择题 - 该题型随机");
        s.sendMessage("§7/出题 选择题 随机 - 同上");
        s.sendMessage("§7/出题 选择题1 - 第1题");
        s.sendMessage("§7/出题 选择题 钻石 - 按内容");
        s.sendMessage("§7/quiz stop/info/reload/test");
        s.sendMessage("§a§l欢迎游玩草原探险服务器");
        s.sendMessage("§b§l官方群：981954292");
        s.sendMessage("§b§l服务器ip：mc2.ypshidifu.cn");
        s.sendMessage("§b§lJava免输端口基岩版30679");
    }

    private void sendQuizInfo(CommandSender s) {
        s.sendMessage("§e§l【快问快答】§r 状态:");
        s.sendMessage("§7模式: §e"
                + quizManager.getMode());
        s.sendMessage("§7答题中: §e"
                + (quizManager.isActive() ? "是" : "否"));
        s.sendMessage("§7选择: §e"
                + quizManager.getChoiceCount()
                + " §7多选: §e"
                + quizManager.getMultiCount()
                + " §7填空: §e"
                + quizManager.getFillCount()
                + " §7问答: §e"
                + quizManager.getOpenCount());
    }

    private void handleStart(CommandSender s,
                             String[] a) {
        if (a.length == 0) {
            doStart(s, null, 0, null);
            return;
        }
        String a0 = a[0];

        if (a0.equals("随机")) {
            doStart(s, null, 0, null);
            return;
        }
        if (TYPES.contains(a0) && a.length == 1) {
            doStart(s, a0, 0, null);
            return;
        }
        if (TYPES.contains(a0) && a.length >= 2
                && a[1].equals("随机")) {
            doStart(s, a0, 0, null);
            return;
        }
        Matcher m = SPEC_NUM.matcher(a0);
        if (m.matches()) {
            doStart(s, m.group(1),
                    Integer.parseInt(m.group(2)),
                    null);
            return;
        }
        if (TYPES.contains(a0) && a.length >= 2) {
            try {
                int n = Integer.parseInt(a[1]);
                doStart(s, a0, n, null);
                return;
            } catch (NumberFormatException e) {
            }
        }
        if (TYPES.contains(a0) && a.length >= 2) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < a.length; i++) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(a[i]);
            }
            String kw = sb.toString().trim();
            Question q = quizManager
                    .findByKeyword(a0, kw);
            if (q != null) {
                doStart(s, null, 0, q);
            } else {
                s.sendMessage("§c未找到: " + kw);
            }
            return;
        }
        s.sendMessage("§c格式错误:");
        sendQuizHelp(s);
    }

    private void doStart(CommandSender s, String type,
                         int idx, Question q) {
        if (Bukkit.getOnlinePlayers().size() < 1) {
            s.sendMessage("§c服务器无人");
            return;
        }
        if (quizManager.isActive()) {
            s.sendMessage("§c题目进行中");
            return;
        }
        if (q != null) {
            quizManager.forceQuestion(q);
            s.sendMessage("§a已出题: ["
                    + q.getTypeName() + "] "
                    + q.getQuestion());
        } else if (type != null && idx > 0) {
            quizManager.forceQuestion(type, idx);
            s.sendMessage("§a已出题: "
                    + type + "第" + idx + "题");
        } else if (type != null) {
            quizManager.forceQuestionByType(type);
            s.sendMessage("§a已出题: "
                    + type + "(随机)");
        } else {
            quizManager.forceQuestion();
            s.sendMessage("§a已随机出题");
        }
    }

    // ========== 寻宝命令 ==========

    private boolean handleTreasure(CommandSender s,
                                   String[] a) {
        if (a.length == 0) {
            sendTreasureHelp(s);
            return true;
        }
        switch (a[0].toLowerCase()) {
            case "info":
                s.sendMessage("§e§l【寻宝】§r 区域数: §e"
                        + treasureManager.getConfigs().size());
                s.sendMessage("§7债券: "
                        + (bondBridge.isHooked()
                        ? "§a已连接" : "§c未连接"));
                return true;
            case "list":
                sendTreasureList(s);
                return true;
            case "reload":
            case "重载":
                treasureManager.forceCleanClaims();
                for (org.bukkit.entity.Player p :
                        Bukkit.getOnlinePlayers()) {
                    treasureManager
                            .reclaimAllTreasureItems(p);
                }
                treasureManager.reload();
                s.sendMessage(
                        "§a[寻宝] 重载完成，领取记录已清空");
                s.sendMessage("§b§l\n欢迎游玩草原探险服务器");
                s.sendMessage("§b§l服务器ip：mc2.ypshidifu.cn\n端口30679");
                s.sendMessage("");
                return true;
            case "工具":
            case "tool":
                return handleTool(s);
            case "expand":
                return handleExpand(s, a);
            case "return":
                return handleReturn(s, a);
            case "removeregion":
                if (a.length < 2) {
                    s.sendMessage("§7/寻宝 removeregion 区域名");
                    return true;
                }
                treasureManager.removeRegion(a[1]);
                s.sendMessage("§a已删除: " + a[1]);
                s.sendMessage("§b§l\n欢迎游玩草原探险服务器");
                s.sendMessage("§b§l服务器ip：mc2.ypshidifu.cn\n端口30679");
                s.sendMessage("");
                return true;
            // ★ on/off 代替 border
            case "on":
            case "开启":
            case "off":
            case "关闭":
                return handleBorder(s, a);
            default:
                return handleSetName(s, a);
        }
    }

    /**
     * /寻宝 on          → 显示圈地中AB点的边框
     * /寻宝 off         → 隐藏圈地中AB点的边框
     * /寻宝 on 新手村   → 显示已保存区域的边框
     * /寻宝 off 新手村  → 隐藏已保存区域的边框
     */
    private boolean handleBorder(CommandSender s,
                                 String[] a) {
        if (!(s instanceof Player)) {
            s.sendMessage("§c仅玩家");
            return true;
        }

        boolean on = a[0].equalsIgnoreCase("on")
                || a[0].equalsIgnoreCase("开启");

        // /寻宝 on  区域名
        if (a.length >= 2) {
            String regionName = a[1];
            if (!treasureManager.getConfigs()
                    .containsKey(regionName)) {
                s.sendMessage("§c区域不存在: "
                        + regionName);
                s.sendMessage("§b§l\n欢迎游玩草原探险服务器");
                s.sendMessage("§b§l服务器ip：mc2.ypshidifu.cn\n端口30679");
                s.sendMessage("");
                return true;
            }

            if (on) {
                treasureManager
                        .showRegionBorder(regionName);
                TreasureConfig tc =
                        treasureManager.getConfigs()
                                .get(regionName);
                s.sendMessage("§a边框已显示: "
                        + regionName);
                s.sendMessage("§7范围: ["
                        + tc.minX + "," + tc.minZ
                        + "] ~ [" + tc.maxX + ","
                        + tc.maxZ + "]");
            } else {
                treasureManager
                        .hideRegionBorder(regionName);
                s.sendMessage("§7边框已隐藏: "
                        + regionName);
            }
            return true;
        }

        // /寻宝 on 无区域名 → 圈地中的AB点
        Player p = (Player) s;
        TreasureRegion tr =
                TreasureRegion.get(p.getName());

        if (tr.getPos1() == null) {
            s.sendMessage("§c请先获取工具并设置AB点");
            s.sendMessage("§7或: /寻宝 on 区域名");
            s.sendMessage("§b§l\n欢迎游玩草原探险服务器");
            s.sendMessage("§b§l服务器ip：mc2.ypshidifu.cn\n端口30679");
            s.sendMessage("");
            return true;
        }

        if (on) {
            tr.showBorder();
            int[] b = tr.getFinalBounds();
            s.sendMessage("§a边框已显示");
            if (b != null) {
                s.sendMessage("§7范围: ["
                        + b[0] + "," + b[1]
                        + "] ~ [" + b[2] + ","
                        + b[3] + "]");
            }
        } else {
            tr.hideBorder();
            s.sendMessage("§7边框已隐藏");
        }
        return true;
    }


    // /寻宝 工具
    private boolean handleTool(CommandSender s) {
        if (!(s instanceof Player)) {
            s.sendMessage("§c仅玩家");
            return true;
        }
        TreasureRegion.giveTool((Player) s);
        s.sendMessage("§a已获得圈地工具");
        s.sendMessage("§7左键=A点 右键=B点");
        s.sendMessage("§b§l\n欢迎游玩草原探险服务器");
        s.sendMessage("§b§l服务器ip：mc2.ypshidifu.cn\n端口30679");
        s.sendMessage("");
        return true;
    }

    // /寻宝 expand 区域名 [格数]
    private boolean handleExpand(CommandSender s,
                                 String[] a) {
        if (a.length < 2) {
            s.sendMessage("§7/寻宝 expand 区域名 [格数]");
            s.sendMessage("§7默认扩展5格");
            return true;
        }
        String name = a[1];
        int n = 5;
        if (a.length >= 3) {
            try {
                n = Integer.parseInt(a[2]);
            } catch (NumberFormatException e) {
                s.sendMessage("§c请输入数字");
                return true;
            }
        }
        if (n <= 0) {
            s.sendMessage("§c数字须大于0");
            return true;
        }
        if (!treasureManager.getConfigs()
                .containsKey(name)) {
            s.sendMessage("§c区域不存在: " + name);
            return true;
        }
        boolean ok = treasureManager
                .expandRegion(name, n);
        if (ok) {
            TreasureConfig tc =
                    treasureManager.getConfigs()
                            .get(name);
            s.sendMessage("§a区域「" + name
                    + "」已扩展" + n + "格");
            s.sendMessage("§7新范围: ["
                    + tc.minX + "," + tc.minZ
                    + "] ~ ["
                    + tc.maxX + "," + tc.maxZ + "]");
        } else {
            s.sendMessage("§c扩展失败");
        }
        return true;
    }

    // /寻宝 return 区域名 [格数]
    private boolean handleReturn(CommandSender s,
                                 String[] a) {
        if (a.length < 2) {
            s.sendMessage("§7/寻宝 return 区域名 [格数]");
            s.sendMessage("§7默认收缩5格");
            return true;
        }
        String name = a[1];
        int n = 5;
        if (a.length >= 3) {
            try {
                n = Integer.parseInt(a[2]);
            } catch (NumberFormatException e) {
                s.sendMessage("§c请输入数字");
                return true;
            }
        }
        if (n <= 0) {
            s.sendMessage("§c数字须大于0");
            return true;
        }
        if (!treasureManager.getConfigs()
                .containsKey(name)) {
            s.sendMessage("§c区域不存在: " + name);
            return true;
        }
        boolean ok = treasureManager
                .returnRegion(name, n);
        if (ok) {
            TreasureConfig tc =
                    treasureManager.getConfigs()
                            .get(name);
            s.sendMessage("§a区域「" + name
                    + "」已收缩" + n + "格");
            s.sendMessage("§7新范围: ["
                    + tc.minX + "," + tc.minZ
                    + "] ~ ["
                    + tc.maxX + "," + tc.maxZ + "]");
        } else {
            s.sendMessage("§c收缩失败，区域太小");
        }
        return true;
    }

    // /寻宝 区域名（圈地保存）
    private boolean handleSetName(CommandSender s,
                                  String[] a) {
        if (!(s instanceof Player)) {
            s.sendMessage("§c仅玩家");
            return true;
        }
        Player p = (Player) s;
        TreasureRegion tr =
                TreasureRegion.get(p.getName());

        if (tr.getPos1() == null
                || tr.getPos2() == null) {
            s.sendMessage("§c请先用工具选AB两点");
            s.sendMessage("§7/寻宝 工具 获取工具");
            return true;
        }

        String name = a[0];
        Location p1 = tr.getPos1();
        Location p2 = tr.getPos2();
        int ex = tr.getExpand();

        int minX = Math.min(p1.getBlockX(),
                p2.getBlockX()) - ex;
        int maxX = Math.max(p1.getBlockX(),
                p2.getBlockX()) + ex;
        int minZ = Math.min(p1.getBlockZ(),
                p2.getBlockZ()) - ex;
        int maxZ = Math.max(p1.getBlockZ(),
                p2.getBlockZ()) + ex;
        // ★ 高度范围 = A/B点Y中较小的~较大的
        int y1 = p1.getBlockY();
        int y2 = p2.getBlockY();
        int hMin = Math.min(y1, y2);
        int hMax = Math.max(y1, y2);

        boolean ok = treasureManager.saveRegion(
                name,
                p1.getWorld().getName(),
                minX, minZ, maxX, maxZ,
                hMin, hMax);

        if (ok) {
            tr.hideBorder();
            tr.reset();
            treasureManager.showRegionBorder(name);
            s.sendMessage("§a区域「" + name
                    + "」已创建!");
            s.sendMessage("§7范围: ["
                    + minX + "," + minZ
                    + "] ~ [" + maxX + "," + maxZ + "]");
            s.sendMessage("§7高度: Y"
                    + hMin + "~Y" + hMax);
            s.sendMessage("§7使用 §e/寻宝 off "
                    + name + " §7关闭边框");
            s.sendMessage("§b§l\n欢迎游玩草原探险服务器");
            s.sendMessage("§b§l服务器ip：mc2.ypshidifu.cn\n端口30679");
            s.sendMessage("");
        }

        boolean o1k = treasureManager.saveRegion(
                name,
                p1.getWorld().getName(),
                minX, minZ, maxX, maxZ,
                hMin, hMax);

        if (ok) {
            tr.reset();
            s.sendMessage("§a区域「" + name
                    + "」已创建!");
            s.sendMessage("§7范围: ["
                    + minX + "," + minZ + "] ~ ["
                    + maxX + "," + maxZ + "]");
            s.sendMessage("§7使用 §e/寻宝 expand "
                    + name + " §7扩展");
            s.sendMessage("§7使用 §e/寻宝 return "
                    + name + " §7收缩");
        } else {
            s.sendMessage("§c创建失败");
        }
        return true;
    }

    private void sendTreasureHelp(CommandSender s) {
        s.sendMessage("§e§l【寻宝】§r 命令:");
        s.sendMessage("§7/寻宝 工具 - 获取圈地工具");
        s.sendMessage("§7/寻宝 expand 区域名 [格数] - 扩展");
        s.sendMessage("§7/寻宝 return 区域名 [格数] - 收缩");
        s.sendMessage("§7/寻宝 区域名 - 保存区域");
        s.sendMessage("§7/寻宝 on/off - 显示/隐藏边框");
        s.sendMessage("§7/寻宝 on/off 区域名 - 显示/隐藏已保存区域边框");
        s.sendMessage("§7/寻宝 removeregion 区域名 - 删除");
        s.sendMessage("§7/寻宝 list - 列表");
        s.sendMessage("§7/寻宝 info - 状态");
        s.sendMessage("§7/寻宝 reload - 重载");
        s.sendMessage("§a欢迎游玩：草原探险服务器");
        s.sendMessage("§b§l服务器ip: mc2.ypshidifu.cn");
        s.sendMessage("§e§lJava免输端口，基岩版端口30679");
        s.sendMessage("§a§l官方群：981954292");
    }


    private void sendTreasureList(CommandSender s) {
        List<String> names =
                treasureManager.getRegionNames();
        if (names.isEmpty()) {
            s.sendMessage("§7暂无区域");
            return;
        }
        s.sendMessage("§e§l【寻宝区域】");
        for (String n : names) {
            TreasureConfig tc =
                    treasureManager.getConfigs().get(n);
            s.sendMessage("§7- §e" + n
                    + " §7[" + tc.world + "]"
                    + " 宝箱§e"
                    + treasureManager
                    .getChestCount(n)
                    + "/" + tc.maxChests);
        }
    }



    public BondBridge getBondBridge() {
        return bondBridge;
    }

    // ========== Tab ==========

    @Override
    public List<String> onTabComplete(
            CommandSender s,
            Command c,
            String l,
            String[] a) {
        List<String> r = new ArrayList<>();
        String cn = c.getName();

        // /quiz 子命令
        if (cn.equals("quiz") && a.length == 1) {
            String in = a[0].toLowerCase();
            for (String x : Arrays.asList(
                    "start", "stop", "reload",
                    "test", "info", "skip")) {
                if (x.startsWith(in)) r.add(x);
            }
        return r;
        }

        // /quiz start 或 /出题 的参数
        boolean isQS = cn.equals("quiz")
                && a.length >= 2
                && a[0].equalsIgnoreCase("start");
        boolean isCT = cn.equals("出题")
                || l.equals("出题");

        if (isQS || isCT) {
            String[] va = isQS
                    ? Arrays.copyOfRange(
                    a, 1, a.length)
                    : a;

            if (va.length == 0) {
                for (String t : TYPES) r.add(t);
                r.add("随机");
                return r;
            }
            if (va.length == 1) {
                String t0 = va[0];
                if (TYPES.contains(t0)) {
                    r.add("随机");
                    int cnt = getCount(t0);
                    for (int i = 1; i <= cnt; i++) {
                        r.add(String.valueOf(i));
                    }
                }
                return r;
            }
            return r;
        }

        // /寻宝 /treasure
        boolean isT = cn.equals("treasure")
                || cn.equals("寻宝")
                || l.equals("寻宝");
        if (isT && a.length == 1) {
            String in = a[0].toLowerCase();
            for (String x : Arrays.asList(
                    "info", "list", "reload",
                    "工具", "expand", "return",
                    "on", "off", "开启", "关闭",
                    "removeregion")) {
                if (x.startsWith(in)) r.add(x);
            }
            return r;
        }

        if (isT && a.length == 2) {
            String sub = a[0].toLowerCase();
            if (sub.equals("on") || sub.equals("off")
                    || sub.equals("开启")
                    || sub.equals("关闭")
                    || sub.equals("expand")
                    || sub.equals("return")
                    || sub.equals("removeregion")) {
                String in = a[1].toLowerCase();
                for (String n :
                        treasureManager.getRegionNames()) {
                    if (n.toLowerCase().startsWith(in))
                        r.add(n);
                }
                return r;
            }
        }


        if (isT && a.length == 2) {
            String sub = a[0].toLowerCase();
            if (sub.equals("expand")
                    || sub.equals("return")
                    || sub.equals("removeregion")) {
                String in = a[1].toLowerCase();
                for (String n :
                        treasureManager.getRegionNames()) {
                    if (n.toLowerCase().startsWith(in))
                        r.add(n);
                }
                return r;
            }
        }


        if (isT && a.length == 2
                && a[0].equalsIgnoreCase("expand")) {
            // 不补全，让玩家自己输数字
            return r;
        }
// 打了包但还没测试。可以先测试，也可以先做return的功能要求。要先把大服务器开起来
        if (isT && a.length == 2
                && a[0].equalsIgnoreCase(
                "removeregion")) {
            String in = a[1].toLowerCase();
            for (String n :
                    treasureManager.getRegionNames()) {
                if (n.toLowerCase().startsWith(in))
                    r.add(n);
            }
            return r;
        }



        return r;
    }

    private int getCount(String t) {
        switch (t) {
            case "选择题":
                return quizManager.getChoiceCount();
            case "多选题":
                return quizManager.getMultiCount();
            case "填空题":
                return quizManager.getFillCount();
            case "问答题":
                return quizManager.getOpenCount();
            default:
                return 0;
        }
    }
}
