package com.byarrival.domain.quiz.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.quiz.dto.QuizOutput;
import com.byarrival.domain.quiz.exception.QuizErrorCode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * AI 퀴즈 응답을 저장 전에 검증한다(AI-04). 실패하면 어긴 규칙을 모두 모아 예외 메시지로 돌려준다.
 * 재요청할 때 이 메시지를 AI에게 그대로 알려 준다.
 */
@Component
public class QuizValidator {

    public static final int WRONG_ANSWER_COUNT = 2;
    // 글자 수는 화면(문제 2줄, 선택지 1줄, 해설 1~2줄)에 맞춰 정한 값이다(2026-11-13)
    public static final int MAX_QUESTION_LENGTH = 50;
    public static final int MAX_CHOICE_LENGTH = 20;
    public static final int MAX_EXPLANATION_LENGTH = 60;
    // 문제와 해설은 실제 문제처럼 쓴다. "요약에 나와요"처럼 출처를 드러내는 표현은 쓰지 않는다(2026-11-14)
    private static final List<String> SOURCE_PHRASES = List.of("요약", "기사에 따르면", "기사에서", "본문");
    // 정답이 이 글자 수 이상 제목에 그대로 있으면 제목만 보고 맞힐 수 있다고 본다
    private static final int MIN_TITLE_MATCH_LENGTH = 2;
    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,.]*");

    public CheckedQuiz validate(QuizOutput output, String title) {
        if (output == null) {
            throw invalid(List.of("응답이 비어 있습니다."));
        }
        List<String> problems = new ArrayList<>();
        if (!title.equals(output.title())) {
            problems.add("title을 입력받은 제목과 똑같이 쓰세요.");
        }

        String question = strip(output.question());
        if (question == null) {
            problems.add("question이 비어 있습니다.");
        } else {
            checkLength(question, MAX_QUESTION_LENGTH, "question", problems);
            if (!question.endsWith("?")) {
                problems.add("question은 물음표(?)로 끝내세요.");
            }
            checkSourcePhrase(question, "question", problems);
        }

        String answer = strip(output.answer());
        Set<String> seen = new HashSet<>();
        if (answer == null) {
            problems.add("answer가 비어 있습니다.");
        } else {
            checkLength(answer, MAX_CHOICE_LENGTH, "answer", problems);
            seen.add(answer);
        }
        List<String> wrongAnswers = new ArrayList<>();
        if (output.wrongAnswers() == null || output.wrongAnswers().size() != WRONG_ANSWER_COUNT) {
            problems.add("wrongAnswers는 정확히 " + WRONG_ANSWER_COUNT + "개여야 합니다.");
        } else {
            for (int i = 0; i < output.wrongAnswers().size(); i++) {
                String wrong = strip(output.wrongAnswers().get(i));
                if (wrong == null) {
                    problems.add((i + 1) + "번 오답이 비어 있습니다.");
                    continue;
                }
                checkLength(wrong, MAX_CHOICE_LENGTH, (i + 1) + "번 오답", problems);
                if (!seen.add(wrong)) {
                    problems.add((i + 1) + "번 오답이 정답이나 다른 오답과 같습니다.");
                }
                wrongAnswers.add(wrong);
            }
        }

        String explanation = strip(output.explanation());
        if (explanation == null) {
            problems.add("explanation이 비어 있습니다.");
        } else {
            checkLength(explanation, MAX_EXPLANATION_LENGTH, "explanation", problems);
            if (!explanation.endsWith("요.") && !explanation.endsWith("요")) {
                problems.add("explanation은 '~요.'로 끝내세요.");
            }
            checkSourcePhrase(explanation, "explanation", problems);
        }

        if (answer != null && wrongAnswers.size() == WRONG_ANSWER_COUNT) {
            checkAnswerNotInTitle(title, answer, wrongAnswers, problems);
        }

        if (!problems.isEmpty()) {
            throw invalid(problems);
        }
        return new CheckedQuiz(question, answer, List.copyOf(wrongAnswers), explanation);
    }

    private static void checkLength(String text, int max, String name, List<String> problems) {
        int length = text.codePointCount(0, text.length());
        if (length > max) {
            problems.add(name + "이(가) " + length + "자입니다. " + max + "자 이내로 쓰세요.");
        }
    }

    // 퀴즈 화면에 기사 제목이 함께 보이므로, 정답이 제목에 드러나면 제목만 보고 맞힐 수 있다(2026-11-14)
    private static void checkAnswerNotInTitle(String title, String answer, List<String> wrongAnswers,
        List<String> problems) {
        String normalizedTitle = normalize(title);
        String normalizedAnswer = normalize(answer);
        if (normalizedAnswer.length() >= MIN_TITLE_MATCH_LENGTH && normalizedTitle.contains(normalizedAnswer)) {
            problems.add("정답 '" + answer + "'이(가) 제목에 그대로 있어 제목만 보고 맞힐 수 있습니다. 제목에 없는 내용을 물으세요.");
            return;
        }
        // 정답의 숫자는 제목에 있는데 오답의 숫자는 제목에 없으면, 숫자만 보고 맞힐 수 있다
        Set<String> titleNumbers = numbersOf(title);
        boolean answerNumberInTitle = numbersOf(answer).stream().anyMatch(titleNumbers::contains);
        boolean wrongNumberInTitle = wrongAnswers.stream()
            .flatMap(wrong -> numbersOf(wrong).stream())
            .anyMatch(titleNumbers::contains);
        if (answerNumberInTitle && !wrongNumberInTitle) {
            problems.add("정답의 숫자가 제목에 있어 제목만 보고 맞힐 수 있습니다. 제목에 없는 내용을 물으세요.");
        }
    }

    // 공백과 문장 부호를 빼고 비교한다
    private static String normalize(String text) {
        return text.replaceAll("[\\s\\p{Punct}·‘’“”…]", "");
    }

    private static Set<String> numbersOf(String text) {
        Set<String> numbers = new HashSet<>();
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            numbers.add(matcher.group().replace(",", ""));
        }
        return numbers;
    }

    private static void checkSourcePhrase(String text, String name, List<String> problems) {
        SOURCE_PHRASES.stream().filter(text::contains).findFirst().ifPresent(phrase ->
            problems.add(name + "에 '" + phrase + "' 같은 출처 표현을 쓰지 말고 사실만 쓰세요."));
    }

    private static String strip(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private static BusinessException invalid(List<String> problems) {
        return new BusinessException(QuizErrorCode.QUIZ_RESPONSE_INVALID, String.join("\n", problems));
    }

    /**
     * 검증을 통과한 퀴즈. 아직 선택지 순서는 정하지 않았다({@link QuizChoiceShuffler}).
     */
    public record CheckedQuiz(String question, String answer, List<String> wrongAnswers, String explanation) {
    }

    /**
     * 선택지 순서와 정답 번호(1부터)까지 정한, 저장할 퀴즈
     */
    public record ValidatedQuiz(String question, List<String> choices, int answerSeq, String explanation) {
    }
}
