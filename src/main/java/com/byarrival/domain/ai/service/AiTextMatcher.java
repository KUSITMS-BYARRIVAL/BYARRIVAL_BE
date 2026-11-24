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

    // 소수점·자릿수 쉼표는 숫자 사이에 있을 때만 숫자로 본다("28.5..."의 끝 마침표, "28명,"의 쉼표는 빼고 28.5, 28).
    // 앞에 붙은 빼기 부호는 글자나 숫자 바로 뒤가 아닐 때만 음수 부호로 본다("-0.5%"는 -0.5, "2026-11-17"·"3-1"은 나열)
    private static final Pattern NUMBER = Pattern.compile("(?:(?<![\\p{L}\\p{N}])[-−])?\\d+(?:[.,]\\d+)*");
    private static final Pattern THOUSANDS_COMMA = Pattern.compile("[-−]?\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?");
    // 인용 구절을 비교할 때 무시하는 공백과 문장 부호. \p{Punct}는 ASCII 부호만 가리키므로 AI가 바꿔 옮기기 쉬운
    // 유니코드 물결표(∼ ～ 〜)와 대시(– —)를 따로 넣는다. "2~3년"을 "2∼3년"으로 옮긴 인용이 떨어지던 문제(2026-11-24)
    private static final Pattern IGNORABLE = Pattern.compile("[\\s\\p{Punct}·‘’“”…「」『』《》〈〉−∼～〜–—]");

    private AiTextMatcher() {
    }

    /**
     * 글에 나온 숫자를 부호와 함께 값으로 모은다. 자릿수 쉼표와 소수점 끝의 0은 무시한다
     * ("1,000"과 "1000", "3.0"과 "3"은 같고, "-0.5"와 "0.5"는 다르다).
     */
    public static Set<String> numbersOf(String text) {
        Set<String> numbers = new HashSet<>();
        if (text == null) {
            return numbers;
        }
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            String raw = matcher.group().replace('−', '-');
            if (raw.contains(",") && !THOUSANDS_COMMA.matcher(raw).matches()) {
                // "1,2"처럼 자릿수 쉼표가 아닌 쉼표는 나열이므로 숫자를 따로 센다. 부호는 첫 숫자에만 붙는다
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
     * 공백과 문장 부호를 빼서 인용 구절이 원문에 있는지 비교할 수 있게 한다. 숫자의 값을 바꾸는 문자는 남긴다.
     * 숫자 사이의 소수점·쉼표("3.0"이 "30"이 되지 않게)와 음수 부호("-0.5"가 "0.5"가 되지 않게)는 지우지 않는다.
     */
    public static String normalize(String text) {
        return normalizeWithOrigin(text).text();
    }

    /**
     * 인용 구절이 원문 안에 있는지 확인한다. {@link #normalize}한 글로 비교하고, 원문의 일부만 옮긴 인용은 허용한다.
     * 다만 인용이 원문 숫자의 중간에서 시작하거나 끝나면 인정하지 않는다. "-0.5%"에서 부호를 뗀 "0.5%",
     * "130%"에서 앞자리를 뗀 "30%", "3.5"에서 소수 자리를 뗀 "3"은 원문과 다른 숫자다.
     * 숫자 경계는 정규화 전 원문에서 확인한다. 정규화하면 "30%. 2026"이 "302026"처럼 붙어 경계를 알 수 없다.
     */
    public static boolean containsQuote(String text, String quote) {
        if (text == null) {
            return false;
        }
        Normalized source = normalizeWithOrigin(text);
        String target = normalize(quote);
        if (target.isEmpty()) {
            return false;
        }
        String normalizedSource = source.text();
        for (int from = normalizedSource.indexOf(target); from >= 0;
            from = normalizedSource.indexOf(target, from + 1)) {
            int start = source.origin()[from];
            int end = source.origin()[from + target.length() - 1];
            if (startsAtNumberBoundary(text, start) && endsAtNumberBoundary(text, end)) {
                return true;
            }
        }
        return false;
    }

    // 정규화한 글과, 정규화한 글자마다 원문에서의 위치
    private static Normalized normalizeWithOrigin(String text) {
        if (text == null) {
            return new Normalized("", new int[0]);
        }
        StringBuilder normalized = new StringBuilder(text.length());
        int[] origin = new int[text.length()];
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isDigitSeparator(text, i) || isMinusSign(text, i)) {
                origin[normalized.length()] = i;
                normalized.append(c == '−' ? '-' : c);
            } else if (!IGNORABLE.matcher(String.valueOf(c)).matches()) {
                origin[normalized.length()] = i;
                normalized.append(c);
            }
        }
        return new Normalized(normalized.toString(), origin);
    }

    // 원문에서 인용 첫 글자(start) 바로 앞이 숫자, 숫자 사이의 소수점·쉼표, 음수 부호면 숫자 중간에서 시작한 것이다
    private static boolean startsAtNumberBoundary(String text, int start) {
        if (start == 0 || !Character.isDigit(text.charAt(start))) {
            return true;
        }
        char before = text.charAt(start - 1);
        if (Character.isDigit(before) || isMinusSign(text, start - 1)) {
            return false;
        }
        return !((before == '.' || before == ',') && start >= 2 && Character.isDigit(text.charAt(start - 2)));
    }

    // 원문에서 인용 마지막 글자(end) 바로 뒤가 숫자이거나, 뒤에 숫자가 이어지는 소수점·쉼표면 숫자 중간에서 끝난 것이다
    private static boolean endsAtNumberBoundary(String text, int end) {
        if (end == text.length() - 1 || !Character.isDigit(text.charAt(end))) {
            return true;
        }
        char after = text.charAt(end + 1);
        if (Character.isDigit(after)) {
            return false;
        }
        return !((after == '.' || after == ',') && end + 2 < text.length()
            && Character.isDigit(text.charAt(end + 2)));
    }

    private record Normalized(String text, int[] origin) {
    }

    public static int length(String text) {
        return text.codePointCount(0, text.length());
    }

    private static boolean isDigitSeparator(String text, int i) {
        char c = text.charAt(i);
        return (c == '.' || c == ',') && i > 0 && i + 1 < text.length()
            && Character.isDigit(text.charAt(i - 1)) && Character.isDigit(text.charAt(i + 1));
    }

    private static boolean isMinusSign(String text, int i) {
        char c = text.charAt(i);
        if ((c != '-' && c != '−') || i + 1 >= text.length() || !Character.isDigit(text.charAt(i + 1))) {
            return false;
        }
        return i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1));
    }

    private static String canonical(String number) {
        try {
            String value = new BigDecimal(number).stripTrailingZeros().toPlainString();
            // "-0"과 "0"은 같은 값이다
            return value.equals("-0") ? "0" : value;
        } catch (NumberFormatException e) {
            return number;
        }
    }
}
