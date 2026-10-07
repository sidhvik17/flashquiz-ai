package com.flashquiz.service;

import com.flashquiz.model.Flashcard;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class FlashcardParserTest {

    @Test
    void parsesPlainQuestionAnswerLines() {
        List<Flashcard> cards = FlashcardParser.parse("""
                Q: What is a JVM?
                A: The Java Virtual Machine.

                Q: What does JIT stand for?
                A: Just-in-time compilation.
                """);

        assertThat(cards).extracting(Flashcard::getQuestion, Flashcard::getAnswer).containsExactly(
                tuple("What is a JVM?", "The Java Virtual Machine."),
                tuple("What does JIT stand for?", "Just-in-time compilation."));
    }

    @Test
    void acceptsNumberedAndMarkdownVariants() {
        List<Flashcard> cards = FlashcardParser.parse(String.join("\r\n",
                "1. Q: numbered",
                "   A: one",
                "Q2: suffixed",
                "A2: two",
                "**Q:** bold label",
                "**A:** three",
                "**Q: bold line**",
                "**A: four**",
                "Question 5: spelled out",
                "Answer 5: five",
                "- q: lower case",
                "- a: six"));

        assertThat(cards).extracting(Flashcard::getQuestion).containsExactly(
                "numbered", "suffixed", "bold label", "bold line", "spelled out", "lower case");
        assertThat(cards).extracting(Flashcard::getAnswer).containsExactly(
                "one", "two", "three", "four", "five", "six");
    }

    @Test
    void ignoresAnswerWithoutQuestionAndQuestionWithoutAnswer() {
        List<Flashcard> cards = FlashcardParser.parse("""
                A: orphan answer
                Q: first question, never answered
                Q: second question
                A: its answer
                A: second answer to the same question
                Q: trailing question
                """);

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).getQuestion()).isEqualTo("second question");
        assertThat(cards.get(0).getAnswer()).isEqualTo("its answer");
    }

    @Test
    void returnsEmptyListWhenNothingMatches() {
        assertThat(FlashcardParser.parse("Sorry, I cannot help with that.\nAn apple a day: keeps the doctor away."))
                .isEmpty();
    }
}
