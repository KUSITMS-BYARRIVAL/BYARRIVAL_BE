\pset pager off
SET statement_timeout = '30s';
SET default_transaction_read_only = on;

\echo '== 접속 상태'
SELECT state, count(*) FROM pg_stat_activity WHERE datname = current_database() GROUP BY state ORDER BY 2 DESC;

\echo '== 1분 넘게 열린 트랜잭션'
SELECT pid, state, now() - xact_start AS open_for, left(query, 100) AS query
FROM pg_stat_activity
WHERE datname = current_database() AND xact_start < now() - interval '1 minute' AND pid <> pg_backend_pid()
ORDER BY xact_start;

\echo '== 큰 테이블 10개 (dead_pct가 계속 20%를 넘으면 autovacuum 확인)'
SELECT relname, pg_size_pretty(pg_total_relation_size(relid)) AS total, n_live_tup AS live, n_dead_tup AS dead,
       round(100.0 * n_dead_tup / greatest(n_live_tup + n_dead_tup, 1), 1) AS dead_pct,
       coalesce(last_autovacuum, last_vacuum)::timestamp(0) AS last_vacuum
FROM pg_stat_user_tables ORDER BY pg_total_relation_size(relid) DESC LIMIT 10;

\echo '== 한 번도 쓰지 않은 인덱스 (통계 초기화 이후 기준. 바로 지우지 말고 기간을 확인)'
SELECT s.relname AS table, s.indexrelname AS index, pg_size_pretty(pg_relation_size(s.indexrelid)) AS size
FROM pg_stat_user_indexes s JOIN pg_index i USING (indexrelid)
WHERE s.idx_scan = 0 AND NOT i.indisunique ORDER BY pg_relation_size(s.indexrelid) DESC LIMIT 10;

\echo '== Seq Scan이 많은 테이블'
SELECT relname, seq_scan, seq_tup_read, idx_scan FROM pg_stat_user_tables
WHERE seq_scan > 0 ORDER BY seq_tup_read DESC LIMIT 10;

SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_stat_statements')
   AND current_setting('shared_preload_libraries') LIKE '%pg_stat_statements%' AS has_statements \gset
\if :has_statements
\echo '== 전체 시간이 긴 쿼리 10개'
SELECT calls, round(total_exec_time::numeric) AS total_ms, round(mean_exec_time::numeric, 2) AS mean_ms,
       round(max_exec_time::numeric, 1) AS max_ms, left(regexp_replace(query, '\s+', ' ', 'g'), 120) AS query
FROM pg_stat_statements WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
ORDER BY total_exec_time DESC LIMIT 10;
\else
\echo '== 느린 쿼리: pg_stat_statements가 꺼져 있습니다 (README의 "느린 쿼리" 참고)'
\endif
