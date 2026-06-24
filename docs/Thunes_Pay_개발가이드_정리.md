# Thunes Pay (MoneyTransfer V2) 개발 가이드 정리

> ⚠️ 출처 주의: 원본 `Pay Functional & Technical Overview (1).pdf` 및 다른 문서(중국 Payout 가이드, corridors CSV, 정책정의서 등)는 **DocSafer DRM으로 암호화**되어 스크립트로 직접 읽지 못했습니다.
> 이 정리는 같은 폴더의 **Postman 컬렉션 4종(B2B / B2C / C2B / C2C, `Thunes - MoneyTransfer V2 API`)** — 즉 그 PDF가 기술하는 실제 API 호출 명세 — 를 파싱해 재구성한 것입니다. 필드의 의미·필수 여부 등 일부 서술은 표준 Thunes 스펙 기준 추정이며, 확정 전 원본 PDF/공식 문서와 대조 필요(추측 표시).

---

## 1. 개요

- **제공자**: Thunes (글로벌 송금 네트워크). 우리는 이 API를 연동해 **소액 해외송금**을 처리.
- **제품**: MoneyTransfer **V2 API** (REST/JSON).
- **거래 유형(transaction_type) 4종** — 송금인/수취인이 개인이냐 사업자냐로 구분:

| 유형 | 송금인(Sender) | 수취인(Beneficiary) | 요청 바디의 송금인 객체 |
|------|----------------|---------------------|--------------------------|
| **C2C** | 개인 | 개인 | `sender` |
| **C2B** | 개인 | 사업자 | `sender` |
| **B2C** | 사업자 | 개인 | `sending_business` |
| **B2B** | 사업자 | 사업자 | `sending_business` |

> 우리 소액해외송금(개인 고객 → 해외 수취인) 시나리오는 주로 **C2C / C2B**. 헥토(법인)가 정산 주체로 들어가는 모델이면 B2C 형태도 검토.

---

## 2. 인증 & 공통

- **인증 방식**: HTTP **Basic Auth**
  - `username` = **api_key**
  - `password` = **api_secret**
  - 헤더: `Authorization: Basic base64(api_key:api_secret)`
- **공통 헤더**: `Content-Type: application/json`
- **Base URL**: 컬렉션 변수 `{{host}}` (Postman 환경에 별도 설정 — Thunes에서 발급한 **Sandbox / Production** 호스트. 값은 컬렉션에 미포함이므로 Thunes 콘솔/계정 정보로 채워야 함)
- **주요 Postman 변수**: `{{host}}`, `{{api_key}}`, `{{api_secret}}`, `{{basic token}}`

---

## 3. 표준 결제 플로우

송금 한 건은 **견적 → (수취인 검증) → 거래 생성 → 거래 확정**의 단계로 진행됩니다.

```
[0] (선택) GET  /ping                         연결 확인
[1]      GET  /v2/money-transfer/services     이용 가능 서비스/코리도 조회 (Discovery)
[2]      GET  /v2/money-transfer/payers       Payer(수취 채널) 목록
[3] (권장) POST .../credit-party-verification  수취인 계좌/번호 유효성 검증
[4]      POST /v2/money-transfer/quotations   견적 생성 → quotation_id 발급
[5]      POST .../quotations/{id}/transactions 거래 생성 → transaction_id 발급
[6]      POST .../transactions/{id}/confirm    거래 확정(실제 자금 이동 트리거)
[7]      GET  .../transactions/{id}            상태 조회 / 콜백 수신
```

> 핵심: **quotation은 환율·수수료를 고정(lock)** 하고, transaction은 그 견적 위에 송금인/수취인 정보를 붙여 생성하며, **confirm을 호출해야 실제 정산이 진행**됩니다. (confirm 전까지는 미확정)

---

## 4. API 엔드포인트 레퍼런스

### 4.1 Connectivity
| Method | Path | 설명 |
|---|---|---|
| GET | `/ping` | API 상태 확인 |

### 4.2 Discovery — 서비스/코리도
| Method | Path | 설명 |
|---|---|---|
| GET | `/v2/money-transfer/services` | 이용 가능 송금 서비스 목록(국가·통화·결제수단 조합). ※ B2B 컬렉션만 `/v1/...` 사용 |

> 동봉된 `List of corridors (1).csv`(DRM) 가 지원 코리도(출발국→도착국/통화) 마스터. 서비스 디스커버리 결과와 매칭.

