package com.azentio.clearlist.controller;

import com.azentio.clearlist.audit.AuditService;
import com.azentio.clearlist.dto.AuditRecordResponse;
import com.azentio.clearlist.dto.DispositionRequest;
import com.azentio.clearlist.dto.HitResponse;
import com.azentio.clearlist.dto.PageResponse;
import com.azentio.clearlist.dto.ReviewQueueItem;
import com.azentio.clearlist.entity.HitStatus;
import com.azentio.clearlist.security.Role;
import com.azentio.clearlist.service.DispositionService;
import com.azentio.clearlist.service.HitQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/hits")
public class HitController {

    private final HitQueryService hitQueryService;
    private final DispositionService dispositionService;
    private final AuditService auditService;

    public HitController(HitQueryService hitQueryService, DispositionService dispositionService,
                         AuditService auditService) {
        this.hitQueryService = hitQueryService;
        this.dispositionService = dispositionService;
        this.auditService = auditService;
    }

    /** Review queue, newest first. */
    @GetMapping
    public PageResponse<ReviewQueueItem> reviewQueue(
            @RequestParam(defaultValue = "OPEN") HitStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return hitQueryService.reviewQueue(status, page, size);
    }

    @PostMapping("/{id}/disposition")
    public HitResponse dispose(@PathVariable UUID id, @Valid @RequestBody DispositionRequest request,
                               Authentication authentication) {
        return dispositionService.dispose(id, request, authentication.getName(), Role.of(authentication));
    }

    /** Disposition history, oldest first. */
    @GetMapping("/{id}/audit")
    public List<AuditRecordResponse> audit(@PathVariable UUID id) {
        return auditService.history(id);
    }
}
