package com.byarrival.domain.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.feed.config.RssFetchProperties;
import com.byarrival.domain.feed.exception.FeedErrorCode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class RssFeedFetcherTest {

    private static final String FEED_XML = "<rss></rss>";
    private static final int MAX_BYTES = 16;

    private HttpServer server;
    private ExecutorService executor;
    private String baseUrl;
    private RssFeedFetcher fetcher;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/feed", exchange -> respond(exchange, 200, FEED_XML));
        server.createContext("/big", exchange -> respond(exchange, 200, "x".repeat(MAX_BYTES + 1)));
        server.createContext("/error", exchange -> respond(exchange, 500, "error"));
        server.createContext("/redirect", exchange -> redirect(exchange, "/feed"));
        server.createContext("/to-blocked", exchange -> redirect(exchange, "http://blocked.test/feed"));
        server.createContext("/loop", exchange -> redirect(exchange, "/loop"));
        server.createContext("/no-location", exchange -> respond(exchange, 302, ""));
        server.createContext("/slow", exchange -> {
            sleep(Duration.ofSeconds(1));
            respond(exchange, 200, FEED_XML);
        });
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        // 로컬 테스트 서버로 붙도록 DNS 검사만 공개 주소로 대체하고, blocked.test는 내부망으로 해석한다
        FeedUrlValidator validator = new FeedUrlValidator(host -> new InetAddress[] {
            InetAddress.getByName("blocked.test".equals(host) ? "10.0.0.1" : "203.0.113.10")
        });
        RssFetchProperties properties = new RssFetchProperties(
            Duration.ofSeconds(1), Duration.ofMillis(300), DataSize.ofBytes(MAX_BYTES), 3, 3);
        fetcher = new RssFeedFetcher(validator, properties);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    void Feed_원문을_받아_온다() {
        byte[] content = fetcher.fetch(baseUrl + "/feed");

        assertThat(new String(content, StandardCharsets.UTF_8)).isEqualTo(FEED_XML);
    }

    @Test
    void 리다이렉트는_다시_검사한_뒤_따라간다() {
        byte[] content = fetcher.fetch(baseUrl + "/redirect");

        assertThat(new String(content, StandardCharsets.UTF_8)).isEqualTo(FEED_XML);
    }

    @Test
    void 내부망으로_리다이렉트하면_따라가지_않는다() {
        assertErrorCode(() -> fetcher.fetch(baseUrl + "/to-blocked"), FeedErrorCode.INVALID_FEED_URL);
    }

    @Test
    void 리다이렉트가_3회를_넘으면_실패한다() {
        assertErrorCode(() -> fetcher.fetch(baseUrl + "/loop"), FeedErrorCode.FEED_FETCH_FAILED);
    }

    @Test
    void Location_없는_리다이렉트는_실패한다() {
        assertErrorCode(() -> fetcher.fetch(baseUrl + "/no-location"), FeedErrorCode.FEED_FETCH_FAILED);
    }

    @Test
    void 성공이_아닌_응답은_실패한다() {
        assertErrorCode(() -> fetcher.fetch(baseUrl + "/error"), FeedErrorCode.FEED_FETCH_FAILED);
    }

    @Test
    void 응답_크기_상한을_넘으면_실패한다() {
        assertErrorCode(() -> fetcher.fetch(baseUrl + "/big"), FeedErrorCode.FEED_FETCH_FAILED);
    }

    @Test
    void 응답이_read_timeout보다_늦으면_실패한다() {
        assertErrorCode(() -> fetcher.fetch(baseUrl + "/slow"), FeedErrorCode.FEED_FETCH_FAILED);
    }

    @Test
    void 내부망_주소_Feed는_호출하지_않는다() {
        assertErrorCode(() -> fetcher.fetch("http://blocked.test/feed"), FeedErrorCode.INVALID_FEED_URL);
    }

    private void assertErrorCode(Runnable call, FeedErrorCode errorCode) {
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
