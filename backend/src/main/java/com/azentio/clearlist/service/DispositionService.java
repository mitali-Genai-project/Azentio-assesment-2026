package com.azentio.clearlist.service;

import com.azentio.clearlist.audit.AuditService;
import com.azentio.clearlist.dto.DispositionRequest;
import com.azentio.clearlist.dto.HitResponse;
import com.azentio.clearlist.entity.HitStatus;
import com.azentio.clearlist.entity.ScreeningHit;
import com.azentio.clearlist.exception.ResourceNotFoundException;
import com.azentio.clearlist.mapper.ScreeningMapper;
import com.azentio.clearlist.repository.ScreeningHitRepository;
import com.azentio.clearlist.security.Role;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DispositionService {

    private static final Logger log = LoggerFactory.getLogger(DispositionService.class);

    private final ScreeningHitRepository hitRepository;
    private final HitTransitionPolicy policy;
    private final AuditService auditService;

    public DispositionService(ScreeningHitRepository hitRepository, HitTransitionPolicy policy,
                              AuditService auditService) {
        this.hitRepository = hitRepository;
        this.policy = policy;
        this.auditService = auditService;
    }

    /**
     * Applies a disposition and appends its audit record in one transaction. The hit's optimistic-lock
     * version makes a concurrent disposition of the same hit fail with 409 instead of silently overwriting.
     */
    @Transactional
    public HitResponse dispose(UUID hitId, DispositionRequest request, String actor, Set<Role> actorRoles) {
        ScreeningHit hit = hitRepository.findById(hitId)
                .orElseThrow(() -> new ResourceNotFoundException("Hit " + hitId + " not found"));
        HitStatus from = hit.getStatus();
        Role authorisedAs = policy.authorise(actorRoles, from, request.toStatus());

        hit.transitionTo(request.toStatus());
        hitRepository.saveAndFlush(hit); // surface optimistic-lock conflicts here, inside the transaction
        auditService.recordDisposition(hitId, from, request.toStatus(), actor, authorisedAs, request.reason().trim());

        log.info("Hit {} moved {} -> {} by {} ({})", hitId, from, request.toStatus(), actor, authorisedAs);
        return ScreeningMapper.toHitResponse(hit);
    }
}
