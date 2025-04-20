package com.flashquiz.controller;

import com.flashquiz.service.FlashcardService;
import com.flashquiz.model.Flashcard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
public class FlashcardController {

    @Autowired
    private FlashcardService flashcardService;

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @PostMapping("/generate")
    public String generateFlashcards(@RequestParam("topicText") String topicText, Model model) {
        List<Flashcard> flashcards = flashcardService.generateFlashcards(topicText);
        model.addAttribute("flashcards", flashcards);
        return "result";
    }
}
