package com.pzhu.eduadmin.modules.schedule.service;

import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.schedule.entity.Period;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.PeriodMapper;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class PeriodService {

    private final PeriodMapper periodMapper;
    private final ScheduleLessonMapper scheduleLessonMapper;

    public List<Period> listAll() {
        return periodMapper.selectList(
                new LambdaQueryWrapper<Period>().orderByAsc(Period::getSlotOrder));
    }

    public Period getById(Long id) {
        Period p = periodMapper.selectById(id);
        if (p == null) throw new BusinessException(404, "时段不存在");
        return p;
    }

    @Transactional(rollbackFor = Exception.class)
    public Period create(Period period) {
        period.setId(null);
        periodMapper.insert(period);
        return period;
    }

    @Transactional(rollbackFor = Exception.class)
    public Period update(Long id, Period period) {
        Period existing = getById(id);
        existing.setName(period.getName());
        existing.setSlotOrder(period.getSlotOrder());
        existing.setStartTime(period.getStartTime());
        existing.setEndTime(period.getEndTime());
        periodMapper.updateById(existing);
        return existing;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getById(id);
        periodMapper.deleteById(id);
    }

    /**
     * 将现有 schedule_lesson 按 startTime/endTime 反推映射到合适的课节时段。
     * 规则：找出时间重叠最长的时段作为 period_id，按课时分钟数反推 period_count。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> migrateExistingLessons() {
        List<Period> periods = listAll();
        List<ScheduleLesson> lessons = scheduleLessonMapper.selectList(
                new LambdaQueryWrapper<ScheduleLesson>().isNull(ScheduleLesson::getPeriodId));

        int migrated = 0, skipped = 0;
        for (ScheduleLesson lesson : lessons) {
            Long bestPeriodId = null;
            long bestOverlap = 0;
            for (Period p : periods) {
                long overlap = overlapMinutes(lesson.getStartTime(), lesson.getEndTime(),
                        p.getStartTime(), p.getEndTime());
                if (overlap > bestOverlap) {
                    bestOverlap = overlap;
                    bestPeriodId = p.getId();
                }
            }
            if (bestPeriodId == null) {
                skipped++;
                continue;
            }

            // 课时分钟数 / 40 向上取整 = 连堂数
            long minutes = Duration.between(lesson.getStartTime(), lesson.getEndTime()).toMinutes();
            int count = (int) Math.max(1, Math.ceil((double) minutes / 40.0));

            lesson.setPeriodId(bestPeriodId);
            lesson.setPeriodCount(count);
            scheduleLessonMapper.updateById(lesson);
            migrated++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", lessons.size());
        result.put("migrated", migrated);
        result.put("skipped", skipped);
        log.info("[period migration] total={} migrated={} skipped={}", lessons.size(), migrated, skipped);
        return result;
    }

    private long overlapMinutes(LocalTime s1, LocalTime e1, LocalTime s2, LocalTime e2) {
        LocalTime start = s1.isAfter(s2) ? s1 : s2;
        LocalTime end = e1.isBefore(e2) ? e1 : e2;
        return start.isBefore(end) ? Duration.between(start, end).toMinutes() : 0;
    }
}
