package Sdf1_game;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.command.CommandSender;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class QuizManager implements Listener {

    private final Main plugin;
    private final BondBridge bondBridge;

    private List<Question> choiceQ = new ArrayList<>();
    private List<Question> fillQ = new ArrayList<>();
    private List<Question> openQ = new ArrayList<>();

    private BukkitTask autoTask;
    private BukkitTask countTask;
    private Question cur;
    private boolean active = false;
    private int timeLeft;
    private boolean firstRun = true;
    private long lastQuizTime = 0;
    private boolean wasEmpty = false;

    private String mode = "自动";

    private final Set<UUID> answered =
            Collections.synchronizedSet(new HashSet<>());
    private final Object lock = new Object();
    // 蒙题防护：记录每个玩家最近的错误答案时间
    private final Map<UUID, List<Long>> wrongTimes =
            new ConcurrentHashMap<>();
    // 蒙题防护：罚时结束时间
    private final Map<UUID, Long> punishUntil =
            new ConcurrentHashMap<>();


    private String prefix = "§e§l【快问快答】§r ";
    private int minPlayers = 3;
    private int minSec = 300;
    private int maxSec = 900;
    private int ansTime = 120;
    private List<Question> multiQ = new ArrayList<>();
    private int spamThreshold = 2;
    private int punishSeconds = 10;
    private String punishStartMsg =
            "§c你回答太快了，请冷静10秒！";
    private String punishEndMsg =
            "§a罚时已结束，你可以继续答题了";
    // 填空题：记录每个玩家的答案
    private final Map<UUID, String> fillAnswers
            = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> multiAnswers
            = new ConcurrentHashMap<>();



    public QuizManager(Main plugin, BondBridge bb) {
        this.plugin = plugin;
        this.bondBridge = bb;
    }

    /**
     * 时间单位自动换算
     * 支持: 纯数字(秒) / 30秒 / 5分钟 / 2小时
     * 单位: 秒(s) / 分钟(m) / 小时(h)
     */
    private int parseTime(String input) {
        String s = input.trim().toLowerCase();

        // 纯数字 → 当作秒
        if (s.matches("\\d+")) {
            return Integer.parseInt(s);
        }

        // 提取数字部分和单位部分
        // "5分钟" "30s" "2小时"
        java.util.regex.Matcher m =
                java.util.regex.Pattern
                        .compile("(\\d+)\\s*(秒|s|分|分钟|m|时|小时|h)")
                        .matcher(s);

        if (m.find()) {
            int val = Integer.parseInt(m.group(1));
            String unit = m.group(2);
            switch (unit) {
                case "秒":
                case "s":
                    return val;
                case "分":
                case "分钟":
                case "m":
                    return val * 60;
                case "时":
                case "小时":
                case "h":
                    return val * 3600;
            }
        }

        // 解析失败，返回原值尝试 parseInt
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 填空题多空：抢答，全空答对才提交 */
    private void handleFillAnswer(
            AsyncPlayerChatEvent event,
            String msg,
            Question q,
            Player player,
            UUID uid) {
        event.setCancelled(true);

        if (answered.contains(uid)) return;

        // 罚时期间忽略
        if (isPunished(uid)) return;

        // 解析玩家答案
        String[] playerParts = msg
                .replaceAll("[,，\\s]+", ",")
                .split(",");
        String[] correctParts =
                q.getAnswer().split("[,，]");

        // 逐空比对
        int correctCount = 0;
        for (int i = 0;
             i < correctParts.length
                     && i < playerParts.length; i++) {
            if (correctParts[i].trim().toLowerCase()
                    .equals(playerParts[i].trim()
                            .toLowerCase())) {
                correctCount++;
            }
        }

        // 全部答对 → 获胜
        if (correctCount >= correctParts.length) {
            answered.add(uid);
            wrongTimes.remove(uid);
            fillAnswers.remove(uid);

            int reward;
            if (q.getRewards().length > 1) {
                reward = q.getRewardForCount(
                        correctCount);
            } else {
                reward = q.getRewards()[0];
            }

            broadcastAnswer(player, q, reward,
                    "填空题");
            return;
        }

        // ★ 答错或部分答对 → 记录，等超时结算
        fillAnswers.put(uid, msg.trim());

        // 蒙题防护：纯数字短答案检测
        if (msg.matches("[\\d,，\\s]+")) {
            recordWrong(uid);
            if (shouldPunish(uid)) {
                applyPunish(uid, player);
            }
        }
    }

    // ========== 读配置 ==========

    public void loadConfig() {
        File f = new File(plugin.getDataFolder(),
                "快问快答/配置.txt");
        if (!f.exists()) return;

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(
                        new FileInputStream(f),
                        StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("#")
                        || line.startsWith("//")
                        || line.startsWith("<!--"))
                    continue;
                if (!line.contains(":")) continue;

                int idx = line.indexOf(':');
                String k = line.substring(0, idx).trim();
                String v = line.substring(idx + 1).trim();
                switch (k) {
                    case "模式":
                        mode = v;
                        break;
                    case "消息前缀":
                        prefix = v;
                        break;
                    case "最少在线人数":
                        minPlayers =
                                Integer.parseInt(v);
                        break;
                    case "最小间隔":
                        minSec = parseTime(v);
                        break;
                    case "最大间隔":
                        maxSec = parseTime(v);
                        break;
                    case "答题时间":
                        ansTime = parseTime(v);
                        break;
                    case "蒙题检测数":
                        spamThreshold =
                                Integer.parseInt(v);
                        break;
                    case "罚时时间":
                        punishSeconds = parseTime(v);
                        break;
                    case "罚时开始提示":
                        punishStartMsg = v;
                        break;
                    case "罚时结束提示":
                        punishEndMsg = v;
                        break;
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning(
                    "[快问快答] 读配置失败: "
                            + e.getMessage());
        }

        plugin.getLogger().info(
                "[快问快答] 模式=" + mode
                        + " 最少" + minPlayers + "人"
                        + " 间隔" + minSec + "-"
                        + maxSec + "秒"
                        + " 答题" + ansTime + "秒");
    }


    // ========== 加载题目 ==========

    public void loadQuestions() {
        loadConfig();

        int oldChoice = choiceQ.size();
        int oldFill = fillQ.size();
        int oldOpen = openQ.size();
        int oldMulti = multiQ.size();

        // ★ 每次重新创建列表
        choiceQ = new ArrayList<>(
                QuestionLoader.load(plugin,
                        "快问快答/题目/选择题.md"));
        multiQ = new ArrayList<>(
                QuestionLoader.load(plugin,
                        "快问快答/题目/多选题.md"));
        fillQ = new ArrayList<>(
                QuestionLoader.load(plugin,
                        "快问快答/题目/填空题.md"));
        openQ = new ArrayList<>(
                QuestionLoader.load(plugin,
                        "快问快答/题目/问答题.md"));

        plugin.getLogger().info(
                "[快问快答] ★题目重载完成:");
        plugin.getLogger().info(
                "[快问快答]   选择题: "
                        + oldChoice + " → "
                        + choiceQ.size());
        plugin.getLogger().info(
                "[快问快答]   多选题: "
                        + oldMulti + " → "
                        + multiQ.size());
        plugin.getLogger().info(
                "[快问快答]   填空题: "
                        + oldFill + " → "
                        + fillQ.size());
        plugin.getLogger().info(
                "[快问快答]   问答题: "
                        + oldOpen + " → "
                        + openQ.size());
    }

    public void reload() {
        stopAuto();
        if (countTask != null) {
            countTask.cancel();
            countTask = null;
        }
        loadQuestions();
        firstRun = true;
        lastQuizTime = System.currentTimeMillis();
        startAuto();
        plugin.getLogger().info(
                "[快问快答] ★已重载并重启自动出题");
        plugin.getLogger().info("\n" +
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

    public int getMultiCount() {
        return multiQ.size();
    }


    // ========== 自动出题 ==========

    public void startAuto() {
        stopAuto();
        lastQuizTime = System.currentTimeMillis();
        plugin.getLogger().info(
                "[快问快答] 模式=" + mode
                        + " 自动出题"
                        + ("手动".equals(mode)
                        ? "已跳过" : "已启动"));
        if (!"手动".equals(mode)) {
            startMonitor();
        }
    }

    public void stopAuto() {
        if (autoTask != null) {
            autoTask.cancel();
            autoTask = null;
        }
    }

    /**
     * 核心监控：每30秒检查一次
     * - 有答题中 → 不管人数，继续
     * - 无人数不足 → 静默跳过
     * - 人数达标 + 距上次≥5分钟 → 出题
     * - 之前没人现在有人了 → 立即出题
     */
    private void startMonitor() {
        if (autoTask != null) autoTask.cancel();

        autoTask = new BukkitRunnable() {
            @Override
            public void run() {
                int online =
                        Bukkit.getOnlinePlayers().size();
                long now =
                        System.currentTimeMillis();
                long elapsed = now - lastQuizTime;

                // 答题中 → 不打断
                if (active) return;

                // 人数不足
                if (online < minPlayers) {
                    wasEmpty = true;
                    return;
                }

                // 人数达标

                // 情况A：之前没人，现在有人了 → 立即出题
                if (wasEmpty) {
                    wasEmpty = false;
                    lastQuizTime = now;
                    broadcastQuestion();
                    return;
                }

                // 情况B：首次（还没出过题）
                if (firstRun) {
                    // 启动后5分钟内不出题
                    if (elapsed < 300_000) return;
                    firstRun = false;
                    lastQuizTime = now;
                    broadcastQuestion();
                    return;
                }

                // 情况C：距上次出题≥设定间隔
                long minInterval = minSec * 1000L;
                if (elapsed >= minInterval) {
                    lastQuizTime = now;
                    broadcastQuestion();
                }
            }
        }.runTaskTimer(plugin, 0L, 30 * 20L);
        // 每30秒检查一次
    }


    private void scheduleNext() {
        int delay;
        if (firstRun) {
            delay = 300;
            firstRun = false;
            plugin.getLogger().info(
                    "[快问快答] 首次定时: "
                            + delay + "秒后检查");
        } else {
            delay = ThreadLocalRandom.current()
                    .nextInt(minSec, maxSec + 1);
            plugin.getLogger().info(
                    "[快问快答] 下次检查: "
                            + delay + "秒后");
        }

        autoTask = new BukkitRunnable() {
            @Override
            public void run() {
                int online =
                        Bukkit.getOnlinePlayers().size();
                plugin.getLogger().info(
                        "[快问快答] 定时检查: 在线="
                                + online + " 需要>="
                                + minPlayers
                                + " 答题中=" + active);

                if (online >= minPlayers && !active) {
                    broadcastQuestion();
                }
                scheduleNext();
            }
        }.runTaskLater(plugin, delay * 20L);
    }

    // ========== 出题 ==========

    public void broadcastQuestion() {
        broadcastQuestion(null, 0);
    }

    public void broadcastQuestion(String type,
                                  int index) {
        // ★ 拦截：在线人数<1时禁止出题
        if (Bukkit.getOnlinePlayers().size() < 1) {
            plugin.getLogger().warning(
                    "[快问快答] 在线人数不足，跳过出题");
            return;
        }

        Question q = pickQuestion(type, index);
        if (q == null) {
            plugin.getLogger().warning(
                    "[快问快答] 抽题失败");
            return;
        }
        startQuiz(q);
    }


    /**
     * 外部强制出指定题目
     */
    public void forceQuestion(Question q) {
        if (!active) startQuiz(q);
    }

    /**
     * 按关键词搜索题目
     */
    public Question findByKeyword(String type,
                                  String keyword) {
        List<Question> pool;
        switch (type) {
            case "选择题":
                pool = choiceQ;
                break;
            case "填空题":
                pool = fillQ;
                break;
            case "问答题":
                pool = openQ;
                break;
            default:
                return null;
        }
        for (Question q : pool) {
            if (q.getQuestion().contains(keyword)
                    || q.getAnswer()
                    .contains(keyword)) {
                return q;
            }
        }
        return null;
    }

    private Question pickQuestion(String type,
                                  int index) {
        if (type != null) {
            List<Question> pool;
            switch (type) {
                case "选择题":
                case "单选题":
                    pool = choiceQ;
                    break;
                case "多选题":
                    pool = multiQ;
                    break;
                case "填空题":
                    pool = fillQ;
                    break;
                case "问答题":
                    pool = openQ;
                    break;
                default:
                    return null;
            }
            if (pool.isEmpty()) return null;
            int idx = Math.max(0,
                    Math.min(index - 1,
                            pool.size() - 1));
            return pool.get(idx);
        }

        List<Question> pool = new ArrayList<>();
        pool.addAll(choiceQ);
        pool.addAll(multiQ);
        pool.addAll(fillQ);
        pool.addAll(openQ);
        if (pool.isEmpty()) return null;
        return pool.get(
                ThreadLocalRandom.current()
                        .nextInt(pool.size()));
    }

    // ========== 开始答题 ==========

    private void startQuiz(Question q) {
        synchronized (lock) {
            cur = q;
            active = true;
            answered.clear();
        }
        timeLeft = ansTime;

        StringBuilder msg = new StringBuilder();
        msg.append(prefix);

        switch (q.getType()) {
            case CHOICE:
                msg.append("§e§l[选择题]§r ")
                        .append(q.getQuestion());
                char letter = 'A';
                for (String opt : q.getOptions()) {
                    msg.append("\n§7")
                            .append(letter)
                            .append(". ")
                            .append(opt);
                    letter++;
                }
                msg.append("\n§e赏金: §6")
                        .append(q.getDisplayReward())
                        .append(" §e债券");
                break;

            case MULTI:
                msg.append("§e§l[多选题]§r ")
                        .append(q.getQuestion());
                char ml = 'A';
                for (String opt : q.getOptions()) {
                    msg.append("\n§7")
                            .append(ml)
                            .append(". ")
                            .append(opt);
                    ml++;
                }
                msg.append("\n§7§o多个答案用逗号分隔"
                        + "，如: A,B");

                // 显示赏金明细
                int[] rw = q.getRewards();
                if (rw.length > 1) {
                    StringBuilder rb =
                            new StringBuilder();
                    for (int i = 0;
                         i < rw.length; i++) {
                        if (i > 0) rb.append("§7, ");
                        rb.append("§6答对")
                                .append(i + 1)
                                .append("个§e")
                                .append(rw[i])
                                .append("§6债券");
                    }
                    msg.append("\n§e赏金: ")
                            .append(rb);
                } else {
                    msg.append("\n§e每个正确选项: §6")
                            .append(rw[0])
                            .append(" §e债券");
                }
                break;

            case FILL:
                msg.append("§e§l[填空题]§r ")
                        .append(q.getQuestion());
                msg.append(
                        "\n§7多个答案用逗号分隔");
                msg.append("\n§e赏金: §6")
                        .append(q.getDisplayReward())
                        .append(" §e债券(全对)");
                break;

            case OPEN:
                msg.append("§e§l[问答题]§r ")
                        .append(q.getQuestion());
                msg.append("\n§7直接输入答案");
                msg.append("\n§e赏金: §6")
                        .append(q.getDisplayReward())
                        .append(" §e债券");
                break;
        }

        msg.append("\n§e倒计时: §c")
                .append(ansTime).append("秒");

        plugin.getLogger().info(
                "[快问快答] 出题: ["
                        + q.getTypeName() + "] "
                        + q.getQuestion());

        Bukkit.broadcastMessage(msg.toString());

        countTask = new BukkitRunnable() {
            @Override
            public void run() {
                timeLeft--;
                if (timeLeft <= 0) {
                    endQuiz(false);
                    return;
                }
                switch (timeLeft) {
                    case 90:
                        Bukkit.broadcastMessage(prefix
                                + "§7还有1分30秒~");
                        break;
                    case 60:
                        Bukkit.broadcastMessage(prefix
                                + "§7还剩1分钟！");
                        break;
                    case 30:
                        Bukkit.broadcastMessage(prefix
                                + "§7还有30秒！");
                        break;
                    case 10:
                        Bukkit.broadcastMessage(prefix
                                + "§c最后10秒！");
                        break;
                }
                if (timeLeft <= 5 && timeLeft >= 1) {
                    Bukkit.broadcastMessage(prefix
                            + "§c" + timeLeft + "...");
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    // ========== 结束 ==========
    private void endQuiz(boolean hadWinner) {
        Question snapshot;
        synchronized (lock) {
            snapshot = cur;
            active = false;
            cur = null;
        }
        if (countTask != null) {
            countTask.cancel();
            countTask = null;
        }

        if (!hadWinner) {
            Bukkit.broadcastMessage(prefix
                    + "§72分钟内无人答对，本题跳过~");

            if (snapshot != null) {
                Bukkit.broadcastMessage(prefix
                        + "§e正确答案: §a"
                        + snapshot.getAnswer());

                // ★ 填空题超时结算
                if (snapshot.getType()
                        == Question.Type.MULTI
                        && !multiAnswers.isEmpty()) {

                    Set<String> correctSet =
                            new HashSet<>();
                    for (String s : snapshot
                            .getAnswer().split(",")) {
                        correctSet.add(s.trim()
                                .toLowerCase());
                    }

                    int bestCount = 0;
                    UUID bestUid = null;

                    for (Map.Entry<UUID,
                            Set<String>> entry
                            : multiAnswers.entrySet()) {
                        int correct = 0;
                        for (String c : correctSet) {
                            if (entry.getValue()
                                    .contains(c))
                                correct++;
                        }
                        if (correct > bestCount) {
                            bestCount = correct;
                            bestUid = entry.getKey();
                        }
                    }

                    if (bestUid == null) return;

                    boolean mustFull = snapshot
                            .getRewards().length == 1;

                    if (mustFull
                            && bestCount < correctSet.size()) {
                        Bukkit.broadcastMessage(prefix
                                + "§7本题要求全对，"
                                + "无人全对，不发奖");
                        return;
                    }

                    // 只给最先到达的那一个
                    giveMultiTimeoutReward(
                            snapshot, bestUid,
                            bestCount);
                }


                String nextTime = getNextQuizTime();
                Bukkit.broadcastMessage(prefix
                        + "§7下次出题: §e"
                        + nextTime);
            }
        }

        multiAnswers.clear();
        fillAnswers.clear();
    }

    private void giveMultiTimeoutReward(
            Question q, UUID pUid, int correctCount) {
        Player p = Bukkit.getPlayer(pUid);
        if (p == null || !p.isOnline()) return;

        int reward = q.getRewards().length > 1
                ? q.getRewardForCount(correctCount)
                : q.getRewards()[0];

        boolean bondOk = false;
        if (bondBridge.isHooked()) {
            bondOk = bondBridge.addBonds(
                    p.getName(), reward,
                    "多选题超时" + correctCount + "对");
        }

        Bukkit.broadcastMessage(prefix
                + "§a" + p.getName()
                + " §7答对了 §e" + correctCount
                + "§7 个选项，获得 §6" + reward
                + " §e债券"
                + (bondOk ? "" : "(债券未连接)"));
    }


    /** 填空题超时：只给最先到达且答对最多的人 */
    private void settleFillTimeout(Question q) {
        String[] correctParts =
                q.getAnswer().split("[,，]");
        int totalBlanks = correctParts.length;

        // 统计每个人答对几个空
        int bestCount = 0;
        UUID bestUid = null;

        for (Map.Entry<UUID, String> entry
                : fillAnswers.entrySet()) {
            int correct = countFillCorrect(
                    entry.getValue(), correctParts);
            if (correct > bestCount) {
                bestCount = correct;
                bestUid = entry.getKey();
            }
        }

        if (bestUid == null) return;

        boolean mustFull =
                q.getRewards().length == 1;

        if (mustFull && bestCount < totalBlanks) {
            Bukkit.broadcastMessage(prefix
                    + "§7本题要求全对，无人全对，不发奖");
            return;
        }

        giveFillReward(q, bestUid, bestCount);
    }

    private int countFillCorrect(String playerAns,
                                 String[] correct) {
        String[] pp = playerAns
                .replaceAll("[,，\\s]+", ",")
                .split(",");
        int c = 0;
        for (int i = 0;
             i < correct.length
                     && i < pp.length; i++) {
            if (correct[i].trim().toLowerCase()
                    .equals(pp[i].trim()
                            .toLowerCase())) {
                c++;
            }
        }
        return c;
    }

    private void giveFillReward(Question q,
                                UUID pUid,
                                int correctCount) {
        Player p = Bukkit.getPlayer(pUid);
        if (p == null || !p.isOnline()) return;

        int reward;
        if (q.getRewards().length > 1) {
            reward = q.getRewardForCount(
                    correctCount);
        } else {
            reward = q.getRewards()[0];
        }

        boolean bondOk = false;
        if (bondBridge.isHooked()) {
            bondOk = bondBridge.addBonds(
                    p.getName(), reward,
                    "填空题" + correctCount + "空对");
        }

        Bukkit.broadcastMessage(prefix
                + "§a" + p.getName()
                + " §7答对了 §e" + correctCount
                + "/" + q.getBlankCount()
                + "§7 空，获得 §6" + reward
                + " §e债券"
                + (bondOk ? "" : "(债券未连接)"));
    }




    /** 多选题发奖 */
    private void giveMultiReward(Question q,
                                 UUID pUid,
                                 int correctCount) {
        Player p = Bukkit.getPlayer(pUid);
        if (p == null || !p.isOnline()) return;

        int reward =
                q.getRewardForCount(correctCount);

        boolean bondOk = false;
        if (bondBridge.isHooked()) {
            bondOk = bondBridge.addBonds(
                    p.getName(), reward,
                    "多选题" + correctCount + "对");
        }

        Bukkit.broadcastMessage(prefix
                + "§a" + p.getName()
                + " §7答对了 §e" + correctCount
                + "§7 个选项，获得 §6" + reward
                + " §e债券"
                + (bondOk ? "" : "(债券未连接)"));
    }
    /** 多选题：抢答，第一个全对的获胜 */
    private void handleMultiAnswer(
            AsyncPlayerChatEvent event,
            String msg,
            Question q,
            Player player,
            UUID uid) {
        event.setCancelled(true);

        if (answered.contains(uid)) return;

        Set<String> correctSet = new HashSet<>();
        for (String s : q.getAnswer().split(",")) {
            correctSet.add(s.trim().toLowerCase());
        }

        Set<String> playerSet = new HashSet<>();
        for (String s :
                msg.split("[,，\\s]+")) {
            String clean = s.trim().toLowerCase()
                    .replaceAll(
                            "[()（）.,。，]", "");
            if (!clean.isEmpty()) {
                playerSet.add(clean);
            }
        }

        // 是否全对
        boolean fullMatch = correctSet
                .equals(playerSet);

        if (!fullMatch) {
            // 没全对 → 提示，继续抢答
            int correct = 0;
            for (String c : correctSet) {
                if (playerSet.contains(c)) correct++;
            }
            if (correct > 0) {
                player.sendMessage("§e答对了 §a"
                        + correct + "/"
                        + correctSet.size()
                        + "§e 个，继续抢答！");
            } else {
                player.sendMessage(
                        "§7答错了，再试试！");
            }
            return;
        }

        // ★ 全对 → 第一个答对的获胜
        answered.add(uid);

        int reward = q.getRewards().length > 1
                ? q.getRewardForCount(
                correctSet.size())
                : q.getRewards()[0];

        boolean bondOk = false;
        if (bondBridge.isHooked()) {
            bondOk = bondBridge.addBonds(
                    player.getName(), reward,
                    "多选题答对");
        }

        String info = bondOk
                ? "§6" + reward + " §e债券"
                : "§7(债券未连接)";

        Bukkit.broadcastMessage(prefix
                + "§a" + player.getName()
                + " §7抢答成功§e[多选题]§7!");
        Bukkit.broadcastMessage(prefix
                + "§e正确答案: §a"
                + q.getAnswer());
        Bukkit.broadcastMessage(prefix
                + "§e赏金: " + info);
        Bukkit.broadcastMessage(prefix
                + "§7下次出题: §e"
                + getNextQuizTime());

        endQuiz(true);
    }

    // ========== 聊天监听 ==========
    @EventHandler(priority = EventPriority.HIGHEST,
            ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Question q;
        synchronized (lock) {
            if (!active || cur == null) return;
            q = cur;
        }

        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();
        if (answered.contains(uid)) return;

        String msg = event.getMessage().trim();
        if (msg.isEmpty()) return;

        if (isPunished(uid)) return;

        // ★ 多选题：只处理全对
        if (q.getType() == Question.Type.MULTI) {
            Set<String> correctSet = new HashSet<>();
            for (String s : q.getAnswer().split(",")) {
                correctSet.add(s.trim().toLowerCase());
            }
            Set<String> playerSet = new HashSet<>();
            for (String s : msg.split("[,，\\s]+")) {
                String clean = s.trim().toLowerCase()
                        .replaceAll("[()（）.,。，]", "");
                if (!clean.isEmpty()) {
                    playerSet.add(clean);
                }
            }
            if (!correctSet.equals(playerSet)) return;

            event.setCancelled(true);
            answered.add(uid);
            wrongTimes.remove(uid);
            int reward = q.getRewards().length > 1
                    ? q.getRewardForCount(correctSet.size())
                    : q.getRewards()[0];
            broadcastAnswer(player, q, reward,
                    "多选题");
            return;
        }

        // ★ 多空填空题：只处理全对
        if (q.getType() == Question.Type.FILL
                && q.getBlankCount() > 1) {

            String[] correctParts =
                    q.getAnswer().split("[,，]");
            String[] playerParts = msg
                    .replaceAll("[,，\\s]+", ",")
                    .split(",");

            int correctCount = 0;
            for (int i = 0;
                 i < correctParts.length
                         && i < playerParts.length; i++) {
                if (correctParts[i].trim().toLowerCase()
                        .equals(playerParts[i].trim()
                                .toLowerCase())) {
                    correctCount++;
                }
            }

            // ★ 全空答对才提交
            if (correctCount >= correctParts.length) {
                event.setCancelled(true);
                answered.add(uid);
                wrongTimes.remove(uid);
                fillAnswers.remove(uid);

                int reward = q.getRewards().length > 1
                        ? q.getRewardForCount(correctCount)
                        : q.getRewards()[0];
                broadcastAnswer(player, q, reward,
                        "填空题");
                return;
            }

            // 部分答对 → 记录不提交
            fillAnswers.put(uid, msg.trim());

            // 蒙题防护
            if (msg.matches("[\\d,，\\s]+")) {
                recordWrong(uid);
                if (shouldPunish(uid)) {
                    applyPunish(uid, player);
                }
            }
            return;
        }

        // ★ 单选题 / 单空填空题 / 问答题
        boolean ok = false;
        int reward = 0;

        switch (q.getType()) {
            case CHOICE:
                if (checkChoice(msg, q)) {
                    ok = true;
                    reward = q.getRewardForCount(1);
                }
                break;
            case OPEN:
                if (msg.equalsIgnoreCase(q.getAnswer())) {
                    ok = true;
                    reward = q.getRewardForCount(1);
                }
                break;
            case FILL:
                int[] r = checkFill(msg, q);
                if (r[0] > 0) {
                    ok = true;
                    reward = r[1];
                }
                break;
        }

        if (!ok) {
            // 蒙题防护
            if (q.getType() == Question.Type.CHOICE
                    || q.getType() == Question.Type.FILL) {
                recordWrong(uid);
                if (shouldPunish(uid)) {
                    applyPunish(uid, player);
                }
            }
            return;
        }

        event.setCancelled(true);
        answered.add(uid);
        wrongTimes.remove(uid);
        broadcastAnswer(player, q, reward,
                q.getTypeName());
    }


    /** 统一广播答案 */
    private void broadcastAnswer(Player player,
                                 Question q,
                                 int reward,
                                 String typeName) {
        boolean bondOk = false;
        if (bondBridge.isHooked()) {
            bondOk = bondBridge.addBonds(
                    player.getName(), reward,
                    typeName + "答对");
        }

        String info = bondOk
                ? "§6" + reward + " §e债券"
                : "§7(债券未连接)";

        Bukkit.broadcastMessage(prefix
                + "§a" + player.getName()
                + " §7答对了§e["
                + typeName + "]§7!");
        Bukkit.broadcastMessage(prefix
                + "§e正确答案: §a"
                + q.getAnswer());
        Bukkit.broadcastMessage(prefix
                + "§e赏金: " + info);
        Bukkit.broadcastMessage(prefix
                + "§7下次出题: §e"
                + getNextQuizTime());
        endQuiz(true);
    }
    /** 跳过当前题目 */
    public void skipQuestion(CommandSender sender) {
        if (!sender.hasPermission("sdf1_quiz.admin")) {
            sender.sendMessage("§c无权限");
            return;
        }

        if (!active) {
            sender.sendMessage("§c当前没有答题");
            return;
        }

        active = false;
        cur = null;

        if (countTask != null) {
            countTask.cancel();
            countTask = null;
        }

        sender.sendMessage("§a已跳过当前题目");

        String nextTime = getNextQuizTime();
        Bukkit.broadcastMessage(prefix
                + "§7本轮答题已跳过");
        Bukkit.broadcastMessage(prefix
                + "§7下次出题: §e" + nextTime);
    }

    // ========== 蒙题防护 ==========

    private boolean isAnswerCorrect(String input,
                                    Question q) {
        switch (q.getType()) {
            case CHOICE:
                return checkChoice(input, q);
            case MULTI:
                return countMultiCorrect(input, q) > 0;
            default:
                return true;
        }
    }

    private void recordWrong(UUID uid) {
        long now = System.currentTimeMillis();
        List<Long> times = wrongTimes
                .computeIfAbsent(uid,
                        k -> new ArrayList<>());
        times.add(now);
        // 只保留最近3秒内的记录
        times.removeIf(t -> now - t > 3000);
    }

    private boolean shouldPunish(UUID uid) {
        List<Long> times = wrongTimes.get(uid);
        if (times == null) return false;
        long now = System.currentTimeMillis();
        // 2秒内错误次数 > 阈值
        long cutoff = now - 2000;
        int recentWrong = 0;
        for (long t : times) {
            if (t > cutoff) recentWrong++;
        }
        return recentWrong > spamThreshold;
    }

    private void applyPunish(UUID uid,
                             Player player) {
        long end = System.currentTimeMillis()
                + (long) punishSeconds * 1000;
        punishUntil.put(uid, end);
        wrongTimes.remove(uid);

        player.sendMessage(punishStartMsg);

        // 罚时结束后自动解除
        final UUID punishUid = uid;
        final Player punishPlayer = player;
        new BukkitRunnable() {
            @Override
            public void run() {
                punishUntil.remove(punishUid);
                if (punishPlayer.isOnline()) {
                    punishPlayer.sendMessage(
                            punishEndMsg);
                }
            }
        }.runTaskLater(plugin, punishSeconds * 20L);

        plugin.getLogger().info(
                "[快问快答] 蒙题罚时: "
                        + player.getName()
                        + " " + punishSeconds + "秒");
    }

    private boolean isPunished(UUID uid) {
        Long until = punishUntil.get(uid);
        if (until == null) return false;
        if (System.currentTimeMillis() < until) {
            return true;
        }
        punishUntil.remove(uid);
        return false;
    }


    // ========== 下次出题时间 ==========

    private String getNextQuizTime() {
        if ("手动".equals(mode)) {
            return "§7(手动模式)";
        }
        int delay = ThreadLocalRandom.current()
                .nextInt(minSec, maxSec + 1);
        LocalTime next = LocalTime.now()
                .plusSeconds(delay);
        return next.format(
                DateTimeFormatter.ofPattern("HH:mm"));
    }

    /**
     * 多选题：统计答对几个选项
     */
    private int countMultiCorrect(String input,
                                  Question q) {
        // 正确选项集合
        java.util.Set<String> correct =
                new java.util.HashSet<>();
        for (String s : q.getAnswer().split(",")) {
            correct.add(s.trim().toLowerCase());
        }

        // 玩家输入的选项集合
        java.util.Set<String> player =
                new java.util.HashSet<>();
        for (String s : input.split("[,，\\s]+")) {
            String clean = s.trim().toLowerCase()
                    .replaceAll("[()（）.,。，]",
                            "");
            if (!clean.isEmpty()) {
                player.add(clean);
            }
        }

        int count = 0;
        for (String c : correct) {
            if (player.contains(c)) {
                count++;
            }
        }
        return count;
    }



    // ========== 校验 ==========

    private boolean checkChoice(String input,
                                Question q) {
        String clean = input.trim().toLowerCase()
                .replaceAll("[()（）\\s.,。，]", "");
        String ans = q.getAnswer().trim()
                .toLowerCase()
                .replaceAll("[()（）\\s.,。，]", "");

        if (clean.equals(ans)) return true;

        int ansIdx = ans.charAt(0) - 'a';
        if (ansIdx >= 0
                && ansIdx < q.getOptions().size()) {
            String expected = q.getOptions()
                    .get(ansIdx).trim().toLowerCase();
            return clean.equals(expected);
        }
        return false;
    }

    private int[] checkFill(String input,
                            Question q) {
        String[] correct =
                q.getAnswer().split("[,，]");
        String[] parts = input
                .replaceAll("[,，\\s]+", ",")
                .split(",");
        int count = 0;
        for (int i = 0;
             i < correct.length
                     && i < parts.length; i++) {
            if (correct[i].trim().toLowerCase()
                    .equals(parts[i].trim()
                            .toLowerCase())) {
                count++;
            }
        }
        return new int[]{count,
                q.getRewardForCount(count)};
    }

    // ========== 管理 ==========

    public boolean isActive() {
        return active;
    }

    public String getMode() {
        return mode;
    }

    public int getChoiceCount() {
        return choiceQ.size();
    }

    public int getFillCount() {
        return fillQ.size();
    }

    public int getOpenCount() {
        return openQ.size();
    }



    /**
     * 随机出题（手动）
     */
    public void forceQuestion() {
        if (Bukkit.getOnlinePlayers().size() < 1) {
            return; // 静默拦截
        }
        if (!active) {
            lastQuizTime = System.currentTimeMillis();
            broadcastQuestion();
        }
    }
    /** 指定题型随机出题 */
    public void forceQuestionByType(String type) {
        if (Bukkit.getOnlinePlayers().size() < 1) {
            return;
        }
        if (!active) {
            lastQuizTime =
                    System.currentTimeMillis();
            broadcastQuestion(type, 0);
            // index=0 表示该题型内随机
        }
    }

    /**
     * 指定题型+编号（手动）
     */
    public void forceQuestion(String type, int index) {
        if (Bukkit.getOnlinePlayers().size() < 1) {
            return;
        }
        if (!active) {
            lastQuizTime = System.currentTimeMillis();
            broadcastQuestion(type, index);
        }
    }

}
