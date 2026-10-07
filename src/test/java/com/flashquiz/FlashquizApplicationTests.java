package com.flashquiz;

import com.flashquiz.service.OpenRouterClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Whole application on an in-memory database; only the outbound model call is replaced. */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
class FlashquizApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OpenRouterClient client;

    @Test
    void repeatTopicIsAnsweredFromTheDatabaseWithoutASecondModelCall() throws Exception {
        when(client.complete(anyString())).thenReturn("Q: What is HTTP?\nA: A protocol.");

        mockMvc.perform(post("/generate").param("topicText", "HTTP basics"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("What is HTTP?")));

        mockMvc.perform(post("/generate").param("topicText", "http  basics"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("What is HTTP?")))
                .andExpect(content().string(containsString("saved deck")));

        verify(client, times(1)).complete(anyString());
    }
}
