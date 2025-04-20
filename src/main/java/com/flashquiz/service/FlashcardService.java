package com.flashquiz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashquiz.model.Flashcard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Service
public class FlashcardService {

    @Value("${HUGGINGFACE_API_KEY}")
    private String huggingfaceApiKey;

    private WebClient buildWebClient() {
        return WebClient.builder()
                .baseUrl("https://api-inference.huggingface.co/models/bigscience/bloomz-560m")
                .defaultHeader("Authorization", "Bearer " + huggingfaceApiKey)
                .build();
    }


    public List<Flashcard> generateFlashcards(String inputText) {
        List<Flashcard> flashcards = new ArrayList<>();

        System.out.println("🔁 Called generateFlashcards()");
        System.out.println("Input Text: " + inputText);
        System.out.println("Using API key? " + (huggingfaceApiKey != null && !huggingfaceApiKey.isBlank()));

        String prompt = "Generate 5 flashcard Q&A pairs on the topic: " + inputText +
                ". Each should start with 'Q:' and the answer with 'A:'. Format:\nQ: ...\nA: ...";

        String requestBody = "{ \"inputs\": \"" + prompt.replace("\"", "\\\"").replace("\n", "\\n") + "\" }";
        System.out.println("➡️ Prompt Sent: " + prompt);

        String response = buildWebClient().post()
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(status -> status.isError(), clientResponse ->
                        clientResponse.bodyToMono(String.class).flatMap(errorBody -> {
                            System.err.println("❌ Error from HuggingFace API: " + errorBody);
                            return Mono.error(new RuntimeException("API error: " + errorBody));
                        })
                )
                .bodyToMono(String.class)
                .onErrorResume(ex -> {
                    System.err.println("❌ HuggingFace API error: " + ex.getMessage());
                    return Mono.just("{\"generations\":[]}");
                })
                .block();


        System.out.println("⬅️ HuggingFace Response: " + response);

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode root = objectMapper.readTree(response);

            if (root.isArray() && root.size() > 0) {
                String text = root.get(0).path("generated_text").asText();

                String[] lines = text.split("\\n");
                String question = null;

                for (String line : lines) {
                    if (line.startsWith("Q:")) {
                        question = line.substring(2).trim();
                    } else if (line.startsWith("A:") && question != null) {
                        String answer = line.substring(2).trim();
                        flashcards.add(new Flashcard(question, answer));
                        question = null;
                    }
                }

                System.out.println("✅ Parsed Flashcards: " + flashcards.size());
            } else {
                System.err.println("❌ No valid generation received.");
            }
        } catch (Exception e) {
            System.err.println("❌ JSON Parsing failed: " + e.getMessage());
        }

        System.out.println("✅ Flashcards Generated: " + flashcards.size());
        return flashcards;
    }
}
