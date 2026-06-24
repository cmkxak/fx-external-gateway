package com.hectofinancial.fxgateway.provider.thunes.dto;

import java.math.BigDecimal;

public record Payer(
        Integer id,
        String name,
        Integer precision,
        BigDecimal increment,
        String currency,
        String countryIsoCode,
        Service service
) {}
