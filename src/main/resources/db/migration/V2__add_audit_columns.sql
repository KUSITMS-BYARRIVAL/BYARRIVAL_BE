-- =========================================================
-- 모든 테이블에 created_at, updated_at 추가 (BaseEntity 공통 상속)
--  - 기존 행은 마이그레이션 시각(KST)으로 채움
-- =========================================================

ALTER TABLE refresh_token
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Seoul');

ALTER TABLE terms
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Seoul');

ALTER TABLE user_terms_agreement
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Seoul'),
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Seoul');

ALTER TABLE category
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Seoul'),
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Seoul');

ALTER TABLE user_interest_category
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Seoul');

ALTER TABLE user_notification_setting
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Seoul');

ALTER TABLE category_mapping
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Seoul'),
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Seoul');

ALTER TABLE quiz
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Seoul');

ALTER TABLE smalltalk
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Seoul');

ALTER TABLE scrap
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Seoul');

ALTER TABLE ai_generation_job
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Seoul');

ALTER TABLE batch_run
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Seoul'),
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'Asia/Seoul');
