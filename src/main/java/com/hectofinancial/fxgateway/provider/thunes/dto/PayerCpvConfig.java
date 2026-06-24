package com.hectofinancial.fxgateway.provider.thunes.dto;

import java.util.List;

/**
 * Payer transaction_type 내 credit_party_verification(CPV) 가용 설정.
 * 비어있으면([[]]) 해당 payer/유형은 CPV 미지원으로 해석.
 */
public record PayerCpvConfig(
        // CPV 호출 시 사용 가능한 수취인 식별자 조합 목록
        List<List<String>> creditPartyIdentifiersAccepted,
        // CPV 시 함께 보낼 수취인 필드 조합 (이름매칭 등에 사용)
        List<List<String>> requiredReceivingEntityFields
) {}
