package com.pzhu.eduadmin.modules.schedule.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.modules.schedule.dto.AdjustRequestVO;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleAdjustRequest;

public interface AdjustRequestService {

    Page<AdjustRequestVO> pageAdjustRequests(int pageNum, int pageSize, Integer status);

    Page<AdjustRequestVO> pageTeacherAdjustRequests(Long userId, int pageNum, int pageSize);

    ScheduleAdjustRequest createAdjustRequest(ScheduleAdjustRequest request);

    ScheduleAdjustRequest auditAdjustRequest(Long id, Integer status, Long auditorId, String remark);
}