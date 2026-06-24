package com.hectofinancial.fxgateway.provider.thunes.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CpiRequest(
        @NotNull @Valid CreditPartyIdentifier creditPartyIdentifier
) {}