### 4.3 Account
| Method | Path | 설명 |
|---|---|---|
| GET | `/v2/money-transfer/balances` | 우리(파트너) 잔액 조회 |

### 4.4 Payers — 수취 채널
| Method | Path | 설명 |
|---|---|---|
| GET | `/v2/money-transfer/payers` | Payer 목록 (수취 가능 은행/월렛/현금 채널) |
| GET | `/v2/money-transfer/payers/{PAYER_ID}` | 특정 Payer 상세 |
| POST | `/v2/money-transfer/payers/{PAYER_ID}/{TYPE}/credit-party-information` | 수취인 정보 추가 조회(채널별 요구 필드 등). `{TYPE}` = B2B/B2C/C2B/C2C |
| POST | `/v2/money-transfer/payers/{PAYER_ID}/{TYPE}/credit-party-verification` | **수취인 계좌/번호 유효성 검증** |

**credit-party-verification 요청 예시:**
```json
{
  "credit_party_identifier": {
    "msisdn": "233244109197"
  }
}
```
> `credit_party_identifier` 는 채널에 따라 `msisdn`(모바일머니), `bank_account_number` + `swift_bic_code`(은행), 기타 식별자를 조합.

### 4.5 Payment — 견적/거래/확정

| Method | Path | 설명 |
|---|---|---|
| POST | `/v2/money-transfer/quotations` | 견적 생성 → `quotation_id` |
| GET  | `/v2/money-transfer/quotations/{QUOTATION_ID}` | 견적 조회(내부 ID) |
| GET  | `/v2/money-transfer/quotations/ext-{EXTERNAL_ID}` | 견적 조회(외부 ID) |
| POST | `/v2/money-transfer/quotations/{QUOTATION_ID}/transactions` | 거래 생성 → `transaction_id` |
| POST | `/v2/money-transfer/quotations/ext-{EXTERNAL_ID}/transactions` | 거래 생성(외부 ID 기준) |
| POST | `/v2/money-transfer/transactions/{TRANSACTION_ID}/confirm` | **거래 확정** |
| POST | `/v2/money-transfer/transactions/ext-{EXTERNAL_ID}/confirm` | 거래 확정(외부 ID) |
| GET  | `/v2/money-transfer/transactions/{TRANSACTION_ID}` | 거래 상태 조회 |
| GET  | `/v2/money-transfer/transactions/ext-{TRANSACTION_ID}` | 거래 상태 조회(외부 ID) |

> **`ext-` 접두어 패턴**: 우리가 부여한 `external_id` 로 모든 리소스를 조회/확정할 수 있음 → 우리 시스템의 거래키를 그대로 멱등키로 활용 가능. (재시도/장애복구 설계에 중요)

---

## 5. 요청 바디 상세

### 5.1 Post Quotation (견적)
```json
{
  "external_id": "9481184321482",
  "payer_id": "214",
  "mode": "SOURCE_AMOUNT",
  "transaction_type": "B2C",
  "source": {
    "amount": "10",
    "currency": "SGD",
    "country_iso_code": "SGP"
  },
  "destination": {
    "amount": null,
    "currency": "IDR"
  },
  "retail_fee": "0",
  "retail_fee_currency": "USD"
}
```
- **`mode`**: 금액 기준
  - `SOURCE_AMOUNT` — 보내는 금액 고정(source.amount 입력, destination.amount = null)
  - `DESTINATION_AMOUNT` — 받는 금액 고정(destination.amount 입력, source.amount = null) *(추정)*
- **`source` / `destination`**: 통화(`currency`, ISO-4217)·국가(`country_iso_code`, ISO-3166 alpha-3)·금액
- **`retail_fee` / `retail_fee_currency`**: 우리가 고객에게 부과하는 리테일 수수료(0 가능)
- 응답으로 **환율·총비용·수취예정액·`quotation_id`** 가 내려옴(견적 유효시간 내 confirm 필요) *(추정)*

### 5.2 Post Transaction (거래 생성)

