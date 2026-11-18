package com.byarrival.domain.route.event;

import java.time.LocalDate;

/**
 * 노선을 처음 완주했을 때 발행한다. 같은 노선에 도착하기를 다시 눌러도 다시 발행하지 않는다.
 */
public record RouteCompletedEvent(Long userId, Long routeId, LocalDate serviceDate) {
}
