# fx-gateway 로직 보강 설계 — Resilience / Observability / 콜백 / 시크릿

> 대상: **해외거래망 전용 GW(외부 통신 경계 · 무상태 패스스루)**.
> 범위 제외: 원장/한도/상태매핑/대사 등 **상태 기반 비즈니스 로직(= API 서버 책임)**.
> 진행 방식: 본 문서로 Tier 1~5 설계 → **각 Tier 사용자와 검토 → 합의 후 구현**.
> 원칙: Thunes 스펙 미확정값(서명방식·rate limit·재시도 헤더 등)은 **추측 금지 → 추상화/훅만**. 폐쇄망 Nexus 의존성 가용성 선확인.

상태: ⬜ 미착수 / 🔲 검토중 / ✅ 합의 / 🚧 구현중 / ☑️ 완료

---

## Tier 1. 불확실 응답의 멱등 복구 (double-send 방지)  ⬜

### 문제
거래 생성·확정 POST 중 **타임아웃/커넥션 끊김**이 나면 "Thunes에 갔는지 안 갔는지 모름". 여기서 **블라인드 재시도 = 이중 송금**(돈 사고).

### 설계 방향
이미 구현된 **`ext-{external_id}`** 경로(우리 번호 조회)를 활용한 **"불확실 → 조회 후 판단"** 래퍼.

```
POST create/confirm
 ├─ 2xx                → 그대로 반환
 ├─ 4xx(명확 실패)      → 예외(ThunesApiException) 전파, 재시도 X
 └─ 타임아웃/5xx/IO(불확실)
        → GET ext-{our_external_id} 로 실제 생성/확정됐는지 확인
            ├─ 존재 & 원하는 상태  → 그 결과 반환 (이미 처리됨)
            ├─ 미존재             → 안전하게 1회 재시도(같은 external_id)
            └─ 여전히 불확실       → 예외 + 상위(API서버)가 후속 reconcile
```

### 구현 요소
- `IdempotentCall` 헬퍼(또는 `ThunesClient` 내부 메서드): `(작업, 복구조회)` 패턴
- 적용: `createTransaction*`, `confirmTransaction*` (멱등키=external_id 있는 호출만)
- **비적용**: `createQuotation`(external_id 있으나 자금이동 아님 — 재견적이 더 안전), GET류

### 전제 / TBD
- create/confirm 호출 시 **external_id가 항상 존재**해야 함(현재 TransactionRequest.externalId 필수 ✅)
- "불확실"의 정의: connect/read timeout, IOException, 5xx → 재시도군 / 4xx·명시에러 → 비재시도군 (Tier 2의 에러분류와 공유)

### 리스크
- ext 조회 자체도 실패할 수 있음 → 그땐 **불확실 상태로 예외** 올리고 상위가 reconcile (GW가 무한루프 금지, 시도 횟수 상한)

---

## Tier 2. 재시도 안전분리 + 장애격리 + 전송 하드닝  ⬜

### 2-1. 에러 분류 & 재시도 정책
- **재시도 가능**: connect/read timeout, IOException, HTTP 5xx, (429는 백오프)
- **재시도 금지(terminal)**: 4xx 일반, `1007005`(잔액부족) 등 → 즉시 중단 + 알림
- **안전(GET/ping/payers)** 만 자동 재시도, **위험(create/confirm)** 은 Tier 1 멱등복구로만

### 2-2. Resilience4j (서킷/벌크헤드/레이트리밋)
- **Circuit Breaker**: Thunes 연속 실패 시 빠른 실패(서킷 오픈) → 스레드/커넥션 고갈 방지
- **Bulkhead**: Thunes 호출 동시성 상한 격리
- **RateLimiter**: Thunes 계약 rate limit 선제 throttle (429 맞기 전)
- 적용: `ThunesClient` 호출에 데코레이션 (어노테이션 또는 프로그램틱)

### 2-3. 전송 계층 하드닝 (현재 실제 갭)
- 현재 `SimpleClientHttpRequestFactory` = **커넥션 풀 없음** → 부하 병목
- **Apache HttpClient 5 풀링**(`HttpComponentsClientHttpRequestFactory`)으로 교체
- connect / read / (write) 타임아웃 분리, keep-alive, 풀 사이즈 설정
- mTLS/IPSec 등 전송보안 설정 위치

