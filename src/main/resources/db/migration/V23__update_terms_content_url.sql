-- =========================================================
-- 약관 본문 URL을 실제 노션 공개 페이지로 변경 (ONB-03)
--  - V5에 넣은 임시 URL(byarrival.notion.site/...)은 존재하지 않는 주소라 앱에서 본문이 열리지 않았다
--  - 약관 문구가 아니라 게시 위치만 바뀌므로 버전(v1.0)은 그대로 두고 URL만 바꾼다(재동의 불필요)
--  - 마케팅 정보 수신 동의(V22)는 공개 URL이 정해지지 않아 그대로 둔다
-- =========================================================

UPDATE terms
SET content_url = 'https://hyunjin-lee.notion.site/406a5a553281800ab919c5bc3a5fb9d8'
WHERE terms_type = 'SERVICE' AND version = 'v1.0';

UPDATE terms
SET content_url = 'https://hyunjin-lee.notion.site/406a5a5532818056926be2b8a92583f1'
WHERE terms_type = 'PRIVACY' AND version = 'v1.0';
