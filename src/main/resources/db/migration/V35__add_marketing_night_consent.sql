-- =========================================================
-- 야간 광고성 정보 수신 동의와 수신 동의 안내 (정보통신망법)
--  - 야간(21~08시) 광고성 정보 수신 동의 약관(선택)을 추가한다. 마케팅 수신 동의를 한 사람만 동의할 수 있다
--  - 동의 기록이 없으면 동의하지 않은 것으로 본다(기본 false). 앱에 동의 화면이 생기기 전까지는 모두 미동의다
--  - 동의·철회 결과 안내와 2년마다 하는 재확인 안내를 알림함에 남기는 알림 유형 MARKETING_CONSENT를 추가한다.
--    바꿀 때마다 남겨야 하므로 유형별 하루 1번 제약에서 뺀다
--  - marketing_consent_reconfirm: 회원별로 마지막으로 재확인 안내를 보낸 시각. 다음 안내 기준일을 정하는 데 쓴다
--  - TODO: content_url은 임시값. 공개 URL이 정해지면 새 마이그레이션으로 UPDATE한다.
-- =========================================================

INSERT INTO terms (terms_type, version, title, content_url, required)
VALUES ('MARKETING_NIGHT', 'v1.0', '야간 광고성 정보 수신 동의', 'https://byarrival.notion.site/marketing-night-consent',
        FALSE);

ALTER TABLE notification DROP CONSTRAINT ck_notification_type;
ALTER TABLE notification ADD CONSTRAINT ck_notification_type CHECK (type IN (
    'ROUTE_READY', 'ROUTE_INCOMPLETE', 'STREAK_MILESTONE', 'SMALLTALK', 'STREAK_BREAK', 'POPULAR_ARTICLE',
    'SERVICE_NOTICE', 'MARKETING_CONSENT', 'TEST'));
-- 수신 동의 안내는 하루에 여러 번 바꿔도 바꿀 때마다 남긴다(유형별 하루 1번 제약에서 뺀다)
DROP INDEX uk_notification_daily_type;
CREATE UNIQUE INDEX uk_notification_daily_type ON notification (user_id, type, service_date)
    WHERE type NOT IN ('SERVICE_NOTICE', 'MARKETING_CONSENT', 'TEST');

CREATE TABLE marketing_consent_reconfirm (
    user_id     BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    notified_at TIMESTAMP NOT NULL
);
