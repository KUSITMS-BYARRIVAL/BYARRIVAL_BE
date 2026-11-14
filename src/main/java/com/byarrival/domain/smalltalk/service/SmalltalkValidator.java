package com.byarrival.domain.smalltalk.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.smalltalk.dto.SmalltalkOutput;
import com.byarrival.domain.smalltalk.exception.SmalltalkErrorCode;
import java.util.ArrayList;
import java.util.List;
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
    public String validate(SmalltalkOutput output, String title) {
        if (output == null) {
            throw invalid(List.of("응답이 비어 있습니다."));
        }
        if (!output.suitable()) {
            throw new UnsuitableArticleException();
        }
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
            String ending = hook.replaceAll("[\\s\"'”’.?!]+$", "");
            if (!ending.endsWith("요")) {
                problems.add("hook은 해요체(~요, ~요?)로 끝내세요.");
            }
        }
        if (!problems.isEmpty()) {
            throw invalid(problems);
        }
        return hook;
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
