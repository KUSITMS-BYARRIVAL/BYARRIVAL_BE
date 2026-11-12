package com.byarrival.domain.stat.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.exception.CommonErrorCode;
import com.byarrival.common.time.ServiceDay;
import com.byarrival.domain.event.service.UsageEventQueryService;
import com.byarrival.domain.route.repository.DailyRideRecord;
import com.byarrival.domain.route.service.RouteRecordService;
import com.byarrival.domain.stat.dto.response.CalendarDayResponse;
import com.byarrival.domain.stat.dto.response.CalendarLevel;
import com.byarrival.domain.stat.dto.response.CalendarResponse;
import com.byarrival.domain.stat.dto.response.StatPeriod;
import com.byarrival.domain.stat.dto.response.StatSummaryResponse;
import com.byarrival.domain.stat.dto.response.UsageGraphResponse;
import com.byarrival.domain.stat.dto.response.WeeklyUsageResponse;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 나의 기록(STAT-01~03). 완독·완주는 노선의 서비스일, 앱 접속은 KST 자정 기준 날짜에 귀속한다.
 */
@Service
@RequiredArgsConstructor
public class StatService {

    private static final LocalDate EPOCH = LocalDate.of(2000, 1, 1);
    private static final int GRAPH_WEEKS = 6;
    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR = 2100;

    private final RouteRecordService routeRecordService;
    private final UsageEventQueryService usageEventQueryService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public StatSummaryResponse getSummary(Long userId, StatPeriod period) {
        LocalDate today = ServiceDay.today(clock);
        LocalDate from = switch (period) {
            case WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> today.withDayOfMonth(1);
            case ALL -> null;
        };
        Usage usage = Usage.of(routeRecordService.findDailyRecords(userId, from == null ? EPOCH : from, today));
        return new StatSummaryResponse(period, from, today, usage.usedMinutes(), usage.readCount(),
            usage.completedCount(), usage.isEmpty());
    }

    /**
     * @param year null이면 올해
     */
    @Transactional(readOnly = true)
    public CalendarResponse getCalendar(Long userId, Integer year) {
        LocalDate today = LocalDate.now(clock);
        int target = year == null ? today.getYear() : year;
        if (target < MIN_YEAR || target > MAX_YEAR) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
        LocalDate first = LocalDate.of(target, 1, 1);
        LocalDate last = LocalDate.of(target, 12, 31);
        Map<LocalDate, DailyRideRecord> rides = routeRecordService.findDailyRecords(userId, first, last).stream()
            .collect(Collectors.toMap(DailyRideRecord::getServiceDate, Function.identity()));
        Set<LocalDate> accessDates = usageEventQueryService.findAppAccessDates(userId, first, last);

        List<CalendarDayResponse> days = new ArrayList<>();
        for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
            DailyRideRecord ride = rides.get(date);
            long readCount = ride == null ? 0 : ride.getReadCount();
            days.add(new CalendarDayResponse(date, levelOf(ride, accessDates.contains(date)), readCount,
                date.isAfter(today)));
        }
        return new CalendarResponse(target, today, days);
    }

    @Transactional(readOnly = true)
    public UsageGraphResponse getWeeklyGraph(Long userId) {
        LocalDate today = ServiceDay.today(clock);
        LocalDate thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate firstWeek = thisWeek.minusWeeks(GRAPH_WEEKS - 1);
        List<DailyRideRecord> records = routeRecordService.findDailyRecords(userId, firstWeek, today);

        List<WeeklyUsageResponse> weeks = new ArrayList<>();
        for (int i = 0; i < GRAPH_WEEKS; i++) {
            LocalDate start = firstWeek.plusWeeks(i);
            LocalDate end = start.plusDays(6);
            Usage usage = Usage.of(records.stream()
                .filter(record -> !record.getServiceDate().isBefore(start) && !record.getServiceDate().isAfter(end))
                .toList());
            weeks.add(new WeeklyUsageResponse(start, end, usage.usedMinutes(), usage.readCount(),
                usage.completedCount()));
        }
        return new UsageGraphResponse(weeks, Usage.of(records).isEmpty());
    }

    // 초록3 완주 > 초록2 1건 이상 완독 > 초록1 접속만(노선을 만들었지만 읽지 않은 날 포함) > 회색
    private static CalendarLevel levelOf(DailyRideRecord ride, boolean accessed) {
        if (ride != null && ride.getCompleted()) {
            return CalendarLevel.COMPLETED;
        }
        if (ride != null && ride.getReadCount() > 0) {
            return CalendarLevel.PARTIAL;
        }
        if (ride != null || accessed) {
            return CalendarLevel.ACCESSED;
        }
        return CalendarLevel.NONE;
    }

    private record Usage(long usedMinutes, long readCount, long completedCount) {

        static Usage of(Collection<DailyRideRecord> records) {
            long minutes = 0;
            long read = 0;
            long completed = 0;
            for (DailyRideRecord record : records) {
                // 노선마다 올림해 완주 화면(ARR-02)의 이용시간과 합이 맞게 한다
                minutes += (record.getUsedSeconds() + 59) / 60;
                read += record.getReadCount();
                completed += record.getCompleted() ? 1 : 0;
            }
            return new Usage(minutes, read, completed);
        }

        boolean isEmpty() {
            return readCount == 0 && completedCount == 0;
        }
    }
}
