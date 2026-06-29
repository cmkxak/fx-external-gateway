# 나라·Payer마다 다른 "필수 입력 항목" 다루기 — 메타데이터 주도 아키텍처

> 작성 관점: 금융 백엔드 10년차 아키텍트 → 3년차 주니어에게
> 짝 문서: [Thunes_Discovery_Redis캐시_설계.md](Thunes_Discovery_Redis캐시_설계.md) (이 문서의 Spec을 어떻게 캐싱하는지)
> 스택 전제: Spring Boot · Redis · Thunes Money Transfer V2

---

## 0. 주니어에게 — 이 문서가 푸는 문제 한 줄

> "인도네시아로 보낼 땐 수취인 `lastname`만 있으면 되는데, 태국 어떤 은행은 `lastname + 신분증종류 + 신분증번호`를 요구한다. 나라가 수십~수백 개고, 은행마다 다르고, **Thunes가 예고 없이 필수 항목을 추가**하기도 한다. 이걸 어떻게 코드 한 줄 안 고치고 다 받아낼까?"

처음엔 다들 `if (country == "TH") {...}` 를 떠올린다. **그게 바로 우리가 절대 안 하려는 것이다.** 왜 안 되는지부터 보자.

---

## 1. 문제 정의

해외송금 거래를 만들려면 `sender`(송금인), `beneficiary`(수취인), `credit_party_identifier`(수취 계좌식별자)를 보내야 한다. 그런데 **각 필드가 필수인지 아닌지가 (지급처 payer) × (거래유형 transaction_type) 마다 다르다.**

Thunes는 이 규칙을 payer 객체의 `transaction_types[유형]` 안에 내려준다:

```json
// payer 214 (BRI, 인도네시아) 의 C2C
"required_sending_entity_fields":   [ ["lastname"] ],
"required_receiving_entity_fields": [ ["lastname"] ],
"credit_party_identifiers_accepted":[ ["bank_account_number"] ]

// payer 49 (Bangkok Bank, 태국) 의 C2C  — 더 복잡, 게다가 "조합 중 택1"
"required_sending_entity_fields": [
  ["lastname","country_iso_code","id_type","id_number"],
  ["lastname","address","city","country_iso_code"],
  ["lastname","date_of_birth","country_of_birth_iso_code","address","country_iso_code"]
]
```

여기서 어려운 점 3가지:
1. **payer마다 다름** — 수백 개 코리도 × 거래유형.
2. **"조합 중 택1"(array of arrays)** — 여러 유효 세트가 있고 하나만 충족하면 됨.
3. **무공지 변경** — Thunes는 non-breaking 변경(신규 필수필드 추가 등)을 **사전 통지 없이** 투입한다(공식 docs 명시).

---

## 2. 안티패턴: 하드코딩 (왜 안 되나)

```java
// ❌ 절대 이렇게 하지 말 것
if (countryCode.equals("TH")) {
    require(sender.getIdType(), sender.getIdNumber());
} else if (countryCode.equals("ID")) {
    // ...
}
```

금융 시니어가 이 코드를 리뷰에서 막는 이유:
- **확장 불가** — 코리도가 추가될 때마다 코드 수정·배포. 수백 개를 if로?
- **무공지 변경에 즉사** — Thunes가 태국 은행에 필수필드 하나 추가하면, 우리가 모른 채 거래가 계속 REJECTED 난다. 운영 사고.
- **이미 정답이 API에 있다** — Thunes가 `required_*_entity_fields`로 정확히 알려주는데, 그걸 무시하고 우리가 다시 추측하는 꼴. (docs: "client는 API 응답으로 필수 여부를 판단하라")
- **SoT 분산** — 같은 규칙이 프론트 검증·백엔드 검증·문서에 각각 박혀 서로 어긋난다.

> **핵심 원칙: 필수 항목의 진실(Source of Truth)은 Thunes의 `transaction_types`다. 우리는 그걸 해석해서 흘려보낼 뿐, 규칙을 소유하지 않는다.**

이런 접근을 **메타데이터 주도(Metadata-Driven) 설계**라고 한다. "데이터가 동작을 결정한다."

---

## 3. 아키텍처 한눈에

