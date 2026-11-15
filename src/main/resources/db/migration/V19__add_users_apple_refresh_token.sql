-- ---------- Apple refresh token (MY-10 회원 탈퇴 시 Apple 연결 끊기) ----------
-- 애플 로그인 때 받은 authorization code를 바꿔 얻은 refresh token이다. 탈퇴할 때 Apple revoke에 쓴다.
-- 평문으로 두지 않고 AES-GCM으로 암호화한 값(base64)을 저장한다.
ALTER TABLE users
    ADD COLUMN apple_refresh_token TEXT;
