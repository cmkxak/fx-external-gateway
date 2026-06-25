package com.hectofinancial.fxgateway.provider.thunes.dto.creditparty;
import com.hectofinancial.fxgateway.provider.thunes.dto.common.Beneficiary;

import java.util.Map;

public record CpiResponse(
        Beneficiary beneficiary,
        Map<String, Object> receivingBusiness,
        Long id
) {}