```
                         ┌─────────────────────────────────────────────┐
  Thunes payer (raw)     │   ① ACL 매퍼                                 │
  transaction_types ───▶ │   Thunes 모양 → 내부 RequirementSpec(정규화)  │
                         └───────────────┬─────────────────────────────┘
                                         │ RequirementSpec (깨끗한 내부 모델)
                         ┌───────────────▼───────────────┐
                         │   Redis 캐시                    │  thunes:disc:reqspec:{payerId}:{txType}
                         └───────────────┬───────────────┘
                  ┌──────────────────────┼──────────────────────┐
        ② Field Registry          ③ JSON Schema 생성         ⑤ 견적에 Spec pin
        (필드→위젯/검증, 정적)      (oneOf로 조합 표현)         (drift·감사 안전)
                  │                      │                      │
            ┌─────▼─────┐         ┌──────▼──────┐         ┌─────▼─────┐
            │ 동적 폼     │         │ 백엔드 검증   │         │ 주문 스냅샷 │
            │(서버주도UI) │         │ (fail-fast) │         │           │
            └───────────┘         └─────────────┘         └───────────┘
```

각 컴포넌트의 **단일 책임**:
| 컴포넌트 | 책임 | 동적/정적 |
|---|---|---|
| ① ACL 매퍼 | Thunes raw → `RequirementSpec` 변환·정규화 | — |
| ② Field Registry | "각 필드를 어떻게 렌더·검증" | **정적**(우리가 소유) |
| ③ Schema 생성기 | `RequirementSpec` → JSON Schema | — |
| ④ Validator | 제출값을 Schema로 검증 | — |
| ⑤ Spec pinner | 견적 시점 Spec을 주문에 고정 | — |

> 외워둘 분리: **"무엇이 필수인가"(동적, Thunes가 결정)** ↔ **"필드를 어떻게 다루는가"(정적, 우리가 결정)**. 이 둘을 섞으면 다시 하드코딩으로 회귀한다.

---

## 4. ① Anti-Corruption Layer — `RequirementSpec`로 정규화

### 왜 ACL인가
Thunes raw 응답을 앱 전체에 그대로 흘리면, Thunes의 **이상한 모양**(문자열 금액 `"50.00000000"`, 중첩배열, 네이밍)이 우리 도메인 곳곳에 전염된다. ACL(Anti-Corruption Layer)은 **외부 모델을 우리 내부 모델로 번역하는 방벽**이다. Thunes가 바뀌면 **매퍼 한 곳만** 고친다.

### 내부 모델 (정규화된 깨끗한 형태)

```java
// 우리가 소유하는 깨끗한 내부 모델. Thunes 냄새 제거됨.
public record RequirementSpec(
    long payerId,
    String transactionType,                  // C2C 등
    AmountRule amount,
    List<List<String>> creditPartyIdentifierOptions,  // 조합(택1)
    List<List<String>> senderFieldOptions,            // 조합(택1, 첫=preferred)
    List<List<String>> beneficiaryFieldOptions,       // 조합(택1)
    List<List<String>> requiredDocumentOptions,
    List<String> purposeValuesAccepted,      // [] = 제약 없음
    boolean cpiAvailable,                    // 수취인 정보조회 가능?
    boolean cpvAvailable                     // 계좌검증 가능?
) {}

public record AmountRule(
    BigDecimal min,
    BigDecimal max,          // null = 무제한
    String currency,
    int precision,           // 소수 자릿수
    BigDecimal increment     // 최소 단위(배수만 허용)
) {}
```

### 매퍼 (Thunes raw → RequirementSpec)

```java
@Component
public class ThunesPayerMapper {

    public RequirementSpec toSpec(PayerDto payer, String txType) {
        var tt = payer.transactionTypes().get(txType);
        if (tt == null) {
            throw new UnsupportedTransactionTypeException(payer.id(), txType);
        }
        return new RequirementSpec(
            payer.id(),
            txType,
            new AmountRule(
                parseAmount(tt.minimumTransactionAmount()),   // 문자열→BigDecimal
                parseAmountNullable(tt.maximumTransactionAmount()), // null 가능
                payer.currency(),
                payer.precision(),
                new BigDecimal(payer.increment().toString())
            ),
            tt.creditPartyIdentifiersAccepted(),
            tt.requiredSendingEntityFields(),
            tt.requiredReceivingEntityFields(),
            tt.requiredDocuments(),
            tt.purposeOfRemittanceValuesAccepted(),
            isCpiAvailable(tt),    // [[]] 이면 미지원 → false
            isCpvAvailable(tt)
        );
    }

    /** Thunes 금액은 docs상 Decimal이나 실측 응답이 문자열("50.00000000")로 옴 → 방어적 파싱 */
    private BigDecimal parseAmount(Object raw) {
        if (raw == null) throw new IllegalStateException("min amount must not be null");
        return new BigDecimal(raw.toString());   // String/Number 모두 안전
    }
    private BigDecimal parseAmountNullable(Object raw) {
        return raw == null ? null : new BigDecimal(raw.toString());
    }
    /** credit_party_information: { "credit_party_identifiers_accepted": [[]] } → 빈 조합이면 미지원 */
    private boolean isCpiAvailable(TransactionTypeDto tt) {
        var accepted = tt.creditPartyInformation() == null ? null
            : tt.creditPartyInformation().creditPartyIdentifiersAccepted();
        return accepted != null && accepted.stream().anyMatch(combo -> !combo.isEmpty());
    }
    private boolean isCpvAvailable(TransactionTypeDto tt) { /* 동일 패턴 */ return false; }
}
```

