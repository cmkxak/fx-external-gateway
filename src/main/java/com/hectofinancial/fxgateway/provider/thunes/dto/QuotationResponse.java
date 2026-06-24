package com.hectofinancial.fxgateway.provider.thunes.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record QuotationResponse(
        Long id,
        String externalId,
        Payer payer,
        String mode,
        String transactionType,
        Money source,
        Money destination,
        Amount sentAmount,
        BigDecimal wholesaleFxRate,
        Amount fee,
        LocalDateTime creationDate,
        LocalDateTime expirationDate
) {}
