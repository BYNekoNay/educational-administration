package com.pzhu.eduadmin.modules.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.QueryHelper;
import com.pzhu.eduadmin.modules.exam.entity.ExamLevel;
import com.pzhu.eduadmin.modules.exam.entity.ExamSignup;
import com.pzhu.eduadmin.modules.exam.mapper.ExamLevelMapper;
import com.pzhu.eduadmin.modules.exam.mapper.ExamSignupMapper;
import com.pzhu.eduadmin.modules.student.entity.Student;
import com.pzhu.eduadmin.modules.student.mapper.StudentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExamServiceImpl implements ExamService {

    private final ExamLevelMapper examLevelMapper;
    private final ExamSignupMapper examSignupMapper;
    private final StudentMapper studentMapper;

    private static final Map<String, SFunction<ExamLevel, ?>> LEVEL_SORT_MAP = Map.of(
            "id", ExamLevel::getId,
            "name", ExamLevel::getName,
            "examDate", ExamLevel::getExamDate,
            "fee", ExamLevel::getFee
    );
    private static final Map<String, SFunction<ExamSignup, ?>> SIGNUP_SORT_MAP = Map.of(
            "id", ExamSignup::getId,
            "score", ExamSignup::getScore,
            "status", ExamSignup::getStatus
    );

    @Override
    public Page<ExamLevel> pageExamLevels(int pageNum, int pageSize, String keyword, String sortField, String sortOrder) {
        LambdaQueryWrapper<ExamLevel> wrapper = new LambdaQueryWrapper<>();
        QueryHelper.applyKeyword(wrapper, keyword, ExamLevel::getName, ExamLevel::getLevelName);
        QueryHelper.applySort(wrapper, sortField, sortOrder, LEVEL_SORT_MAP, () -> wrapper.orderByDesc(ExamLevel::getId));
        return examLevelMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
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
    public void deleteExamLevel(Long id) {
        examLevelMapper.deleteById(id);
    }

    @Override
    public Page<ExamSignup> pageExamSignups(int pageNum, int pageSize, String sortField, String sortOrder) {
        LambdaQueryWrapper<ExamSignup> wrapper = new LambdaQueryWrapper<>();
        QueryHelper.applySort(wrapper, sortField, sortOrder, SIGNUP_SORT_MAP, () -> wrapper.orderByDesc(ExamSignup::getId));
        Page<ExamSignup> page = examSignupMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        enrichSignupNames(page.getRecords());
        return page;
    }

    private void enrichSignupNames(List<ExamSignup> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        // 查询考级项目
        Set<Long> examIds = records.stream().map(ExamSignup::getExamId).filter(id -> id != null).collect(Collectors.toSet());
        final Map<Long, String> examNameMap;
        if (!examIds.isEmpty()) {
            List<ExamLevel> levels = examLevelMapper.selectList(
                    new LambdaQueryWrapper<ExamLevel>().in(ExamLevel::getId, examIds));
            examNameMap = levels.stream().collect(Collectors.toMap(
                    ExamLevel::getId,
                    l -> l.getName() + (l.getLevelName() != null && !l.getLevelName().isBlank() ? " - " + l.getLevelName() : ""),
                    (a, b) -> a));
        } else {
            examNameMap = Collections.emptyMap();
        }
        // 查询学员
        Set<Long> studentIds = records.stream().map(ExamSignup::getStudentId).filter(id -> id != null).collect(Collectors.toSet());
        final Map<Long, String> studentNameMap;
        if (!studentIds.isEmpty()) {
            List<Student> students = studentMapper.selectList(
                    new LambdaQueryWrapper<Student>().in(Student::getId, studentIds));
            studentNameMap = students.stream().collect(Collectors.toMap(Student::getId, Student::getName, (a, b) -> a));
        } else {
            studentNameMap = Collections.emptyMap();
        }
        // 回填姓名
        records.forEach(s -> {
            s.setExamName(examNameMap.getOrDefault(s.getExamId(), "未知项目"));
            s.setStudentName(studentNameMap.getOrDefault(s.getStudentId(), "未知学员"));
        });
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
