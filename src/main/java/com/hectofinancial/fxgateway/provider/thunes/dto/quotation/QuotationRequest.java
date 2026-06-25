package com.hectofinancial.fxgateway.provider.thunes.dto.quotation;
import com.hectofinancial.fxgateway.provider.thunes.dto.common.Money;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record QuotationRequest(
        @NotBlank String externalId,
        @NotBlank String payerId,
        @NotBlank String mode,                 // SOURCE_AMOUNT | DESTINATION_AMOUNT
        @NotBlank String transactionType,      // C2C | C2B | B2C | B2B
        @NotNull @Valid Money source,
        @NotNull @Valid Money destination
) {
    // source.country_iso_code 는 필수 (destination 엔 없음)
    @AssertTrue(message = "source.countryIsoCode is required")
    private boolean isSourceCountryPresent() {
        return source == null || (source.countryIsoCode() != null && !source.countryIsoCode().isBlank());
    }

    // mode 별 amount 필수: SOURCE_AMOUNT→source.amount, DESTINATION_AMOUNT→destination.amount
    @AssertTrue(message = "amount required by mode")
    private boolean isAmountPresentForMode() {
        if (mode == null || source == null || destination == null) {
            return true;
        }
        return switch (mode) {
            case "SOURCE_AMOUNT" -> source.amount() != null;
            case "DESTINATION_AMOUNT" -> destination.amount() != null;
            default -> true;
        };
    }
}
