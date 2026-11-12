-- ---------- 스몰토크 (TALK-01, PICK-01~03) ----------
-- 명세의 스몰토크는 한 줄(hook)과 연결 뉴스뿐이다. '이어서(followup)'는 쓰지 않으므로 비워 둘 수 있게 한다.
ALTER TABLE smalltalk ALTER COLUMN followup DROP NOT NULL;

-- 배치를 다시 돌려도 기사 하나에 스몰토크는 하나만 남게 한다
CREATE UNIQUE INDEX uk_smalltalk_article ON smalltalk (article_id);
