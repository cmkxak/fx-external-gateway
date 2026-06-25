package com.hectofinancial.fxgateway.provider.thunes.dto.transaction;
import com.hectofinancial.fxgateway.provider.thunes.dto.common.Beneficiary;
import com.hectofinancial.fxgateway.provider.thunes.dto.common.CreditPartyIdentifier;
import com.hectofinancial.fxgateway.provider.thunes.dto.common.Sender;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 거래 생성 요청 — C2C 전용.
 * C2C 는 sender + beneficiary 가 필수(†), sending_business/receiving_business 는 미사용이라 제외.
 * (다른 거래유형 지원 시 별도 요청 타입으로 분리)
 */
public record TransactionRequest(
        @NotNull @Valid CreditPartyIdentifier creditPartyIdentifier,   // 필수
        BigDecimal retailRate,                                         // 선택: 리테일 환율 override
        BigDecimal retailFee,                                          // 선택: 리테일 수수료
        @Size(max = 3) String retailFeeCurrency,                       // 선택: 수수료 통화(ISO 4217)
        @NotNull @Valid Sender sender,                                 // C2C 필수
        @NotNull @Valid Beneficiary beneficiary,                       // C2C 필수
        @NotBlank @Size(max = 64) String externalId,                   // 필수: 우리 거래키(멱등)
        @Size(max = 64) String externalCode,                           // 선택: 외부 참조코드
        String callbackUrl,                                            // 선택: 상태 콜백 URL
        @NotBlank @Size(max = 64) String purposeOfRemittance,          // 필수: 송금 목적
        @Size(max = 64) String documentReferenceNumber,                // 선택(B2B만 필수)
        @Size(max = 64) String additionalInformation1,                 // 선택
        @Size(max = 64) String additionalInformation2,                 // 선택
        @Size(max = 64) String additionalInformation3,                 // 선택
        @Size(max = 16) String reference                               // 선택: 수취인 명세서에 찍히는 값
) {}