> 주니어 포인트: **문자열 금액을 `double`로 받지 마라.** 금융 금액은 무조건 `BigDecimal`. `double`은 부동소수 오차로 1원이 틀어지고, 그건 금융에서 사고다.

---

## 5. ② Field Registry — "어떻게 렌더·검증하나" (정적, 우리 소유)

`RequirementSpec`은 **어떤 필드가 필요한지**만 말해준다(`["lastname","id_type",...]`). 그 필드를 **화면에 어떻게 그리고 어떻게 검증할지**는 우리가 정한다. 이걸 한 곳(Registry)에 모은다.

```java
public record FieldDescriptor(
    String name,
    WidgetType widget,        // TEXT, DATE, COUNTRY_SELECT, ENUM_SELECT, PHONE ...
    String i18nLabelKey,      // "field.sender.lastname"
    Set<String> enumValues,   // ENUM_SELECT 일 때 (예: id_type 값들)
    String regex              // 형식 검증(선택)
) {}

@Component
public class FieldRegistry {
    private final Map<String, FieldDescriptor> reg = Map.of(
        "firstname",        new FieldDescriptor("firstname", TEXT, "field.firstname", Set.of(), null),
        "lastname",         new FieldDescriptor("lastname",  TEXT, "field.lastname",  Set.of(), null),
        "date_of_birth",    new FieldDescriptor("date_of_birth", DATE, "field.dob", Set.of(), null),
        "country_iso_code", new FieldDescriptor("country_iso_code", COUNTRY_SELECT, "field.country", Set.of(), "[A-Z]{3}"),
        "gender",           new FieldDescriptor("gender", ENUM_SELECT, "field.gender", Set.of("MALE","FEMALE"), null),
        "id_type",          new FieldDescriptor("id_type", ENUM_SELECT, "field.idType",
                                Set.of("PASSPORT","NATIONAL_ID","DRIVING_LICENSE","SOCIAL_SECURITY"), null),
        "id_number",        new FieldDescriptor("id_number", TEXT, "field.idNumber", Set.of(), null)
        // ...
    );

    /** 핵심: 모르는 필드가 와도 깨지지 않는다 (Open/Closed) */
    public FieldDescriptor describe(String fieldName) {
        var d = reg.get(fieldName);
        if (d == null) {
            log.warn("Unknown Thunes field '{}' — 기본 TEXT 위젯으로 렌더. Registry에 추가 필요", fieldName);
            return new FieldDescriptor(fieldName, TEXT, "field." + fieldName, Set.of(), null);
        }
        return d;
    }
}
```

> 이게 **Open/Closed 원칙**의 실제 효과다. Thunes가 내일 `tax_id`라는 신규 필수필드를 무공지로 추가해도 → 시스템은 **안 깨지고** 일반 텍스트 입력으로 렌더 + 경고 로그. 운영자가 로그 보고 Registry에 위젯 한 줄 추가하면 끝. 하드코딩이면 이때 장애 + 긴급배포다.

---

## 6. ③ RequirementSpec → JSON Schema (조합을 `oneOf`로)

왜 JSON Schema? **프론트(동적 폼)와 백엔드(검증)가 같은 표준 문서 하나를 공유**하기 위해서다. 검증 로직을 직접 짜지 말고 표준 validator(everit, networknt 등)에 맡긴다.

"조합 중 택1"은 JSON Schema의 `oneOf`(또는 `anyOf`)로 자연스럽게 표현된다.

```java
public ObjectNode toSenderSchema(RequirementSpec spec) {
    var schema = mapper.createObjectNode();
    schema.put("type", "object");
    // properties: 알려진 필드의 타입/형식 (Registry 활용)
    var props = schema.putObject("properties");
    spec.senderFieldOptions().stream().flatMap(List::stream).distinct()
        .forEach(f -> props.set(f, fieldSchema(registry.describe(f))));
    // oneOf: 조합 중 하나만 충족하면 통과
    var oneOf = schema.putArray("oneOf");
    for (var combo : spec.senderFieldOptions()) {       // 첫 조합이 preferred
        var branch = oneOf.addObject();
        var req = branch.putArray("required");
        combo.forEach(req::add);
    }
    return schema;
}
```

