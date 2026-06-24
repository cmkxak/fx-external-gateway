# fx-external-gateway

Thunes Money Transfer V2 API를 직접 붙여보면서 "해외거래망 게이트웨이"가 어떤 모양이어야 하는지 감을 잡으려고 만든 개인 연습 프로젝트입니다. 회사 운영 코드와는 별개이고, 혼자 구조를 그려보고 API 흐름을 손에 익히는 용도로 쓰고 있습니다.

그래서 여기 있는 코드는 완성된 정답이라기보다 이해를 돕기 위한 스케치에 가깝습니다. 군데군데 TODO와 임시 처리가 남아 있고, 실제 거래에 필요한 보안·정합성 요건은 아직 채우지 않았습니다. 운영에 그대로 올릴 물건은 아니라는 점을 먼저 적어둡니다.

## 무엇을 해보려는 건가

- Thunes 송금 흐름(견적 → 거래 → 확정 → 콜백)을 코드로 한 번 따라가 보기
- 해외송금 네트워크를 여러 개 붙일 수 있도록 provider 단위로 갈라놓는 구조 연습
- Spring Boot 3.5.x / JDK 21 / `RestClient` 같은 최신 스택에 익숙해지기

## 어디까지 만들었나

운영 코드와 다르게, 아직 비워둔 부분이 많습니다.

- 인증정보·엔드포인트는 전부 자리표시(placeholder)만 잡아둔 상태
- 빌드 의존성 저장소(`repositories`)도 자리표시라, 사내 Nexus 설정 전에는 빌드가 안 됩니다
- 멱등 처리, 콜백 서명 검증, 대사(reconciliation)처럼 운영에 꼭 필요한 부분은 일부러 TODO로 남겨뒀습니다
- 상태·enum·공통 모델 추상화는 다음 단계로 미뤄둔 상태입니다

## 구조

우리(파트너) → **게이트웨이(이 프로젝트)** → 해외망(Thunes)으로 나가는 아웃바운드 게이트웨이입니다.

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

송금은 다음 순서로 진행되고, 각 단계를 따로 떼어놔서 호출 순서는 상위(API 서버)가 제어하도록 했습니다.

```
① 견적(Quotation) → ② 자체 한도 검증 → ③ 수취인 검증(CPV) → ④ 거래 생성 → (선택) 확정
```

## 빌드

```bash
# JDK 21 필요
./gradlew compileJava
```

`build.gradle.kts`의 `repositories`가 아직 `mavenCentral()` 자리표시라, 폐쇄망·사내 환경에서는 사내 Nexus로 바꾼 뒤에 빌드해야 합니다.

## 분석 메모 (`docs/`)

Thunes API를 뜯어보면서 정리해둔 노트입니다.

- [공식 문서 정리](docs/Thunes_MoneyTransfer_V2_공식문서_정리.md) — V2 API 섹션별 정리
- [Getting Started 단계별](docs/Thunes_GettingStarted_단계별.md) — 단계별 해석
- [정보 흐름](docs/Thunes_정보흐름_information-flow.md) — 거래 시퀀스
- [자금 흐름](docs/Thunes_자금흐름_funds-flow.md) — Prefunding 모델
- [Pay 개발 가이드](docs/Thunes_Pay_개발가이드_정리.md) — Postman 기반 정리

---

개인 학습용이라 정확성이나 완전성을 보장하지는 않습니다. 운영 환경 사용을 전제로 하지 않는다는 점만 다시 한번 적어둡니다.
