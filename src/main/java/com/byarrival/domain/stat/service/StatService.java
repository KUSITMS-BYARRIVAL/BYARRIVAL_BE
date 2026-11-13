package com.byarrival.domain.stat.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.exception.CommonErrorCode;
import com.byarrival.common.time.ServiceDay;
import com.byarrival.domain.event.service.UsageEventQueryService;
import com.byarrival.domain.route.entity.RouteCondition;
import com.byarrival.domain.route.repository.DailyRideRecord;
import com.byarrival.domain.route.service.RouteRecordService;
import com.byarrival.domain.route.service.RouteStreakService;
import com.byarrival.domain.stat.dto.response.CalendarDayResponse;
import com.byarrival.domain.stat.dto.response.CalendarLevel;
import com.byarrival.domain.stat.dto.response.CalendarResponse;
import com.byarrival.domain.stat.dto.response.ConditionCountResponse;
import com.byarrival.domain.stat.dto.response.ConditionStatResponse;
import com.byarrival.domain.stat.dto.response.StatPeriod;
import com.byarrival.domain.stat.dto.response.StatSummaryResponse;
import com.byarrival.domain.stat.dto.response.StreakResponse;
import com.byarrival.domain.stat.dto.response.UsageGraphResponse;
import com.byarrival.domain.stat.dto.response.WeeklyUsageResponse;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 나의 기록(STAT-01~03)과 마이 홈 카드. 완독·완주는 노선의 서비스일, 앱 접속은 KST 자정 기준 날짜에 귀속한다.
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
    private final RouteStreakService routeStreakService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public StatSummaryResponse getSummary(Long userId, StatPeriod period) {
        LocalDate today = ServiceDay.today(clock);
        LocalDate from = switch (period) {
            case WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> today.withDayOfMonth(1);
            case YEAR -> today.withDayOfYear(1);
            case ALL -> null;
        };
        Usage usage = Usage.of(routeRecordService.findDailyRecords(userId, from == null ? EPOCH : from, today));
        return new StatSummaryResponse(period, from, today, usage.usedMinutes(), usage.readCount(),
            usage.completedCount(), usage.isEmpty());
    }

    /**
     * 연간 기록 잔디(STAT-02). 평일(월~금)만 담는다.
     *
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
        int completedDays = 0;
        for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
            if (!isWeekday(date)) {
                continue;
            }
            DailyRideRecord ride = rides.get(date);
            long readCount = ride == null ? 0 : ride.getReadCount();
            CalendarLevel level = levelOf(ride, accessDates.contains(date));
            if (level == CalendarLevel.COMPLETED) {
                completedDays++;
            }
            days.add(new CalendarDayResponse(date, level, readCount, date.isAfter(today)));
        }
        return new CalendarResponse(target, today, completedDays, days);
    }

    @Transactional(readOnly = true)
    public StreakResponse getStreak(Long userId) {
        LocalDate today = ServiceDay.today(clock);
        return new StreakResponse(routeStreakService.streakDays(userId, today),
            routeStreakService.longestStreakDays(userId, today));
    }

    /**
     * 컨디션 기록. 올해 완주한 날의 컨디션 분포와 이번 달 가장 많았던 컨디션.
     */
    @Transactional(readOnly = true)
    public ConditionStatResponse getConditions(Long userId) {
        LocalDate today = ServiceDay.today(clock);
        List<DailyRideRecord> completed = routeRecordService.findDailyRecords(userId, today.withDayOfYear(1), today)
            .stream()
            .filter(DailyRideRecord::getCompleted)
            .toList();
        Map<RouteCondition, Long> year = countByCondition(completed);
        Map<RouteCondition, Long> month = countByCondition(completed.stream()
            .filter(record -> !record.getServiceDate().isBefore(today.withDayOfMonth(1)))
            .toList());
        RouteCondition top = null;
        for (RouteCondition condition : RouteCondition.values()) {
            long count = month.getOrDefault(condition, 0L);
            if (count > 0 && (top == null || count > month.get(top))) {
                top = condition;
            }
        }
        return new ConditionStatResponse(today.getYear(), countsOf(year), countsOf(month), top);
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

    private static boolean isWeekday(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    private static Map<RouteCondition, Long> countByCondition(List<DailyRideRecord> records) {
        Map<RouteCondition, Long> counts = new EnumMap<>(RouteCondition.class);
        records.forEach(record -> counts.merge(record.getCondition(), 1L, Long::sum));
        return counts;
    }

    private static ConditionCountResponse countsOf(Map<RouteCondition, Long> counts) {
        return new ConditionCountResponse(counts.getOrDefault(RouteCondition.SUNNY, 0L),
            counts.getOrDefault(RouteCondition.NORMAL, 0L), counts.getOrDefault(RouteCondition.CLOUDY, 0L),
            counts.getOrDefault(RouteCondition.RAINY, 0L));
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
