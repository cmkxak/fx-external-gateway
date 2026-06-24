package com.hectofinancial.fxgateway.provider.thunes.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.Map;

// sender(개인) / sendingBusiness(사업자), beneficiary 는 코리도별 요구필드가 달라 Map 으로 유연 처리.
public record TransactionRequest(
        @NotBlank String externalId,
        Map<String, Object> sender,
        Map<String, Object> sendingBusiness,
        CreditPartyIdentifier creditPartyIdentifier,
        Map<String, Object> beneficiary,
        BigDecimal retailFee,
        String retailFeeCurrency,
        String purposeOfRemittance,
        String documentReferenceNumber,
        String callbackUrl
) {}