**개인 송금인(C2C/C2B) — `sender`:**
```json
{
  "sender": {
    "lastname": "Doe",
    "firstname": "John",
    "nationality_country_iso_code": "FRA",
    "date_of_birth": "1970-01-01",
    "country_of_birth_iso_code": "FRA",
    "gender": "MALE",
    "address": "42 Rue des fleurs",
    "postal_code": "75000",
    "city": "Paris",
    "country_iso_code": "FRA",
    "msisdn": "33712345678",
    "email": "john.doe@mail.com",
    "id_type": "SOCIAL_SECURITY",
    "id_number": "502-42-0158",
    "id_delivery_date": "2016-01-01",
    "occupation": "Residential Advisor"
  },
  "credit_party_identifier": {
    "msisdn": "+263775892100",
    "bank_account_number": "0123456789",
    "swift_bic_code": "ABCDEFGH"
  },
  "beneficiary": {
    "lastname": "Doe",
    "firstname": "Jane",
    "nationality_country_iso_code": "FRA",
    "date_of_birth": "1971-01-01",
    "country_of_birth_iso_code": "ZWE",
    "gender": "MALE",
    "address": "3 Norfolk Road",
    "postal_code": "4581",
    "city": "Harare",
    "country_iso_code": "ZWE",
    "msisdn": "263775892364",
    "email": "jane.doe@mail.com",
    "id_type": "SOCIAL_SECURITY",
    "id_country_iso_code": "ZWE",
    "id_number": "178027317681327",
    "occupation": "Sales Executive"
  },
  "external_id": "9481184321482",
  "retail_fee": 1,
  "retail_fee_currency": "EUR",
  "purpose_of_remittance": "FAMILY_SUPPORT",
  "document_reference_number": "12345678",
  "callback_url": "{URL_PLACEHOLDER}"
}
```

**사업자 송금인(B2C/B2B) — `sending_business`** (위 `sender` 대신 사용):
```json
"sending_business": {
  "registered_name": "Company ABC",
  "trading_name": "Brand name",
  "address": "12, Victoria Lane",
  "postal_code": "12345",
  "city": "Singapore",
  "country_iso_code": "SGP",
  "msisdn": "33712345678",
  "email": "john.doe@mail.com",
  "registration_number": "ABC12345T",
  "date_of_incorporation": "2014-10-01",
  "representative_lastname": "Doe",
  "representative_firstname": "John",
  "representative_id_country_iso_code": "SGP"
}
```

**거래 생성의 주요 최상위 필드(B2B/C2B 기준 전체 키):**
`sender`/`sending_business`, `beneficiary`, `credit_party_identifier`, `external_id`,
`retail_fee`, `retail_fee_currency`, `retail_rate`, `purpose_of_remittance`,
`document_reference_number`, `external_code`, `callback_url`,
`additional_information_1~3`

> **최소 바디(외부 ID 견적 기준) 예시** — 채널/코리도에 따라 요구 필드가 줄 수 있음:
> ```json
> {
>   "sending_business": { "registration_number": "ABC12345T", "date_of_incorporation": "1980-12-19",
>     "registered_name": "ABC COMPANY", "country_iso_code": "SGP", "address": "12B Baker Street" },
>   "credit_party_identifier": { "bank_account_number": "212938100" },
>   "beneficiary": { "lastname": "Liam" },
>   "external_id": "9481184321482"
> }
> ```
> 실제 필수 필드는 **서비스(코리도)·Payer 별로 다름** → Discovery / credit-party-information 응답의 required 필드를 따라야 함.

### 5.3 Confirm (확정)
- `POST /v2/money-transfer/transactions/{TRANSACTION_ID}/confirm` (바디 없음/빈 바디)
- 호출 성공 시 실제 자금 이동 단계로 진입. 이후 상태는 GET 조회 또는 `callback_url` 로 통지.

---

## 6. 주요 필드 / Enum 값

> ⚠️ 아래 enum은 Postman 샘플에 등장한 값 위주. **전체 허용값 목록은 외부 스펙(원본 PDF/Thunes 문서)으로 확정 필요 — 임의 추가 금지.**

| 필드 | 샘플/의미 |
|---|---|
| `transaction_type` | `C2C`, `C2B`, `B2C`, `B2B` |
| `mode` | `SOURCE_AMOUNT` (보내는금액 고정), `DESTINATION_AMOUNT`(받는금액 고정, 추정) |
| `gender` | `MALE` (그 외 `FEMALE` 등 추정) |
| `id_type` | `SOCIAL_SECURITY` (그 외 여권/주민번호 등 코드 — 확인 필요) |
| `purpose_of_remittance` | `FAMILY_SUPPORT` (그 외 송금목적 코드 — 확인 필요) |
| `*_country_iso_code` | 국가 ISO-3166 **alpha-3** (예: `SGP`, `FRA`, `ZWE`, `IDR`은 통화) |
| `currency` | 통화 ISO-4217 (예: `SGD`, `IDR`, `USD`, `EUR`) |
| `credit_party_identifier` | `msisdn` / `bank_account_number` / `swift_bic_code` 등 채널별 조합 |

