package com.hectofinancial.fxgateway.provider.thunes.dto;

import java.util.Map;

public record CpiResponse(
        Beneficiary beneficiary,
        Map<String, Object> receivingBusiness,
        Long id
) {}
