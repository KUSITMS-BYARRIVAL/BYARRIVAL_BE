-- =========================================================
-- 어드민 권한을 운영자(OPERATOR) 하나로 정리한다 (ADM-02, 2026-11-29 결정)
--  - 에디터(EDITOR) 계정은 운영자로 바꾼다. 권한이 넓어지므로 기존 토큰은 무효로 만들어 다시 로그인하게 한다
--  - role 컬럼은 나중에 권한이 다시 생길 때를 위해 남긴다
-- =========================================================

UPDATE admin_account
SET role          = 'OPERATOR',
    token_version = token_version + 1,
    updated_at    = (now() AT TIME ZONE 'Asia/Seoul')
WHERE role = 'EDITOR';

ALTER TABLE admin_account DROP CONSTRAINT ck_admin_account_role;
ALTER TABLE admin_account ADD CONSTRAINT ck_admin_account_role CHECK (role IN ('OPERATOR'));
