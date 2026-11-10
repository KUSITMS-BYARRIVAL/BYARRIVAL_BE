-- ---------- 서비스 관심 분야 8개 (기능명세 2026-11-08, ONB-09·RSS-07) ----------
-- 순서는 온보딩 화면(UI) 배치 순서를 따른다.
-- 이미 같은 이름이 있으면 순서만 맞추고 다시 활성화한다. 다시 실행해도 결과가 같다.
INSERT INTO category (name, display_order, active) VALUES
    ('경제·금융', 1, TRUE),
    ('건강·생활', 2, TRUE),
    ('연예·문화', 3, TRUE),
    ('정치·사회', 4, TRUE),
    ('과학·환경', 5, TRUE),
    ('IT·기술', 6, TRUE),
    ('세계·국제', 7, TRUE),
    ('스포츠', 8, TRUE)
ON CONFLICT (name) DO UPDATE
    SET display_order = EXCLUDED.display_order,
        active        = TRUE;

-- 8개가 아닌 카테고리(출시 전 테스트용 등)는 선택지에서 빠지도록 비활성화한다.
-- 기사·매핑·관심 분야가 참조할 수 있어 지우지는 않는다.
UPDATE category
SET active = FALSE
WHERE name NOT IN ('경제·금융', '건강·생활', '연예·문화', '정치·사회', '과학·환경', 'IT·기술', '세계·국제', '스포츠');
