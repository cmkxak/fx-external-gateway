package com.hectofinancial.fxgateway.provider.thunes;

import com.hectofinancial.fxgateway.core.provider.RemittanceProvider;
import com.hectofinancial.fxgateway.provider.thunes.client.ThunesClient;
import com.hectofinancial.fxgateway.provider.thunes.dto.ThunesDtos.QuotationRequest;
import com.hectofinancial.fxgateway.provider.thunes.dto.ThunesDtos.QuotationResponse;
import com.hectofinancial.fxgateway.provider.thunes.dto.ThunesDtos.TransactionRequest;
import com.hectofinancial.fxgateway.provider.thunes.dto.ThunesDtos.TransactionResponse;
import com.hectofinancial.fxgateway.provider.thunes.dto.ThunesDtos.VerificationRequest;
import org.springframework.stereotype.Component;

/**
 * Thunes 망 구현체.
 *
 * 송금 시퀀스의 단계를 한 덩어리로 묶지 않고 개별 연산으로 노출한다.
 * 단계 사이의 고객 한도 누적액 검증 등은 GW 가 아닌 상위(API 서버)가 제어하므로,
 * 실제 호출 순서는 호출자가 조립한다:
 *   1) createQuotation  (견적 생성)
 *   2) [API 서버] 고객 한도 누적액 검증
 *   3) verifyBeneficiary (수취인 검증)
 *   4) createTransaction (거래 생성)  → [선택] confirmTransaction (확정 = 자금 이동)
 */
@Component
public class ThunesRemittanceProvider implements RemittanceProvider {

    public static final String NETWORK = "THUNES";

    private final ThunesClient thunes;

    public ThunesRemittanceProvider(ThunesClient thunes) {
        this.thunes = thunes;
    }

    @Override
    public String network() {
        return NETWORK;
    }

    /** 1단계: 견적 생성 (환율/수수료 고정). */
    public QuotationResponse createQuotation(QuotationRequest req) {
        return thunes.createQuotation(req);
    }

    /** 수취인 검증 (거래 생성 전 수행). type = C2C | C2B | B2C | B2B */
    public String verifyBeneficiary(String payerId, String type, VerificationRequest req) {
        return thunes.verifyCreditParty(payerId, type, req);
    }

    /** 거래 생성 (견적 ID 기준). 확정 전까지는 미확정 상태. */
    public TransactionResponse createTransaction(long quotationId, TransactionRequest req) {
        return thunes.createTransaction(quotationId, req);
    }

    /** [선택] 거래 확정 — 이 호출부터 실제 자금 이동. 최종 상태는 콜백/조회로 확인. */
    public TransactionResponse confirmTransaction(long transactionId) {
        return thunes.confirmTransaction(transactionId);
    }
}
