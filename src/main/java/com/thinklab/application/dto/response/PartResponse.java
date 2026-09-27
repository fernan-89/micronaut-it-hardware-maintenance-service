package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record PartResponse(UUID partId, String description, int quantity) {}
