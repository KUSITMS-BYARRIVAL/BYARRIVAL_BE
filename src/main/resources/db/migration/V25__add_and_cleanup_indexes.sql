-- =========================================================
-- 인덱스 추가·정리
-- =========================================================

-- 출근길 공감대(HOME-05·06) 전체 칩 집계용 서비스일 인덱스
--  - countCartedUsers·findMostCarted는 서비스일만 조건이라 기존 uk_route_user_service_date(user_id 선두)를 못 쓰고
--    route 전체를 Seq Scan으로 읽었다
--  - 로컬 측정(가상 6개월치: route 36만 행, route_card 720만 행)에서 집계 쿼리 p95 10.34ms → 1.54ms,
--    GET /api/v1/home/popular p95 759.91ms → 302.80ms(k6 50 VU · 1분)
--  - (service_date, user_id) 복합 인덱스는 단일 인덱스보다 이득이 없어 단일 인덱스로 둔다
CREATE INDEX ix_route_service_date ON route (service_date);

-- 탈퇴 회원 파기(MY-10) 연쇄 삭제용 인덱스
--  - 회원을 지우면 route가 ON DELETE CASCADE로 지워지고, 노선마다 usage_event_log를 route_id로 찾는다.
--    기존 usage_event_log 인덱스는 모두 user_id 선두라 route_id만으로는 못 쓰고 로그 전체를 Seq Scan으로 읽었다
--  - recommendation_log도 user_id로 연쇄 삭제되는데 user_id 인덱스가 없었다
CREATE INDEX ix_usage_event_route ON usage_event_log (route_id) WHERE route_id IS NOT NULL;
CREATE INDEX ix_recommendation_log_user ON recommendation_log (user_id);

-- 중복 인덱스 정리
--  - ix_quiz_article·ix_smalltalk_article은 V17·V18의 유니크 인덱스(uk_quiz_article·uk_smalltalk_article)와
--    같은 컬럼이라 지운다
DROP INDEX ix_quiz_article;
DROP INDEX ix_smalltalk_article;
