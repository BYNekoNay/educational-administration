package com.pzhu.eduadmin.modules.user.controller;

import com.pzhu.eduadmin.common.PageQuery;
import com.pzhu.eduadmin.common.PageResult;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.user.dto.ResignTeacherRequest;
import com.pzhu.eduadmin.modules.user.dto.TeacherDetailVO;
import com.pzhu.eduadmin.modules.user.dto.TeacherListItemVO;
import com.pzhu.eduadmin.modules.user.service.TeacherAdminService;
import com.pzhu.eduadmin.security.RequireRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 教师管理（教务可操作）。
 * <p>与 {@code UserController}（/api/admin/users，仅超管）分开的原因：
 * 教师日常管理是教务的职责，不应被超管权限卡住。
 */
@RestController
@RequestMapping("/api/edu/teachers")
@RequiredArgsConstructor
@RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
public class TeacherAdminController {

    private final TeacherAdminService teacherAdminService;

    /**
     * 教师分页列表。
     * <p><b>路径必须是 /page</b>：GET /api/edu/teachers 已被
     * {@link com.pzhu.eduadmin.modules.schedule.controller.ScheduleController#listTeachers()}
     * 占用（教师下拉），重复映射会在启动期抛 Ambiguous mapping 直接失败。
     *
     * @param status 可选：1-在职（含已排定离职日）；0-已停用/已离职
     */
    @GetMapping("/page")
    public Result<PageResult<TeacherListItemVO>> page(PageQuery query,
                                                     @RequestParam(required = false) Integer status) {
        return Result.success(teacherAdminService.pageTeachers(
                (int) query.getPageNum(), (int) query.getPageSize(), query.getKeyword(), status));
    }

    /**
     * 教师详情：档案 + 当前任教班级 + 历史任教班级。
     */
    @GetMapping("/{id}")
    public Result<TeacherDetailVO> detail(@PathVariable Long id) {
        return Result.success(teacherAdminService.getDetail(id));
    }

    /**
     * 办理离职：写离职日 + 停用账号 + 递增 token 版本号，一次完成。
     */
    @PutMapping("/{id}/resign")
    public Result<Void> resign(@PathVariable Long id, @Valid @RequestBody ResignTeacherRequest request) {
        teacherAdminService.resign(id, request.getLeaveDate());
        return Result.success();
    }
}
