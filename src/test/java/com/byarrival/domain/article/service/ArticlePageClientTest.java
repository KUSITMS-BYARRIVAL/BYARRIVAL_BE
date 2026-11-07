package com.byarrival.domain.article.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.exception.ErrorCode;
import com.byarrival.domain.article.config.ArticleCrawlProperties;
import com.byarrival.domain.article.exception.ArticleErrorCode;
import com.byarrival.domain.article.service.ArticlePageClient.Page;
import com.byarrival.domain.feed.exception.FeedErrorCode;
import com.byarrival.domain.feed.service.FeedUrlValidator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class ArticlePageClientTest {

    private static final String PAGE = "<html>기사</html>";
    private static final int MAX_BYTES = 64;
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; ByArrival/1.0)";

    private HttpServer server;
    private ExecutorService executor;
    private String baseUrl;
    private ArticlePageClient client;
    private final AtomicReference<String> receivedUserAgent = new AtomicReference<>();
    private final AtomicReference<String> receivedContentType = new AtomicReference<>();
    private final AtomicReference<String> receivedBody = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/article", exchange -> {
            receivedUserAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            respond(exchange, 200, PAGE);
        });
        server.createContext("/form", exchange -> {
            receivedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, "POST".equals(exchange.getRequestMethod()) ? 200 : 405, "ok");
        });
        server.createContext("/form-redirect", exchange -> redirect(exchange, "/article"));
        server.createContext("/big", exchange -> respond(exchange, 200, "x".repeat(MAX_BYTES + 1)));
        server.createContext("/error", exchange -> respond(exchange, 403, "forbidden"));
        server.createContext("/redirect", exchange -> redirect(exchange, "/article"));
        server.createContext("/to-blocked", exchange -> redirect(exchange, "http://blocked.test/article"));
        server.createContext("/loop", exchange -> redirect(exchange, "/loop"));
        server.createContext("/no-location", exchange -> respond(exchange, 302, ""));
        server.createContext("/slow", exchange -> {
            sleep(Duration.ofSeconds(1));
            respond(exchange, 200, PAGE);
        });
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        // 로컬 테스트 서버로 붙도록 URL 검사를 대체하고, blocked.test는 내부망 주소로 본다
        FeedUrlValidator validator = mock(FeedUrlValidator.class);
        given(validator.validate(anyString(), anyInt())).willAnswer(invocation -> {
            String url = invocation.getArgument(0);
            int maxLength = invocation.getArgument(1);
            if (url.contains("blocked.test") || url.length() > maxLength) {
                throw new BusinessException(FeedErrorCode.INVALID_FEED_URL);
            }
            return URI.create(url);
        });
        ArticleCrawlProperties properties = new ArticleCrawlProperties(Duration.ofSeconds(1), Duration.ofMillis(300),
            DataSize.ofBytes(MAX_BYTES), 3, 1, 200, 0.5, USER_AGENT);
        client = new ArticlePageClient(validator, properties);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    void 원문_페이지를_받아_온다() {
        Page page = client.get(baseUrl + "/article");

        assertThat(new String(page.body(), StandardCharsets.UTF_8)).isEqualTo(PAGE);
        assertThat(page.url()).isEqualTo(baseUrl + "/article");
    }

    @Test
    void 설정한_User_Agent로_요청한다() {
        client.get(baseUrl + "/article");

        assertThat(receivedUserAgent.get()).isEqualTo(USER_AGENT);
    }

    @Test
    void 리다이렉트는_다시_검사한_뒤_따라가고_최종_주소를_돌려준다() {
        Page page = client.get(baseUrl + "/redirect");

        assertThat(new String(page.body(), StandardCharsets.UTF_8)).isEqualTo(PAGE);
        assertThat(page.url()).isEqualTo(baseUrl + "/article");
    }

    @Test
    void 내부망으로_리다이렉트하면_따라가지_않는다() {
        assertErrorCode(() -> client.get(baseUrl + "/to-blocked"), FeedErrorCode.INVALID_FEED_URL);
    }

    @Test
    void 내부망_주소는_호출하지_않는다() {
        assertErrorCode(() -> client.get("http://blocked.test/article"), FeedErrorCode.INVALID_FEED_URL);
    }

    @Test
    void 기사_링크는_Feed_URL보다_긴_1000자까지_허용한다() {
        String longPath = "/article?q=" + "a".repeat(600);

        assertThat(client.get(baseUrl + longPath).body()).isNotEmpty();
        assertErrorCode(() -> client.get(baseUrl + "/article?q=" + "a".repeat(1000)), FeedErrorCode.INVALID_FEED_URL);
    }

    @Test
    void 리다이렉트가_3회를_넘으면_실패한다() {
        assertErrorCode(() -> client.get(baseUrl + "/loop"), ArticleErrorCode.ARTICLE_CRAWL_FAILED);
    }

    @Test
    void Location_없는_리다이렉트는_실패한다() {
        assertErrorCode(() -> client.get(baseUrl + "/no-location"), ArticleErrorCode.ARTICLE_CRAWL_FAILED);
    }

    @Test
    void 성공이_아닌_응답은_실패한다() {
        assertErrorCode(() -> client.get(baseUrl + "/error"), ArticleErrorCode.ARTICLE_CRAWL_FAILED);
    }

    @Test
    void 응답_크기_상한을_넘으면_실패한다() {
        assertErrorCode(() -> client.get(baseUrl + "/big"), ArticleErrorCode.ARTICLE_CRAWL_FAILED);
    }

    @Test
    void 응답이_read_timeout보다_늦으면_실패한다() {
        assertErrorCode(() -> client.get(baseUrl + "/slow"), ArticleErrorCode.ARTICLE_CRAWL_FAILED);
    }

    @Test
    void 폼을_URL_인코딩해_POST한다() {
        client.postForm(baseUrl + "/form", Map.of("f.req", "[[\"한글 값\",1]]"));

        assertThat(receivedContentType.get()).startsWith("application/x-www-form-urlencoded");
        assertThat(receivedBody.get()).isEqualTo("f.req=%5B%5B%22%ED%95%9C%EA%B8%80+%EA%B0%92%22%2C1%5D%5D");
    }

    @Test
    void POST는_리다이렉트를_따라가지_않는다() {
        assertErrorCode(() -> client.postForm(baseUrl + "/form-redirect", Map.of("a", "b")),
            ArticleErrorCode.ARTICLE_CRAWL_FAILED);
    }

    private void assertErrorCode(Runnable call, ErrorCode errorCode) {
        assertThatThrownBy(call::run)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(errorCode);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        respond(exchange, 302, "");
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
