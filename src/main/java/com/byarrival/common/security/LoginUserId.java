package com.byarrival.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * 인증된 회원 ID를 컨트롤러 파라미터로 받는다. 예: {@code @LoginUserId Long userId}
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal(expression = "T(java.lang.Long).valueOf(subject)")
public @interface LoginUserId {
}
