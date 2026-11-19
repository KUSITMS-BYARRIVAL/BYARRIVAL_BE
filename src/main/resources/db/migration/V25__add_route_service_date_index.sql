-- =========================================================
-- 출근길 공감대(HOME-05·06) 전체 칩 집계용 서비스일 인덱스
--  - countCartedUsers·findMostCarted는 서비스일만 조건이라 기존 uk_route_user_service_date(user_id 선두)를 못 쓰고
--    route 전체를 Seq Scan으로 읽었다
--  - 로컬 측정(가상 6개월치: route 36만 행, route_card 720만 행)에서 집계 쿼리 p95 10.34ms → 1.54ms,
--    GET /api/v1/home/popular p95 759.91ms → 302.80ms(k6 50 VU · 1분)
--  - (service_date, user_id) 복합 인덱스는 단일 인덱스보다 이득이 없어 단일 인덱스로 둔다
-- =========================================================

CREATE INDEX ix_route_service_date ON route (service_date);
