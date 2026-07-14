package com.pzhu.eduadmin.modules.attendance.controller;

import com.pzhu.eduadmin.common.PageQuery;
import com.pzhu.eduadmin.common.PageResult;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.attendance.entity.LeaveRequest;
import com.pzhu.eduadmin.modules.attendance.service.LeaveRequestService;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.LoginUser;
import com.pzhu.eduadmin.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/edu")
@RequiredArgsConstructor
public class AdminLeaveRequestController {

    private final LeaveRequestService leaveRequestService;

    @GetMapping("/leave-requests")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<PageResult<LeaveRequest>> list(PageQuery query) {
        return Result.success(PageResult.of(leaveRequestService.pageAll(
                (int) query.getPageNum(), (int) query.getPageSize(), query.getKeyword())));
    }

    @PutMapping("/leave-requests/{id}/audit")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<LeaveRequest> audit(@PathVariable Long id,
                                      @RequestParam Integer status,
                                      @RequestParam(required = false) String remark) {
        LoginUser loginUser = CurrentUserHolder.get();
        return Result.success(leaveRequestService.audit(id, status, loginUser.getUserId(), remark));
    }
}
