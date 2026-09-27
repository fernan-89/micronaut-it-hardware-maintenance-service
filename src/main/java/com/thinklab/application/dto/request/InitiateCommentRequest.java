package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO for adding a Comment to a WorkOrder (BIAN Behavior Qualifier: {@code comment/initiate}).
 *
 * <p>{@code internal} is a request from an OPERATOR/ADMIN caller only - the use case forces it to
 * {@code false} whenever the caller's role is {@code REQUESTER}, so a requester can never author a
 * note that gets hidden from their own future reads.
 */
@Serdeable
public record InitiateCommentRequest(
        @NotBlank(message = "Text is required")
        String text,

        boolean internal
) {}
