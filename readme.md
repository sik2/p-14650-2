# 0000 - 초기 프로젝트 설정

## 개요
Spring Boot 4.0.1 기반의 모놀리식 백엔드 애플리케이션 초기 설정

## 기술 스택
- Java 25
- Spring Boot 4.0.1
- Spring Data JPA
- Spring Batch
- H2 Database
- Lombok

## 주요 도메인
- Member: 회원 관리
- Market: 상품 및 주문 관리
- Cash: 지갑 및 결제 관리
- Payout: 정산 관리
- Post: 게시글 관리

---

# 0001 - Spring Security 인증/인가 시스템 구현

## 개요
JWT 기반 인증/인가 시스템 도입

## 변경 사항

### 의존성 추가
- spring-boot-starter-security
- spring-boot-starter-json
- springdoc-openapi-starter-webmvc-ui
- jjwt-api, jjwt-impl, jjwt-jackson

### Security 패키지 (com.back.global.security)
- SecurityConfig.java: Spring Security 설정
- CustomAuthenticationFilter.java: JWT/API Key 기반 인증 필터
- SecurityUser.java: UserDetails 구현체
- PasswordEncoderConfig.java: BCrypt 패스워드 인코더

### Rq 클래스 (com.back.global.rq)
- Request Scope 빈으로 현재 요청 컨텍스트 관리

### 전역 설정 (com.back.global.global)
- SwaggerConfig.java: Swagger/OpenAPI 설정
- JacksonConfig.java: ObjectMapper 빈 등록

### Util 클래스 확장
- Util.jwt: JWT 토큰 생성/검증/파싱
- Util.json: JSON 직렬화

### Member 도메인 변경
- BaseMember: apiKey 필드, isAdmin(), getAuthorities() 추가
- SourceMember: UUID 기반 apiKey 자동 생성
- MemberRepository: findByApiKey() 추가
- MemberJoinUseCase: 비밀번호 BCrypt 암호화
- MemberLoginUseCase: 로그인 처리 (신규)
- MemberAuthTokenUseCase: JWT 토큰 생성/검증 (신규)

### API 엔드포인트
- POST /api/v1/member/members/join: 회원가입
- POST /api/v1/member/members/login: 로그인
- DELETE /api/v1/member/members/logout: 로그아웃
- GET /api/v1/member/members/me: 현재 로그인 사용자 정보

### API 권한 설정 (SecurityConfig)
| 엔드포인트 | 권한 |
|-----------|------|
| /api/v1/member/members/login | permitAll |
| /api/v1/member/members/join | permitAll |
| /api/v1/member/members/logout | permitAll |
| /api/v1/member/members/randomSecureTip | permitAll |
| /api/v1/member/members/me | authenticated |
| /api/v1/market/orders/** | authenticated |
| 내부용 API (/{id}, /by-apikey, /validate-token 등) | systemApiKey 검증 |
