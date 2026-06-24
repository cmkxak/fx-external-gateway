package com.hectofinancial.fxgateway.provider.thunes.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record Money(
        BigDecimal amount,             // 조건부(mode별 필수) — QuotationRequest 에서 검증
        @NotBlank String currency,     // source/destination 공통 필수
        String countryIsoCode          // source 만 필수 — QuotationRequest 에서 검증
) {}
