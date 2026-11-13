-- ---------- 보관함 삭제 되돌리기 (LIB-04) ----------
-- 보관함에서 지우면 deleted_at만 채우고, [되돌리기]하면 비워서 원래 저장 시각(정렬 위치)을 그대로 살린다.
-- 지운 뒤 같은 콘텐츠를 다시 저장하면 같은 행을 살리고 저장 시각을 새로 쓴다(uk_scrap 유지).
ALTER TABLE scrap ADD COLUMN deleted_at TIMESTAMP;

DROP INDEX ix_scrap_list;
CREATE INDEX ix_scrap_list ON scrap (user_id, target_type, created_at DESC) WHERE deleted_at IS NULL;
