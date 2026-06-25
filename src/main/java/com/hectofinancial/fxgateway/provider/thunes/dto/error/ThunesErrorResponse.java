package com.hectofinancial.fxgateway.provider.thunes.dto.error;

import java.util.List;

// Thunes 에러 응답: { "errors": [ { code, message }, ... ] }
public record ThunesErrorResponse(List<ThunesError> errors) {}
