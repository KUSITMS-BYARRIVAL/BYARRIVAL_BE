package com.byarrival.domain.ai.service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI가 쓴 글이 입력 글에 근거하는지 서버에서 확인할 때 쓰는 비교 도구. 추가 AI 호출 없이 숫자와 인용 구절을 대조한다.
 */
public final class AiTextMatcher {

    // 소수점·자릿수 쉼표는 숫자 사이에 있을 때만 숫자로 본다("28.5..."의 끝 마침표, "28명,"의 쉼표는 빼고 28.5, 28)
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)*");
    private static final Pattern THOUSANDS_COMMA = Pattern.compile("\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?");

    private AiTextMatcher() {
    }

    /**
     * 글에 나온 숫자를 값으로 모은다. 자릿수 쉼표와 소수점 끝의 0은 무시한다("1,000"과 "1000", "3.0"과 "3"은 같다).
     */
    public static Set<String> numbersOf(String text) {
        Set<String> numbers = new HashSet<>();
        if (text == null) {
            return numbers;
        }
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            String raw = matcher.group();
            if (raw.contains(",") && !THOUSANDS_COMMA.matcher(raw).matches()) {
                // "1,2"처럼 자릿수 쉼표가 아닌 쉼표는 나열이므로 숫자를 따로 센다
                for (String part : raw.split(",")) {
                    numbers.add(canonical(part));
                }
                continue;
            }
            numbers.add(canonical(raw.replace(",", "")));
        }
        return numbers;
    }

    /**
     * 공백과 문장 부호를 빼서 인용 구절이 원문에 있는지 비교할 수 있게 한다.
     */
    public static String normalize(String text) {
        return text == null ? "" : text.replaceAll("[\\s\\p{Punct}·‘’“”…「」『』《》〈〉]", "");
    }

    public static int length(String text) {
        return text.codePointCount(0, text.length());
    }

    private static String canonical(String number) {
        try {
            return new BigDecimal(number).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return number;
        }
    }
}
