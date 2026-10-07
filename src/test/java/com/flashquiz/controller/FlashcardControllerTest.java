package com.flashquiz.controller;

import com.flashquiz.model.Deck;
import com.flashquiz.model.Flashcard;
import com.flashquiz.service.FlashcardGenerationException;
import com.flashquiz.service.FlashcardGenerationException.Reason;
import com.flashquiz.service.FlashcardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(FlashcardController.class)
class FlashcardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FlashcardService flashcardService;

    @Test
    void homeShowsTheForm() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(not(containsString("role=\"alert\""))));
    }

    @Test
    void generateRendersTheDeck() throws Exception {
        when(flashcardService.generateFlashcards("java", false))
                .thenReturn(new Deck("java", List.of(new Flashcard("What is <b>JIT</b>?", "A compiler")), false));

        mockMvc.perform(post("/generate").param("topicText", "  java  "))
                .andExpect(status().isOk())
                .andExpect(view().name("result"))
                .andExpect(content().string(containsString("What is &lt;b&gt;JIT&lt;/b&gt;?")))
                .andExpect(content().string(containsString("A compiler")))
                .andExpect(content().string(not(containsString("saved deck"))));
    }

    @Test
    void storedDeckIsLabelledAndRegenerateSendsRefresh() throws Exception {
        when(flashcardService.generateFlashcards("java", false))
                .thenReturn(new Deck("java", List.of(new Flashcard("q", "a")), true));
        when(flashcardService.generateFlashcards("java", true))
                .thenReturn(new Deck("java", List.of(new Flashcard("q2", "a2")), false));

        mockMvc.perform(post("/generate").param("topicText", "java"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("saved deck")))
                .andExpect(content().string(containsString("name=\"refresh\" value=\"true\"")));

        mockMvc.perform(post("/generate").param("topicText", "java").param("refresh", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("q2")));
        verify(flashcardService).generateFlashcards("java", true);
    }

    @Test
    void blankTopicIsRejectedWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/generate").param("topicText", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("index"))
                .andExpect(content().string(containsString("Enter a topic first.")));
        verifyNoInteractions(flashcardService);
    }

    @Test
    void overlongTopicIsRejectedWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/generate").param("topicText", "x".repeat(FlashcardController.MAX_TOPIC_LENGTH + 1)))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("index"))
                .andExpect(content().string(containsString("Topic is too long")));
        verifyNoInteractions(flashcardService);
    }

    @ParameterizedTest
    @CsvSource({
            "NOT_CONFIGURED, 503, not configured",
            "TIMEOUT, 504, took too long",
            "UPSTREAM_ERROR, 502, unavailable right now",
            "BAD_RESPONSE, 502, could not turn into flashcards"
    })
    void generationFailureShowsTheFormWithAMessageAndKeepsTheTopic(Reason reason, int status, String message)
            throws Exception {
        when(flashcardService.generateFlashcards(anyString(), anyBoolean()))
                .thenThrow(new FlashcardGenerationException(reason, "internal detail: sk-secret"));

        mockMvc.perform(post("/generate").param("topicText", "java generics"))
                .andExpect(status().is(status))
                .andExpect(view().name("index"))
                .andExpect(content().string(containsString(message)))
                .andExpect(content().string(containsString("java generics")))
                .andExpect(content().string(not(containsString("internal detail"))));
    }
}
