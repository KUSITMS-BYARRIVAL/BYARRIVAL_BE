# 백엔드 코드 컨벤션

`.coderabbit.yaml` 리뷰 지침과 `common/` 패키지의 기존 코드를 기준으로 정리했습니다. 둘이 어긋나면 기존 코드를 우선 따르고 사용자에게 알립니다.

## 패키지 구조

```
com.byarrival
├── common/                 # 도메인에 종속되지 않는 공통 모듈만
│   ├── exception/          # ErrorCode, CommonErrorCode, BusinessException, GlobalExceptionHandler
│   └── response/           # ApiResponse
└── domain/<도메인>/
    ├── controller/
    ├── service/
    ├── repository/
    ├── entity/
    ├── dto/                # request/, response/ 로 나눠도 됨
    └── exception/          # <도메인>ErrorCode
```

- 도메인 이름은 명세 그룹을 따릅니다. 예: `user`, `onboarding`, `route`, `article`, `quiz`, `scrap`, `stat`, `feed`, `smalltalk`, `term`
- 특정 도메인에서만 쓰는 코드는 `common/`에 넣지 않습니다.

## 계층별 책임

### Controller
- 요청 검증(`@Valid`)과 응답 변환만 합니다. 비즈니스 로직은 Service에 둡니다.
- 응답은 항상 `ApiResponse.ok(data)` 또는 `ApiResponse.ok()`로 감쌉니다.
- Entity를 요청이나 응답에 직접 쓰지 않고 DTO를 씁니다.
- URI는 복수형 명사, 동작은 HTTP Method로 표현합니다. 예: `POST /api/routes`, `GET /api/routes/{routeId}/articles/{articleId}`

### Service
- 변경 메서드는 `@Transactional`, 조회 메서드는 `@Transactional(readOnly = true)`를 붙입니다.
- 트랜잭션 안에서 외부 API(AI, RSS, 소셜 로그인) 호출을 하지 않습니다. 호출은 트랜잭션 밖에서 하고 결과 저장만 트랜잭션으로 묶습니다.
- 다른 도메인의 Repository를 직접 부르지 않고 해당 도메인의 Service를 거칩니다.

### Repository
- 반복문 안에서 쿼리를 호출하지 않습니다.
- N+1이 생길 수 있는 조회는 fetch join이나 `@EntityGraph`를 씁니다.

