-- =========================================================
-- 마케팅 정보 수신 동의 약관 (ONB-03, 선택)
--  - 약관 화면의 선택 항목. 동의하지 않아도 가입·서비스 이용에 지장이 없다(필수 약관만 판단)
--  - TODO: content_url은 임시값. 노션 페이지를 웹에 게시한 공개 URL이 정해지면 새 마이그레이션으로 UPDATE한다.
-- =========================================================

INSERT INTO terms (terms_type, version, title, content_url, required)
VALUES ('MARKETING', 'v1.0', '마케팅 정보 수신 동의', 'https://byarrival.notion.site/marketing-consent', FALSE)
ON CONFLICT (terms_type, version) DO NOTHING;
