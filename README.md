# roomlog-Server

> 자취/원룸 거주자를 위한 **3D 방 기록 & 하자 관리 서비스**의 백엔드 서버
> 2026 한이음 드림업 프로젝트 (Team 404)

LiDAR로 촬영한 방을 3D로 재구성하고, AI가 하자를 자동 탐지해 수리 견적까지 연결하는 서비스의 Spring Boot 백엔드입니다.

## ✨ 주요 기능

| 기능 | 설명 |
|------|------|
| **파일 업로드 & R2 저장** | multipart로 수신한 LiDAR ZIP 파일을 Cloudflare R2에 업로드하고 CDN URL을 DB에 저장합니다. |
| **비동기 AI 연동** | AI 서버에 작업을 요청하고 즉시 202를 반환합니다. 완료 후 콜백으로 결과를 수신해 DB에 반영합니다. |
| **하자 비용 자동 산정** | 콜백으로 수신한 하자 데이터에 종류·심각도별 단가 테이블과 면적(㎠)을 적용해 수리 예상 비용을 자동 계산합니다. |
| **이중 인증 분리** | 사용자 API는 JWT, AI 콜백 엔드포인트는 API Key로 인증을 분리해 내부 통신을 보호합니다. |
| **계층형 데이터 관리** | 집→방→스캔→분석→하자 계층을 JPA cascade로 관리해 상위 삭제 시 하위 데이터를 일괄 제거합니다. |
| **Kakao Local API 연동** | 방 주소 기반으로 주변 수리 업체를 검색하고 견적·수리 이력을 저장합니다. |

## 🚀 백엔드 포인트

### 비동기 AI 파이프라인

3D 재구성과 하자 분석 모두 처리 시간이 길어 동기 응답이 불가합니다. 모든 작업은 클라이언트에 201을 즉시 반환하고, AI 서버가 완료 후 콜백 엔드포인트로 결과를 전송합니다. 클라이언트는 상태 폴링(`/status`)으로 완료 여부를 확인합니다.

```
POST /rooms/{id}/scans      POST /analyses (D01)        POST /analyses (D02)
        │                           │                              │
  201 즉시 반환              201 즉시 반환                  201 즉시 반환
  (SCANNING)                 (ANALYZING)                   (ANALYZING)
        │                           │                              │
  R2에 ZIP 업로드            AI 서버 요청                   AI 서버 요청
  AI 서버 요청               POST /defect-detection         POST /defect-comparison
  POST /reconstruction               │                              │
        │                   [AI 서버 처리 중]              [AI 서버 처리 중]
  [AI 서버 처리 중]                  │                              │
        └──────────────────── callback_url POST ───────────────────┘
                                     │
                              결과 수신 · DB 반영
                              하자 비용 산정
                              상태 → COMPLETED
```

### 하자 비용 산정

AI 서버 콜백으로 수신한 하자 데이터를 저장할 때, DB의 단가 테이블(`repair_unit_price`, `repair_visit_fee`)을 참조해 각 하자의 수리 예상 비용을 자동 산정합니다.

```
콜백 수신 (하자 종류 · 심각도 · 면적 ㎠)
        ↓
repair_unit_price 조회 (종류 × 심각도별 ㎡당 표준시장단가 + 주재료비)
        ↓
하자별 비용 = 기본료 5,000원 + (㎡당 단가 ÷ 100)원/㎠ × 면적(㎠), 100원 단위 올림
        ↓
총액 = 하자별 비용 합 + 가장 비싼 하자 종류의 출장비(시중노임단가 0.5일) 한 번
        ↓
Defect · Analysis 저장
```

### 계층형 데이터 구조

집 → 방 → 스캔 → 분석 → 하자로 이어지는 계층 구조를 JPA로 관리합니다. 상위 엔티티 삭제 시 하위 데이터가 `cascade = CascadeType.ALL` + `orphanRemoval`로 일괄 삭제되어 고아 데이터가 남지 않습니다.

