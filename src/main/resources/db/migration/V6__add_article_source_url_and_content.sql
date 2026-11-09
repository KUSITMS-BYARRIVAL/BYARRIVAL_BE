-- =========================================================
-- 기사 원문 크롤링 (2026-11-06, 2026-11-07 결정)
--  - original_url: RSS의 기사 링크(구글 뉴스 링크). 중복 검사(RSS-04)에 그대로 쓴다.
--  - source_url: 크롤링한 언론사 원문 주소. 앱의 원문 보기가 이 주소로 이동한다. 같은 원문은 한 번만 저장한다.
--  - 원문 본문은 article에 두지 않는다. 기사 조회가 본문 TEXT를 읽지 않도록 article_content로 분리하고,
--    AI 요약·난이도 판정이 끝나면 지운다(임시 보관).
-- =========================================================

ALTER TABLE article
    ADD COLUMN source_url VARCHAR(1000);
CREATE UNIQUE INDEX uk_article_source_url ON article (source_url) WHERE source_url IS NOT NULL;

-- 배치에서 서비스일·카테고리별 저장 수와 이미 고른 기사를 조회
CREATE INDEX ix_article_service_date_category ON article (service_date, category_id);

-- article.cleaned_content는 더 이상 쓰지 않는다(본문은 article_content에 둔다). 값을 쓴 적이 없어 늘 NULL이다.
-- 배포 전환·롤백 중 이전 버전 앱이 이 컬럼을 참조할 수 있으므로 여기서 지우지 않고, 롤백 기간이 지난 뒤 지운다.

CREATE TABLE article_content (
    article_id BIGINT    PRIMARY KEY REFERENCES article (id) ON DELETE CASCADE,
    content    TEXT      NOT NULL,                                       -- 정제한 원문 본문 (RSS-06)
    created_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Seoul'),
    updated_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Seoul')
);
