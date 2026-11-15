-- ---------- 구글 뉴스 주제 섹션 Feed 8개와 서비스 카테고리 매핑 (RSS-01, RSS-07) ----------
-- 구글 뉴스 Item에는 카테고리가 없어 Feed의 rss_category(섹션 코드)로 매핑한다. Feed 하나 = 서비스 카테고리 하나다.
--   BUSINESS → 경제·금융, HEALTH → 건강·생활, ENTERTAINMENT → 연예·문화, NATION → 정치·사회,
--   SCIENCE → 과학·환경, TECHNOLOGY → IT·기술, WORLD → 세계·국제, SPORTS → 스포츠
-- 운영 DB에 직접 등록해 둔 Feed·매핑(2026-11-10)과 같은 값이다. 이미 등록된 Feed는 rss_category를 그대로 두고
-- (비어 있으면 섹션 코드로 채운다) 매핑만 이 표대로 맞춘다. 다시 실행해도 결과가 같다.

INSERT INTO rss_feed (press_name, feed_url, rss_category)
SELECT '구글 뉴스',
       'https://news.google.com/rss/headlines/section/topic/' || topic.code || '?hl=ko&gl=KR&ceid=KR:ko',
       topic.code
FROM (VALUES ('BUSINESS'), ('HEALTH'), ('ENTERTAINMENT'), ('NATION'),
             ('SCIENCE'), ('TECHNOLOGY'), ('WORLD'), ('SPORTS')) AS topic (code)
ON CONFLICT (feed_url) DO UPDATE
    SET rss_category = COALESCE(rss_feed.rss_category, EXCLUDED.rss_category);

INSERT INTO category_mapping (feed_id, rss_category, category_id)
SELECT feed.id, feed.rss_category, category.id
FROM (VALUES ('BUSINESS', '경제·금융'),
             ('HEALTH', '건강·생활'),
             ('ENTERTAINMENT', '연예·문화'),
             ('NATION', '정치·사회'),
             ('SCIENCE', '과학·환경'),
             ('TECHNOLOGY', 'IT·기술'),
             ('WORLD', '세계·국제'),
             ('SPORTS', '스포츠')) AS topic (code, category_name)
JOIN rss_feed feed
    ON feed.feed_url = 'https://news.google.com/rss/headlines/section/topic/' || topic.code || '?hl=ko&gl=KR&ceid=KR:ko'
JOIN category
    ON category.name = topic.category_name
ON CONFLICT (feed_id, rss_category) DO UPDATE
    SET category_id = EXCLUDED.category_id;
