package com.hectofinancial.fxgateway.provider.thunes.dto.transaction;
import com.hectofinancial.fxgateway.provider.thunes.dto.common.Amount;
import com.hectofinancial.fxgateway.provider.thunes.dto.common.Beneficiary;
import com.hectofinancial.fxgateway.provider.thunes.dto.common.CreditPartyIdentifier;
import com.hectofinancial.fxgateway.provider.thunes.dto.common.Money;
import com.hectofinancial.fxgateway.provider.thunes.dto.common.Sender;
import com.hectofinancial.fxgateway.provider.thunes.dto.payer.Payer;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 거래 응답(Transaction 객체). 생성/확정/조회 공통.
 * 날짜(creation/expiration)는 스펙상 HTTP format String. id 는 64bit(Long).
 * sending_business/receiving_business 는 필드 스키마 미문서화 + C2C 미사용 → Map.
 */
public record TransactionResponse(
        Long id,                                 // 거래 ID (64bit)
        String status,                           // 상태 코드 (예: "10000")
        String statusMessage,                    // 상태 설명 (예: "CREATED")
        String statusClass,                      // 상태 클래스 (1~7)
        String statusClassMessage,               // 상태 클래스 설명
        String externalId,
        String externalCode,
        String transactionType,                  // C2C | C2B | B2C | B2B
        String payerTransactionReference,
        String payerTransactionCode,
        String creationDate,                     // HTTP format
        String expirationDate,                   // HTTP format
        CreditPartyIdentifier creditPartyIdentifier,
        Money source,                            // 보내는 금액 {currency, amount, country}
        Money destination,                       // 받는 금액 {currency, amount}
        Payer payer,
        Sender sender,
        Beneficiary beneficiary,
        Map<String, Object> sendingBusiness,     // C2C 미사용 (스키마 미문서화)
        Map<String, Object> receivingBusiness,   // C2C 미사용 (스키마 미문서화)
        String callbackUrl,
        Amount sentAmount,                       // {currency, amount}
        BigDecimal wholesaleFxRate,
        BigDecimal retailRate,
        BigDecimal retailFee,
        String retailFeeCurrency,
        Amount fee,                              // Thunes 수수료 {currency, amount}
        String purposeOfRemittance,
        String documentReferenceNumber,
        String additionalInformation1,
        String additionalInformation2,
        String additionalInformation3,
        String reference
) {}
