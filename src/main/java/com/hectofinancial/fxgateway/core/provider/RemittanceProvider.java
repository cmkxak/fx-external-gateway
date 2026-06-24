package com.hectofinancial.fxgateway.core.provider;

/**
 * 해외송금 네트워크(망) 공통 추상화 — 게이트웨이 확장점.
 * 신규 해외망 연동 시 이 인터페이스를 구현해 스프링 빈으로 등록한다.
 *
 * 주의: 공통 견적/거래/상태 모델 통일과 망 선택 디스패처는
 * 2번째 망 스펙이 확정된 뒤 설계한다(섣부른 공통모델 확정 금지 — TBD).
 */
public interface RemittanceProvider {

    /** 망 식별자. 예: "THUNES" */
    String network();
}
