package Sdf1_game;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Question {

    public enum Type {
        CHOICE, MULTI, FILL, OPEN
    }

    private static final Pattern BLANK =
            Pattern.compile("\\(\\s*\\)");

    private final Type type;
    private final String question;
    private final String answer;
    private final int[] rewards;
    private final List<String> options;

    public Question(Type type, String question,
                    String answer, int[] rewards,
                    List<String> options) {
        this.type = type;
        this.question = question;
        this.answer = answer;
        this.rewards = rewards;
        this.options = options != null
                ? new ArrayList<>(options)
                : new ArrayList<>();
    }

    public Question(Type type, String question,
                    String answer, int reward,
                    List<String> options) {
        this(type, question, answer,
                new int[]{reward}, options);
    }

    public Question(Type type, String question,
                    String answer, int reward) {
        this(type, question, answer,
                new int[]{reward}, null);
    }

    public Question(Type type, String question,
                    String answer, int[] rewards) {
        this(type, question, answer,
                rewards, new ArrayList<>());
    }

    public Type getType() {
        return type;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public int[] getRewards() {
        return rewards;
    }

    public List<String> getOptions() {
        return options;
    }

    public int getDisplayReward() {
        int max = 0;
        for (int r : rewards) {
            max = Math.max(max, r);
        }
        return max;
    }

    public int getRewardForCount(int count) {
        if (count <= 0) return 0;
        return rewards[Math.min(count,
                rewards.length) - 1];
    }

    public int getBlankCount() {
        Matcher m = BLANK.matcher(question);
        int c = 0;
        while (m.find()) c++;
        return c;
    }

    public int getCorrectCount() {
        return answer.split(",").length;
    }

    public String getTypeName() {
        switch (type) {
            case CHOICE:
                return "选择题";
            case MULTI:
                return "多选题";
            case FILL:
                return "填空题";
            default:
                return "问答题";
        }
    }
}
