package com.hectofinancial.fxgateway.provider.thunes.dto.transaction;

/**
 * 거래 첨부 문서. POST attachments 응답 / GET attachments 목록 항목.
 */
public record TransactionAttachment(
        Long id,
        String contentType,      // MIME (예: application/pdf)
        String name,
        Long transactionId,
        String type              // invoice | purchase_order | contract | ... (전체값 미문서화)
) {}
