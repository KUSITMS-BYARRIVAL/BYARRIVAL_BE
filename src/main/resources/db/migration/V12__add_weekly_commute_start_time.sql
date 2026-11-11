-- ---------- 요일별 출근(이동 시작) 시각 (MY-07, 2026-11-11 결정) ----------
-- 요일마다 이동 시작 시각과 이동시간을 함께 정한다. 둘 다 비어 있는 요일은 이동하지 않는 날(출근 알림 없음)이다.
--  - 이동시간: 5~120분, 5분 단위. 쉬는 날은 0분이 아니라 설정하지 않는 것으로 표현한다
--  - 시각과 이동시간은 함께 있거나 함께 비어 있어야 한다
--  - 설정하지 않은 요일에 노선을 만들면 기본 이동시간을 쓴다(RIDE-04)

ALTER TABLE users
    ADD COLUMN mon_start_time TIME,
    ADD COLUMN tue_start_time TIME,
    ADD COLUMN wed_start_time TIME,
    ADD COLUMN thu_start_time TIME,
    ADD COLUMN fri_start_time TIME,
    ADD COLUMN sat_start_time TIME,
    ADD COLUMN sun_start_time TIME;

-- 기존 0분(쉬는 날)은 '설정하지 않음'으로 바꾸고, 이동시간만 있던 요일은 기본 출근 시각으로 채운다.
-- 출근 시각이 없어 채울 수 없으면 그 요일 설정을 비운다.
UPDATE users SET mon_minutes = NULL WHERE mon_minutes = 0 OR (mon_minutes IS NOT NULL AND commute_start_time IS NULL);
UPDATE users SET tue_minutes = NULL WHERE tue_minutes = 0 OR (tue_minutes IS NOT NULL AND commute_start_time IS NULL);
UPDATE users SET wed_minutes = NULL WHERE wed_minutes = 0 OR (wed_minutes IS NOT NULL AND commute_start_time IS NULL);
UPDATE users SET thu_minutes = NULL WHERE thu_minutes = 0 OR (thu_minutes IS NOT NULL AND commute_start_time IS NULL);
UPDATE users SET fri_minutes = NULL WHERE fri_minutes = 0 OR (fri_minutes IS NOT NULL AND commute_start_time IS NULL);
UPDATE users SET sat_minutes = NULL WHERE sat_minutes = 0 OR (sat_minutes IS NOT NULL AND commute_start_time IS NULL);
UPDATE users SET sun_minutes = NULL WHERE sun_minutes = 0 OR (sun_minutes IS NOT NULL AND commute_start_time IS NULL);
UPDATE users
SET mon_start_time = CASE WHEN mon_minutes IS NOT NULL THEN commute_start_time END,
    tue_start_time = CASE WHEN tue_minutes IS NOT NULL THEN commute_start_time END,
    wed_start_time = CASE WHEN wed_minutes IS NOT NULL THEN commute_start_time END,
    thu_start_time = CASE WHEN thu_minutes IS NOT NULL THEN commute_start_time END,
    fri_start_time = CASE WHEN fri_minutes IS NOT NULL THEN commute_start_time END,
    sat_start_time = CASE WHEN sat_minutes IS NOT NULL THEN commute_start_time END,
    sun_start_time = CASE WHEN sun_minutes IS NOT NULL THEN commute_start_time END;

ALTER TABLE users
    DROP CONSTRAINT ck_users_mon_minutes,
    DROP CONSTRAINT ck_users_tue_minutes,
    DROP CONSTRAINT ck_users_wed_minutes,
    DROP CONSTRAINT ck_users_thu_minutes,
    DROP CONSTRAINT ck_users_fri_minutes,
    DROP CONSTRAINT ck_users_sat_minutes,
    DROP CONSTRAINT ck_users_sun_minutes;

ALTER TABLE users
    ADD CONSTRAINT ck_users_mon_commute CHECK ((mon_minutes IS NULL) = (mon_start_time IS NULL)
        AND (mon_minutes IS NULL OR (mon_minutes BETWEEN 5 AND 120 AND mon_minutes % 5 = 0))),
    ADD CONSTRAINT ck_users_tue_commute CHECK ((tue_minutes IS NULL) = (tue_start_time IS NULL)
        AND (tue_minutes IS NULL OR (tue_minutes BETWEEN 5 AND 120 AND tue_minutes % 5 = 0))),
    ADD CONSTRAINT ck_users_wed_commute CHECK ((wed_minutes IS NULL) = (wed_start_time IS NULL)
        AND (wed_minutes IS NULL OR (wed_minutes BETWEEN 5 AND 120 AND wed_minutes % 5 = 0))),
    ADD CONSTRAINT ck_users_thu_commute CHECK ((thu_minutes IS NULL) = (thu_start_time IS NULL)
        AND (thu_minutes IS NULL OR (thu_minutes BETWEEN 5 AND 120 AND thu_minutes % 5 = 0))),
    ADD CONSTRAINT ck_users_fri_commute CHECK ((fri_minutes IS NULL) = (fri_start_time IS NULL)
        AND (fri_minutes IS NULL OR (fri_minutes BETWEEN 5 AND 120 AND fri_minutes % 5 = 0))),
    ADD CONSTRAINT ck_users_sat_commute CHECK ((sat_minutes IS NULL) = (sat_start_time IS NULL)
        AND (sat_minutes IS NULL OR (sat_minutes BETWEEN 5 AND 120 AND sat_minutes % 5 = 0))),
    ADD CONSTRAINT ck_users_sun_commute CHECK ((sun_minutes IS NULL) = (sun_start_time IS NULL)
        AND (sun_minutes IS NULL OR (sun_minutes BETWEEN 5 AND 120 AND sun_minutes % 5 = 0)));
