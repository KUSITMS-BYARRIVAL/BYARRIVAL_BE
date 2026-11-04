-- =========================================================
-- RSS Feed 연속 실패 횟수 (RSS-01 접근 불가 Feed 자동 비활성화)
--  - 서비스일당 1회만 세고, 기준 횟수에 닿으면 비활성화
--  - 수집에 성공하면 초기화
-- =========================================================

ALTER TABLE rss_feed
    ADD COLUMN consecutive_failure_count INT  NOT NULL DEFAULT 0,
    ADD COLUMN last_failed_service_date  DATE,                           -- 같은 서비스일 중복 집계 방지
    ADD CONSTRAINT ck_rss_feed_failure_count CHECK (consecutive_failure_count >= 0);
