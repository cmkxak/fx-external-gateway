# fx-external-gateway

Thunes Money Transfer V2 API 뜯어보기

## OverView
- Thunes 송금 흐름(견적 → 거래 → 확정 → 콜백) 스켈레톤 코드 구현
- 해외송금 네트워크를 여러 개 붙일 수 있게 provider 단위로 분리
- Spring Boot 3.5.x / JDK 21 / `RestClient` 같은 최신 스택 적용

## Architecture

API Server → **Gateway(this)** → 해외망(Thunes)의 흐름으로 통신을 진행하는 Out-Bound Gateway

```
com.hectofinancial.fxgateway
├─ FxGatewayApplication
├─ core/provider/RemittanceProvider     # 망 공통 추상화(확장점)
└─ provider/
    └─ thunes/                          # Thunes 망 구현
        ├─ ThunesRemittanceProvider     # 견적 / 검증 / 거래 / 확정 단계
        ├─ client/  ThunesClient, ThunesUri, ThunesApiException
        ├─ config/  ThunesProperties, ThunesClientConfig (RestClient + Basic auth)
        ├─ dto/     ThunesDtos
        └─ web/     ThunesRemittanceController(인바운드), ThunesCallbackController(콜백)
```

```
① 견적(Quotation) → ② 자체 한도 검증 → ③ 수취인 검증(CPV) → ④ 거래 생성 → (선택) 확정
```

## Build

```bash
# JDK 21 필요
./gradlew compileJava
```

## 분석 메모 (`docs/`)

- [공식 문서 정리](docs/Thunes_MoneyTransfer_V2_공식문서_정리.md) — V2 API 섹션별 정리
- [Getting Started 단계별](docs/Thunes_GettingStarted_단계별.md) — 단계별 해석
- [정보 흐름](docs/Thunes_정보흐름_information-flow.md) — 거래 시퀀스
- [자금 흐름](docs/Thunes_자금흐름_funds-flow.md) — Prefunding 모델
- [Pay 개발 가이드](docs/Thunes_Pay_개발가이드_정리.md) — Postman 기반 정리

---

