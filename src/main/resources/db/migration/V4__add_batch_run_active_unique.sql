-- 같은 서비스일에 실행 중이거나 완료된 배치는 하나만 허용한다.
-- 블루/그린 전환 등으로 앱이 두 개 떠 있을 때 두 스케줄러가 동시에 배치를 시작하는 것을 막는다.
-- 실패(FAILED)한 배치는 다시 시작할 수 있도록 제외한다.
CREATE UNIQUE INDEX uk_batch_run_service_date_active ON batch_run (service_date) WHERE status IN ('RUNNING', 'COMPLETED');
