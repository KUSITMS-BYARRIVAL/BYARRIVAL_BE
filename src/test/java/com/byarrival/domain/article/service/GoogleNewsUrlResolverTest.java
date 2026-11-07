package com.byarrival.domain.article.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.article.exception.ArticleErrorCode;
import com.byarrival.domain.article.service.ArticlePageClient.Page;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class GoogleNewsUrlResolverTest {

    private static final String ARTICLE_ID = "CBMiAbc_def-123";
    private static final String GOOGLE_LINK = "https://news.google.com/rss/articles/" + ARTICLE_ID + "?oc=5";
    private static final String BATCH_EXECUTE_URL = "https://news.google.com/_/DotsSplashUi/data/batchexecute";
    private static final String SIGNED_PAGE = "<html><body><c-wiz><div jscontroller=\"x\" data-n-a-sg=\"SIG\" "
        + "data-n-a-ts=\"1762500000\" data-n-a-id=\"" + ARTICLE_ID + "\"></div></c-wiz></body></html>";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private ArticlePageClient pageClient;
    private GoogleNewsUrlResolver resolver;

    @BeforeEach
    void setUp() {
        pageClient = mock(ArticlePageClient.class);
        resolver = new GoogleNewsUrlResolver(pageClient, jsonMapper);
    }

    @Test
    void 구글_뉴스_링크는_서명값으로_원문_주소를_받아_온다() {
        givenSignedPage();
        given(pageClient.postForm(eq(BATCH_EXECUTE_URL), anyMap()))
            .willReturn(page(")]}'\n\n[[\"wrb.fr\",\"Fbv4je\",\"[\\\"garturlres\\\",\\\"https://press.example.com/1\\\",1]\","
                + "null,null,null,\"generic\"],[\"di\",14]]"));

        assertThat(resolver.resolve(GOOGLE_LINK)).isEqualTo("https://press.example.com/1");
    }

    @Test
    void batchexecute에_기사_ID와_서명값을_보낸다() {
        givenSignedPage();
        given(pageClient.postForm(eq(BATCH_EXECUTE_URL), anyMap()))
            .willReturn(page(")]}'\n\n[[\"wrb.fr\",\"Fbv4je\",\"[\\\"garturlres\\\",\\\"https://press.example.com/1\\\",1]\"]]"));

        resolver.resolve(GOOGLE_LINK);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> form = ArgumentCaptor.forClass(Map.class);
        verify(pageClient).postForm(eq(BATCH_EXECUTE_URL), form.capture());
        JsonNode rpc = jsonMapper.readTree(form.getValue().get("f.req")).path(0).path(0);
        JsonNode request = jsonMapper.readTree(rpc.path(1).asString());
        assertThat(rpc.path(0).asString()).isEqualTo("Fbv4je");
        assertThat(request.path(2).asString()).isEqualTo(ARTICLE_ID);
        assertThat(request.path(3).asLong()).isEqualTo(1762500000L);
        assertThat(request.path(4).asString()).isEqualTo("SIG");
    }

    @Test
    void 구글_뉴스_기사_링크가_아니면_그대로_돌려준다() {
        assertThat(resolver.resolve("https://press.example.com/1")).isEqualTo("https://press.example.com/1");
        assertThat(resolver.resolve("https://news.google.com/topics/abc")).isEqualTo("https://news.google.com/topics/abc");
        verify(pageClient, never()).get(anyString());
    }

    @Test
    void 기사_페이지에_서명값이_없으면_실패한다() {
        given(pageClient.get(anyString())).willReturn(page("<html><body>no signature</body></html>"));

        assertCrawlFailed(GOOGLE_LINK);
        verify(pageClient, never()).postForm(anyString(), any());
    }

    @Test
    void 응답에_원문_주소가_없으면_실패한다() {
        givenSignedPage();
        given(pageClient.postForm(eq(BATCH_EXECUTE_URL), anyMap()))
            .willReturn(page(")]}'\n\n[[\"wrb.fr\",\"Fbv4je\",null,null,null,[3],\"generic\"]]"));

        assertCrawlFailed(GOOGLE_LINK);
    }

    @Test
    void 원문_주소가_http가_아니면_실패한다() {
        givenSignedPage();
        given(pageClient.postForm(eq(BATCH_EXECUTE_URL), anyMap()))
            .willReturn(page(")]}'\n\n[[\"wrb.fr\",\"Fbv4je\",\"[\\\"garturlres\\\",\\\"javascript:alert(1)\\\",1]\"]]"));

        assertCrawlFailed(GOOGLE_LINK);
    }

    private void givenSignedPage() {
        given(pageClient.get("https://news.google.com/rss/articles/" + ARTICLE_ID)).willReturn(page(SIGNED_PAGE));
    }

    private void assertCrawlFailed(String link) {
        assertThatThrownBy(() -> resolver.resolve(link))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ArticleErrorCode.ARTICLE_CRAWL_FAILED);
    }

    private Page page(String body) {
        return new Page("https://news.google.com/x", body.getBytes(StandardCharsets.UTF_8));
    }
}
