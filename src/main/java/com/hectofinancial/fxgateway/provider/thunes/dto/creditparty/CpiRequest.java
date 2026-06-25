package com.hectofinancial.fxgateway.provider.thunes.dto.creditparty;
import com.hectofinancial.fxgateway.provider.thunes.dto.common.CreditPartyIdentifier;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CpiRequest(
        @NotNull @Valid CreditPartyIdentifier creditPartyIdentifier
) {}
