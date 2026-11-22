package com.byarrival.domain.article.service;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 요약 문장이 제목·다른 문장과 글자가 얼마나 겹치는지 잰다(2026-11-23).
 * s2·s3는 제목·s1·서로와 겹치지 않는 새 사실을 담아야 한다. s1은 핵심 사건이라 제목과 비슷해도 된다.
 * 응답을 거부하지 않고 로그로만 남긴다. 기준값은 배치 로그로 실제 겹침과 비교해 정한다.
 * 글자만 보므로 표현을 바꾼 겹침("사망했습니다"/"숨졌습니다")은 낮게 나오고, 고유명사가 많이 겹치면 높게 나올 수 있다.
 */
final class SummaryOverlap {

    // 공백, 문장 부호, 기호를 지운다
    private static final Pattern IGNORABLE = Pattern.compile("[\\s\\p{P}\\p{S}]");
    // 모든 문장에 붙는 어미는 겹침이 아니므로 뗀다. 예: "투입했습니다" → "투입"
    private static final Pattern POLITE_ENDING = Pattern.compile("[했됐였았었겠입합됩옵]?(?:습니다|니다)$");

    private SummaryOverlap() {
    }

    /**
     * 비교 쌍(title-s2, title-s3, s1-s2, s1-s3, s2-s3)별 겹침 점수. 문장이 3개가 아니면 빈 결과다.
     */
    static Map<String, Double> scores(String title, List<String> sentences) {
        Map<String, Double> scores = new LinkedHashMap<>();
        if (title == null || sentences == null || sentences.size() != 3) {
            return scores;
        }
        String s1 = sentences.get(0);
        String s2 = sentences.get(1);
        String s3 = sentences.get(2);
        scores.put("title-s2", score(title, s2));
        scores.put("title-s3", score(title, s3));
        scores.put("s1-s2", score(s1, s2));
        scores.put("s1-s3", score(s1, s3));
        scores.put("s2-s3", score(s2, s3));
        return scores;
    }

    /**
     * 두 글자씩 자른 조각 중 겹치는 조각 수를 짧은 쪽 조각 수로 나눈 값(0~1).
     * 짧은 문장이 긴 문장에 통째로 들어 있으면 1이다.
     */
    static double score(String a, String b) {
        Set<String> first = bigrams(normalize(a));
        Set<String> second = bigrams(normalize(b));
        int smaller = Math.min(first.size(), second.size());
        if (smaller == 0) {
            return 0;
        }
        Set<String> common = new HashSet<>(first);
        common.retainAll(second);
        return (double) common.size() / smaller;
    }

    /**
     * 로그 한 줄. 예: "max=0.86(title-s3) title-s2=0.21 title-s3=0.86 s1-s2=0.18 s1-s3=0.40 s2-s3=0.15"
     */
    static String describe(Map<String, Double> scores) {
        if (scores.isEmpty()) {
            return "";
        }
        Map.Entry<String, Double> max = scores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .orElseThrow();
        StringBuilder line = new StringBuilder("max=").append(format(max.getValue()))
            .append('(').append(max.getKey()).append(')');
        scores.forEach((pair, score) -> line.append(' ').append(pair).append('=').append(format(score)));
        return line.toString();
    }

    private static String format(double score) {
        return String.format(Locale.ROOT, "%.2f", score);
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String compact = IGNORABLE.matcher(Normalizer.normalize(text, Normalizer.Form.NFKC)).replaceAll("");
        return POLITE_ENDING.matcher(compact).replaceFirst("");
    }

    private static Set<String> bigrams(String text) {
        int[] codePoints = text.codePoints().toArray();
        Set<String> bigrams = new HashSet<>();
        for (int i = 0; i + 1 < codePoints.length; i++) {
            bigrams.add(new String(codePoints, i, 2));
        }
        return bigrams;
    }
}
