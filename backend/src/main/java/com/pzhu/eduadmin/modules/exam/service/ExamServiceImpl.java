package com.pzhu.eduadmin.modules.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.modules.exam.entity.ExamLevel;
import com.pzhu.eduadmin.modules.exam.entity.ExamSignup;
import com.pzhu.eduadmin.modules.exam.mapper.ExamLevelMapper;
import com.pzhu.eduadmin.modules.exam.mapper.ExamSignupMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExamServiceImpl implements ExamService {

    private final ExamLevelMapper examLevelMapper;
    private final ExamSignupMapper examSignupMapper;

    @Override
    public Page<ExamLevel> pageExamLevels(int pageNum, int pageSize) {
        return examLevelMapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<>());
    }

    @Override
    public ExamLevel getExamLevelById(Long id) {
        return examLevelMapper.selectById(id);
    }

    @Override
    public ExamLevel createExamLevel(ExamLevel examLevel) {
        examLevelMapper.insert(examLevel);
        return examLevel;
    }

    @Override
    public ExamLevel updateExamLevel(ExamLevel examLevel) {
        examLevelMapper.updateById(examLevel);
        return examLevelMapper.selectById(examLevel.getId());
    }

    @Override
    public Page<ExamSignup> pageExamSignups(int pageNum, int pageSize) {
        return examSignupMapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<>());
    }

    @Override
    public ExamSignup createExamSignup(ExamSignup signup) {
        examSignupMapper.insert(signup);
        return signup;
    }

    @Override
    public ExamSignup updateExamSignup(ExamSignup signup) {
        examSignupMapper.updateById(signup);
        return examSignupMapper.selectById(signup.getId());
    }
}
