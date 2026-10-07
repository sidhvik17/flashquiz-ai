package com.flashquiz.model;

import jakarta.persistence.*;

@Entity
@Table(name = "flashcards", indexes = @Index(name = "idx_flashcards_topic_key", columnList = "topic_key"))
public class Flashcard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // SHA-256 of the normalized topic; a deck is every card sharing one key
    @Column(name = "topic_key", nullable = false, length = 64)
    private String topicKey;

    @Column(nullable = false, length = 2000)
    private String question;

    @Column(nullable = false, length = 2000)
    private String answer;

    public Flashcard() {}

    public Flashcard(String question, String answer) {
        this.question = question;
        this.answer = answer;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTopicKey() { return topicKey; }
    public void setTopicKey(String topicKey) { this.topicKey = topicKey; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
}
