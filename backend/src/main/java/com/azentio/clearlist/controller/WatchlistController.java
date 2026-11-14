package com.azentio.clearlist.controller;

import com.azentio.clearlist.dto.WatchlistVersionResponse;
import com.azentio.clearlist.service.WatchlistService;
import java.io.IOException;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/watchlists/versions")
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    /** Multipart upload: {@code curl -F file=@watchlist.csv}. Accepts .csv or .json. SUPERVISOR only. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public WatchlistVersionResponse upload(@RequestPart("file") MultipartFile file, Principal principal)
            throws IOException {
        return watchlistService.upload(file.getOriginalFilename(), file.getContentType(), file.getBytes(),
                principal.getName());
    }

    @GetMapping("/current")
    public WatchlistVersionResponse current() {
        return watchlistService.currentVersion();
    }
}
