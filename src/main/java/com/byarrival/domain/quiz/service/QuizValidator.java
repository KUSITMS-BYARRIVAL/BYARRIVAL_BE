package com.byarrival.domain.quiz.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.quiz.dto.QuizOutput;
import com.byarrival.domain.quiz.exception.QuizErrorCode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * AI 퀴즈 응답을 저장 전에 검증한다(AI-04). 실패하면 어긴 규칙을 모두 모아 예외 메시지로 돌려준다.
 * 재요청할 때 이 메시지를 AI에게 그대로 알려 준다.
 */
@Component
public class QuizValidator {

    public static final int CHOICE_COUNT = 3;
    // 글자 수는 화면(문제 2줄, 선택지 1줄, 해설 1~2줄)에 맞춰 정한 값이다(2026-11-13)
    public static final int MAX_QUESTION_LENGTH = 50;
    public static final int MAX_CHOICE_LENGTH = 20;
    public static final int MAX_EXPLANATION_LENGTH = 60;

    public ValidatedQuiz validate(QuizOutput output, String title) {
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
            if (length(question) > MAX_QUESTION_LENGTH) {
                problems.add("question이 " + length(question) + "자입니다. " + MAX_QUESTION_LENGTH + "자 이내로 쓰세요.");
            }
            if (!question.endsWith("?")) {
                problems.add("question은 물음표(?)로 끝내세요.");
            }
        }

        List<String> choices = new ArrayList<>();
        if (output.choices() == null || output.choices().size() != CHOICE_COUNT) {
            problems.add("choices는 정확히 " + CHOICE_COUNT + "개여야 합니다.");
        } else {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < output.choices().size(); i++) {
                String choice = strip(output.choices().get(i));
                if (choice == null) {
                    problems.add((i + 1) + "번 선택지가 비어 있습니다.");
                    continue;
                }
                if (length(choice) > MAX_CHOICE_LENGTH) {
                    problems.add((i + 1) + "번 선택지가 " + length(choice) + "자입니다. " + MAX_CHOICE_LENGTH + "자 이내로 쓰세요.");
                }
                if (!seen.add(choice)) {
                    problems.add((i + 1) + "번 선택지가 다른 선택지와 같습니다.");
                }
                choices.add(choice);
            }
        }
        if (output.answer() < 1 || output.answer() > CHOICE_COUNT) {
            problems.add("answer는 1부터 " + CHOICE_COUNT + " 사이의 선택지 번호여야 합니다.");
        }

        String explanation = strip(output.explanation());
        if (explanation == null) {
            problems.add("explanation이 비어 있습니다.");
        } else {
            if (length(explanation) > MAX_EXPLANATION_LENGTH) {
                problems.add("explanation이 " + length(explanation) + "자입니다. " + MAX_EXPLANATION_LENGTH
                    + "자 이내로 쓰세요.");
            }
            if (!explanation.endsWith("요.") && !explanation.endsWith("요")) {
                problems.add("explanation은 '~요.'로 끝내세요.");
            }
        }

        if (!problems.isEmpty()) {
            throw invalid(problems);
        }
        return new ValidatedQuiz(question, List.copyOf(choices), output.answer(), explanation);
    }

    private static String strip(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private static int length(String text) {
        return text.codePointCount(0, text.length());
    }

    private static BusinessException invalid(List<String> problems) {
        return new BusinessException(QuizErrorCode.QUIZ_RESPONSE_INVALID, String.join("\n", problems));
    }

    public record ValidatedQuiz(String question, List<String> choices, int answerSeq, String explanation) {
    }
}
