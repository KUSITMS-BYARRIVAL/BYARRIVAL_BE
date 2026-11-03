-- =========================================================
-- 초기 약관 데이터 (ONB-03)
--  - 서비스 이용약관 v1.0, 개인정보처리방침 v1.0 모두 필수
--  - TODO: content_url은 임시값. 노션 페이지를 웹에 게시한 공개 URL이 정해지면 새 마이그레이션으로 UPDATE한다.
-- =========================================================

INSERT INTO terms (terms_type, version, title, content_url, required)
VALUES ('SERVICE', 'v1.0', '서비스 이용약관', 'https://byarrival.notion.site/terms-of-service', TRUE),
       ('PRIVACY', 'v1.0', '개인정보처리방침', 'https://byarrival.notion.site/privacy-policy', TRUE);
