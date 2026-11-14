package com.azentio.clearlist.service.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.azentio.clearlist.dto.ErrorResponse.Detail;
import com.azentio.clearlist.entity.ListType;
import com.azentio.clearlist.exception.UnsupportedUploadException;
import com.azentio.clearlist.exception.WatchlistValidationException;
import com.azentio.clearlist.matcher.NameNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class WatchlistUploadParsingTest {

    private static final String HEADER = "entryId,primaryName,aliases,dob,nationality,listType\n";

    private final WatchlistFileParser parser = new WatchlistFileParser(new ObjectMapper());
    private final WatchlistRowValidator validator = new WatchlistRowValidator(new NameNormalizer(),
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void parsesCsvWithQuotedFieldsAndAliasList() {
        String csv = HEADER + "WL-1,\"PETROV, Aleksandr\",Sasha Petrov|A. Petrov,1980-01-01,ru,sanctions\n";

        List<ValidWatchlistEntry> entries = validator.validate(parser.parse("list.csv", null, bytes(csv)));

        assertThat(entries).singleElement().satisfies(e -> {
            assertThat(e.primaryName()).isEqualTo("PETROV, Aleksandr");
            assertThat(e.aliases()).containsExactly("Sasha Petrov", "A. Petrov");
            assertThat(e.dob()).isEqualTo(LocalDate.of(1980, 1, 1));
            assertThat(e.nationality()).isEqualTo("RU");
            assertThat(e.listType()).isEqualTo(ListType.SANCTIONS);
        });
    }

    @Test
    void parsesJsonArray() {
        String json = """
                [{"entryId":"WL-1","primaryName":"Viktor Orlov","aliases":["V. Orlov"],"listType":"PEP"},
                 {"entryId":"WL-2","primaryName":"Ivan Petrov","dob":null,"listType":"SANCTIONS"}]
                """;

        List<ValidWatchlistEntry> entries = validator.validate(parser.parse("list.json", null, bytes(json)));

        assertThat(entries).extracting(ValidWatchlistEntry::entryId).containsExactly("WL-1", "WL-2");
        assertThat(entries.get(0).aliases()).containsExactly("V. Orlov");
    }

    @Test
    void reportsEveryInvalidRowTogether() {
        String csv = HEADER
                + ",Missing Id,,,,SANCTIONS\n"
                + "WL-2,,,,,PEP\n"
                + "WL-3,Bad Date,,1980-13-45,,PEP\n"
                + "WL-4,Bad Type,,,,TERRORIST\n"
                + "WL-5,Bad Country,,,RUS,PEP\n"
                + "WL-6,Future Dob,,2030-01-01,,PEP\n"
                + "WL-7,Valid Name,,,,PEP\n"
                + "WL-7,Duplicate Id,,,,PEP\n";

        assertThatThrownBy(() -> validator.validate(parser.parse("list.csv", null, bytes(csv))))
                .isInstanceOf(WatchlistValidationException.class)
                .satisfies(ex -> assertThat(((WatchlistValidationException) ex).getDetails())
                        .extracting(Detail::location)
                        .containsExactly("row 1: entryId", "row 2: primaryName", "row 3: dob",
                                "row 4: listType", "row 5: nationality", "row 6: dob", "row 8: entryId"));
    }

    @Test
    void rejectsCsvMissingRequiredColumns() {
        assertThatThrownBy(() -> parser.parse("list.csv", null, bytes("entryId,name\nWL-1,Ivan Petrov\n")))
                .isInstanceOf(WatchlistValidationException.class)
                .hasMessageContaining("missing required columns");
    }

    @Test
    void rejectsJsonThatIsNotAnArray() {
        assertThatThrownBy(() -> parser.parse("list.json", null, bytes("{\"entryId\":\"WL-1\"}")))
                .isInstanceOf(WatchlistValidationException.class);
    }

    @Test
    void rejectsUnknownFileType() {
        assertThatThrownBy(() -> parser.parse("list.xlsx", "application/octet-stream", bytes("x")))
                .isInstanceOf(UnsupportedUploadException.class);
    }

    @Test
    void headerOnlyFileYieldsNoRows() {
        assertThat(parser.parse("list.csv", null, bytes(HEADER))).isEmpty();
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
