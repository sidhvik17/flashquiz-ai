package com.flashquiz.controller;

import com.flashquiz.model.Deck;
import com.flashquiz.service.FlashcardGenerationException;
import com.flashquiz.service.FlashcardGenerationException.Reason;
import com.flashquiz.service.FlashcardService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class FlashcardController {

    static final int MAX_TOPIC_LENGTH = 2000;

    private static final Logger log = LoggerFactory.getLogger(FlashcardController.class);

    private final FlashcardService flashcardService;

    public FlashcardController(FlashcardService flashcardService) {
        this.flashcardService = flashcardService;
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @PostMapping("/generate")
    public String generateFlashcards(@RequestParam("topicText") String topicText,
                                     @RequestParam(value = "refresh", defaultValue = "false") boolean refresh,
                                     Model model,
                                     HttpServletResponse response) {
        String topic = topicText.strip();
        if (topic.isEmpty()) {
            return formError(model, response, HttpStatus.BAD_REQUEST, topicText, "Enter a topic first.");
        }
        if (topic.length() > MAX_TOPIC_LENGTH) {
            return formError(model, response, HttpStatus.BAD_REQUEST, topicText,
                    "Topic is too long. Keep it under " + MAX_TOPIC_LENGTH + " characters.");
        }

        try {
            Deck deck = flashcardService.generateFlashcards(topic, refresh);
            model.addAttribute("flashcards", deck.cards());
            model.addAttribute("topic", deck.topic());
            model.addAttribute("cached", deck.cached());
            return "result";
        } catch (FlashcardGenerationException e) {
            log.warn("Flashcard generation failed: reason={} detail={}", e.getReason(), e.getMessage());
            return formError(model, response, statusFor(e.getReason()), topicText, messageFor(e.getReason()));
        }
    }

    private static String formError(Model model, HttpServletResponse response, HttpStatus status,
                                    String topicText, String message) {
        response.setStatus(status.value());
        model.addAttribute("topicText", topicText);
        model.addAttribute("error", message);
        return "index";
    }

    private static HttpStatus statusFor(Reason reason) {
        return switch (reason) {
            case NOT_CONFIGURED -> HttpStatus.SERVICE_UNAVAILABLE;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case UPSTREAM_ERROR, BAD_RESPONSE -> HttpStatus.BAD_GATEWAY;
        };
    }

    private static String messageFor(Reason reason) {
        return switch (reason) {
            case NOT_CONFIGURED -> "Flashcard generation is not configured on this server.";
            case TIMEOUT -> "The AI service took too long to respond. Please try again.";
            case UPSTREAM_ERROR -> "The AI service is unavailable right now. Please try again shortly.";
            case BAD_RESPONSE -> "The AI service returned something we could not turn into flashcards. Please try again.";
        };
    }
}
