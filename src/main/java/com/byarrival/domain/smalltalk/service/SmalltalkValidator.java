package com.byarrival.domain.smalltalk.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.ai.service.AiTextMatcher;
import com.byarrival.domain.smalltalk.dto.SmalltalkOutput;
import com.byarrival.domain.smalltalk.exception.SmalltalkErrorCode;
import com.byarrival.domain.smalltalk.service.SmalltalkGenerator.SmalltalkSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/**
 * AI 스몰토크 응답을 저장 전에 검증한다(TALK-01 서버 검증). 금칙어·평가어 목록은 명세에 없어 아직 검사하지 않는다.
 */
@Component
public class SmalltalkValidator {

    // 명세: 30자 이내 권장
    public static final int MAX_HOOK_LENGTH = 30;

    /**
     * @return 앞뒤 공백을 뗀 hook
     * @throws UnsuitableArticleException AI가 대화 소재로 알맞지 않다고 판단한 기사. 다시 요청하지 않고 다음 기사로 넘어간다
     */
    public String validate(SmalltalkOutput output, SmalltalkSource source) {
        if (output == null) {
            throw invalid(List.of("응답이 비어 있습니다."));
        }
        if (!output.suitable()) {
            throw new UnsuitableArticleException();
        }
        String title = source.title();
        List<String> problems = new ArrayList<>();
        if (!title.equals(output.title())) {
            problems.add("title을 입력받은 제목과 똑같이 쓰세요.");
        }
        String hook = output.hook() == null ? "" : output.hook().strip();
        if (hook.isEmpty()) {
            problems.add("hook이 비어 있습니다.");
        } else {
            if (hook.contains("\n") || hook.contains("\r")) {
                problems.add("hook은 줄바꿈 없이 한 줄로 쓰세요.");
            }
            int length = hook.codePointCount(0, hook.length());
            if (length > MAX_HOOK_LENGTH) {
                problems.add("hook이 " + length + "자입니다. " + MAX_HOOK_LENGTH + "자 이내로 쓰세요.");
            }
            // 대화를 여는 말이라 상대에게 묻는 해요체 질문(~요?)으로 끝낸다(2026-11-19 QA)
            String ending = hook.replaceAll("[\\s\"'”’]+$", "");
            if (!ending.endsWith("요?")) {
                problems.add("hook은 해요체 질문(~요?)으로 끝내세요.");
            }
            checkNumbers(hook, source, problems);
        }
        if (!problems.isEmpty()) {
            throw invalid(problems);
        }
        return hook;
    }

    // 한 줄의 숫자는 요약이나 제목에 적힌 그대로여야 한다(2026-11-17). 추가 AI 호출 없이 지어낸 수치를 막는다
    private static void checkNumbers(String hook, SmalltalkSource source, List<String> problems) {
        Set<String> sourceNumbers = new HashSet<>(AiTextMatcher.numbersOf(source.title()));
        sourceNumbers.addAll(AiTextMatcher.numbersOf(source.oneLine()));
        if (source.summary() != null) {
            source.summary().forEach(sentence -> sourceNumbers.addAll(AiTextMatcher.numbersOf(sentence)));
        }
        Set<String> missing = new TreeSet<>(AiTextMatcher.numbersOf(hook));
        missing.removeAll(sourceNumbers);
        if (!missing.isEmpty()) {
            problems.add("hook의 숫자 " + missing + "가 요약에 없습니다. 요약에 적힌 숫자를 그대로 쓰세요.");
        }
    }

    private static BusinessException invalid(List<String> problems) {
        return new BusinessException(SmalltalkErrorCode.SMALLTALK_RESPONSE_INVALID, String.join("\n", problems));
    }

    public static class UnsuitableArticleException extends RuntimeException {

        public UnsuitableArticleException() {
            super("대화 소재로 알맞지 않은 기사", null, false, false);
        }
    }
}
