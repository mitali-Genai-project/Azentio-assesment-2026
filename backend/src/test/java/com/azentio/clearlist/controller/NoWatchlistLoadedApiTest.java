package com.azentio.clearlist.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Uses its own empty in-memory database so no watchlist has been loaded. */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:empty;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "ANALYST")
class NoWatchlistLoadedApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void screeningWithoutAnyWatchlistIsRejected() throws Exception {
        mockMvc.perform(post("/api/screenings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerRef\":\"CUST-1\",\"fullName\":\"Aleksandr Petrov\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("WATCHLIST_NOT_LOADED"));
    }

    @Test
    void currentVersionIsNotFoundBeforeFirstUpload() throws Exception {
        mockMvc.perform(get("/api/watchlists/versions/current"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }
}
