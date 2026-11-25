package com.pzhu.eduadmin.modules.salary.controller;

import com.pzhu.eduadmin.common.PageQuery;
import com.pzhu.eduadmin.common.PageResult;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.salary.entity.TeacherSalary;
import com.pzhu.eduadmin.modules.salary.service.SalaryService;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.LoginUser;
import com.pzhu.eduadmin.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 教师端「我的课酬」。
 * <p>为什么不复用 {@code SalaryController}（/api/finance/salaries）：
 * 那个类的每个方法都锁了 SUPER_ADMIN/FINANCE，教师调必然 403；
 * 而且它允许按 teacherId 任意筛选，直接开放给教师等于泄露全员工资。
 */
@RestController
@RequestMapping("/api/teacher/salaries")
@RequiredArgsConstructor
@RequireRole({"TEACHER"})
public class TeacherSalaryController {

    private final SalaryService salaryService;

    /**
     * 本人课酬分页。
     * <p>teacherId 一律取自当前登录态，不接受前端传入。
     */
    @GetMapping
    public Result<PageResult<TeacherSalary>> mySalaries(
            PageQuery query,
            @RequestParam(required = false) String month) {
        LoginUser loginUser = CurrentUserHolder.get();
        return Result.success(PageResult.of(salaryService.pageMySalaries(
                loginUser.getUserId(), (int) query.getPageNum(), (int) query.getPageSize(), month)));
    }
}
