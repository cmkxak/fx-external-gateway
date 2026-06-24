package com.hectofinancial.fxgateway.provider.thunes.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Thunes MoneyTransfer V2 요청/응답 DTO 모음 (핵심 필드 위주).
 * 전체 필드/enum 은 docs/Thunes_Pay_개발가이드_정리.md §5~6 참조.
 * Jackson SNAKE_CASE 전략으로 camelCase <-> snake_case 자동 매핑.
 */
@JsonInclude(Include.NON_NULL)
public final class ThunesDtos {

    private ThunesDtos() {}

    // ----- 공통 -----
    // amount: 통화별 정밀도 보존을 위해 BigDecimal (null 허용 — SOURCE/DESTINATION mode 에 따라 한쪽이 null)
    public record Money(BigDecimal amount, String currency, String countryIsoCode) {}

    public record CreditPartyIdentifier(
            String msisdn,
            String bankAccountNumber,
            String swiftBicCode
    ) {}

    // ----- Quotation (견적) -----
    public record QuotationRequest(
            @NotBlank String externalId,
            @NotBlank String payerId,
            String mode,                 // SOURCE_AMOUNT(송금액 입력) | DESTINATION_AMOUNT (수취액 입력)
            String transactionType,      // C2C | C2B | B2C | B2B
            Money source,
            Money destination,
            BigDecimal retailFee,
            String retailFeeCurrency
    ) {}

    public record QuotationResponse(
            Long id,
            String externalId,
            Money source,
            Money destination,
            Object wholesaleFxRate,      // 응답 스펙 확정 후 타입 강화
            Object payer
    ) {}

    // ----- Verification (수취인 검증) -----
    public record VerificationRequest(CreditPartyIdentifier creditPartyIdentifier) {}

    // ----- Transaction (거래 생성) -----
    // sender(개인) / sendingBusiness(사업자), beneficiary 는 코리도별 요구필드가 달라 Map 으로 유연 처리.
    // 안정화 후 전용 record(Sender, SendingBusiness, Beneficiary)로 교체 권장.
    public record TransactionRequest(
            @NotBlank String externalId,
            Map<String, Object> sender,
            Map<String, Object> sendingBusiness,
            CreditPartyIdentifier creditPartyIdentifier,
            Map<String, Object> beneficiary,
            BigDecimal retailFee,
            String retailFeeCurrency,
            String purposeOfRemittance,
            String documentReferenceNumber,
            String callbackUrl
    ) {}

    public record TransactionResponse(
            Long id,
            String externalId,
            String status,
            Object creditPartyIdentifier
    ) {}
}
