package com.azentio.clearlist.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The six assessment scenarios plus audit trail and authentication, using real HTTP Basic credentials
 * against the configured in-memory users (test-profile passwords).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DispositionAndSecurityApiTest {

    private static final RequestPostProcessor ANALYST = httpBasic("analyst", "analyst-test-only");
    private static final RequestPostProcessor SUPERVISOR = httpBasic("supervisor", "supervisor-test-only");
    private static final String LIST = "entryId,primaryName,aliases,dob,nationality,listType\n"
            + "WL-1,Aleksandr Petrov,,1980-01-01,RU,SANCTIONS\n";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private UUID openHitId;

    @BeforeEach
    void createOpenHit() throws Exception {
        mockMvc.perform(multipart("/api/watchlists/versions").file(csv(LIST)).with(SUPERVISOR))
                .andExpect(status().isCreated());
        JsonNode screening = json(mockMvc.perform(post("/api/screenings").with(ANALYST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerRef\":\"CUST-D\",\"fullName\":\"Aleksandr Petrov\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        openHitId = UUID.fromString(screening.get("topHits").get(0).get("hitId").asText());
    }

    // --- the six assessment scenarios -----------------------------------------------------------

    @Test
    void scenario1_analystEscalatesOpenHit_200() throws Exception {
        dispose(openHitId, ANALYST, "ESCALATED", "Requires supervisor review")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ESCALATED"));
    }

    @Test
    void scenario2_analystConfirmsHit_403() throws Exception {
        dispose(openHitId, ANALYST, "ESCALATED", "Needs review").andExpect(status().isOk());

        dispose(openHitId, ANALYST, "CONFIRMED_MATCH", "Looks like the same person")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("TRANSITION_NOT_PERMITTED"));
    }

    @Test
    void scenario3_supervisorConfirmsEscalatedHit_200() throws Exception {
        dispose(openHitId, ANALYST, "ESCALATED", "Needs review").andExpect(status().isOk());

        dispose(openHitId, SUPERVISOR, "CONFIRMED_MATCH", "DOB and nationality agree")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED_MATCH"));
    }

    @Test
    void scenario4_supervisorConfirmsOpenHit_409() throws Exception {
        dispose(openHitId, SUPERVISOR, "CONFIRMED_MATCH", "Skipping escalation")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_TRANSITION"));
    }

    @Test
    void scenario5_blankReason_400() throws Exception {
        dispose(openHitId, ANALYST, "ESCALATED", "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].location").value("reason"));
    }

    @Test
    void scenario6_analystUploadsWatchlist_403() throws Exception {
        mockMvc.perform(multipart("/api/watchlists/versions").file(csv(LIST)).with(ANALYST))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    // --- audit trail ----------------------------------------------------------------------------

    @Test
    void successfulDispositionsAreAuditedInChronologicalOrder() throws Exception {
        dispose(openHitId, ANALYST, "ESCALATED", "Requires supervisor review").andExpect(status().isOk());
        dispose(openHitId, SUPERVISOR, "FALSE_POSITIVE", "Different date of birth").andExpect(status().isOk());

        mockMvc.perform(get("/api/hits/{id}/audit", openHitId).with(ANALYST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].hitId").value(openHitId.toString()))
                .andExpect(jsonPath("$[0].fromStatus").value("OPEN"))
                .andExpect(jsonPath("$[0].toStatus").value("ESCALATED"))
                .andExpect(jsonPath("$[0].actor").value("analyst"))
                .andExpect(jsonPath("$[0].role").value("ANALYST"))
                .andExpect(jsonPath("$[0].reason").value("Requires supervisor review"))
                .andExpect(jsonPath("$[0].timestamp").isNotEmpty())
                .andExpect(jsonPath("$[1].fromStatus").value("ESCALATED"))
                .andExpect(jsonPath("$[1].toStatus").value("FALSE_POSITIVE"))
                .andExpect(jsonPath("$[1].actor").value("supervisor"))
                .andExpect(jsonPath("$[1].role").value("SUPERVISOR"));
    }

    @Test
    void rejectedDispositionsAreNotAudited() throws Exception {
        dispose(openHitId, SUPERVISOR, "CONFIRMED_MATCH", "Skipping escalation").andExpect(status().isConflict());
        dispose(openHitId, ANALYST, "ESCALATED", " ").andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/hits/{id}/audit", openHitId).with(ANALYST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void escalatedHitLeavesOpenQueueAndAppearsInEscalatedQueue() throws Exception {
        dispose(openHitId, ANALYST, "ESCALATED", "Needs review").andExpect(status().isOk());

        String escalated = mockMvc.perform(get("/api/hits").param("status", "ESCALATED").param("size", "100")
                .with(SUPERVISOR)).andReturn().getResponse().getContentAsString();
        String open = mockMvc.perform(get("/api/hits").param("status", "OPEN").param("size", "100")
                .with(SUPERVISOR)).andReturn().getResponse().getContentAsString();
        assertThat(escalated).contains(openHitId.toString());
        assertThat(open).doesNotContain(openHitId.toString());
    }

    // --- authentication and not-found -----------------------------------------------------------

    @Test
    void unauthenticatedRequestIsRejectedWithJson401() throws Exception {
        mockMvc.perform(get("/api/hits"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mockMvc.perform(get("/api/hits").with(httpBasic("analyst", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownHitIs404() throws Exception {
        dispose(UUID.randomUUID(), ANALYST, "ESCALATED", "reason").andExpect(status().isNotFound());
        mockMvc.perform(get("/api/hits/{id}/audit", UUID.randomUUID()).with(ANALYST))
                .andExpect(status().isNotFound());
    }

    @Test
    void malformedHitIdIs400() throws Exception {
        mockMvc.perform(get("/api/hits/{id}/audit", "not-a-uuid").with(ANALYST))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));
    }

    private ResultActions dispose(UUID hitId, RequestPostProcessor user, String toStatus, String reason)
            throws Exception {
        return mockMvc.perform(post("/api/hits/{id}/disposition", hitId).with(user)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("toStatus", toStatus, "reason", reason))));
    }

    private static MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "watchlist.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
