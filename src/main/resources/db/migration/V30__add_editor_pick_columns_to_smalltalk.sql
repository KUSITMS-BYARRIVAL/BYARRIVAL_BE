-- =========================================================
-- 어드민 에디터 픽 관리 (EDITOR-01·03·04·05·07)
--  - 배치가 만든 스몰토크를 운영자가 직접 추가·수정·숨김·삭제·순서 변경한다
--  - sort_order: 운영자가 정한 순서(1부터). 비어 있으면 카테고리 순서·기사 순서로 정렬해 뒤에 둔다
--  - created_by_admin_id: 운영자가 직접 추가했으면 그 운영자. 배치가 만들었으면 비어 있다
--  - edited_at·edited_by_admin_id: 마지막으로 수정한 시각과 운영자
--  - deleted_at·deleted_by_admin_id: 삭제 표시. 행을 남겨 보관함 저장이 깨지지 않게 하고,
--    배치 재실행이 그 자리를 다른 기사로 다시 채우지 않게 한다
-- =========================================================

ALTER TABLE smalltalk
    ADD COLUMN sort_order          INT,
    ADD COLUMN created_by_admin_id BIGINT REFERENCES admin_account (id),
    ADD COLUMN edited_at           TIMESTAMP,
    ADD COLUMN edited_by_admin_id  BIGINT REFERENCES admin_account (id),
    ADD COLUMN deleted_at          TIMESTAMP,
    ADD COLUMN deleted_by_admin_id BIGINT REFERENCES admin_account (id),
    ADD CONSTRAINT ck_smalltalk_sort_order CHECK (sort_order IS NULL OR sort_order > 0),
    ADD CONSTRAINT ck_smalltalk_edited CHECK ((edited_at IS NULL) = (edited_by_admin_id IS NULL)),
    ADD CONSTRAINT ck_smalltalk_deleted CHECK ((deleted_at IS NULL) = (deleted_by_admin_id IS NULL));
