-- ---------- AI 요약 한 줄 요약·핵심 키워드 (2026-11-10 AI 파트 프롬프트 기준) ----------
-- 3문장 요약과 같은 AI 호출로 생성하고, 서버에서 검증한 뒤 저장한다. 요약 전 기사는 NULL이다.
--  - summary_one_line: 한 줄 요약. 공백 포함 40자 이내
--  - summary_keywords: 핵심 키워드 목록. 개수와 글자 수는 서버 검증 기준을 따른다
ALTER TABLE article
    ADD COLUMN summary_one_line VARCHAR(60),
    ADD COLUMN summary_keywords VARCHAR(30)[];
