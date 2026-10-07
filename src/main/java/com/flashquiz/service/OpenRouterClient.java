package com.flashquiz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flashquiz.config.OpenRouterProperties;
import com.flashquiz.service.FlashcardGenerationException.Reason;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;

@Component
public class OpenRouterClient {

    private static final String SYSTEM_PROMPT = "You are a helpful flashcard generator.";
    private static final String USER_PROMPT = "Generate 15 flashcards (Q: and A:) about the topic: %s";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final OpenRouterProperties properties;

    public OpenRouterClient(RestTemplateBuilder builder, ObjectMapper objectMapper, OpenRouterProperties properties) {
        this.restTemplate = builder
                .rootUri(properties.baseUrl())
                .setConnectTimeout(properties.connectTimeout())
                .setReadTimeout(properties.readTimeout())
                .build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /** Asks the model for flashcards on the topic and returns the text of its reply. */
    public String complete(String topic) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new FlashcardGenerationException(Reason.NOT_CONFIGURED, "OPENROUTER_API_KEY is not set");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.apiKey());

        JsonNode response;
        try {
            response = restTemplate.postForObject(
                    "/chat/completions", new HttpEntity<>(requestBody(topic), headers), JsonNode.class);
        } catch (HttpStatusCodeException e) {
            throw new FlashcardGenerationException(
                    Reason.UPSTREAM_ERROR, "OpenRouter returned HTTP " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new FlashcardGenerationException(reasonFor(e), "OpenRouter request failed: " + e.getMessage(), e);
        }

        String content = response == null
                ? ""
                : response.path("choices").path(0).path("message").path("content").asText("");
        if (content.isBlank()) {
            throw new FlashcardGenerationException(Reason.BAD_RESPONSE, "OpenRouter reply had no message content");
        }
        return content;
    }

    private ObjectNode requestBody(String topic) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.model());
        body.put("temperature", 0.7);
        body.putArray("messages")
                .add(message("system", SYSTEM_PROMPT))
                .add(message("user", USER_PROMPT.formatted(topic)));
        return body;
    }

    private ObjectNode message(String role, String content) {
        return objectMapper.createObjectNode().put("role", role).put("content", content);
    }

    private static Reason reasonFor(RestClientException e) {
        // A read timeout surfaces as ResourceAccessException while waiting for headers
        // and as a plain RestClientException while reading the body, so check the cause.
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException) {
                return Reason.TIMEOUT;
            }
        }
        return e instanceof ResourceAccessException ? Reason.UPSTREAM_ERROR : Reason.BAD_RESPONSE;
    }
}
