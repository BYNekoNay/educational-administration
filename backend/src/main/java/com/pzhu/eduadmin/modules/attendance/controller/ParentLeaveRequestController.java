package com.pzhu.eduadmin.modules.attendance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.attendance.entity.LeaveRequest;
import com.pzhu.eduadmin.modules.attendance.service.LeaveRequestService;
import com.pzhu.eduadmin.modules.student.entity.ParentStudent;
import com.pzhu.eduadmin.modules.student.mapper.ParentStudentMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/parent")
@RequiredArgsConstructor
public class ParentLeaveRequestController {

    private final LeaveRequestService leaveRequestService;
    private final ParentStudentMapper parentStudentMapper;

    @PostMapping("/leave-requests")
    public Result<LeaveRequest> submit(@RequestParam Long studentId,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lessonDate,
                                       @RequestParam String reason) {
        Long parentUserId = CurrentUserHolder.get().getUserId();
        checkParentBinding(parentUserId, studentId);
        return Result.success(leaveRequestService.submitLeaveRequest(parentUserId, studentId, lessonDate, reason));
    }

    @GetMapping("/leave-requests")
    public Result<List<LeaveRequest>> list(@RequestParam(required = false) Long studentId) {
        Long parentUserId = CurrentUserHolder.get().getUserId();
        if (studentId == null) {
            // 使用第一个绑定的学员
            ParentStudent binding = parentStudentMapper.selectOne(
                    new LambdaQueryWrapper<ParentStudent>()
                            .eq(ParentStudent::getParentUserId, parentUserId)
                            .orderByAsc(ParentStudent::getId)
                            .last("LIMIT 1"));
            if (binding == null) {
                throw new BusinessException(403, "暂无绑定的学员，请联系教务绑定");
            }
            studentId = binding.getStudentId();
        } else {
            checkParentBinding(parentUserId, studentId);
        }
        return Result.success(leaveRequestService.getByParent(parentUserId, studentId));
    }

    private void checkParentBinding(Long parentUserId, Long studentId) {
        Long count = parentStudentMapper.selectCount(
                new LambdaQueryWrapper<ParentStudent>()
                        .eq(ParentStudent::getParentUserId, parentUserId)
                        .eq(ParentStudent::getStudentId, studentId));
        if (count == 0) {
            throw new BusinessException(403, "无权操作该学员数据");
        }
    }
}
