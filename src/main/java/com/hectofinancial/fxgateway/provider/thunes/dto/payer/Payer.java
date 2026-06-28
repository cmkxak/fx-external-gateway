package com.hectofinancial.fxgateway.provider.thunes.dto.payer;
import com.hectofinancial.fxgateway.provider.thunes.dto.common.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 지급처(Payer). Discovery(GET /payers, /payers/{id}) 응답.
 * 견적 요청의 payer_id 와 거래에 필요한 요구사항(필수필드/식별자/한도)의 단일 진실원.
 */
public record Payer(
        Integer id,                                  // 지급처 고유 ID (견적 요청의 payer_id 로 사용)
        String name,                                 // 지급처 이름
        Integer precision,                           // 금액 소수 정밀도(자릿수). 예: IDR=0, USD=2
        BigDecimal increment,                        // 금액 최소 증분 단위 (이 배수로만 금액 지정 가능)
        String currency,                             // 지급 통화 (ISO 4217)
        String countryIsoCode,                       // 지급 국가 (ISO 3166-1 alpha-3)
        Service service,                             // 지급 수단(서비스): MobileWallet / BankAccount 등
        Map<String, PayerTransactionType> transactionTypes  // 거래유형(C2C/C2B/B2C/B2B)별 요구사항
) {}
