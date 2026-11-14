package com.azentio.clearlist.service.upload;

import com.azentio.clearlist.entity.ListType;
import java.time.LocalDate;
import java.util.List;

/** A row that passed validation, with typed fields. */
public record ValidWatchlistEntry(
        String entryId,
        String primaryName,
        List<String> aliases,
        LocalDate dob,
        String nationality,
        ListType listType) {
}
