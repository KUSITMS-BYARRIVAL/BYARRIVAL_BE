---
name: ship
description: 현재 브랜치의 변경 사항을 의도별로 커밋하고, 명세 대조 셀프 리뷰를 거쳐 dev로 PR을 만든다. "/ship", "커밋하고 PR 올려줘"처럼 작업을 마무리할 때 사용한다.
argument-hint: "[commit-only]"
---

# /ship

인자 `commit-only`가 있으면 3단계까지만 한다.

## 1. 상태 확인

1. 현재 브랜치가 `main`이나 `dev`면 멈추고 작업 브랜치를 먼저 만들라고 알린다.
2. `git status --short`, `git diff --name-only dev...HEAD`로 **파일 이름만** 먼저 확인한다. `.env`, `application-local.yaml`, 키·토큰 파일(`*.pem`, `*.key`, `*credentials*` 등)이 있으면 내용을 열지 말고 멈춘 뒤 알린다.
3. 문제가 없을 때만 `git diff`, `git log dev..HEAD`로 변경 내용을 확인한다.
4. 브랜치 이름에서 이슈 번호를 읽는다. 예: `feat/#12-DCK-40-route-create` → `#12`. 브랜치 이름의 `DCK-xx`는 머지 시 Jira 하위 작업 완료에 쓰이므로 브랜치 이름을 바꾸지 않는다.

## 2. 검증

`./gradlew build`를 실행한다. 실패하면 커밋하지 않고 원인을 보고한다.

## 3. 커밋

1. 변경을 의도 단위로 나눈다. 예: Entity·Repository / 서비스 로직 / API / 테스트 / 포맷
2. 각 묶음의 커밋 메시지를 `.claude/conventions/workflow.md` 형식으로 정해 목록으로 보여 준다.
3. 사용자가 확인하면 묶음별로 `git add <파일>` 후 커밋한다. `git add .`과 `git add -A`는 쓰지 않는다.

## 4. 셀프 리뷰

1. `spec-checker` 에이전트에 브랜치 이름, 관련 DCK 티켓, `git diff dev...HEAD`를 넘겨 명세 대조를 맡긴다.
2. 불일치가 나오면 사용자에게 보여 주고, 고칠지 PR에 남길지 정한다. 고치면 2단계로 돌아간다.

## 5. PR

1. `.github/PULL_REQUEST_TEMPLATE.md` 구조로 본문 초안을 만든다.
   - 관련 이슈: `- close #<이슈번호>`
   - PR 유형 체크
   - 작업 내용: 기능 ID별 구현 요약, 추가한 API 목록
   - 추가 사항: 미확정이라 가정한 내용, spec-checker가 남긴 확인 필요 사항
   - 스크린샷 안내 콜아웃은 그대로 둔다.
2. 제목·본문·라벨을 보여 주고 **사용자가 승인한 뒤에만** push하고 `gh pr create --base dev`로 만든다.
3. PR 링크를 알려 준다. 머지는 하지 않는다.
