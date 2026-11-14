package com.azentio.clearlist.dto;

import com.azentio.clearlist.entity.HitStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DispositionRequest(
        @NotNull(message = "toStatus is required") HitStatus toStatus,
        @NotBlank(message = "reason is required") @Size(max = 1000) String reason) {
}
