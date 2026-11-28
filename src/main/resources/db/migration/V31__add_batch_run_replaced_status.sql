-- =========================================================
-- 어드민 기사 새로고침 (ARTICLE-03)
--  - REPLACED: 운영자가 새로고침해 그 서비스일 기사를 지우고 다시 실행한 배치. 완료 이력을 지우지 않고 남긴다
--  - uk_batch_run_service_date_active 는 RUNNING·COMPLETED만 보므로 REPLACED는 다시 시작을 막지 않는다
-- =========================================================

ALTER TABLE batch_run
    DROP CONSTRAINT ck_batch_run_status,
    ADD CONSTRAINT ck_batch_run_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED', 'REPLACED'));
