package com.byarrival.domain.admin.entity;

/**
 * 어드민 에디터 픽 목록의 상태.
 * HIDDEN은 운영자가 숨겼거나 연결 기사가 숨김·삭제돼 앱에 보이지 않는 것이다.
 * EDITED는 운영자가 직접 추가했거나 수정한 것, EXPOSED는 배치가 만든 그대로인 것이다.
 */
public enum AdminEditorPickStatus {
    EXPOSED,
    EDITED,
    HIDDEN
}
