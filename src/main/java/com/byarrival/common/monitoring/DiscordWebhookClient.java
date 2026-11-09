package com.byarrival.common.monitoring;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 디스코드 웹훅으로 메시지를 보낸다. 전송은 별도 스레드에서 하며, 실패해도 호출한 쪽에 예외를 넘기지 않는다.
 */
@Slf4j
@Component
public class DiscordWebhookClient implements DisposableBean {

    static final int MAX_CONTENT_LENGTH = 2000;
    private static final int QUEUE_CAPACITY = 100;
    private static final String ELLIPSIS = "\n…(생략)";

    private final RestClient restClient;
    private final ThreadPoolExecutor executor;

    public DiscordWebhookClient(MonitoringProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.discord().connectTimeout());
        requestFactory.setReadTimeout(properties.discord().readTimeout());
        this.restClient = RestClient.builder()
            .requestFactory(requestFactory)
            .build();
        // 알림이 몰려도 메모리를 잡아먹지 않도록 대기열을 제한하고, 넘치면 버린다
        this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY), runnable -> {
                Thread thread = new Thread(runnable, "discord-webhook");
                thread.setDaemon(true);
                return thread;
            });
    }

    public void send(String webhookUrl, String content) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }
        String body = truncate(content);
        try {
            executor.execute(() -> post(webhookUrl, body));
        } catch (RejectedExecutionException e) {
            log.warn("[Discord] 알림 대기열이 가득 차 알림 1건을 버림");
        }
    }

    static String truncate(String content) {
        if (content.length() <= MAX_CONTENT_LENGTH) {
            return content;
        }
        return content.substring(0, MAX_CONTENT_LENGTH - ELLIPSIS.length()) + ELLIPSIS;
    }

    private void post(String webhookUrl, String content) {
        try {
            restClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                // 메시지에 들어간 @everyone 같은 문자열로 멘션이 가지 않게 막는다
                .body(Map.of("content", content, "allowed_mentions", Map.of("parse", List.of())))
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.warn("[Discord] 알림 전송 실패 - status={}", e.getStatusCode().value());
        } catch (RestClientException e) {
            // 예외 메시지에 웹훅 토큰이 든 URL이 포함되므로 종류만 남긴다
            log.warn("[Discord] 알림 전송 실패 - {}", e.getClass().getSimpleName());
        }
    }

    @Override
    public void destroy() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }
}
