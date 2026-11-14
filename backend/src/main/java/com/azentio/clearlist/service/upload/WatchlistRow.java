package com.azentio.clearlist.service.upload;

import java.util.List;

/** One raw, not-yet-validated row from an uploaded file. rowNumber is 1-based over data rows. */
public record WatchlistRow(
        int rowNumber,
        String entryId,
        String primaryName,
        List<String> aliases,
        String dob,
        String nationality,
        String listType) {
}
