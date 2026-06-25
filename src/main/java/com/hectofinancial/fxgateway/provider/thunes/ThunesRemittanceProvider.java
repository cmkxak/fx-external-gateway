package com.hectofinancial.fxgateway.provider.thunes;

import com.hectofinancial.fxgateway.core.provider.RemittanceProvider;
import com.hectofinancial.fxgateway.provider.thunes.client.ThunesClient;
import com.hectofinancial.fxgateway.provider.thunes.dto.creditparty.CpiRequest;
import com.hectofinancial.fxgateway.provider.thunes.dto.creditparty.CpiResponse;
import com.hectofinancial.fxgateway.provider.thunes.dto.payer.Payer;
import com.hectofinancial.fxgateway.provider.thunes.dto.quotation.QuotationRequest;
import com.hectofinancial.fxgateway.provider.thunes.dto.quotation.QuotationResponse;
import com.hectofinancial.fxgateway.provider.thunes.dto.transaction.TransactionRequest;
import com.hectofinancial.fxgateway.provider.thunes.dto.transaction.TransactionResponse;
import com.hectofinancial.fxgateway.provider.thunes.dto.creditparty.VerificationRequest;
import org.springframework.stereotype.Component;

import java.util.List;

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

    /** 지급처 목록 조회 (Discovery). 견적 전 payer_id 확보용. */
    public List<Payer> listPayers(Integer page, Integer perPage, Integer serviceId,
                                  String countryIsoCode, String currency) {
        return thunes.getPayers(page, perPage, serviceId, countryIsoCode, currency);
    }

    /** 지급처 단건 조회. */
    public Payer getPayer(long id) {
        return thunes.getPayer(id);
    }

    /** 1단계: 견적 생성 (환율/수수료 고정). */
    public QuotationResponse createQuotation(QuotationRequest req) {
        return thunes.createQuotation(req);
    }

    /** 수취인 검증 (거래 생성 전 수행). type = C2C | C2B | B2C | B2B */
    public String verifyBeneficiary(String payerId, String type, VerificationRequest req) {
        return thunes.verifyCreditParty(payerId, type, req);
    }

    /** 수취인 정보 조회(CPI). type = C2C | C2B | B2C | B2B */
    public CpiResponse retrieveBeneficiaryInformation(String payerId, String type, CpiRequest req) {
        return thunes.retrieveCreditPartyInformation(payerId, type, req);
    }

    /** 거래 생성 (견적 ID 기준). 확정 전까지는 미확정 상태. */
    public TransactionResponse createTransaction(long quotationId, TransactionRequest req) {
        return thunes.createTransaction(quotationId, req);
    }

    /** 거래 생성 (견적 external_id 기준 = 우리 번호). 응답 유실 시 재시도/복구에 사용. */
    public TransactionResponse createTransactionByQuotationExternalId(String quotationExternalId, TransactionRequest req) {
        return thunes.createTransactionByQuotationExternalId(quotationExternalId, req);
    }

    /** [선택] 거래 확정 — 이 호출부터 실제 자금 이동. 최종 상태는 콜백/조회로 확인. */
    public TransactionResponse confirmTransaction(long transactionId) {
        return thunes.confirmTransaction(transactionId);
    }

    /** 거래 확정 (external_id 기준 = 우리 번호). 멱등/복구용. */
    public TransactionResponse confirmTransactionByExternalId(String transactionExternalId) {
        return thunes.confirmTransactionByExternalId(transactionExternalId);
    }
}
