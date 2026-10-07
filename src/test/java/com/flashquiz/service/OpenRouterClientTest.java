package com.flashquiz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashquiz.config.OpenRouterProperties;
import com.flashquiz.service.FlashcardGenerationException.Reason;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** Runs the client against a real local HTTP server so timeouts and status handling are exercised end to end. */
class OpenRouterClientTest {

    private static final String JSON = "application/json";
    private static final Duration READ_TIMEOUT = Duration.ofMillis(300);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();

    private HttpServer server;
    private ExecutorService executor;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    void returnsMessageContentAndSendsKeyModelAndTopic() throws Exception {
        respondWith(200, JSON, "{\"choices\":[{\"message\":{\"content\":\"Q: a\\nA: b\"}}]}", Duration.ZERO);
        String topic = "He said \"hi\"\nsecond line \\ back\\slash";

        String content = client("test-key").complete(topic);

        assertThat(content).isEqualTo("Q: a\nA: b");
        assertThat(lastAuthorization.get()).isEqualTo("Bearer test-key");
        JsonNode sent = objectMapper.readTree(lastBody.get());
        assertThat(sent.path("model").asText()).isEqualTo("test-model");
        assertThat(sent.path("messages").path(1).path("role").asText()).isEqualTo("user");
        assertThat(sent.path("messages").path(1).path("content").asText()).endsWith(topic);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 500, 503})
    void errorStatusIsAnUpstreamError(int status) {
        respondWith(status, JSON, "{\"error\":{\"message\":\"nope\"}}", Duration.ZERO);

        assertThat(failure(client("test-key")).getReason()).isEqualTo(Reason.UPSTREAM_ERROR);
    }

    @Test
    void malformedJsonIsABadResponse() {
        respondWith(200, JSON, "{\"choices\":[{\"mess", Duration.ZERO);

        assertThat(failure(client("test-key")).getReason()).isEqualTo(Reason.BAD_RESPONSE);
    }

    @Test
    void nonJsonBodyIsABadResponse() {
        respondWith(200, "text/html", "<html>gateway page</html>", Duration.ZERO);

        assertThat(failure(client("test-key")).getReason()).isEqualTo(Reason.BAD_RESPONSE);
    }

    @Test
    void replyWithoutChoicesIsABadResponse() {
        respondWith(200, JSON, "{\"choices\":[]}", Duration.ZERO);

        assertThat(failure(client("test-key")).getReason()).isEqualTo(Reason.BAD_RESPONSE);
    }

    @Test
    void slowUpstreamHitsTheReadTimeout() {
        Duration upstreamDelay = Duration.ofSeconds(5);
        respondWith(200, JSON, "{\"choices\":[{\"message\":{\"content\":\"Q: a\\nA: b\"}}]}", upstreamDelay);

        long start = System.nanoTime();
        FlashcardGenerationException failure = failure(client("test-key"));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(failure.getReason()).isEqualTo(Reason.TIMEOUT);
        assertThat(elapsed).isGreaterThanOrEqualTo(READ_TIMEOUT).isLessThan(upstreamDelay);
    }

    @Test
    void unreachableUpstreamIsAnUpstreamError() {
        OpenRouterClient client = client("test-key");
        server.stop(0);

        assertThat(failure(client).getReason()).isEqualTo(Reason.UPSTREAM_ERROR);
    }

    @Test
    void missingApiKeyFailsWithoutCallingUpstream() {
        respondWith(200, JSON, "{}", Duration.ZERO);

        assertThat(failure(client("  ")).getReason()).isEqualTo(Reason.NOT_CONFIGURED);
        assertThat(requests).hasValue(0);
    }

    private OpenRouterClient client(String apiKey) {
        OpenRouterProperties properties = new OpenRouterProperties(
                apiKey,
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "test-model",
                Duration.ofSeconds(2),
                READ_TIMEOUT);
        return new OpenRouterClient(new RestTemplateBuilder(), objectMapper, properties);
    }

    private static FlashcardGenerationException failure(OpenRouterClient client) {
        return catchThrowableOfType(() -> client.complete("java"), FlashcardGenerationException.class);
    }

    private void respondWith(int status, String contentType, String body, Duration delay) {
        server.createContext("/chat/completions", exchange -> {
            requests.incrementAndGet();
            lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException e) {
                exchange.close();
                return;
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }
}