```
User
 └── House (집 · 주소 · 입주일 · 퇴거일)
      └── Room (방 이름 · 면적 · 구조)
           └── Scan (상태 · PLY URL · 썸네일 URL)
                └── Analysis (단일/비교 · 상태 · 요약)
                     └── Defect (종류 · 심각도 · 3D 좌표 · 예상 비용)
                          └── Estimate (수리 업체 · 요청 금액 · 상태)
                               └── Repair (실비용 · 메모)
```

### 이중 인증 분리

외부 사용자와 AI 서버 간 인증을 필터 레이어에서 분리합니다.

- **사용자 API** — `Authorization: Bearer {accessToken}` JWT 검증 (`JwtAuthenticationFilter`)
- **AI 콜백 API** — `X-Api-Key` 헤더 검증 (`ApiKeyFilter`), 내부 전용·외부 노출 차단

## 🛠 기술 스택

- **언어/프레임워크**: Java 17, Spring Boot 3.2.4
- **보안**: Spring Security, JWT (Access 24h / Refresh 14d)
- **데이터**: MySQL, Spring Data JPA
- **파일 저장**: Cloudflare R2 (AWS S3 호환, boto3 SDK)
- **외부 API**: Kakao Local API (수리 업체 검색), AI Server (LiDAR 재구성 / 하자 탐지)
- **문서**: SpringDoc OpenAPI (Swagger UI `/swagger-ui/index.html`)

## 🏛 패키지 구조

```text
com.roomlog
├── auth          — 회원가입 · 로그인 · JWT 재발급
├── user          — 사용자 프로필
├── house         — 집 관리
├── room          — 방 관리
├── scan          — LiDAR 스캔 업로드 및 3D 재구성 (R01)
├── analysis      — 하자 분석 단일(D01) / 비교(D02) · 수리 업체 검색
├── defect        — 하자 정보 및 비용 산정
├── estimate      — 견적 요청 및 수리 업체 연결
├── repair        — 수리 완료 이력
└── global
    ├── config    — Security · Swagger · R2 · Jackson 설정
    ├── exception — 에러 코드 및 전역 예외 처리
    ├── infra     — AI 클라이언트 · Kakao API 클라이언트 · R2 업로더
    ├── response  — 공통 API 응답 래퍼
    └── security  — JWT 필터 · API Key 필터
```

## ⚙️ 환경 변수

프로덕션 환경(`--spring.profiles.active=prod`)에서 아래 환경 변수가 필요합니다.

| 환경 변수 | 설명 | 기본값 |
|-----------|------|--------|
| `PORT` | 서버 포트 | `8080` |
| `JWT_SECRET` | JWT 서명 키 (32자 이상) | 필수 |
| `AI_API_KEY` | AI 서버 인증 키 (콜백 엔드포인트 보호용) | 필수 |
| `AI_SERVER_URL` | AI 서버 베이스 URL | 필수 |
| `AI_CALLBACK_BASE_URL` | AI 콜백 수신 URL (이 서버의 외부 주소) | 필수 |
| `KAKAO_API_KEY` | Kakao REST API 키 (수리 업체 검색) | 필수 |
| `SPRING_DATASOURCE_URL` | MySQL JDBC URL | 필수 |
| `SPRING_DATASOURCE_USERNAME` | DB 사용자명 | 필수 |
| `SPRING_DATASOURCE_PASSWORD` | DB 비밀번호 | 필수 |
| `R2_ACCOUNT_ID` | Cloudflare R2 계정 ID | 필수 |
| `R2_ACCESS_KEY` | R2 액세스 키 | 필수 |
| `R2_SECRET_KEY` | R2 시크릿 키 | 필수 |
| `R2_BUCKET` | R2 버킷 이름 | 필수 |
| `R2_CDN_URL` | R2 퍼블릭 CDN URL | 필수 |

> 로컬 개발 환경(`--spring.profiles.active=dev`)은 `application-dev.yml`에 직접 값을 설정합니다.

## ▶️ 실행

```bash
# 개발 서버
./gradlew bootRun --args='--spring.profiles.active=dev'

# 프로덕션 빌드 후 실행
./gradlew build
java -jar build/libs/roomlog-server-*.jar --spring.profiles.active=prod
```

## 📄 License

[MIT License](LICENSE) © 2026 Team404
