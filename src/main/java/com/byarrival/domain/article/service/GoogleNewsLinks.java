package com.byarrival.domain.article.service;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

/**
 * 구글 뉴스 링크와 RSS description에서 기사 ID를 꺼낸다.
 * 구글 뉴스 RSS의 description에는 같은 사건을 다룬 다른 언론사 기사 링크가 함께 들어 있다.
 */
final class GoogleNewsLinks {

    private static final String GOOGLE_NEWS_HOST = "news.google.com";
    private static final Pattern ARTICLE_PATH = Pattern.compile("^/(?:rss/)?articles/([A-Za-z0-9_-]+)$");

    private GoogleNewsLinks() {
    }

    static Optional<String> articleId(String link) {
        if (link == null) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = URI.create(link.strip());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (!GOOGLE_NEWS_HOST.equalsIgnoreCase(uri.getHost()) || uri.getPath() == null) {
            return Optional.empty();
        }
        Matcher matcher = ARTICLE_PATH.matcher(uri.getPath());
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    /**
     * @return description에 링크된 구글 뉴스 기사 ID. description이 없거나 링크가 없으면 빈 집합
     */
    static Set<String> relatedArticleIds(String description) {
        Set<String> ids = new LinkedHashSet<>();
        if (description == null || description.isBlank()) {
            return ids;
        }
        for (Element anchor : Jsoup.parseBodyFragment(description).select("a[href]")) {
            articleId(anchor.attr("href")).ifPresent(ids::add);
        }
        return ids;
    }
}
