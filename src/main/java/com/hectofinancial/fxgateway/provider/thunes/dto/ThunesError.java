package com.hectofinancial.fxgateway.provider.thunes.dto;

// Thunes 에러 항목: { "code": "...", "message": "..." }
public record ThunesError(String code, String message) {}
