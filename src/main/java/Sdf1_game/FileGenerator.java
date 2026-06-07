package Sdf1_game;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class FileGenerator {

    public static void ensureAll(File base, File qDir) {
        qDir.mkdirs();

        writeIfAbsent(
                new File(base, "配置.txt"),
                defaultConfig());
        writeIfAbsent(
                new File(qDir, "选择题.md"),
                defaultChoice());
        writeIfAbsent(
                new File(qDir, "填空题.md"),
                defaultFill());
        writeIfAbsent(
                new File(qDir, "问答题.md"),
                defaultOpen());

    }

    private static void writeIfAbsent(File f,
                                      List<String> lines) {
        if (f.exists()) return;
        try {
            OutputStreamWriter w =
                    new OutputStreamWriter(
                            new FileOutputStream(f),
                            StandardCharsets.UTF_8);
            for (String line : lines) {
                w.write(line);
                w.write("\n");
            }
            w.close();
        } catch (IOException ignored) {
        }
    }

    // ========== 快问快答配置 ==========

    private static List<String> defaultConfig() {
        List<String> l = new ArrayList<>();
        l.add("# ===== 快问快答 主配置 =====");
        l.add("# 运行模式: 自动 / 手动");
        l.add("模式: 自动");
        l.add("# 最少在线人数");
        l.add("最少在线人数: 3");
        l.add("# 出题间隔（支持中文单位）");
        l.add("最小间隔: 5分钟");
        l.add("最大间隔: 15分钟");
        l.add("# 答题时间");
        l.add("答题时间: 2分钟");
        l.add("# 蒙题防护");
        l.add("蒙题检测数: 2");
        l.add("罚时时间: 10秒");
        l.add("罚时开始提示: §c你回答太快了，请冷静！");
        l.add("罚时结束提示: §a罚时已结束");
        l.add("# 消息前缀");
        l.add("消息前缀: §e§l【快问快答】§r");
        l.add("# 欢迎游玩草原探险服务器 ");
        l.add("# 服务器ip：mc2.ypshidifu.cn");
        l.add("# 端口：30679,Java版免输入");
        return l;
    }

    private static List<String> defaultChoice() {
        List<String> l = new ArrayList<>();
        l.add("# ===== 选择题/多选题 =====");
        l.add("# 答案含逗号=多选，全选=全选");
        l.add("| 问题 | A | B | C | D | 答案 | 赏金 |");
        l.add("|------|---|---|---|---|------|------|");
        l.add("| 钻石用什么镐挖？ | 木镐 | 石镐 | 铁镐 | 钻石镐 | D | 10 |");
        l.add("| 末影龙在哪？ | 下界 | 主世界 | 末地 | 末地城 | C | 10 |");
        l.add("| 以下哪些是负面效果？ | 速度 | 中毒 | 夜视 | 力量 | B | 10 |");
        return l;
    }

    private static List<String> defaultFill() {
        List<String> l = new ArrayList<>();
        l.add("# ===== 填空题 =====");
        l.add("# (空) 表示填空位");
        l.add("1. 钻石矿在Y=(空)以下生成(20)");
        l.add("答案: 16");
        return l;
    }

    private static List<String> defaultOpen() {
        List<String> l = new ArrayList<>();
        l.add("# ===== 问答题 =====");
        l.add("| 问题 | 答案 | 赏金 |");
        l.add("|------|------|------|");
        l.add("| M.C的全称是什么？ | Mojang Studios | 10 |");
        return l;
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
            w.write("# 刷新费用(债券)\n");
            w.write("刷新费用: 150\n");
            w.write("# 刷新最低奖励数\n");
            w.write("刷新最低奖励: 3\n");
            w.write("# 刷新次数(每个宝箱)\n");
            w.write("刷新次数: 1\n");
            w.write("# 欢迎游玩草原探险服务器");
            w.write("# 服务器ip：mc2.ypshidifu.cn");
            w.write("# 端口号30679，Java免输入");
            w.close();
        } catch (IOException ignored) {
        }
    }
}