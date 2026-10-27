# 작업 흐름 규칙

이슈 → 브랜치 → 커밋 → PR → 머지 순서로 진행합니다. 이슈와 브랜치, Jira 하위 작업은 GitHub Actions가 함께 만듭니다.

## Jira 연동

- 이슈 폼으로 이슈를 만들면 `.github/workflows/create-jira-subtask.yml`이 다음을 처리합니다.
  1. 입력한 Jira 스토리(`DCK-xx`) 아래에 하위 작업을 만들고 진행 중으로 바꿉니다. 스토리가 To Do면 스토리도 진행 중으로 바꿉니다.
  2. 담당자는 이슈 작성자입니다(Variables `JIRA_USER_MAP`에 GitHub 아이디가 있을 때).
  3. `dev`에서 작업 브랜치를 만들고, 이슈에 Jira 링크와 브랜치 이름을 댓글로 남깁니다.
- PR이 머지되면 `.github/workflows/close-jira-subtask.yml`이 브랜치 이름의 하위 작업 키를 찾아 완료로 바꿉니다. 스토리 완료는 Jira에서 직접 합니다.
- 브랜치를 직접 만들지 않습니다. 자동화가 실패하면 이슈 댓글의 안내를 따릅니다.

## 브랜치

- 기본 브랜치는 `dev`입니다. 모든 작업 브랜치는 `dev`에서 만들고 `dev`로 PR을 보냅니다.
- `main`은 배포용입니다. `main`과 `dev`에 직접 push하지 않습니다.
- 이름 형식: `<타입>/#<이슈번호>-<Jira 하위 작업 키>-<영문-kebab-case-설명>`
  - 예: `feat/#12-DCK-40-route-create`, `fix/#15-DCK-43-route-expire-date`
  - 이슈 폼의 `브랜치 설명` 칸에 적은 값이 설명이 됩니다.
  - 타입은 이슈 제목 접두사에서 정합니다. `[FEAT]` → `feat`, `[FIX]`·`[BUG]` → `fix`, `[REFACTOR]` → `refactor`, `[CHORE]` → `chore`
  - 브랜치 이름의 `DCK-xx`로 머지 시 Jira 하위 작업이 완료되므로 이름을 바꾸지 않습니다.

## 커밋

- 형식: `<타입>: <작업 내용>` (한글, 마침표 없음)
  - 예: `feat: 공통 응답 및 예외 처리 구현`
- 타입

  | 타입 | 용도 |
  |---|---|
  | feat | 새로운 기능 |
  | fix | 버그 수정 |
  | refactor | 동작 변화 없는 구조 개선 |
  | style | 포맷팅, 코드 스타일 (동작 변화 없음) |
  | test | 테스트 코드 |
  | docs | 문서 |
  | chore | 빌드, 설정, 패키지 등 기타 |

- 한 커밋에는 한 가지 의도만 담습니다. 기능 구현과 무관한 포맷 변경은 따로 커밋합니다.
- 커밋 전에 `./gradlew build`(또는 최소 `./gradlew compileJava`)가 통과해야 합니다.
- 시크릿, `.env`, `application-local.yaml`은 커밋하지 않습니다.

## 이슈

- `.github/ISSUE_TEMPLATE/`의 이슈 폼(Feature, Bug, Fix, Refactor, Chore)을 씁니다. 폼으로 만든 이슈만 자동화가 동작합니다.
- 제목 형식: `[<타입 대문자>] <작업 내용>`. 예: `[FEAT] 노선 생성 API 구현`
- 템플릿별 라벨(폼이 자동으로 붙임): Feature → `✨Feat`, Bug → `🐞BugFix`, Chore → `⚙️Chore`, Refactor → `🔨 Refactor`, Fix → `🐞BugFix`
- `상위 작업` 칸에 Jira 스토리 번호(`DCK-xx`), `브랜치 설명` 칸에 kebab-case 설명을 적습니다.
- 본문 `참고사항`에 관련 기능 ID를 적습니다. 예: `RIDE-01, RIDE-04, RIDE-07`
- `gh issue create`로 만들 때는 본문을 폼과 같은 구조(`### 🎟️ 상위 작업 (Jira 스토리)` 같은 제목 + 다음 줄에 값)로 쓰고 같은 라벨을 붙입니다.

## PR

- 대상 브랜치는 `dev`입니다.
- 제목 형식: `[<타입 대문자>] <작업 내용>`
- 본문은 `.github/PULL_REQUEST_TEMPLATE.md`를 그대로 채웁니다.
  - `관련 이슈`에 `- close #<이슈번호>`
  - `PR 유형` 체크박스 표시
  - `작업 내용`에 구현한 기능 ID별 요약. 템플릿의 스크린샷 안내 콜아웃은 사람이 직접 처리하므로 Claude가 지우지 않습니다.
  - `추가 사항`에 명세에서 미확정이라 가정한 내용과 확인이 필요한 점
- 이슈와 같은 라벨을 붙입니다.
- CodeRabbit 자동 리뷰가 붙으므로 리뷰 코멘트를 확인하고 반영합니다.
- 머지는 사람이 합니다.
