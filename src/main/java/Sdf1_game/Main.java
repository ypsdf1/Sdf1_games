package Sdf1_game;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

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

    private static final Pattern SPEC_NO_SPACE =
            Pattern.compile("^(选择题|填空题|问答题)(\\d+)$");

    private static final Pattern SPEC_WITH_SPACE =
            Pattern.compile("^(选择题|填空题|问答题)\\s+(\\d+)$");

    private static final List<String> TYPES =
            Arrays.asList("选择题", "单选题",
                    "多选题", "填空题", "问答题");
    @Override
    public void onEnable() {
        File base = new File(getDataFolder(), "快问快答");
        File qDir = new File(base, "题目");
        qDir.mkdirs();

        FileGenerator.ensureAll(base, qDir);

        bondBridge = new BondBridge();
        bondBridge.hook(this);

        quizManager = new QuizManager(this, bondBridge);
        quizManager.loadQuestions();

        getServer().getPluginManager()
                .registerEvents(quizManager, this);

        getCommand("quiz").setTabCompleter(this);
        getCommand("出题").setTabCompleter(this);

        quizManager.startAuto();

        getLogger().info("=== 快问快答 v1.0.0 ===");
        getLogger().info("模式: " + quizManager.getMode());
        getLogger().info("债券桥接: "
                + (bondBridge.isHooked() ? "已连接" : "未连接"));
        getLogger().info("\n" +
                "  ____      _  __ _                                              \n" +
                " / ___|  __| |/ _/ |     __ _  __ _ _ __ ___   ___               \n" +
                " \\___ \\ / _` | |_| |    / _` |/ _` | '_ ` _ \\ / _ \\              \n" +
                "  ___) | (_| |  _| |   | (_| | (_| | | | | | |  __/              \n" +
                " |____/ \\__,_|_| |_|____\\__, |\\__,_|_| |_| |_|\\___|              \n" +
                "  ____      _  __ |_____|___/                                    \n" +
                " / ___|  __| |/ _/ |                                             \n" +
                " \\___ \\ / _` | |_| |                                             \n" +
                "  ___) | (_| |  _| |                                             \n" +
                " |____/ \\__,_|_| |_|             _             ____      _  __ _ \n" +
                "  _ __   _____      _____ _ __  | |__  _   _  / ___|  __| |/ _/ |\n" +
                " | '_ \\ / _ \\ \\ /\\ / / _ \\ '__| | '_ \\| | | | \\___ \\ / _` | |_| |\n" +
                " | |_) | (_) \\ V  V /  __/ |    | |_) | |_| |  ___) | (_| |  _| |\n" +
                " | .__/ \\___/ \\_/\\_/ \\___|_|    |_.__/ \\__, | |____/ \\__,_|_| |_|\n" +
                " |_|                                   |___/                     ");
    }

    @Override
    public void onDisable() {
        if (quizManager != null) {
            quizManager.stopAuto();
        }
        getLogger().info("[快问快答] 已卸载");
    }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {
        if (!sender.hasPermission("sdf1_quiz.admin")) {
            sender.sendMessage("§c无权限");
            return true;
        }

        String cmd = command.getName();

        // ===== /出题 =====
        if (cmd.equals("出题") || label.equals("出题")) {
            handleStart(sender, args);
            return true;
        }

        // ===== /quiz =====
        if (!cmd.equals("quiz")) {
            return false;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start":
                handleStart(sender, args);
                return true;
            case "stop":
                quizManager.stopAuto();
                sender.sendMessage("§a已停止自动出题");
                return true;
            case "reload":
                quizManager.reload();
                sender.sendMessage("§a已重载");
                return true;
            case "info":
                sendInfo(sender);
                return true;
            case "test":
                sender.sendMessage(bondBridge.isHooked()
                        ? "§a桥接: 已连接"
                        : "§c桥接: 未连接");
                return true;
            default:
                sendHelp(sender);
                return true;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§e§l【快问快答】§r 命令:");
        sender.sendMessage("§7/出题 - 随机出题");
        sender.sendMessage("§7/出题 选择题1 - 指定题号");
        sender.sendMessage("§7/出题 多选题2 - 多选题");
        sender.sendMessage("§7/出题 填空题 钻石矿 - 按内容");
        sender.sendMessage("§7/quiz stop - 停止自动");
        sender.sendMessage("§7/quiz reload - 重载");
        sender.sendMessage("§7/quiz info - 查看状态");
        sender.sendMessage("§7/quiz test - 测试桥接");
    }

    private void sendInfo(CommandSender sender) {
        sender.sendMessage("§e§l【快问快答】§r 状态:");
        sender.sendMessage("§7模式: §e" + quizManager.getMode());
        sender.sendMessage("§7答题中: §e"
                + (quizManager.isActive() ? "是" : "否"));
        sender.sendMessage("§7选择题: §e" + quizManager.getChoiceCount()
                + " §7填空题: §e" + quizManager.getFillCount()
                + " §7问答题: §e" + quizManager.getOpenCount());
        sender.sendMessage("§7债券桥接: "
                + (bondBridge.isHooked() ? "§a已连接" : "§c未连接"));
    }

    // ===== /出题 参数解析 =====

    private void handleStart(CommandSender sender, String[] args) {
        // 无参数 → 随机
        if (args.length == 0) {
            doStart(sender, null, 0, null);
            return;
        }

        // /quiz start 无后续 → 随机
        if (args.length == 1 && args[0].equalsIgnoreCase("start")) {
            doStart(sender, null, 0, null);
            return;
        }

        // 确定参数起始位置
        int offset = 0;
        if (args.length >= 2 && args[0].equalsIgnoreCase("start")) {
            offset = 1;
        }

        // 拼接剩余参数
        StringBuilder sb = new StringBuilder();
        for (int i = offset; i < args.length; i++) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(args[i]);
        }
        String spec = sb.toString().trim();

        // 格式1: "选择题1" 无空格
        Matcher m1 = SPEC_NO_SPACE.matcher(spec);
        if (m1.matches()) {
            doStart(sender, m1.group(1),
                    Integer.parseInt(m1.group(2)), null);
            return;
        }

        // 格式2: "选择题 1" 有空格
        Matcher m2 = SPEC_WITH_SPACE.matcher(spec);
        if (m2.matches()) {
            doStart(sender, m2.group(1),
                    Integer.parseInt(m2.group(2)), null);
            return;
        }

        // 格式3: "选择题 钻石矿" 按内容搜索
        for (String typeName : TYPES) {
            if (spec.startsWith(typeName)) {
                String keyword = spec.substring(typeName.length()).trim();
                if (!keyword.isEmpty()) {
                    Question q = quizManager.findByKeyword(typeName, keyword);
                    if (q != null) {
                        doStart(sender, null, 0, q);
                    } else {
                        sender.sendMessage("§c未找到包含「" + keyword + "」的题目");
                    }
                    return;
                }
            }
        }

        sender.sendMessage("§c格式错误，示例:");
        sender.sendMessage("§7/出题 - 随机出题");
        sender.sendMessage("§7/出题 选择题1");
        sender.sendMessage("§7/出题 选择题 1");
        sender.sendMessage("§7/出题 选择题 钻石矿");
    }

    private void doStart(CommandSender sender,
                         String type, int index,
                         Question specific) {
        // ★ 在线人数必须>=1
        if (Bukkit.getOnlinePlayers().size() < 1) {
            sender.sendMessage("§c服务器无人，无法出题");
            return;
        }

        if (quizManager.isActive()) {
            sender.sendMessage("§c当前有题目进行中");
            return;
        }

        if (specific != null) {
            quizManager.forceQuestion(specific);
            sender.sendMessage("§a已出题: ["
                    + specific.getTypeName() + "] "
                    + specific.getQuestion());
        } else if (type != null) {
            quizManager.forceQuestion(type, index);
            sender.sendMessage("§a已出题: "
                    + type + "第" + index + "题");
        } else {
            quizManager.forceQuestion();
            sender.sendMessage("§a已随机出题");
        }
    }

    // ===== Tab 补全 =====

    @Override
    public List<String> onTabComplete(CommandSender sender,
                                      Command command,
                                      String label,
                                      String[] args) {
        if (!sender.hasPermission("sdf1_quiz.admin")) {
            return new ArrayList<>();
        }

        List<String> result = new ArrayList<>();
        String cmd = command.getName();

        boolean isStart = cmd.equals("quiz")
                && args.length >= 1
                && args[0].equalsIgnoreCase("start");

        // /出题 或 /quiz start 的参数
        boolean isQuizSpec = cmd.equals("出题")
                || label.equals("出题")
                || isStart;

        if (!isQuizSpec) {
            // /quiz 的子命令补全
            if (cmd.equals("quiz") && args.length == 1) {
                String input = args[0].toLowerCase();
                for (String s : Arrays.asList(
                        "start", "stop", "reload", "test", "info")) {
                    if (s.startsWith(input)) {
                        result.add(s);
                    }
                }
            }
            return result;
        }

        // 确定参数位置
        int argPos = 0;
        if (cmd.equals("quiz") && isStart) {
            argPos = args.length - 1;
        } else {
            argPos = args.length - 1;
        }

        if (argPos == 0) {
            // 补全题型
            String input = args[0].toLowerCase();
            for (String t : TYPES) {
                if (t.startsWith(input) || input.isEmpty()) {
                    result.add(t);
                }
            }
        } else if (argPos == 1) {
            // 补全题号或关键词
            String typeName = args[0];
            if (TYPES.contains(typeName)) {
                int count = getCountForType(typeName);
                for (int i = 1; i <= count; i++) {
                    result.add(String.valueOf(i));
                }
            }
        }

        return result;
    }

    private int getCountForType(String type) {
        switch (type) {
            case "选择题":
            case "单选题":
                return quizManager.getChoiceCount();
            case "多选题":
                return quizManager.getMultiCount();
            case "填空题":
                return quizManager.getFillCount();
            case "问答题":
                return quizManager.getOpenCount();
            default: return 0;
        }
    }}