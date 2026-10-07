package com.flashquiz.service;

import com.flashquiz.model.Flashcard;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class FlashcardParser {

    // The prompt asks for "Q:" and "A:" lines, but models also send "1. Q:", "Q1:",
    // "**Q:**" or "Question 2:". The prefix class absorbs numbering and markdown.
    private static final Pattern QUESTION = linePattern("Q|Question");
    private static final Pattern ANSWER = linePattern("A|Answer");

    private FlashcardParser() {}

    static List<Flashcard> parse(String content) {
        List<Flashcard> flashcards = new ArrayList<>();
        String question = null;
        for (String line : content.split("\\R")) {
            Matcher questionLine = QUESTION.matcher(line);
            if (questionLine.matches()) {
                question = text(questionLine);
                continue;
            }
            Matcher answerLine = ANSWER.matcher(line);
            if (answerLine.matches() && question != null) {
                flashcards.add(new Flashcard(question, text(answerLine)));
                question = null;
            }
        }
        return flashcards;
    }

    private static Pattern linePattern(String label) {
        return Pattern.compile(
                "^[\\s\\d.)*#-]*(?:" + label + ")\\s*\\d*\\s*\\**\\s*:\\s*\\**\\s*(.+)$",
                Pattern.CASE_INSENSITIVE);
    }

    private static String text(Matcher line) {
        return line.group(1).replaceAll("[\\s*]+$", "");
    }
}
