package com.azentio.clearlist.service;

import com.azentio.clearlist.dto.PageResponse;
import com.azentio.clearlist.dto.ReviewQueueItem;
import com.azentio.clearlist.entity.HitStatus;
import com.azentio.clearlist.mapper.ScreeningMapper;
import com.azentio.clearlist.repository.ScreeningHitRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HitQueryService {

    /** Newest first; id is a tie-breaker so paging is stable when hits share a timestamp. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final ScreeningHitRepository hitRepository;

    public HitQueryService(ScreeningHitRepository hitRepository) {
        this.hitRepository = hitRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewQueueItem> reviewQueue(HitStatus status, int page, int size) {
        return PageResponse.of(hitRepository.findByStatus(status, PageRequest.of(page, size, NEWEST_FIRST)),
                ScreeningMapper::toReviewQueueItem);
    }
}
