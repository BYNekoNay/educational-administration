package com.pzhu.eduadmin.modules.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.QueryHelper;
import com.pzhu.eduadmin.modules.exam.entity.ExamLevel;
import com.pzhu.eduadmin.modules.exam.entity.ExamSignup;
import com.pzhu.eduadmin.modules.exam.mapper.ExamLevelMapper;
import com.pzhu.eduadmin.modules.exam.mapper.ExamSignupMapper;
import com.pzhu.eduadmin.modules.student.entity.Student;
import com.pzhu.eduadmin.modules.student.mapper.StudentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
        if (examLevel.getName() == null || examLevel.getName().isBlank()) {
            throw new BusinessException(400, "考级项目名称不能为空");
        }
        examLevelMapper.insert(examLevel);
        return examLevel;
    }

    @Override
    public ExamLevel updateExamLevel(ExamLevel examLevel) {
        ExamLevel existing = examLevelMapper.selectById(examLevel.getId());
        if (existing == null) {
            throw new BusinessException(404, "考级项目不存在");
        }
        examLevelMapper.updateById(examLevel);
        return examLevelMapper.selectById(examLevel.getId());
    }

    @Override
    public void deleteExamLevel(Long id) {
        Long signupCount = examSignupMapper.selectCount(new LambdaQueryWrapper<ExamSignup>()
                .eq(ExamSignup::getExamId, id));
        if (signupCount > 0) {
            throw new BusinessException(409, "该考级项目下已有报名记录，无法删除");
        }
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
        // 防止批量赋值绕过状态机：强制重置服务端控制字段
        signup.setId(null);
        signup.setStatus(1); // 强制初始状态为"已报名"
        signup.setScore(null);
        signup.setCertificateNo(null);
        signup.setCertificateFileUrl(null);
        signup.setIsDeleted(null);

        if (signup.getStudentId() == null) throw new BusinessException(400, "学员ID不能为空");
        if (signup.getExamId() == null) throw new BusinessException(400, "考级项目ID不能为空");
        if (examLevelMapper.selectById(signup.getExamId()) == null) {
            throw new BusinessException(404, "考级项目不存在");
        }
        Student student = studentMapper.selectById(signup.getStudentId());
        if (student == null) {
            throw new BusinessException(404, "学员不存在");
        }
        // 与 Enrollment.create 保持一致，拒绝已退班(status=4)学员报名考级
        if (student.getStatus() != null && student.getStatus() == 4) {
            throw new BusinessException(409, "该学员已退班，无法报名考级");
        }
        Long existCount = examSignupMapper.selectCount(new LambdaQueryWrapper<ExamSignup>()
                .eq(ExamSignup::getExamId, signup.getExamId())
                .eq(ExamSignup::getStudentId, signup.getStudentId()));
        if (existCount > 0) {
            throw new BusinessException(409, "该学员已报名此考级项目");
        }
        try {
            examSignupMapper.insert(signup);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "该学员已报名此考级项目");
        }
        return signup;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamSignup updateExamSignup(ExamSignup signup) {
        // 校验记录存在性及状态变更合法性
        ExamSignup existing = examSignupMapper.selectById(signup.getId());
        if (existing == null) {
            throw new BusinessException(404, "报名记录不存在");
        }
        // 有效状态变更: 1→2(通过), 1→3(未通过)，不允许其他变更
        if (signup.getStatus() != null && !signup.getStatus().equals(existing.getStatus())) {
            // 使用 Integer.equals 防止 null 自动拆箱 NPE
            if (!Integer.valueOf(1).equals(existing.getStatus())
                    || (!Integer.valueOf(2).equals(signup.getStatus()) && !Integer.valueOf(3).equals(signup.getStatus()))) {
                throw new BusinessException(400, "无效的状态变更");
            }
        }
        // studentId/examId 创建后不可变更，防止 mass-assignment 将报名转移到其他学员/考试
        signup.setStudentId(null);
        signup.setExamId(null);
        // 剥离服务端控制的审计字段，防止客户端覆盖报名时间
        signup.setCreateTime(null);
        signup.setUpdateTime(null);

        // CAS 原子更新（对齐 EnrollmentServiceImpl 的审核写法）。
        // 原实现是 updateById 裸更新（WHERE 只有主键）：两名教务同时审批、或同一人
        // 用陈旧页面连点时，两次 UPDATE 都会成功，先提交者的终态会被后提交者静默覆盖，
        // 且 1→2/3 only 的规则在数据库层没有任何强制力（终态可被改回）。
        // 前置条件用"读到的状态快照"而非固定的 1：这样无论本次是否变更状态，
        // 只要记录在我读取之后被别人推走，本次写入就会被拒绝。
        LambdaUpdateWrapper<ExamSignup> wrapper = new LambdaUpdateWrapper<ExamSignup>()
                .eq(ExamSignup::getId, signup.getId())
                .eq(ExamSignup::getStatus, existing.getStatus());
        // 只 set 允许客户端变更的字段，不做整体回写（mass-assignment 防护）
        if (signup.getStatus() != null) {
            wrapper.set(ExamSignup::getStatus, signup.getStatus());
        }
        if (signup.getScore() != null) {
            wrapper.set(ExamSignup::getScore, signup.getScore());
        }
        if (signup.getCertificateNo() != null) {
            wrapper.set(ExamSignup::getCertificateNo, signup.getCertificateNo());
        }
        if (signup.getCertificateFileUrl() != null) {
            wrapper.set(ExamSignup::getCertificateFileUrl, signup.getCertificateFileUrl());
        }
        // update(null, wrapper) 不触发自动填充，显式刷新 update_time
        wrapper.set(ExamSignup::getUpdateTime, LocalDateTime.now());

        int updated = examSignupMapper.update(null, wrapper);
        if (updated == 0) {
            throw new BusinessException(409, "该考级报名状态已变更，请刷新后重试");
        }
        return examSignupMapper.selectById(signup.getId());
    }

    @Override
    public Page<ExamSignup> pageArchives(int pageNum, int pageSize, String keyword) {
        LambdaQueryWrapper<ExamSignup> wrapper = new LambdaQueryWrapper<ExamSignup>()
                .eq(ExamSignup::getStatus, 2);
        if (keyword != null && !keyword.isBlank()) {
            List<Student> matched = studentMapper.selectList(
                    new LambdaQueryWrapper<Student>().like(Student::getName, keyword));
            List<Long> studentIds = matched.stream().map(Student::getId).collect(Collectors.toList());
            if (!studentIds.isEmpty()) {
                wrapper.in(ExamSignup::getStudentId, studentIds);
            } else {
                wrapper.eq(ExamSignup::getId, -1L);
            }
        }
        wrapper.orderByDesc(ExamSignup::getCreateTime);
        Page<ExamSignup> page = examSignupMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        enrichSignupNames(page.getRecords());
        return page;
    }
}
