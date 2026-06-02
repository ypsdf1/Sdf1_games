package Sdf1_game;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QuestionLoader {

    private static final Pattern REWARD_TRAIL =
            Pattern.compile("\\((\\d+)\\)$");

    public static List<Question> load(Main plugin,
                                      String resourcePath) {
        List<Question> questions = new ArrayList<>();

        File file = new File(
                plugin.getDataFolder(), resourcePath);

        plugin.getLogger().info(
                "[快问快答] 加载文件: "
                        + file.getAbsolutePath()
                        + " 存在=" + file.exists()
                        + " 大小=" + (file.exists()
                        ? file.length() + "字节"
                        : "N/A"));

        if (!file.exists()) {
            plugin.getLogger().warning(
                    "[快问快答] 文件不存在: "
                            + resourcePath);
            return questions;
        }

        Question.Type type;
        if (resourcePath.contains("选择题")) {
            type = Question.Type.CHOICE;
        } else if (resourcePath.contains("填空题")) {
            type = Question.Type.FILL;
        } else {
            type = Question.Type.OPEN;
        }

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(
                        new FileInputStream(file),
                        StandardCharsets.UTF_8))) {

            List<String> cleaned = cleanComments(br);

            plugin.getLogger().info(
                    "[快问快答] 有效行数: "
                            + cleaned.size());
            if (!cleaned.isEmpty()) {
                plugin.getLogger().info(
                        "[快问快答] 前3行: "
                                + cleaned.get(0)
                                + (cleaned.size() > 1
                                ? " | " + cleaned.get(1)
                                : "")
                                + (cleaned.size() > 2
                                ? " | " + cleaned.get(2)
                                : ""));
            }

            switch (type) {
                case CHOICE:
                    parseChoice(cleaned, questions);
                    break;
                case FILL:
                    parseFill(cleaned, questions);
                    break;
                default:
                    parseOpen(cleaned, questions);
                    break;
            }

            plugin.getLogger().info(
                    "[快问快答] 解析出: "
                            + questions.size() + "题"
                            + " (" + resourcePath + ")");

        } catch (IOException e) {
            plugin.getLogger().warning(
                    "[快问快答] 加载失败: "
                            + resourcePath + " - "
                            + e.getMessage());
        }

        return questions;
    }

    private static List<String> cleanComments(
            BufferedReader br) {
        List<String> result = new ArrayList<>();
        try {
            String line;
            boolean inBlock = false;
            boolean inHtml = false;

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (inBlock) {
                    int end = line.indexOf("*/");
                    if (end >= 0) {
                        inBlock = false;
                        line = line
                                .substring(end + 2)
                                .trim();
                        if (line.isEmpty()) continue;
                    } else {
                        continue;
                    }
                }
                if (line.startsWith("/*")) {
                    int end = line.indexOf("*/", 2);
                    if (end >= 0) {
                        line = line
                                .substring(end + 2)
                                .trim();
                        if (line.isEmpty()) continue;
                    } else {
                        inBlock = true;
                        continue;
                    }
                }

                if (inHtml) {
                    int end = line.indexOf("-->");
                    if (end >= 0) {
                        inHtml = false;
                        line = line
                                .substring(end + 3)
                                .trim();
                        if (line.isEmpty()) continue;
                    } else {
                        continue;
                    }
                }
                if (line.startsWith("<!--")) {
                    int end = line.indexOf("-->", 4);
                    if (end >= 0) {
                        line = line
                                .substring(end + 3)
                                .trim();
                        if (line.isEmpty()) continue;
                    } else {
                        inHtml = true;
                        continue;
                    }
                }

                if (line.startsWith("#")) continue;

                int h = line.indexOf('#');
                if (h > 0) {
                    line = line.substring(0, h).trim();
                }
                int s = line.indexOf("//");
                if (s > 0) {
                    line = line.substring(0, s).trim();
                }

                if (!line.isEmpty()) {
                    result.add(line);
                }
            }
        } catch (IOException ignored) {
        }
        return result;
    }

    /**
     * 选择题表格
     * 答案含逗号 → 多选题
     * 答案为"全选" → 所有选项
     */
    private static void parseChoice(
            List<String> lines,
            List<Question> result) {

        for (String line : lines) {
            if (!line.startsWith("|")) continue;
            if (line.contains("---")) continue;

            String[] parts = line.split("\\|");
            if (parts.length < 5) continue;

            String question = parts[1].trim();
            String answer =
                    parts[parts.length - 2].trim();
            String rewardStr =
                    parts[parts.length - 1].trim();

            if (question.equals("问题")) continue;
            if (question.isEmpty()
                    || answer.isEmpty()) continue;

            // 收集非空选项
            List<String> options = new ArrayList<>();
            for (int i = 2;
                 i <= parts.length - 3; i++) {
                String opt = parts[i].trim();
                if (!opt.isEmpty()) {
                    options.add(opt);
                }
            }
            if (options.isEmpty()) continue;

            // 判断是否多选
            boolean isMulti =
                    answer.contains(",")
                            || answer.contains("，")
                            || answer.equals("全选");

            if (isMulti) {
                String realAnswer;
                if (answer.equals("全选")) {
                    // 生成全选答案
                    StringBuilder sb =
                            new StringBuilder();
                    char ch = 'A';
                    for (int i = 0;
                         i < options.size(); i++) {
                        if (i > 0) sb.append(",");
                        sb.append(ch);
                        ch++;
                    }
                    realAnswer = sb.toString();
                } else {
                    realAnswer = answer;
                }

                // 赏金解析
                String[] rp = rewardStr.split(",");
                int correctCount =
                        realAnswer.split(",").length;
                int[] rewards;

                if (rp.length > 1) {
                    rewards = new int[Math.min(
                            rp.length, correctCount)];
                    for (int i = 0;
                         i < rewards.length; i++) {
                        rewards[i] = Integer.parseInt(
                                rp[i].trim());
                    }
                } else {
                    int base = Integer.parseInt(
                            rewardStr.trim());
                    rewards = new int[correctCount];
                    for (int i = 0;
                         i < correctCount; i++) {
                        rewards[i] = base * (i + 1);
                    }
                }

                result.add(new Question(
                        Question.Type.MULTI,
                        question, realAnswer,
                        rewards, options));
            } else {
                // 单选题
                int reward;
                try {
                    reward = Integer.parseInt(
                            rewardStr);
                } catch (NumberFormatException e) {
                    continue;
                }

                result.add(new Question(
                        Question.Type.CHOICE,
                        question, answer,
                        reward, options));
            }
        }
    }

    private static void parseOpen(
            List<String> lines,
            List<Question> result) {

        for (String line : lines) {
            if (!line.startsWith("|")) continue;
            if (line.contains("---")) continue;

            String[] parts = line.split("\\|");
            if (parts.length < 4) continue;

            String question = parts[1].trim();
            String answer = parts[2].trim();
            String rewardStr = parts[3].trim();

            if (question.equals("问题")) continue;
            if (question.isEmpty()
                    || answer.isEmpty()) continue;

            int reward;
            try {
                reward = Integer.parseInt(rewardStr);
            } catch (NumberFormatException e) {
                continue;
            }

            result.add(new Question(
                    Question.Type.OPEN,
                    question, answer, reward));
        }
    }

    private static void parseFill(
            List<String> lines,
            List<Question> result) {

        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            if (!line.matches("\\d+\\.\\s*.*")) {
                i++;
                continue;
            }

            int dot = line.indexOf('.');
            String content =
                    line.substring(dot + 1).trim();

            List<Integer> rewardList =
                    new ArrayList<>();
            while (true) {
                Matcher m = REWARD_TRAIL
                        .matcher(content);
                if (m.find()) {
                    rewardList.add(0,
                            Integer.parseInt(
                                    m.group(1)));
                    content = content.substring(0,
                            m.start()).trim();
                } else {
                    break;
                }
            }

            if (content.isEmpty()) {
                i++;
                continue;
            }

            String answer = "";
            if (i + 1 < lines.size()) {
                String aLine =
                        lines.get(i + 1).trim();
                int ci = Math.max(
                        aLine.indexOf(':'),
                        aLine.indexOf('：'));
                if (ci >= 0) {
                    answer = aLine
                            .substring(ci + 1).trim();
                }
                i += 2;
            } else {
                i++;
                continue;
            }

            if (answer.isEmpty()) continue;

            int[] rewards = rewardList.stream()
                    .mapToInt(Integer::intValue)
                    .toArray();

            result.add(new Question(
                    Question.Type.FILL,
                    content, answer, rewards));
        }
    }
}
