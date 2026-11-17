package com.byarrival.domain.article.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.ai.exception.AiErrorCode;
import com.byarrival.domain.ai.service.AiTextMatcher;
import com.byarrival.domain.article.dto.ArticleSummaryOutput;
import com.byarrival.domain.article.entity.ArticleDifficulty;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/**
 * AI 요약 응답을 저장 전에 검증한다(AI-02). 프롬프트에 규칙이 있어도 AI가 어길 수 있어 서버에서 다시 확인한다.
 * 실패하면 어긴 규칙을 모두 모아 예외 메시지로 돌려준다. 재요청할 때 이 메시지를 AI에게 그대로 알려 준다.
 */
@Component
public class ArticleSummaryValidator {

    public static final int SENTENCE_COUNT = 3;
    public static final int MAX_SENTENCE_LENGTH = 30;
    public static final int MAX_ONE_LINE_LENGTH = 40;
    // 키워드 개수와 글자 수는 명세에 없어 가정한 값이다(2026-11-10). 기획 확정 후 바꾼다
    public static final int KEYWORD_COUNT = 3;
    public static final int MAX_KEYWORD_LENGTH = 10;
    // 문체는 "~했습니다/~입니다"로 고정한다(2026-11-10 AI 파트 기준)
    private static final String POLITE_ENDING = "니다";
    // 근거 구절이 너무 짧으면 본문 아무 데나 들어맞는다
    private static final int MIN_EVIDENCE_LENGTH = 8;

    /**
     * @return 앞뒤 공백을 뗀 한 줄 요약, 3문장, 핵심 키워드
     */
    public ValidatedSummary validate(ArticleSummaryOutput output, String title, String content,
        ArticleDifficulty difficulty) {
        if (output == null) {
            throw invalid(List.of("응답이 비어 있습니다."));
        }
        List<String> problems = new ArrayList<>();
        if (!title.equals(output.title())) {
            problems.add("title을 입력받은 제목과 똑같이 쓰세요.");
        }
        if (difficulty != output.difficulty()) {
            problems.add("difficulty를 입력값 " + difficulty + "로 쓰세요.");
        }

        String oneLine = checkSentence(output.oneLine(), MAX_ONE_LINE_LENGTH, "oneLine", title, problems);

        List<String> summary = output.summary();
        List<String> sentences = new ArrayList<>();
        if (summary == null || summary.size() != SENTENCE_COUNT) {
            problems.add("summary는 정확히 " + SENTENCE_COUNT + "문장이어야 합니다.");
        } else {
            for (int i = 0; i < summary.size(); i++) {
                sentences.add(checkSentence(summary.get(i), MAX_SENTENCE_LENGTH,
                    "summary " + (i + 1) + "번 문장", title, problems));
            }
        }

        List<String> keywords = checkKeywords(output.keywords(), problems);

        checkGrounding(oneLine, sentences, output.evidence(), title, content, problems);

        if (!problems.isEmpty()) {
            throw invalid(problems);
        }
        return new ValidatedSummary(oneLine, List.copyOf(sentences), keywords);
    }

    private String checkSentence(String text, int maxLength, String name, String title, List<String> problems) {
        if (text == null || text.isBlank()) {
            problems.add(name + "이 비어 있습니다.");
            return null;
        }
        String stripped = text.strip();
        int length = length(stripped);
        if (length > maxLength) {
            problems.add(name + "이 " + length + "자입니다. 공백 포함 " + maxLength + "자 이내로 줄이세요: \""
                + stripped + "\"");
        }
        if (!endsPolitely(stripped)) {
            problems.add(name + "을 \"~했습니다/~입니다\"처럼 \"니다\"로 끝내세요: \"" + stripped + "\"");
        }
        if (stripped.equals(title.strip())) {
            problems.add(name + "이 제목을 그대로 반복합니다. 다른 말로 쓰세요.");
        }
        return stripped;
    }

