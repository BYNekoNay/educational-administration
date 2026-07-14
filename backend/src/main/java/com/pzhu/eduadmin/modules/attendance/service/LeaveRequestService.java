package com.pzhu.eduadmin.modules.attendance.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.modules.attendance.entity.LeaveRequest;

import java.time.LocalDate;
import java.util.List;

public interface LeaveRequestService {

    /** 家长查询请假记录 */
    List<LeaveRequest> getByParent(Long parentUserId, Long studentId);

    /** 家长提交请假申请 */
    LeaveRequest submitLeaveRequest(Long parentUserId, Long studentId, LocalDate lessonDate, String reason);

    /** 教务分页查询请假列表 */
    Page<LeaveRequest> pageAll(int pageNum, int pageSize, String keyword);

    /** 教务审核请假（status=2 时自动创建考勤记录 status=3/请假） */
    LeaveRequest audit(Long id, Integer status, Long auditUserId, String remark);
}