### Entity
- `@Setter`를 쓰지 않고, 의도가 드러나는 메서드로 상태를 바꿉니다. 예: `route.complete()`, `article.markRecommendable()`
- 기본 생성자는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`로 제한합니다.
- 연관관계는 `fetch = FetchType.LAZY`를 씁니다.
- 상태 값은 `@Enumerated(EnumType.STRING)` enum으로 둡니다. 예: 노선 `CREATED / IN_PROGRESS / COMPLETED / EXPIRED`

### DTO
- 요청은 `*Request`, 응답은 `*Response`로 이름 짓고 `record`로 만듭니다.
- 정적 팩토리 메서드는 인자가 1개면 `from`, 2개 이상이면 `of`를 씁니다.

## 예외 처리

- `RuntimeException`이나 `IllegalArgumentException`을 직접 던지지 않고 `BusinessException`을 씁니다.
- 도메인 에러는 도메인별 `<도메인>ErrorCode` enum이 `ErrorCode`를 구현합니다. 응답 `code`에는 enum 상수 이름이 그대로 쓰입니다.

```java
@Getter
@RequiredArgsConstructor
public enum RouteErrorCode implements ErrorCode {

    ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "노선을 찾을 수 없습니다."),
    ROUTE_EXPIRED(HttpStatus.BAD_REQUEST, "만료된 노선입니다.");

    private final HttpStatus status;
    private final String message;
}
```

- 범용 에러(잘못된 입력, 인증, 권한, 리소스 없음)는 `CommonErrorCode`를 재사용합니다.
- 전역 처리는 `GlobalExceptionHandler`가 맡습니다. 컨트롤러에서 try-catch로 응답을 직접 만들지 않습니다.

## 보안·정합성 기본 규칙

명세에 적혀 있지 않아도 모든 티켓에 적용하는 서버 쪽 기본 규칙입니다. 크기 제한이나 보관 기간처럼 **값을 정해야 하는 것은 정책**이므로 임의로 정하지 않고 사용자에게 묻습니다.

### 소유권 확인
- 사용자 소유 리소스(노선, 진행 상태, 퀴즈 응답, 스크랩, 설정, 통계)는 요청 ID로 바로 조회하지 않습니다. 인증된 사용자 ID와 소유자를 함께 조건으로 조회합니다. 예: `findByIdAndUserId(routeId, userId)`
- 하위 리소스는 상위 리소스 포함 여부도 확인합니다. 예: `articleId`가 해당 `routeId`의 스냅샷에 있는지
- 소유자가 아니면 다른 사용자의 데이터 존재 여부가 드러나지 않도록 `NOT_FOUND`로 응답합니다.

### 외부 URL 호출 (RSS Feed 등)
- `http`, `https`만 허용합니다.
- 호출 직전에 DNS를 해석해서 loopback, private, link-local, 클라우드 메타데이터(169.254.169.254) 주소면 차단합니다.
- 리다이렉트는 자동으로 따라가지 않고, 따라갈 경우 대상 주소를 다시 검사합니다.
- connect·read timeout과 응답 크기 상한을 반드시 겁니다.
- Feed 등록·수정은 어드민 권한에서만 가능하게 합니다.

### 파일 업로드
- 확장자와 `Content-Type`만 믿지 않고 실제 이미지로 디코딩되는지 확인합니다.
- 저장 파일명은 서버에서 새로 만들고 원본 파일명은 경로에 쓰지 않습니다.
- 허용 형식, 최대 크기, 저장 위치, 공개 URL 정책은 명세 확정값을 따릅니다.

### 인증 토큰
- 로그아웃과 탈퇴 시 서버에 저장된 refresh token을 폐기합니다.
- 폐기된 토큰으로 재발급을 요청하면 `UNAUTHORIZED`로 응답합니다.

### AI 생성 결과
- 저장 전에 형식(JSON 파싱), 타입, 개수, 글자 수를 검증합니다.
- 관계도 검증합니다. 응답의 `articleId`·`title`·`difficulty`가 요청값과 같은지, 퀴즈 정답이 선택지 안에 있는지 확인합니다.
- 검증에 실패하면 저장하지 않고 실패 상태로 기록합니다(AI-05 재처리 대상).

### 재시도와 멱등성
- 재시도할 수 있는 쓰기(완주, 퀴즈 제출, 이벤트 기록, 배치 저장)는 같은 요청이 두 번 와도 결과가 한 번만 반영되게 합니다. 유니크 제약이나 상태 확인으로 막습니다.
- 배치는 서비스일 단위로 멱등하게 만듭니다. 같은 서비스일 배치를 다시 돌려도 기사와 요약이 중복 저장되지 않아야 합니다.
- 배치 도중 실패해서 일부만 가공된 기사는 추천 후보로 노출되지 않게 합니다.

## 로그

- `@Slf4j`를 씁니다. `System.out.println`과 `printStackTrace`는 남기지 않습니다.
- 4xx는 `warn`, 5xx는 `error`로 남깁니다. `GlobalExceptionHandler`의 `[예외이름] METHOD URI - 메시지` 형식을 따릅니다.
- 토큰, 소셜 인증 값, 개인정보는 로그에 남기지 않습니다.

## 시간과 날짜

- 서비스일은 **KST 자정 기준**입니다. 서버 기본 시간대에 의존하지 말고 `ZoneId.of("Asia/Seoul")`을 명시하거나 `Clock`을 주입받습니다.
- 노선 만료(생성일 다음 날 04:00), 수집 배치(23:45~23:59)처럼 시각이 걸린 로직은 `Clock`을 주입받아 테스트에서 시각을 고정할 수 있게 만듭니다.
- 비율, 허용 범위, 읽기 속도처럼 명세에서 "설정값으로 관리"라고 한 값은 하드코딩하지 않고 `@ConfigurationProperties`로 뺍니다.

## 포맷

- 들여쓰기 4칸, 연속 줄은 4칸 추가로 들여씁니다(기존 코드와 같게).
- import는 와일드카드 없이 정렬합니다.
- 클래스 Javadoc은 역할을 한 줄로 적습니다. 메서드 주석은 이유가 분명하지 않을 때만 답니다.

## 테스트

- 테스트 메서드 이름은 한글과 밑줄로 씁니다. 예: `노선은_서비스일당_한_번만_생성된다()`
- 성공 케이스와 함께 명세의 **예외 처리** 칸에 적힌 실패 케이스를 테스트합니다.
- 컨트롤러는 `MockMvc`로 응답의 `success`, `code`, `data`를 검증합니다(`GlobalExceptionHandlerTest` 참고).
- 랜덤이 들어가는 로직(노선 기사 추출)은 `Random`을 주입받아 테스트에서 고정합니다.