    /**
     * 요약이 원문에 근거하는지 추가 AI 호출 없이 확인한다(2026-11-17). 근거 구절은 원문에 그대로 있어야 하고,
     * 요약 문장의 숫자는 그 문장의 근거 구절에, 한 줄 요약의 숫자는 원문이나 제목에 있어야 한다.
     * 원문에 없는 숫자를 지어내는 것을 막는다. 표현을 바꿔 뜻을 틀리게 쓴 경우는 잡지 못한다.
     */
    private void checkGrounding(String oneLine, List<String> sentences, List<String> evidence, String title,
        String content, List<String> problems) {
        String normalizedContent = AiTextMatcher.normalize(content);
        Set<String> sourceNumbers = new HashSet<>(AiTextMatcher.numbersOf(content));
        sourceNumbers.addAll(AiTextMatcher.numbersOf(title));
        if (oneLine != null) {
            Set<String> missing = missingNumbers(oneLine, sourceNumbers);
            if (!missing.isEmpty()) {
                problems.add("oneLine의 숫자 " + missing + "가 기사 본문에 없습니다. 본문에 적힌 숫자를 그대로 쓰세요.");
            }
        }
        if (evidence == null || evidence.size() != SENTENCE_COUNT) {
            problems.add("evidence는 summary 문장마다 하나씩 정확히 " + SENTENCE_COUNT + "개여야 합니다.");
            return;
        }
        for (int i = 0; i < evidence.size(); i++) {
            String quote = evidence.get(i) == null ? "" : evidence.get(i).strip();
            String normalizedQuote = AiTextMatcher.normalize(quote);
            boolean tooShort = AiTextMatcher.length(normalizedQuote) < MIN_EVIDENCE_LENGTH;
            if (tooShort || !normalizedContent.contains(normalizedQuote)) {
                problems.add("evidence " + (i + 1) + "번이 기사 본문에 없습니다. 본문 구절을 고치지 말고 글자 그대로 옮기세요: \""
                    + quote + "\"");
                continue;
            }
            if (i < sentences.size() && sentences.get(i) != null) {
                Set<String> missing = missingNumbers(sentences.get(i), AiTextMatcher.numbersOf(quote));
                if (!missing.isEmpty()) {
                    problems.add("summary " + (i + 1) + "번 문장의 숫자 " + missing + "가 evidence " + (i + 1)
                        + "번 구절에 없습니다. 근거 구절에 적힌 숫자를 그대로 쓰세요.");
                }
            }
        }
    }

    private static Set<String> missingNumbers(String text, Set<String> sourceNumbers) {
        Set<String> missing = new TreeSet<>(AiTextMatcher.numbersOf(text));
        missing.removeAll(sourceNumbers);
        return missing;
    }

    // 마침표는 있어도 없어도 된다
    private boolean endsPolitely(String sentence) {
        String body = sentence.endsWith(".") ? sentence.substring(0, sentence.length() - 1) : sentence;
        return body.endsWith(POLITE_ENDING);
    }

    private List<String> checkKeywords(List<String> keywords, List<String> problems) {
        if (keywords == null || keywords.size() != KEYWORD_COUNT) {
            problems.add("keywords는 정확히 " + KEYWORD_COUNT + "개여야 합니다.");
            return List.of();
        }
        List<String> stripped = new ArrayList<>();
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) {
                problems.add("빈 keyword가 있습니다.");
                continue;
            }
            String value = keyword.strip();
            if (length(value) > MAX_KEYWORD_LENGTH) {
                problems.add("keyword \"" + value + "\"가 " + MAX_KEYWORD_LENGTH + "자를 넘습니다.");
            }
            if (value.startsWith("#")) {
                problems.add("keyword \"" + value + "\" 앞의 #을 빼세요.");
            }
            stripped.add(value);
        }
        if (stripped.stream().distinct().count() != stripped.size()) {
            problems.add("keywords가 중복됩니다.");
        }
        return List.copyOf(stripped);
    }

    // 한글 음절은 코드포인트 하나가 한 글자다. 이모지 같은 서로게이트 쌍도 한 글자로 센다
    private static int length(String text) {
        return text.codePointCount(0, text.length());
    }

    private BusinessException invalid(List<String> problems) {
        return new BusinessException(AiErrorCode.AI_RESPONSE_INVALID, String.join("\n", problems));
    }

    public record ValidatedSummary(String oneLine, List<String> sentences, List<String> keywords) {
    }
}
