package com.azentio.clearlist.service;

import com.azentio.clearlist.dto.WatchlistVersionResponse;
import com.azentio.clearlist.entity.WatchlistEntry;
import com.azentio.clearlist.entity.WatchlistVersion;
import com.azentio.clearlist.exception.EmptyWatchlistException;
import com.azentio.clearlist.exception.ResourceNotFoundException;
import com.azentio.clearlist.exception.WatchlistNotLoadedException;
import com.azentio.clearlist.mapper.ScreeningMapper;
import com.azentio.clearlist.repository.WatchlistEntryRepository;
import com.azentio.clearlist.repository.WatchlistVersionRepository;
import com.azentio.clearlist.service.upload.ValidWatchlistEntry;
import com.azentio.clearlist.service.upload.WatchlistFileParser;
import com.azentio.clearlist.service.upload.WatchlistRow;
import com.azentio.clearlist.service.upload.WatchlistRowValidator;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WatchlistService {

    private static final Logger log = LoggerFactory.getLogger(WatchlistService.class);

    private final WatchlistFileParser parser;
    private final WatchlistRowValidator validator;
    private final WatchlistVersionRepository versionRepository;
    private final WatchlistEntryRepository entryRepository;
    private final Clock clock;

    public WatchlistService(WatchlistFileParser parser, WatchlistRowValidator validator,
                            WatchlistVersionRepository versionRepository, WatchlistEntryRepository entryRepository,
                            Clock clock) {
        this.parser = parser;
        this.validator = validator;
        this.versionRepository = versionRepository;
        this.entryRepository = entryRepository;
        this.clock = clock;
    }

    /**
     * Validates the whole file, then stores it as a new immutable version in one transaction. The new version
     * becomes current on commit; if anything fails, nothing is stored and the previous version stays current.
     */
    @Transactional
    public WatchlistVersionResponse upload(String filename, String contentType, byte[] content, String loadedBy) {
        List<WatchlistRow> rows = parser.parse(filename, contentType, content);
        if (rows.isEmpty()) {
            throw new EmptyWatchlistException();
        }
        List<ValidWatchlistEntry> entries = validator.validate(rows);

        WatchlistVersion version = versionRepository.save(
                new WatchlistVersion(filename, entries.size(), Instant.now(clock), loadedBy));
        entryRepository.saveAll(entries.stream()
                .map(e -> new WatchlistEntry(version, e.entryId(), e.primaryName(), e.aliases(), e.dob(),
                        e.nationality(), e.listType()))
                .toList());

        log.info("Loaded watchlist version {} ({} entries) by {}", version.getId(), entries.size(), loadedBy);
        return ScreeningMapper.toVersionResponse(version);
    }

    @Transactional(readOnly = true)
    public WatchlistVersionResponse currentVersion() {
        return versionRepository.findTopByOrderByIdDesc()
                .map(ScreeningMapper::toVersionResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No watchlist version has been loaded"));
    }

    /** The version a screening must use right now. */
    @Transactional(readOnly = true)
    public WatchlistVersion requireCurrentVersion() {
        return versionRepository.findTopByOrderByIdDesc().orElseThrow(WatchlistNotLoadedException::new);
    }
}
