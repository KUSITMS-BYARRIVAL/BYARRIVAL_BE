package com.byarrival.domain.article.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.article.exception.ArticleErrorCode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 구글 뉴스 기사 링크에서 언론사 원문 주소를 알아낸다. 구글 뉴스 링크는 원문으로 리다이렉트되지 않아서,
 * 기사 페이지의 서명값으로 구글 뉴스 웹이 쓰는 batchexecute를 호출한다. 공개 API가 아니므로 구글이 바꾸면 실패할 수 있다.
 */
@Component
@RequiredArgsConstructor
public class GoogleNewsUrlResolver {

    private static final String BATCH_EXECUTE_URL = "https://news.google.com/_/DotsSplashUi/data/batchexecute";
    private static final String RPC_ID = "Fbv4je";

    private final ArticlePageClient pageClient;
    private final JsonMapper jsonMapper;

    /**
     * @return 원문 주소. 구글 뉴스 기사 링크가 아니면 받은 링크를 그대로 돌려준다.
     */
    public String resolve(String link) {
        String articleId = GoogleNewsLinks.articleId(link).orElse(null);
        if (articleId == null) {
            return link;
        }
        Element signed = Jsoup.parse(new String(
                pageClient.get("https://news.google.com/rss/articles/" + articleId).body(), StandardCharsets.UTF_8))
            .selectFirst("[data-n-a-sg][data-n-a-ts]");
        if (signed == null) {
            throw failed("구글 뉴스 기사 페이지에서 서명값을 찾지 못했습니다.");
        }
        String response = new String(pageClient.postForm(BATCH_EXECUTE_URL,
            Map.of("f.req", batchRequest(articleId, signed.attr("data-n-a-ts"), signed.attr("data-n-a-sg")))).body(),
            StandardCharsets.UTF_8);
        return parseSourceUrl(response);
    }

    private String batchRequest(String articleId, String timestamp, String signature) {
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw failed("구글 뉴스 기사 페이지의 타임스탬프 형식이 올바르지 않습니다.");
        }
        // JSON null 자리가 있어 null을 받는 Arrays.asList를 쓴다
        List<Object> options = Arrays.asList("X", "X", List.of("X", "X"), null, null, 1, 1, "US:en", null, 1, null, null,
            null, null, null, 0, 1);
        List<Object> client = Arrays.asList(options, "X", "X", 1, List.of(1, 1, 1), 1, 1, null, 0, 0, null, 0);
        List<Object> inner = List.of("garturlreq", client, articleId, ts, signature);
        List<Object> outer = List.of(List.of(Arrays.asList(RPC_ID, jsonMapper.writeValueAsString(inner), null, "generic")));
        return jsonMapper.writeValueAsString(outer);
    }

    // 응답 형식: )]}'\n\n[["wrb.fr","Fbv4je","[\"garturlres\",\"<원문 주소>\",1]",...]]
    private String parseSourceUrl(String response) {
        int start = response.indexOf('[');
        if (start < 0) {
            throw failed("구글 뉴스 응답 형식이 올바르지 않습니다.");
        }
        try {
            JsonNode payload = jsonMapper.readTree(response.substring(start)).path(0).path(2);
            String url = jsonMapper.readTree(payload.asString()).path(1).asString();
            URI uri = URI.create(url);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw failed("구글 뉴스가 돌려준 원문 주소가 http(s)가 아닙니다.");
            }
            return url;
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw failed("구글 뉴스 응답에서 원문 주소를 찾지 못했습니다.");
        }
    }

    private static BusinessException failed(String message) {
        return new BusinessException(ArticleErrorCode.ARTICLE_CRAWL_FAILED, message);
    }
}
