package com.pzhu.eduadmin.modules.risk.controller;

import com.pzhu.eduadmin.common.PageQuery;
import com.pzhu.eduadmin.common.PageResult;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.risk.dto.FollowUpRequest;
import com.pzhu.eduadmin.modules.risk.dto.NotifyRequest;
import com.pzhu.eduadmin.modules.risk.dto.RiskStudentVO;
import com.pzhu.eduadmin.modules.risk.dto.RiskSummaryVO;
import com.pzhu.eduadmin.modules.risk.entity.StudentRiskFollowup;
import com.pzhu.eduadmin.modules.risk.service.RiskWarningService;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.RequireRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 流失预警清单（P4）。
 *
 * <p>教务管理员/超级管理员可查看与操作名单；财务管理员可查看统计口径（Summary）以评估续费压力。</p>
 */
@RestController
@RequestMapping("/api/admin/risk-warnings")
@RequiredArgsConstructor
@RequireRole({"SUPER_ADMIN", "EDU_ADMIN", "FINANCE"})
public class RiskWarningController {

    private final RiskWarningService riskWarningService;

    @GetMapping
    public Result<PageResult<RiskStudentVO>> list(
            PageQuery query,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Integer followUpStatus) {
        return Result.success(riskWarningService.pageRiskWarnings(
                (int) query.getPageNum(), (int) query.getPageSize(),
                level, classId, courseId, query.getKeyword(), followUpStatus));
    }

    @GetMapping("/summary")
    public Result<RiskSummaryVO> summary() {
        return Result.success(riskWarningService.summary());
    }

    @PutMapping("/{studentId}/follow-up")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<StudentRiskFollowup> followUp(@PathVariable Long studentId,
                                                @Valid @RequestBody FollowUpRequest request) {
        Long operatorId = CurrentUserHolder.get() != null ? CurrentUserHolder.get().getUserId() : null;
        return Result.success(riskWarningService.updateFollowUp(studentId, request.getStatus(), request.getRemark(), operatorId));
    }

    @PostMapping("/notify")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Map<String, Object>> notify(@Valid @RequestBody NotifyRequest request) {
        return Result.success(riskWarningService.notifyParents(request.getStudentIds(), request.getMessage()));
    }
}
