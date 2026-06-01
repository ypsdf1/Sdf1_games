package Sdf1_game;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class FileGenerator {

    public static void ensureAll(File base, File qDir) {
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
            f.getParentFile().mkdirs();
            OutputStreamWriter w = new OutputStreamWriter(
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

    private static List<String> defaultConfig() {
        List<String> l = new ArrayList<>();
        l.add("# ===== 快问快答 主配置 =====");
        l.add("# 支持 # // /* */ <!-- --> 注释");
        l.add("");
        l.add("# 运行模式: 自动 / 手动");
        l.add("# 模式区别：");
        l.add("# 手动模式，纯手动出题");
        l.add("# 自动模式：手自一体化，按下面配置规则自动出题");
        l.add("模式: 自动");
        l.add("");
        l.add("# 最少在线人数");
        l.add("最少在线人数: 3");
        l.add("");
        l.add("# 出题间隔");
        l.add("# 支持: 秒(s) 分钟(m) 小时(h)");
        l.add("最小间隔: 5分钟");
        l.add("最大间隔: 15分钟");
        l.add("");
        l.add("# 答题时间");
        l.add("答题时间: 2分钟");
        l.add("");
        l.add("# 蒙题防护");
        l.add("# 选择题中，2秒内错误答案超过此数则罚时");
        l.add("# 罚时期间答案无效");
        l.add("蒙题检测数: 2");
        l.add("");
        l.add("# 罚时时间（秒）");
        l.add("# 支持: 秒(s) 分钟(m) 小时(h)");
        l.add("罚时时间: 10秒");
        l.add("");
        l.add("# 消息前缀");
        l.add("消息前缀: §e§l【快问快答】§r");
        l.add("");
        l.add("# 蒙题防护提示");
        l.add("罚时开始提示: §c你回答太快了，请冷静10秒！");
        l.add("罚时结束提示: §a罚时已结束，你可以继续答题了");
        return l;
    }


    private static List<String> defaultChoice() {
        List<String> l = new ArrayList<>();
        l.add("# ===== 选择题 =====");
        l.add("# 格式: | 问题 | 选项A | 选项B | [选项C] | [选项D] | 答案 | 赏金 |");
        l.add("# 空选项列自动跳过");
        l.add("");
        l.add("| 问题 | 选项A | 选项B | 选项C | 选项D | 答案 | 赏金 |");
        l.add("|------|-------|-------|-------|-------|------|------|");
        l.add("| 钻石矿在几层以下生成？ | 4层 | 16层 | 32层 | 64层 | B | 10 |");
        l.add("| 末影龙的血量是多少？ | 100 | 200 | 300 | 500 | B | 15 |");
        l.add("| 下界合金锭需要几个碎片？ | 2个 | 3个 | 4个 | 5个 | C | 10 |");
        l.add("| 铁砧最多修复几次后损坏？ | 3次 | 5次 | 6次 | 10次 | C | 10 |");
        l.add("| 下界合金剑的攻击力？ | 6 | 7 | 8 | 9 | D | 12 |");
        l.add("| 哪种生物不在平原生成？ | 苦力怕 | 僵尸 | 末影人 | 骷髅 | C | 8 |");
        l.add("| 信标最少几层方块？ | 1层 | 2层 | 3层 | 4层 | C | 10 |");
        l.add("| 附魔台最多放几本书？ | 4本 | 8本 | 12本 | 15本 | D | 10 |");
        l.add("| 熔岩在几格内造成伤害？ | 1格 | 2格 | 3格 | | A | 8 |");
        l.add("| 末影珍珠冷却时间？ | 0秒 | 1秒 | 5秒 | | B | 10 |");
        return l;
    }

    private static List<String> defaultFill() {
        List<String> l = new ArrayList<>();
        l.add("# ===== 填空题 =====");
        l.add("# 编号. 问题(赏金1)(赏金2)...");
        l.add("# 答案:答案1,答案2,...");
        l.add("");
        l.add("1. 钻石矿在( )层以下生成。(10)");
        l.add("答案:16");
        l.add("");
        l.add("2. 末影龙被击败后掉落( )经验。(15)");
        l.add("答案:12000");
        l.add("");
        l.add("3. ( )是游戏中最坚硬的方块。(15)");
        l.add("答案:基岩");
        l.add("");
        l.add("4. 下界合金锭由( )个碎片和( )个金锭合成。(10)(10)");
        l.add("答案:4,4");
        l.add("");
        l.add("5. MC一共有( )个维度。(5)");
        l.add("答案:3");
        l.add("");
        l.add("6. 信标最少需要( )层方块才能激活。(10)");
        l.add("答案:3");
        l.add("");
        l.add("7. 钻石剑攻击力( )点，下界合金剑( )点。(8)(8)");
        l.add("答案:7,8");
        l.add("");
        l.add("8. 饱和效果的英文ID是( )。(5)");
        l.add("答案:saturation");
        l.add("");
        l.add("9. 每个铁砧使用后消耗( )点耐久。(5)");
        l.add("答案:1");
        l.add("");
        l.add("10. 凋零Boss被击败后掉落( )个下界之星。(10)");
        l.add("答案:1");
        return l;
    }

    private static List<String> defaultOpen() {
        List<String> l = new ArrayList<>();
        l.add("# ===== 问答题 =====");
        l.add("# | 问题 | 答案 | 赏金 |");
        l.add("");
        l.add("| 问题 | 答案 | 赏金 |");
        l.add("|------|------|------|");
        l.add("| MC的全称是什么？ | Minecraft | 20 |");
        l.add("| Minecraft的创始人是谁？ | Notch | 20 |");
        l.add("| 下界合金锭的英文名？ | Netherite Ingot | 15 |");
        l.add("| 末影螨的英文名？ | Endermite | 12 |");
        l.add("| 信标的英文名？ | Beacon | 10 |");
        return l;
    }
}
