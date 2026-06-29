package com.hectofinancial.fxgateway.provider.thunes.dto.common;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record Destination(
        @NotBlank String currency,
        BigDecimal amount
) {
}
