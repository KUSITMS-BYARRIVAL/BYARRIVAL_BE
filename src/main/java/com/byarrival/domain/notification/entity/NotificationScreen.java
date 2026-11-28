package com.byarrival.domain.notification.entity;

/**
 * 푸시를 눌렀을 때 앱이 여는 화면. 서버와 앱이 같은 값을 쓴다. 푸시 데이터의 screen에 넣는다.
 * NOTICE는 공지사항 웹뷰라 url이 함께 간다.
 */
public enum NotificationScreen {
    HOME,
    TODAY_ROUTE,
    TODAY_SMALLTALK,
    MY_RECORD,
    NOTICE
}
