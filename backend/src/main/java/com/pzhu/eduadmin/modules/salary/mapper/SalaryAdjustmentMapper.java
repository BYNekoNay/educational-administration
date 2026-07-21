package com.pzhu.eduadmin.modules.salary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pzhu.eduadmin.modules.salary.entity.SalaryAdjustment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface SalaryAdjustmentMapper extends BaseMapper<SalaryAdjustment> {

    @Select("SELECT COALESCE(SUM(adjust_amount), 0) FROM salary_adjustment WHERE teacher_salary_id = #{teacherSalaryId} AND is_deleted = 0")
    BigDecimal sumByTeacherSalaryId(Long teacherSalaryId);
}
