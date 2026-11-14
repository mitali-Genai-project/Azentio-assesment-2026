package com.azentio.clearlist.dto;

import java.time.Instant;

public record WatchlistVersionResponse(long versionId, int entryCount, Instant loadedAt, String sourceFilename) {
}
