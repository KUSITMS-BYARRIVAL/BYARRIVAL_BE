# BYARRIVAL Backend

출퇴근 이동시간에 맞춰 뉴스 묶음(노선)을 추천하고, AI 3줄 요약과 퀴즈로 읽게 하는 서비스 「도착까지」의 백엔드입니다.

## 기술 스택

- Java 21, Spring Boot 4.1, Gradle(Groovy DSL)
- Spring Web MVC, Spring Data JPA, Bean Validation, Lombok
- PostgreSQL

## 작업 원칙

- 구현 전에 관련 코드와 명세(`.claude/spec/`)를 먼저 읽습니다.
- **명세에 없는 비즈니스 정책을 만들지 않습니다.** 명세가 비어 있거나 "확정 필요", "제안"으로 표시된 부분은 사용자에게 묻고, 묻지 못하면 가정을 PR에 적습니다.
- 요청 범위 밖의 리팩터링, 의존성 추가, 설정 변경은 하지 않습니다. 필요하면 먼저 제안합니다.
- 시크릿, 토큰, `.env`, `application-local.yaml`은 읽거나 커밋하지 않습니다.

## 문서 지도

| 무엇을 할 때 | 읽을 문서 |
|---|---|
| 코드 작성 | `.claude/conventions/backend.md` |
| 이슈·브랜치·커밋·PR | `.claude/conventions/workflow.md` |
| 티켓 범위와 순서 확인 | `.claude/spec/roadmap.md` |
| 기능 요구사항 확인 | `.claude/spec/features/<그룹>.md` |

명세 파일은 기능 그룹별로 나뉘어 있습니다. 필요한 그룹만 읽습니다.

| 파일 | 기능 ID |
|---|---|
| `common.md` | C-xx, LOG-01 |
| `onboarding.md` | ONB-xx |
| `home.md` | HOME-xx, RIDE-xx |
| `news.md` | READ-xx, QUIZ-xx, ARR-xx |
| `archive.md` | LIB-xx, PICK-xx, STAT-xx |
| `my.md` | MY-xx |
| `content-admin.md` | RSS-xx, AI-xx, TALK-01 |

## 도메인 핵심 규칙

명세 여러 곳에 걸친 규칙입니다. 자세한 내용은 해당 기능 ID를 확인합니다.

- **서비스일은 KST 04:00에 바뀝니다.** 04:00부터 다음 날 03:59까지가 하루입니다. 명세에 "자정", "24:00"으로 적힌 서비스일 경계는 모두 04:00으로 읽습니다(`roadmap.md` 확정된 결정 참고).
- 기사는 해당 서비스일에만 추천되며, 23:45 배치로 수집한 기사는 다음 04:00부터 노출됩니다(RSS-08).
- 노선은 서비스일당 1회만 생성되고, 서비스일이 끝나는 04:00에 완주 여부와 상관없이 만료됩니다. 만료된 노선은 이어보기도 다시 보기도 할 수 없습니다(RIDE-01, RIDE-02, RIDE-04).
- 노선은 생성 시점의 스냅샷(기사 목록·순서·난이도·컨디션)으로 고정되며, 이후 설정이 바뀌어도 바뀌지 않습니다(RIDE-02).
- 수집부터 AI 요약까지는 매일 23:45~23:59 배치 하나로 처리합니다(RSS-02).
- AI 응답은 서버에서 반드시 검증한 뒤 저장합니다(AI-02, AI-04, TALK-01).
- 로그·추천 로그·탈퇴 회원 데이터는 6개월 보관 후 파기합니다(LOG-01, RIDE-07, MY-10).

## 검증

- 컴파일: `./gradlew compileJava`
- 테스트: `./gradlew test`
- 전체 빌드: `./gradlew build`
- 변경 범위에 맞는 검증을 실행하고, 실행하지 못한 검증은 그렇다고 알립니다.
- `.java` 파일을 고치면 훅이 자동으로 컴파일을 확인합니다. 실패하면 먼저 고칩니다.

## 스킬

- `/ticket DCK-xx`: 티켓의 명세를 확인하고 이슈·브랜치를 만든 뒤 구현하고 테스트합니다.
- `/ship`: 변경 사항을 커밋하고 셀프 리뷰를 거쳐 PR을 만듭니다.
- `/spec-sync`: 노션 기능명세서를 `.claude/spec/features/`에 다시 동기화합니다.

## 에이전트

- `spec-checker`: 구현이 명세의 입력·출력·예외 처리와 맞는지 대조합니다. `/ship`이 PR 전에 호출합니다.
