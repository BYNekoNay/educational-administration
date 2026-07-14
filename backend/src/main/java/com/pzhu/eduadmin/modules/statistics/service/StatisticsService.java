package com.pzhu.eduadmin.modules.statistics.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.modules.statistics.entity.OperationLog;
import com.pzhu.eduadmin.modules.statistics.entity.Organization;
import com.pzhu.eduadmin.modules.statistics.entity.StatisticsSnapshot;

import java.util.List;
import java.util.Map;

public interface StatisticsService {

    Page<StatisticsSnapshot> pageSnapshots(int pageNum, int pageSize);

    Organization getOrganization();

    Organization updateOrganization(Organization organization);

    Page<OperationLog> pageOperationLogs(int pageNum, int pageSize, String keyword, String sortField, String sortOrder);

    Map<String, Object> getDashboard();

    /** 教师工作量统计（按月份），返回每位教师的完成课次数 */
    List<Map<String, Object>> getTeacherWorkload(String month);

    /** 学员流失率趋势（近6月），返回每月在班/退班人数及流失率 */
    List<Map<String, Object>> getStudentLossTrend();
}
