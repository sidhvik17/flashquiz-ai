package com.flashquiz.service;

import com.flashquiz.model.Flashcard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Service
public class FlashcardService {

    @Value("${OPENAI_API_KEY}")
    private String openaiApiKey;

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://api.openai.com/v1/chat/completions")
            .defaultHeader("Authorization", "Bearer " + openaiApiKey)
            .defaultHeader("Content-Type", "application/json")
            .build();

    public List<Flashcard> generateFlashcards(String inputText) {
        System.out.println("🔁 Called generateFlashcards()");
        System.out.println("Input Text: " + inputText);
        System.out.println("Using API key? " + (openaiApiKey != null && !openaiApiKey.isBlank()));

        String prompt = "Generate 5 high-quality flashcards from the following topic:\n\n"
                + inputText + "\n\nFormat each flashcard as:\nQ: question\nA: answer";

        String requestBody = "{\n" +
                "  \"model\": \"gpt-3.5-turbo\",\n" +
                "  \"messages\": [\n" +
                "    {\"role\": \"system\", \"content\": \"You are a helpful assistant that generates flashcards.\"},\n" +
                "    {\"role\": \"user\", \"content\": \"" + prompt.replace("\"", "\\\"") + "\"}\n" +
                "  ]\n" +
                "}";

        String response = webClient.post()
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(ex -> {
                    System.err.println("❌ OpenAI API error: " + ex.getMessage());
                    return Mono.just("{\"choices\":[]}");
                })
                .block();

        System.out.println("⬅️ OpenAI Response: " + response);

        List<Flashcard> flashcards = new ArrayList<>();

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);
            String content = root.path("choices").get(0).path("message").path("content").asText();

            String[] lines = content.split("\\n");
            String question = null;
            for (String line : lines) {
                if (line.trim().startsWith("Q:")) {
                    question = line.substring(2).trim();
                } else if (line.trim().startsWith("A:") && question != null) {
                    String answer = line.substring(2).trim();
                    flashcards.add(new Flashcard(question, answer));
                    question = null;
                }
            }

            System.out.println("✅ Parsed Flashcards: " + flashcards.size());

        } catch (Exception e) {
            System.err.println("❌ JSON Parsing failed: " + e.getMessage());
        }

        return flashcards;
    }
}
