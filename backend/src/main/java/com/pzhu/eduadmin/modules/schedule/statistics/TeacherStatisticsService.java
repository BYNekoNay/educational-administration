package com.pzhu.eduadmin.modules.schedule.statistics;

import java.util.Map;

/**
 * 教师个人课时统计服务（零数据库变更，全聚合查询）。
 */
public interface TeacherStatisticsService {

    /**
     * 教师概览统计：当月/上月课时数、学员数、完成率、到课率。
     */
    Map<String, Object> getOverview(Long teacherId);

    /**
     * 按月分组统计：返回各月份的 lessonCount / totalHours / studentCount 列表。
     * @param year 年份（如 2026），null 表示全部
     */
    java.util.List<Map<String, Object>> getMonthly(Long teacherId, Integer year);
}
