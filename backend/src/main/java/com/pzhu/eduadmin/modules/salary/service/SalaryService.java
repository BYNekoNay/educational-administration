package com.pzhu.eduadmin.modules.salary.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.modules.salary.entity.SalaryAdjustment;
import com.pzhu.eduadmin.modules.salary.entity.SalaryRule;
import com.pzhu.eduadmin.modules.salary.entity.TeacherSalary;

import java.math.BigDecimal;

public interface SalaryService {

    // 薪资规则
    Page<SalaryRule> pageSalaryRules(int pageNum, int pageSize, String keyword, String sortField, String sortOrder);

    SalaryRule createSalaryRule(SalaryRule rule);

    SalaryRule updateSalaryRule(SalaryRule rule);

    void deleteSalaryRule(Long id);

    // 薪资核算与管理
    Page<TeacherSalary> pageTeacherSalaries(int pageNum, int pageSize, String keyword, String sortField, String sortOrder);

    TeacherSalary calculateSalary(String salaryMonth, Long teacherId, BigDecimal bonusAmount);

    TeacherSalary confirmSalary(Long id);

    TeacherSalary voidSalary(Long id);

    // 薪资调整
    SalaryAdjustment createAdjustment(Long salaryId, BigDecimal adjustAmount, String reason, Long operatorId);

    Page<SalaryAdjustment> pageSalaryAdjustments(int pageNum, int pageSize);
}
