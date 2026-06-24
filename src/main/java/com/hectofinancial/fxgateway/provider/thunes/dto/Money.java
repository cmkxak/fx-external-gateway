package com.hectofinancial.fxgateway.provider.thunes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record Money(
        BigDecimal amount,                       // 조건부(mode별 필수) — QuotationRequest 에서 검증
        @NotBlank @Size(max = 3) String currency, // source/destination 공통 필수, ISO 4217
        @Size(max = 3) String countryIsoCode      // source 만 필수 — QuotationRequest 에서 검증, ISO 3166-1 alpha-3
) {}