### 전제 / TBD
- **폐쇄망 Nexus에 `resilience4j-spring-boot3`, `httpclient5` 존재 여부 선확인** (없으면 반입 절차)
- 서킷 임계치·rate limit 수치 = Thunes 계약/운영 기준 (TBD → 보수적 기본값 + 설정화)

---

## Tier 3. 관측 · 감사 (Observability / Audit)  ⬜

### 3-1. 아웃바운드 감사 로그 (audit trail)
- 모든 **자금이동 호출**(create/confirm/cancel)의 요청·응답·status·지연·external_id·Thunes id 를 **불변 기록**
- 송금업 규제상 "누가 언제 무엇을" 추적 필수
- ⚠️ **결정 필요**: GW는 무상태 지향 → 감사로그를 **구조화 로그(파일→ELK/로그파이프라인)** 로 흘릴지 vs **DB 적재**(GW에 상태 도입). 권장: **로그 파이프라인**(무상태 유지)

### 3-2. Correlation ID + 메트릭 + 트레이싱
- 우리 거래키 ↔ trace/correlation id 전파 (요청 헤더 + MDC)
- Micrometer 메트릭: 엔드포인트별 지연/에러율/**Thunes 에러코드 분포**
- (선택) 분산 트레이싱(Micrometer Tracing)
- 잔액 모니터링: `/balances` 주기 조회 + 임계치 알림 (소진=서비스중단 → 운영 SLO)

### 전제 / TBD
- 로그 파이프라인/모니터링 스택(ELK·Prometheus 등) 사내 인프라 확인
- Actuator/Micrometer 의존성 (Nexus 확인)

---

## Tier 4. 콜백 엣지 보안 · 신뢰성  ⬜

### 4-1. 콜백 보안 (인바운드 외부 인입)
- **서명/HMAC 검증** + **IP 허용목록** + **replay 방지**(timestamp/nonce)
- 인터셉터/필터로 `/v1/thunes/callback/**` 에만 적용
- ⚠️ **서명 방식은 Thunes 스펙/계약 확인 전까지 TBD → 검증 인터페이스(SignatureVerifier)만 추상화**, 실제 알고리즘은 확인 후 주입

### 4-2. durable 수신 (유실/중복 안전)
- at-least-once → **중복 콜백 정상**. 수신 멱등 처리 필요
- 패턴 후보:
  - (A) **수신 즉시 저장 → 2XX 응답 → 비동기 처리/API서버 전달** (inbox/outbox) — 유실 0, 단 GW에 저장소 필요(상태 도입)
  - (B) **동기 전달**: GW가 API서버로 즉시 포워딩 성공해야 2XX — 단순하나 API서버 장애 시 Thunes 재시도에 의존
- ⚠️ **결정 필요**: A(견고·상태도입) vs B(단순·무상태유지). GW 무상태 원칙과 충돌 → 검토 포인트

### 전제 / TBD
- Thunes 콜백 서명 헤더/알고리즘, 소스 IP 대역 (확인 필요)
- 콜백 키가 송금 API 키와 별도(문서상 "Callback용 키 따로 발급")

---

## Tier 5. 자격증명 관리  ⬜

### 설계
- api_key/secret **Vault**(HashiCorp/사내 KMS) 연동 + **로테이션**
- **콜백 전용 키 분리** (`thunes.callback.*`)
- 현재 구조(`application.yml ${ENV}` + gitignore된 local)에서 → 운영은 Vault 주입으로 승격

### 전제 / TBD
- 사내 Vault/시크릿 매니저 종류·접근 방식 확인
- 로테이션 주기·무중단 갱신 메커니즘

---

## 공통 고려사항
- **폐쇄망 Nexus 의존성 가용성**을 Tier별 착수 전 확인 (resilience4j, httpclient5, micrometer-tracing 등)
- **추측 금지**: Thunes 서명방식·rate limit·재시도 헤더·IP 대역은 확인 전 **추상화/훅/보수적 기본값**으로만
- **무상태 원칙 vs 상태 도입**(감사로그·콜백 inbox)은 Tier 3/4의 핵심 결정 포인트

## 진행 순서 (합의된 우선순위)
1. Tier 1 멱등 복구 → 2. Tier 2 재시도/격리/전송 → 3. Tier 3 관측/감사 → 4. Tier 4 콜백 → 5. Tier 5 시크릿

---

## 검토 로그
- (작성) 초안 — 각 Tier 사용자 검토 대기
