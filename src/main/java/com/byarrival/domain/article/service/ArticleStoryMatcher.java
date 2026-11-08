package com.byarrival.domain.article.service;

import com.byarrival.domain.article.config.ArticleSelectionProperties;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 두 기사가 같은 사건을 다룬 기사인지 판단한다. 다른 언론사가 쓴 같은 사건도 같은 사건으로 본다.
 * <ul>
 *   <li>구글 뉴스가 같은 사건으로 묶은 기사(RSS description의 기사 링크)가 겹치면 같은 사건이다.</li>
 *   <li>구글이 묶지 않았어도 제목이 비슷하면(글자 2-gram Dice 계수가 기준 이상) 같은 사건이다.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ArticleStoryMatcher {

    private final ArticleSelectionProperties properties;

    /**
     * @param link        RSS 기사 링크
     * @param description RSS description. 구글 뉴스는 같은 사건의 기사 링크 목록이 들어 있다.
     */
    public Story storyOf(String link, String description, String title) {
        Set<String> keys = new HashSet<>(GoogleNewsLinks.relatedArticleIds(description));
        keys.add(GoogleNewsLinks.articleId(link).orElse(link));
        return new Story(Set.copyOf(keys), titleBigrams(title));
    }

    public boolean isSameStory(Story a, Story b) {
        if (a.keys().stream().anyMatch(b.keys()::contains)) {
            return true;
        }
        return dice(a.titleBigrams(), b.titleBigrams()) >= properties.similarTitleThreshold();
    }

    // [단독]·(종합) 같은 말머리를 빼고 한글·영문·숫자만 남겨 글자 2개씩 자른다.
    // 美·年 같은 한자 약어는 언론사마다 쓰는 방식이 달라 비교에서 뺀다.
    static Set<String> titleBigrams(String title) {
        String normalized = title.replaceAll("\\[[^]]*]|\\([^)]*\\)", " ")
            .replaceAll("[^가-힣A-Za-z0-9]", "")
            .toLowerCase();
        Set<String> bigrams = new HashSet<>();
        for (int i = 0; i + 2 <= normalized.length(); i++) {
            bigrams.add(normalized.substring(i, i + 2));
        }
        return Set.copyOf(bigrams);
    }

    static double dice(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        long common = a.stream().filter(b::contains).count();
        return 2.0 * common / (a.size() + b.size());
    }

    /**
     * @param keys         구글 뉴스 기사 ID(구글 링크가 아니면 링크). 같은 사건으로 묶인 기사의 ID를 포함한다.
     * @param titleBigrams 정규화한 제목의 글자 2-gram
     */
    public record Story(Set<String> keys, Set<String> titleBigrams) {
    }
}