생성 결과 (Bangkok Bank 송금인 예):
```json
{
  "type": "object",
  "properties": {
    "lastname": { "type": "string" },
    "country_iso_code": { "type": "string", "pattern": "^[A-Z]{3}$" },
    "id_type": { "enum": ["PASSPORT","NATIONAL_ID","DRIVING_LICENSE","SOCIAL_SECURITY"] },
    "id_number": { "type": "string" },
    "address": { "type": "string" }, "city": { "type": "string" },
    "date_of_birth": { "type": "string", "format": "date" },
    "country_of_birth_iso_code": { "type": "string", "pattern": "^[A-Z]{3}$" }
  },
  "oneOf": [
    { "required": ["lastname","country_iso_code","id_type","id_number"] },
    { "required": ["lastname","address","city","country_iso_code"] },
    { "required": ["lastname","date_of_birth","country_of_birth_iso_code","address","country_iso_code"] }
  ]
}
```

검증(백엔드, fail-fast):
```java
public void validateSender(RequirementSpec spec, JsonNode senderPayload) {
    JsonSchema schema = schemaFactory.getSchema(toSenderSchema(spec));
    Set<ValidationMessage> errors = schema.validate(senderPayload);
    if (!errors.isEmpty()) {
        // Thunes 호출 전에 막는다 → 헛된 콜·REJECTED·rate limit 소모 방지
        throw new FieldValidationException(errors);
    }
}
```

`credit_party_identifiers_accepted` 도 동일하게 `oneOf`로 → "계좌번호" 또는 "IBAN+SWIFT" 중 하나 충족.

---

## 7. ④ "조합(one-of)" 처리 — 가장 헷갈리는 부분

`required_sending_entity_fields = [[A],[B,C]]` 의 의미: **(A) 또는 (B와 C)** 중 하나만 완성하면 됨.

| 단계 | 베스트 프랙티스 |
|---|---|
| **렌더** | **첫(preferred) 조합**을 기본 노출(docs: 첫 조합이 우선). 사용자에게 가장 적은 입력 경로. |
| **검증** | `oneOf` — **어느 한 조합이라도** 충족하면 통과(API가 그렇게 받으므로). |
| **식별자 조합 2개↑** | "확인 수단 선택"(예: 계좌번호 입력 vs 전화번호 입력) UI로 분기. |

> 흔한 실수: 모든 조합의 필드를 **전부 필수**로 만들어버리는 것(`AND`). 그러면 사용자가 불필요한 정보까지 강요받고, PII 과수집이 된다. 반드시 `oneOf`(택1)로.

---

## 8. ⑤ Spec을 견적에 pin (drift 안전 + 감사)

Thunes는 필수필드를 **무공지로 추가**한다. 사용자가 폼을 채우는 5분 사이에 일일 캐시 갱신이 요구사항을 바꿀 수 있다. 그러면?

> **견적(quote) 생성 시점의 `RequirementSpec`을 주문에 스냅샷·고정(pin)한다.** 폼 렌더·제출 검증은 모두 **그 pin된 Spec** 기준으로 일관되게.

```java
// 견적 시점
RequirementSpec spec = specProvider.get(payerId, txType); // 캐시에서
String specHash = hash(spec);
order.pinRequirementSpec(spec, specHash);  // 주문에 동결 (감사 로그에도)

// 제출 시점 — 캐시의 최신본이 아니라, 견적에 pin된 Spec으로 검증
validateSender(order.pinnedSpec(), senderPayload);
```

효과:
- **일관성**: 폼이 보여준 규칙 == 검증 규칙. 중간 변경에도 흔들리지 않음.
- **감사/재현성**: "이 거래는 그때 어떤 규칙으로 받았나"를 증명 가능(금융 컴플라이언스 필수). 앞선 `sender_snapshot`(주문에 송금인 동결)과 같은 사상.
- (선택) 제출 시 캐시 Spec과 `specHash`가 달라졌으면 "정책이 변경되었습니다, 다시 확인" 안내 후 재검증.

---

## 9. 전체 플로우 (시퀀스)

