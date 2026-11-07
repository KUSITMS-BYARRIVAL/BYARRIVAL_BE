package com.byarrival.domain.feed.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RSS Item을 언론사와 상관없이 같은 구조로 바꾼 정규화 기사 데이터
 *
 * @param guid        GUID, 없으면 null
 * @param link        원문 URL
 * @param title       제목
 * @param content     content:encoded, 없으면 description. 둘 다 없으면 null
 * @param publishedAt 발행 시각 (KST)
 * @param categories  RSS category 목록, 나온 순서대로
 * @param source      원래 기사를 낸 언론사(RSS source). 구글 뉴스처럼 여러 언론사를 모은 Feed에 있다. 없으면 null
 */
public record RssItem(
    String guid,
    String link,
    String title,
    String content,
    LocalDateTime publishedAt,
    List<String> categories,
    String source
) {

    public RssItem {
        categories = List.copyOf(categories);
    }
}
