package com.pzhu.eduadmin.modules.salary.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.modules.salary.entity.SalaryAdjustment;
import com.pzhu.eduadmin.modules.salary.entity.SalaryRule;
import com.pzhu.eduadmin.modules.salary.entity.TeacherSalary;

import java.math.BigDecimal;
import java.util.Map;

public interface SalaryService {

    // 薪资规则
    Page<SalaryRule> pageSalaryRules(int pageNum, int pageSize, String keyword, String sortField, String sortOrder);

    SalaryRule createSalaryRule(SalaryRule rule);

    SalaryRule updateSalaryRule(SalaryRule rule);

    void deleteSalaryRule(Long id);

    // 薪资核算与管理
    Page<TeacherSalary> pageTeacherSalaries(int pageNum, int pageSize, String keyword, Integer status, String month, String sortField, String sortOrder);

    TeacherSalary calculateSalary(String salaryMonth, Long teacherId, BigDecimal bonusAmount);

    /**
     * 一键结算指定月份的所有教师薪资。返回汇总：成功/失败条数、合计金额、失败原因列表。
     */
    Map<String, Object> calculateBatchSalary(String salaryMonth, BigDecimal bonusAmount);

    TeacherSalary confirmSalary(Long id);

    TeacherSalary paySalary(Long id);

    TeacherSalary voidSalary(Long id);

    // 薪资调整
    SalaryAdjustment createAdjustment(Long salaryId, BigDecimal adjustAmount, String reason, Long operatorId);

    Page<SalaryAdjustment> pageSalaryAdjustments(int pageNum, int pageSize);
}
