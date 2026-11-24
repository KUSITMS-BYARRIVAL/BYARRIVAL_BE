-- 부하 테스트용 가상 데이터. 실행 변수: users(회원 수)
-- 외부 서비스와 실제 회원 정보는 쓰지 않는다.
BEGIN;

INSERT INTO users (provider, provider_user_id, nickname, travel_type, default_commute_minutes,
                   onboarding_status, onboarded_at)
SELECT 'KAKAO', 'perf-' || n, '부하' || n, 'WORKER', 30, 'COMPLETED', now() AT TIME ZONE 'Asia/Seoul'
FROM generate_series(1, :users) n;

INSERT INTO user_interest_category (user_id, category_id)
SELECT u.id, c.id FROM users u JOIN category c ON c.active AND (c.id + u.id) % 2 = 0
WHERE u.provider_user_id LIKE 'perf-%';

INSERT INTO user_job_group (user_id, job_group, sort_order)
SELECT id, (ARRAY['IT_DATA', 'DESIGN', 'MEDICAL', 'ETC_FREELANCE'])[1 + id % 4], 1
FROM users WHERE provider_user_id LIKE 'perf-%';

-- 오늘(04:00 기준 서비스일)과 지난 29일의 기사를 카테고리마다 20건씩 만든다
INSERT INTO article (feed_id, category_id, guid, original_url, title, press_name, published_at, cleaned_content,
                     summary_sentence1, summary_sentence2, summary_sentence3, summary_one_line, summary_keywords,
                     service_date, difficulty, reading_minutes, process_status, recommendable)
SELECT m.feed_id, m.category_id, 'perf-' || m.category_id || '-' || d || '-' || n,
       'https://example.com/perf/' || m.category_id || '/' || d || '/' || n,
       '부하 테스트 기사 ' || m.category_id || '-' || d || '-' || n, '테스트일보',
       now() AT TIME ZONE 'Asia/Seoul', repeat('본문 ', 300), '첫 문장입니다', '둘째 문장입니다', '셋째 문장입니다',
       '한 줄 요약입니다', ARRAY['가', '나', '다'],
       ((now() AT TIME ZONE 'Asia/Seoul') - interval '4 hours')::date - d,
       CASE WHEN n % 2 = 0 THEN 'HARD' ELSE 'EASY' END, 2 + (n % 4), 'READY', TRUE
FROM category_mapping m CROSS JOIN generate_series(0, 29) d CROSS JOIN generate_series(1, 20) n;

INSERT INTO smalltalk (article_id, service_date, hook)
SELECT id, service_date, '이 소식 들으셨어요?' FROM article
WHERE guid LIKE 'perf-%' AND (guid LIKE '%-1' OR guid LIKE '%-2');

-- 지난 29일의 완주 노선. 서비스일마다 회원의 40%가 탔다고 본다.
-- 오늘은 뒤쪽 40% 회원만 이미 완주했다고 두고(인기 집계 대상), 앞쪽 60%는 k6 commute가 노선을 새로 만든다
INSERT INTO route (user_id, service_date, route_condition, target_minutes, status, fallback_step, expires_at,
                   boarded_at, started_at, completed_at)
SELECT u.id, s.day, 'SUNNY', 30, 'COMPLETED', 0, s.day + interval '28 hours',
       s.day + interval '8 hours', s.day + interval '8 hours', s.day + interval '9 hours'
FROM users u
CROSS JOIN LATERAL (
    SELECT ((now() AT TIME ZONE 'Asia/Seoul') - interval '4 hours')::date - d AS day, d FROM generate_series(0, 29) d
) s
WHERE u.provider_user_id LIKE 'perf-%'
  AND CASE WHEN s.d = 0 THEN substr(u.provider_user_id, 6)::int > :users * 0.6 ELSE (u.id + s.d) % 5 < 2 END;

INSERT INTO route_card (route_id, article_id, pool_order, difficulty, reading_minutes, recommended, cart_order, carted_at, read_at)
SELECT r.id, a.id, a.rn, a.difficulty, a.reading_minutes, a.rn <= 8, CASE WHEN a.rn <= 8 THEN a.rn END,
       CASE WHEN a.rn <= 8 THEN r.boarded_at END, CASE WHEN a.rn <= 8 THEN r.completed_at END
FROM route r
JOIN (
    SELECT id, service_date, difficulty, reading_minutes,
           row_number() OVER (PARTITION BY service_date ORDER BY id) AS rn
    FROM article WHERE guid LIKE 'perf-%'
) a ON a.service_date = r.service_date AND a.rn <= 16;

COMMIT;
ANALYZE;
SELECT min(id) AS first_user_id, count(*) AS users FROM users WHERE provider_user_id LIKE 'perf-%' \gset
\echo FIRST_USER_ID=:first_user_id
