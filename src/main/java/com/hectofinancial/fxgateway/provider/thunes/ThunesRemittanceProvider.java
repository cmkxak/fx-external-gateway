package com.hectofinancial.fxgateway.provider.thunes;

import com.hectofinancial.fxgateway.core.provider.RemittanceProvider;
import com.hectofinancial.fxgateway.provider.thunes.client.ThunesClient;
import com.hectofinancial.fxgateway.provider.thunes.dto.creditparty.CpiRequest;
import com.hectofinancial.fxgateway.provider.thunes.dto.creditparty.CpiResponse;
import com.hectofinancial.fxgateway.provider.thunes.dto.creditparty.VerificationRequest;
import com.hectofinancial.fxgateway.provider.thunes.dto.payer.Payer;
import com.hectofinancial.fxgateway.provider.thunes.dto.quotation.QuotationRequest;
import com.hectofinancial.fxgateway.provider.thunes.dto.quotation.QuotationResponse;
import com.hectofinancial.fxgateway.provider.thunes.dto.transaction.TransactionAttachment;
import com.hectofinancial.fxgateway.provider.thunes.dto.transaction.TransactionRequest;
import com.hectofinancial.fxgateway.provider.thunes.dto.transaction.TransactionResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Thunes 망 구현체 (무상태 패스스루 + Thunes 방언↔공용어 번역).
 *
 * GW 책임: 멱등 실행 + 상태/에러 해석 + 조회 제공.
 * 재시도/망취소 결정·지속(원장 기반)은 GW가 아닌 상위(API 서버) 책임 — 여기서 in-call 재시도 안 함.
 * 불확실(타임아웃/IO/5xx) 응답은 ThunesExceptionHandler 가 전용 신호로 표면화 → API 서버가 GET 조회로 reconcile.
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

    /** 거래 생성 (견적 ID 기준). */
    public TransactionResponse createTransaction(long quotationId, TransactionRequest req) {
        return thunes.createTransaction(quotationId, req);
    }

    /** 거래 생성 (견적 external_id 기준 = 우리 번호). 멱등 안전망. */
    public TransactionResponse createTransactionByQuotationExternalId(String quotationExternalId, TransactionRequest req) {
        return thunes.createTransactionByQuotationExternalId(quotationExternalId, req);
    }

    /** [선택] 거래 확정 — 이 호출부터 실제 자금 이동. */
    public TransactionResponse confirmTransaction(long transactionId) {
        return thunes.confirmTransaction(transactionId);
    }

    /** 거래 확정 (external_id 기준 = 우리 번호). 멱등 안전망. */
    public TransactionResponse confirmTransactionByExternalId(String transactionExternalId) {
        return thunes.confirmTransactionByExternalId(transactionExternalId);
    }

    /** 거래 조회 (id 기준). API 서버 reconcile/상태판단용. */
    public TransactionResponse getTransaction(long transactionId) {
        return thunes.getTransaction(transactionId);
    }

    /** 거래 조회 (external_id 기준 = 우리 번호). API 서버 reconcile/상태판단용. */
    public TransactionResponse getTransactionByExternalId(String transactionExternalId) {
        return thunes.getTransactionByExternalId(transactionExternalId);
    }

    /** 거래 취소 (id 기준). CREATED / CONFIRMED-WAITING-FOR-PICKUP 만 가능 (상태판정은 Thunes 위임). */
    public TransactionResponse cancelTransaction(long transactionId) {
        return thunes.cancelTransaction(transactionId);
    }

    /** 거래 취소 (external_id 기준 = 우리 번호). */
    public TransactionResponse cancelTransactionByExternalId(String transactionExternalId) {
        return thunes.cancelTransactionByExternalId(transactionExternalId);
    }

    /** 증빙 첨부 추가. 확정 전, 거래당 최대 3개·8MB. */
    public TransactionAttachment addAttachment(long transactionId, String name, String type, MultipartFile file) {
        return thunes.addAttachment(transactionId, name, type, file);
    }

    /** 증빙 첨부 목록 조회. */
    public List<TransactionAttachment> listAttachments(long transactionId) {
        return thunes.listAttachments(transactionId);
    }
}
