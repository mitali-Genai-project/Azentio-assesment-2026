package com.azentio.clearlist.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.azentio.clearlist.entity.Decision;
import com.azentio.clearlist.entity.HitStatus;
import com.azentio.clearlist.entity.Screening;
import com.azentio.clearlist.entity.ScreeningHit;
import com.azentio.clearlist.repository.ScreeningHitRepository;
import com.azentio.clearlist.repository.ScreeningRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * End-to-end API behaviour on H2. The database is shared across tests in this context, so every test
 * uploads its own watchlist first (making it current) and asserts only on data it created.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "supervisor", roles = "SUPERVISOR") // security rules are tested in DispositionAndSecurityApiTest
class WatchlistAndScreeningApiTest {

    private static final String HEADER = "entryId,primaryName,aliases,dob,nationality,listType\n";
    private static final String PETROV_LIST = HEADER
            + "WL-1,Aleksandr Ivanovich Petrov,Sasha Petrov|Alexander Petrov,1980-01-01,RU,SANCTIONS\n"
            + "WL-2,Viktor Orlov,,1975-05-05,RU,PEP\n";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ScreeningRepository screeningRepository;
    @Autowired
    private ScreeningHitRepository hitRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    // --- Watchlist versions -------------------------------------------------------------------

    @Test
    void uploadCreatesVersionThatBecomesCurrent() throws Exception {
        long versionId = uploadCsv(PETROV_LIST);

        mockMvc.perform(get("/api/watchlists/versions/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionId").value(versionId))
                .andExpect(jsonPath("$.entryCount").value(2))
                .andExpect(jsonPath("$.loadedAt").isNotEmpty());
    }

