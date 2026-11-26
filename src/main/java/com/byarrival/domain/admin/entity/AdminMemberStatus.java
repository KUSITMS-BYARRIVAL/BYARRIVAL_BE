package com.byarrival.domain.admin.entity;

/**
 * 어드민 회원 목록의 회원 상태(ADM-S07). 지금 적용 중인 이용 제한이 있으면 RESTRICTED다.
 */
public enum AdminMemberStatus {
    NORMAL,
    RESTRICTED
}
