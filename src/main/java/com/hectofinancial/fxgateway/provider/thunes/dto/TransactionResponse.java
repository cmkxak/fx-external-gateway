package com.hectofinancial.fxgateway.provider.thunes.dto;

public record TransactionResponse(
        Long id,
        String externalId,
        String status,
        Object creditPartyIdentifier
) {}
