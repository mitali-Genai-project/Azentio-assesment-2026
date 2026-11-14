package com.azentio.clearlist.service.upload;

import com.azentio.clearlist.dto.ErrorResponse.Detail;
import com.azentio.clearlist.entity.ListType;
import com.azentio.clearlist.exception.WatchlistValidationException;
import com.azentio.clearlist.matcher.NameNormalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Validates every row and reports all problems together (capped), so an operator can fix a file in one pass.
 * The upload is all-or-nothing: any invalid row rejects the whole file.
 */
@Component
public class WatchlistRowValidator {

    static final int MAX_REPORTED_ERRORS = 100;
    private static final Pattern ENTRY_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,64}$");
    private static final Pattern COUNTRY = Pattern.compile("^[A-Z]{2}$");
    private static final int MAX_NAME_LENGTH = 255;

    private final NameNormalizer normalizer;
    private final Clock clock;

    public WatchlistRowValidator(NameNormalizer normalizer, Clock clock) {
        this.normalizer = normalizer;
        this.clock = clock;
    }

    public List<ValidWatchlistEntry> validate(List<WatchlistRow> rows) {
        List<Detail> errors = new ArrayList<>();
        List<ValidWatchlistEntry> valid = new ArrayList<>(rows.size());
        Set<String> seenIds = new HashSet<>();

        for (WatchlistRow row : rows) {
            int errorsBefore = errors.size();
            String prefix = "row " + row.rowNumber() + ": ";

            if (row.entryId() == null) {
                errors.add(new Detail(prefix + "entryId", "is required"));
            } else if (!ENTRY_ID.matcher(row.entryId()).matches()) {
                errors.add(new Detail(prefix + "entryId", "must be 1-64 characters of letters, digits, . _ : -"));
            } else if (!seenIds.add(row.entryId())) {
                errors.add(new Detail(prefix + "entryId", "duplicate entryId '" + row.entryId() + "'"));
            }

            validateName(row.primaryName(), prefix + "primaryName", true, errors);
            for (String alias : row.aliases()) {
                validateName(alias, prefix + "aliases", false, errors);
            }

            LocalDate dob = parseDob(row.dob(), prefix, errors);

            String nationality = row.nationality() == null ? null : row.nationality().toUpperCase(Locale.ROOT);
            if (nationality != null && !COUNTRY.matcher(nationality).matches()) {
                errors.add(new Detail(prefix + "nationality", "must be a 2-letter ISO country code"));
            }

            ListType listType = parseListType(row.listType(), prefix, errors);

            if (errors.size() == errorsBefore) {
                valid.add(new ValidWatchlistEntry(row.entryId(), row.primaryName(), row.aliases(), dob,
                        nationality, listType));
            }
        }

        if (!errors.isEmpty()) {
            List<Detail> reported = errors.size() > MAX_REPORTED_ERRORS ? errors.subList(0, MAX_REPORTED_ERRORS) : errors;
            throw new WatchlistValidationException(
                    errors.size() + " invalid value(s) found; no entries were stored", reported);
        }
        return valid;
    }

    private void validateName(String name, String location, boolean required, List<Detail> errors) {
        if (name == null) {
            if (required) {
                errors.add(new Detail(location, "is required"));
            }
        } else if (name.length() > MAX_NAME_LENGTH) {
            errors.add(new Detail(location, "must be at most " + MAX_NAME_LENGTH + " characters"));
        } else if (normalizer.normalize(name).isEmpty()) {
            errors.add(new Detail(location, "'" + name + "' contains no matchable name tokens"));
        }
    }

    private LocalDate parseDob(String raw, String prefix, List<Detail> errors) {
        if (raw == null) {
            return null;
        }
        try {
            LocalDate dob = LocalDate.parse(raw);
            if (dob.isAfter(LocalDate.now(clock))) {
                errors.add(new Detail(prefix + "dob", "must not be in the future"));
            }
            return dob;
        } catch (DateTimeParseException e) {
            errors.add(new Detail(prefix + "dob", "must be an ISO date (yyyy-MM-dd)"));
            return null;
        }
    }

    private static ListType parseListType(String raw, String prefix, List<Detail> errors) {
        if (raw == null) {
            errors.add(new Detail(prefix + "listType", "is required"));
            return null;
        }
        try {
            return ListType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            errors.add(new Detail(prefix + "listType", "must be one of SANCTIONS, PEP"));
            return null;
        }
    }
}