---

## 7. 거래 상태 & 시뮬레이션(Sandbox)

| Method | Path | 설명 |
|---|---|---|
| GET | `/v2/simulation/transactions/{transaction_id}/{status_name}` | Sandbox에서 거래를 특정 상태로 강제 전이 |
| GET | `/v2/simulation/transactions/ext-{external_id}/{status_name}` | 외부 ID 기준 동일 |

- `{status_name}` 에 원하는 상태값을 넣어 **콜백/상태처리 로직을 테스트**.
- 실 상태 enum(예: 성공/실패/취소/보류 등) 목록은 원본 문서 확인 필요.
- 운영에선 상태 변화가 **`callback_url`(웹훅)** 으로 비동기 통지됨 → 우리 쪽 **콜백 수신 엔드포인트** 구현 필요.

---

## 8. 멱등성 / external_id 설계 포인트

- 모든 리소스(quotation/transaction)에 우리 **`external_id`** 부여 → `ext-{id}` 로 조회·거래생성·확정 가능.
- 네트워크 재시도 시 중복 거래 방지를 위해 **external_id를 멱등키로** 사용하는 패턴 권장.
- 우리 내부 거래 PK ↔ Thunes `transaction_id` ↔ `external_id` 매핑 테이블 유지.

---

## 9. 함께 받은 부가 자료 (참고)

| 파일 | 상태 | 내용 |
|---|---|---|
| `Pay Functional & Technical Overview (1).pdf` | 🔒 DRM | 원본 기능·기술 개요 (본 정리의 출처 문서) |
| `Thunes Business Hub Intro & Guide (1).pdf` | 🔒 DRM | Thunes 비즈니스 허브 콘솔 사용 가이드 |
| `China CNY/USD Bank Account Payout Integration Guide` | 🔒 DRM | 중국 은행계좌 지급 연동 가이드(코리도별 필수필드) |
| `List of corridors (1).csv` | 🔒 DRM | 지원 코리도(출발→도착) 마스터 |
| `소액해외송금_정책정의서_260526_v0.4.pptx` | 🔒 DRM | 우리 측 정책 정의 |
| `소액해외송금_ORIS_개발 자료/` | PDF/PPTX | **한국은행 ORIS 해외송금통합관리시스템** 연동(API명세서, 참가기관 매뉴얼, 제노솔루션 중계시스템, PublicKey 발급) — **규제보고 채널** |

> 즉 전체 그림 = **고객 → 우리 시스템 → (Thunes로 실제 송금) + (한국은행 ORIS로 규제 보고)** 의 2축. Thunes는 자금 이동, ORIS는 외환거래 보고.

---

## 10. 개발 착수 체크리스트

- [ ] Thunes **Sandbox host / api_key / api_secret** 발급·환경변수화 (`{{host}}` 채우기)
- [ ] Basic Auth 클라이언트 + 공통 `Content-Type: application/json` 래퍼
- [ ] `GET /ping`, `GET /services`, `GET /payers` 로 연결·디스커버리 검증
- [ ] 플로우 구현: **quotation → (verification) → transaction → confirm → status**
- [ ] `external_id` 멱등 설계 + 내부 거래키 매핑 테이블
- [ ] **callback_url 웹훅 수신** 엔드포인트 + 서명/검증(원본 문서 확인)
- [ ] 거래유형(C2C/C2B/B2C/B2B)별 `sender`/`sending_business` 분기
- [ ] 코리도별 **필수 필드** 동적 처리(Discovery/credit-party-information 기반)
- [ ] Sandbox **시뮬레이션 API** 로 상태 전이/콜백 테스트
- [ ] enum 값(`id_type`, `purpose_of_remittance`, 상태 등) **원본 PDF로 확정** (추측값 사용 금지)
- [ ] 한국은행 **ORIS 보고** 연동 별도 트랙 설계

---

### ❗ 다음 단계 제안
DRM 문서(원본 PDF/코리도 CSV/정책정의서)의 내용까지 정리하려면, **DocSafer 인증된 환경에서 열어 → 텍스트/표를 복사해 주시거나, DRM 해제(반출 승인) 사본**을 주시면 enum 전체값·필수필드·상태머신까지 정밀하게 채워 드리겠습니다.
