package com.pzhu.eduadmin.modules.statistics.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.modules.statistics.entity.OperationLog;
import com.pzhu.eduadmin.modules.statistics.entity.Organization;
import com.pzhu.eduadmin.modules.statistics.entity.StatisticsSnapshot;

import java.util.Map;

public interface StatisticsService {

    Page<StatisticsSnapshot> pageSnapshots(int pageNum, int pageSize);

    Organization getOrganization();

    Organization updateOrganization(Organization organization);

    Page<OperationLog> pageOperationLogs(int pageNum, int pageSize);

    Map<String, Object> getDashboard();
}