```
[일일 12:00 갱신]  Thunes /payers 전체 쓸기
                  → ThunesPayerMapper.toSpec()  (payer×txType 별)
                  → Redis SET thunes:disc:reqspec:{payerId}:{txType}

[견적 요청]        payer 선택 → reqspec 캐시 조회
                  → order 에 Spec pin (+ specHash)

[폼 렌더]          pinned Spec → JSON Schema 생성 → 서버주도 동적 폼
                  → 각 필드는 FieldRegistry 위젯으로 표현(모르는 필드도 안전)

[제출]            pinned Spec 으로 백엔드 검증(oneOf, fail-fast)
                  → 통과 시에만 Thunes 거래 생성 호출
                  → 실패 시 Thunes 콜 없이 즉시 사용자에게 필드 오류 반환
```

---

## 10. 금융 도메인 유의사항 (시니어가 꼭 보는 것)

1. **Fail-fast** — Thunes 호출 **전에** 검증. 잘못된 요청으로 Thunes를 찌르면 ① rate limit 소모 ② REJECTED 거래 양산 ③ 사용자 경험 악화. 검증은 우리 쪽에서 먼저.
2. **데이터 최소화(PII)** — 필수 + 통제된 선택 필드만 수집. 메타데이터 주도면 **자동으로** 최소 수집이 된다(payer가 요구하는 것만 렌더). 주민/여권번호 같은 고유식별정보 과수집은 법적 리스크.
3. **감사(Audit)** — 어떤 Spec으로 받았는지 pin + 로그. 분쟁·검사 대비.
4. **금액은 BigDecimal** — 절대 float/double 금지. `precision`/`increment` 규칙도 검증(IDR는 정수만 등).
5. **멱등성 연계** — 검증 통과 후 거래 생성은 멱등키(우리 주문 id)로. (별도 문서)
6. **Open/Closed** — 신규/미지 필드에 시스템이 안 죽고 degrade. 하드코딩 0.
7. **단일 SoT** — 폼·검증·ORIS 보고가 같은 Spec을 본다. 불일치 = 사고.

---

## 11. 주니어가 흔히 빠지는 함정 ✅ 체크리스트

- [ ] `if (country == ...)` 로 필수필드 분기하지 않았는가? (→ 메타데이터 주도로)
- [ ] 조합(array of arrays)을 `AND`(전부 필수)로 처리하지 않았는가? (→ `oneOf` 택1)
- [ ] 금액을 `double`로 받지 않았는가? (→ `BigDecimal`)
- [ ] 모르는 필드가 오면 시스템이 깨지는가? (→ Registry 기본 위젯 + 경고 로그)
- [ ] 검증을 Thunes 호출 **후**에 하고 있지 않은가? (→ fail-fast, 호출 전)
- [ ] 폼 렌더 규칙과 검증 규칙이 다른 소스인가? (→ 같은 JSON Schema 공유)
- [ ] 견적 후 정책이 바뀌면 일관성이 깨지는가? (→ Spec pin)
- [ ] Thunes raw 모양이 도메인까지 새어 들어왔는가? (→ ACL 매퍼로 차단)

---

## 12. 캐시 연계 (짝 문서와 연결)

일일 Discovery 쓸기 때 raw payer만 캐싱하지 말고, **파생 `RequirementSpec`까지 materialize**한다:

```
thunes:disc:reqspec:{payer_id}:{transaction_type}  → RequirementSpec(JSON)
```

폼/검증 레이어는 raw Thunes를 모른다. 오직 이 깨끗한 Spec만 읽는다. TTL·갱신·단일실행 정책은 [Redis 캐시 설계 문서](Thunes_Discovery_Redis캐시_설계.md) 동일.

---

## 13. 용어집

| 용어 | 뜻 |
|---|---|
| Metadata-Driven | 데이터(메타데이터)가 동작·UI·검증을 결정하는 설계. 규칙을 코드가 아니라 데이터로 표현. |
| SoT (Source of Truth) | 진실의 단일 출처. 여기선 Thunes `transaction_types`. |
| ACL (Anti-Corruption Layer) | 외부 모델을 내부 모델로 번역하는 방벽. 외부 변경의 전염 차단. |
| RequirementSpec | Thunes 요구사항을 정규화한 우리 내부 모델. |
| Field Registry | 필드명 → 위젯·검증·라벨 매핑(정적, 우리 소유). |
| oneOf | "여러 조합 중 하나만 충족" 을 표현하는 JSON Schema 키워드. |
| Server-Driven UI | 서버가 폼 스키마를 내려주고 클라이언트가 동적 렌더. |
| pin / 스냅샷 | 특정 시점의 규칙/데이터를 동결해 일관성·감사 보장. |
| Fail-fast | 외부 호출 전에 우리 쪽에서 먼저 검증해 빨리 실패. |
| Open/Closed | 확장에 열려있고(미지 필드 수용) 수정에 닫혀있는(코드 안 고침) 설계. |
