-- ---------- 같은 사건 기사 중복 제외 (RSS-04, RSS-08) ----------
-- 요약이 끝난 뒤 AI로 같은 사건 기사를 묶고, 묶음마다 먼저 고른 기사 1건만 추천 후보로 남긴다.
-- 나머지는 추천에서 빼고 어느 기사와 같은 사건인지 남긴다(어드민 확인용).
ALTER TABLE article
    ADD COLUMN duplicate_of_article_id BIGINT REFERENCES article (id),
    ADD CONSTRAINT ck_article_duplicate_not_recommendable
        CHECK (duplicate_of_article_id IS NULL OR NOT recommendable),
    ADD CONSTRAINT ck_article_duplicate_not_self
        CHECK (duplicate_of_article_id IS NULL OR duplicate_of_article_id <> id);
