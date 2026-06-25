package com.hectofinancial.fxgateway.provider.thunes.dto.common;

import java.math.BigDecimal;

// sent_amount, fee = { currency, amount }
public record Amount(String currency, BigDecimal amount) {}
