package com.hectofinancial.fxgateway.provider.thunes.dto.payer;

import java.util.List;

/**
 * Payer 의 거래유형별(C2C/C2B/B2C/B2B) 설정.
 * *_accepted / required_* 는 "조합(combination)의 배열" — 각 내부 배열이 하나의 허용 조합이고,
 * 목록의 첫 조합이 preferred(우선 사용). (min/max 거래금액은 Payer 최상위에 있음)
 */
public record PayerTransactionType(
        // 허용되는 수취인 식별자 조합. 예: [["msisdn"]] → msisdn 으로 지정 가능
        List<List<String>> creditPartyIdentifiersAccepted,
        // 송금인(sender)에 필수인 필드 조합. 예: [["firstname","date_of_birth","lastname"]]
        List<List<String>> requiredSendingEntityFields,
        // 수취인(beneficiary)에 필수인 필드 조합. 예: [["firstname","lastname"]]
        List<List<String>> requiredReceivingEntityFields,
        // 거래에 필요한 첨부 문서 조합. 예: [["invoice","contract"]]
        List<List<String>> requiredDocuments,
        // CPI(수취인 정보조회) 가용 설정
        PayerCpiConfig creditPartyInformation,
        // CPV(수취인 계좌검증) 가용 설정
        PayerCpvConfig creditPartyVerification,
        // 이 거래유형에서 허용되는 송금 목적(purpose_of_remittance) 값 목록 (비어있으면 제약 없음)
        List<String> purposeOfRemittanceValuesAccepted
) {}