    @Test
    void uploadAcceptsJson() throws Exception {
        String json = "[{\"entryId\":\"J-1\",\"primaryName\":\"Ivan Sokolov\",\"listType\":\"PEP\"}]";
        mockMvc.perform(multipart("/api/watchlists/versions")
                        .file(new MockMultipartFile("file", "list.json", "application/json", bytes(json))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entryCount").value(1));
    }

    @Test
    void malformedRowsAreRejectedWithDetailsAndPreviousVersionStaysCurrent() throws Exception {
        long before = uploadCsv(PETROV_LIST);

        mockMvc.perform(multipart("/api/watchlists/versions").file(csvFile(HEADER
                        + "WL-1,Valid Name,,,,PEP\n"
                        + "WL-2,,,not-a-date,,UNKNOWN\n")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_WATCHLIST"))
                .andExpect(jsonPath("$.details[*].location", hasItem("row 2: primaryName")))
                .andExpect(jsonPath("$.details[*].location", hasItem("row 2: dob")))
                .andExpect(jsonPath("$.details[*].location", hasItem("row 2: listType")));

        mockMvc.perform(get("/api/watchlists/versions/current"))
                .andExpect(jsonPath("$.versionId").value(before));
    }

    @Test
    void emptyUploadIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/watchlists/versions").file(csvFile(HEADER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("EMPTY_WATCHLIST"));
    }

    @Test
    void unsupportedFileTypeIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/watchlists/versions")
                        .file(new MockMultipartFile("file", "list.txt", "text/plain", bytes("hello"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("UNSUPPORTED_FILE_TYPE"));
    }

    @Test
    void uploadWithoutFilePartIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/watchlists/versions"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));
    }

    // --- Screening ------------------------------------------------------------------------------

    @Test
    void potentialMatchIsPersistedWithHitsAndExactVersion() throws Exception {
        long versionId = uploadCsv(PETROV_LIST);

        // full three-part name, so the primary name scores higher than the two-part aliases
        JsonNode response = screen("CUST-PM", "Mr. PETROV, Aleksandr Ivanovich", "1980-01-01", "RU");

        assertThat(response.get("listVersion").asLong()).isEqualTo(versionId);
        assertThat(response.get("decision").asText()).isEqualTo("POTENTIAL_MATCH");
        JsonNode top = response.get("topHits").get(0);
        assertThat(top.get("entryId").asText()).isEqualTo("WL-1");
        assertThat(top.get("matchedName").asText()).isEqualTo("Aleksandr Ivanovich Petrov");
        assertThat(top.get("matchedOn").asText()).isEqualTo("primary");
        assertThat(top.get("score").asDouble()).isBetween(0.85, 1.0);

        UUID screeningId = UUID.fromString(response.get("screeningId").asText());
        transactionTemplate.executeWithoutResult(tx -> {
            Screening saved = screeningRepository.findById(screeningId).orElseThrow();
            assertThat(saved.getWatchlistVersion().getId()).isEqualTo(versionId);
            assertThat(saved.getDecision()).isEqualTo(Decision.POTENTIAL_MATCH);
            assertThat(saved.getCustomerRef()).isEqualTo("CUST-PM");
            List<ScreeningHit> hits = hitRepository.findByScreeningId(screeningId);
            assertThat(hits).singleElement().satisfies(hit -> {
                assertThat(hit.getStatus()).isEqualTo(HitStatus.OPEN);
                assertThat(hit.getWatchlistEntry().getEntryRef()).isEqualTo("WL-1");
            });
        });
    }

    @Test
    void aliasMatchIsReportedAsAlias() throws Exception {
        uploadCsv(PETROV_LIST);

        JsonNode top = screen("CUST-AL", "Sasha Petrov", null, null).get("topHits").get(0);

        assertThat(top.get("matchedOn").asText()).isEqualTo("alias");
        assertThat(top.get("matchedName").asText()).isEqualTo("Sasha Petrov");
    }

    @Test
    void clearScreeningIsPersistedWithoutHits() throws Exception {
        long versionId = uploadCsv(PETROV_LIST);

        JsonNode response = screen("CUST-CLR", "Ivan Petrov", null, null);

        assertThat(response.get("decision").asText()).isEqualTo("CLEAR");
        assertThat(response.get("topHits")).isEmpty();
        UUID screeningId = UUID.fromString(response.get("screeningId").asText());
        assertThat(screeningRepository.findById(screeningId)).isPresent();
        assertThat(hitRepository.findByScreeningId(screeningId)).isEmpty();
        assertThat(response.get("listVersion").asLong()).isEqualTo(versionId);
    }

    @Test
    void newVersionIsUsedBySubsequentScreenings() throws Exception {
        long v1 = uploadCsv(PETROV_LIST);
        JsonNode first = screen("CUST-V", "Aleksandr Petrov", null, null);

        long v2 = uploadCsv(HEADER + "WL-9,Viktor Orlov,,,RU,PEP\n"); // Petrov removed from the list
        JsonNode second = screen("CUST-V", "Aleksandr Petrov", null, null);

        assertThat(v2).isGreaterThan(v1);
        assertThat(first.get("listVersion").asLong()).isEqualTo(v1);
        assertThat(first.get("decision").asText()).isEqualTo("POTENTIAL_MATCH");
        assertThat(second.get("listVersion").asLong()).isEqualTo(v2);
        assertThat(second.get("decision").asText()).isEqualTo("CLEAR");

        // the earlier screening still points at the version it actually used
        UUID firstId = UUID.fromString(first.get("screeningId").asText());
        transactionTemplate.executeWithoutResult(tx -> assertThat(
                screeningRepository.findById(firstId).orElseThrow().getWatchlistVersion().getId()).isEqualTo(v1));
    }

    @Test
    void missingCustomerNameIsRejected() throws Exception {
        mockMvc.perform(post("/api/screenings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerRef\":\"CUST-1\",\"fullName\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].location").value("fullName"));
    }

    @Test
    void invalidFieldsAreRejected() throws Exception {
        mockMvc.perform(post("/api/screenings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerRef\":\"CUST-1\",\"fullName\":\"Ivan Petrov\",\"nationality\":\"Russia\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].location").value("nationality"));
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        mockMvc.perform(post("/api/screenings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerRef\":\"CUST-1\",\"dob\":\"yesterday\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));
    }

    @Test
    void nameWithOnlyHonorificIsRejected() throws Exception {
        uploadCsv(PETROV_LIST);
        mockMvc.perform(post("/api/screenings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerRef\":\"CUST-1\",\"fullName\":\"Mr.\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_NAME"));
    }

    // --- Review queue ---------------------------------------------------------------------------

    @Test
    void reviewQueueReturnsOpenHitsNewestFirstWithContext() throws Exception {
        long versionId = uploadCsv(PETROV_LIST);
        String olderRef = "CUST-Q-" + UUID.randomUUID();
        String newerRef = "CUST-Q-" + UUID.randomUUID();
        screen(olderRef, "Aleksandr Petrov", null, null);
        Thread.sleep(5); // distinct created_at timestamps
        screen(newerRef, "Viktor Orlov", null, null);

        JsonNode page = json(mockMvc.perform(get("/api/hits").param("status", "OPEN").param("page", "0")
                .param("size", "100")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        List<JsonNode> items = new ArrayList<>();
        page.get("content").forEach(items::add);
        assertThat(items).extracting(i -> Instant.parse(i.get("createdAt").asText()))
                .isSortedAccordingTo(Comparator.reverseOrder());
        List<String> refs = items.stream().map(i -> i.get("customerRef").asText()).toList();
        assertThat(refs.indexOf(newerRef)).isNotNegative().isLessThan(refs.indexOf(olderRef));

        JsonNode newer = items.get(refs.indexOf(newerRef));
        assertThat(newer.get("entryId").asText()).isEqualTo("WL-2");
        assertThat(newer.get("listVersion").asLong()).isEqualTo(versionId);
        assertThat(newer.get("status").asText()).isEqualTo("OPEN");
        assertThat(newer.get("score").asDouble()).isEqualTo(1.0);
        assertThat(page.get("size").asInt()).isEqualTo(100);
    }

    @Test
    void reviewQueueRejectsInvalidParameters() throws Exception {
        mockMvc.perform(get("/api/hits").param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));
        mockMvc.perform(get("/api/hits").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));
        mockMvc.perform(get("/api/hits").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));
    }

    // --- helpers --------------------------------------------------------------------------------

    private long uploadCsv(String csv) throws Exception {
        String body = mockMvc.perform(multipart("/api/watchlists/versions").file(csvFile(csv)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json(body).get("versionId").asLong();
    }

    private JsonNode screen(String customerRef, String fullName, String dob, String nationality) throws Exception {
        var request = new java.util.LinkedHashMap<String, String>();
        request.put("customerRef", customerRef);
        request.put("fullName", fullName);
        request.put("dob", dob);
        request.put("nationality", nationality);
        String body = mockMvc.perform(post("/api/screenings").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json(body);
    }

    private static MockMultipartFile csvFile(String csv) {
        return new MockMultipartFile("file", "watchlist.csv", "text/csv", bytes(csv));
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
