package com.pzhu.eduadmin.modules.exam.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.modules.exam.entity.ExamLevel;
import com.pzhu.eduadmin.modules.exam.entity.ExamSignup;

public interface ExamService {

    Page<ExamLevel> pageExamLevels(int pageNum, int pageSize, String keyword, String sortField, String sortOrder);

    ExamLevel getExamLevelById(Long id);

    ExamLevel createExamLevel(ExamLevel examLevel);

    ExamLevel updateExamLevel(ExamLevel examLevel);

    void deleteExamLevel(Long id);

    Page<ExamSignup> pageExamSignups(int pageNum, int pageSize, String sortField, String sortOrder);

    ExamSignup createExamSignup(ExamSignup signup);

    ExamSignup updateExamSignup(ExamSignup signup);

    /** 证书归档：查询已通过(status=2)的报名 */
    Page<ExamSignup> pageArchives(int pageNum, int pageSize, String keyword);
}
