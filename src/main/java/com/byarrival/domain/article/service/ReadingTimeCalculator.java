package com.byarrival.domain.article.service;

import com.byarrival.domain.article.config.ArticleReadingProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 정제 본문 글자수(공백 포함) ÷ 읽기 속도로 예상 읽기시간(분)을 계산한다.
 */
@Component
@RequiredArgsConstructor
public class ReadingTimeCalculator {

    // article.reading_minutes NUMERIC(7, 3)
    private static final int SCALE = 3;

    private final ArticleReadingProperties properties;

    // 반올림하지 않고 소수 셋째 자리 아래를 버린다. 화면 표시용 올림은 조회하는 쪽에서 한다.
    public BigDecimal calculate(String cleanedContent) {
        int characters = cleanedContent.codePointCount(0, cleanedContent.length());
        return BigDecimal.valueOf(characters)
            .divide(BigDecimal.valueOf(properties.charsPerMinute()), SCALE, RoundingMode.DOWN);
    }
}
