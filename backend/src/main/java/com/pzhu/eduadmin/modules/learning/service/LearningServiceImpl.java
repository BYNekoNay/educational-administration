package com.pzhu.eduadmin.modules.learning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.attendance.entity.Attendance;
import com.pzhu.eduadmin.modules.attendance.mapper.AttendanceMapper;
import com.pzhu.eduadmin.modules.learning.entity.Homework;
import com.pzhu.eduadmin.modules.learning.entity.LearningRecord;
import com.pzhu.eduadmin.modules.learning.mapper.HomeworkMapper;
import com.pzhu.eduadmin.modules.learning.mapper.LearningRecordMapper;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LearningServiceImpl implements LearningService {

    private final HomeworkMapper homeworkMapper;
    private final LearningRecordMapper learningRecordMapper;
    private final AttendanceMapper attendanceMapper;
    private final ScheduleLessonMapper scheduleLessonMapper;

    @Override
    public Page<Homework> pageHomeworks(int pageNum, int pageSize) {
        return homeworkMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Homework>().orderByDesc(Homework::getCreateTime));
    }

    @Override
    public Homework createHomework(Homework homework) {
        if (homework.getLessonId() == null) throw new BusinessException(400, "课次ID不能为空");
        homeworkMapper.insert(homework);
        return homework;
    }

    @Override
    public List<Homework> getHomeworksByLessonId(Long lessonId) {
        return homeworkMapper.selectList(
                new LambdaQueryWrapper<Homework>().eq(Homework::getLessonId, lessonId));
    }

    @Override
    public Page<LearningRecord> pageLearningRecords(int pageNum, int pageSize) {
        return learningRecordMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<LearningRecord>().orderByDesc(LearningRecord::getCreateTime));
    }

    @Override
    public LearningRecord createLearningRecord(LearningRecord record) {
        if (record.getStudentId() == null) throw new BusinessException(400, "学员ID不能为空");
        learningRecordMapper.insert(record);
        return record;
    }

    @Override
    public List<LearningRecord> getRecordsByLessonIdAndStudentId(Long lessonId, Long studentId) {
        return learningRecordMapper.selectList(
                new LambdaQueryWrapper<LearningRecord>()
                        .eq(LearningRecord::getLessonId, lessonId)
                        .eq(LearningRecord::getStudentId, studentId));
    }

    @Override
    public List<LearningRecord> getRecordsByLessonId(Long lessonId) {
        return learningRecordMapper.selectList(
                new LambdaQueryWrapper<LearningRecord>().eq(LearningRecord::getLessonId, lessonId));
    }

    @Override
    public List<LearningRecord> getRecordsByStudentId(Long studentId) {
        return learningRecordMapper.selectList(
                new LambdaQueryWrapper<LearningRecord>()
                        .eq(LearningRecord::getStudentId, studentId)
                        .orderByDesc(LearningRecord::getCreateTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<LearningRecord> batchCreateRecords(List<LearningRecord> records) {
        List<LearningRecord> result = new ArrayList<>();
        for (LearningRecord r : records) {
            if (r.getStudentId() == null) {
                throw new BusinessException(400, "学情记录中学员ID不能为空");
            }
            // 同一课次同一学员去重
            Long exists = learningRecordMapper.selectCount(
                    new LambdaQueryWrapper<LearningRecord>()
                            .eq(LearningRecord::getLessonId, r.getLessonId())
                            .eq(LearningRecord::getStudentId, r.getStudentId()));
            if (exists == 0) {
                learningRecordMapper.insert(r);
                result.add(r);
            }
        }
        return result;
    }

    @Override
    public Map<String, Object> getStudentArchive(Long studentId) {
        // 1. 出勤统计
        List<Attendance> attendances = attendanceMapper.selectList(
                new LambdaQueryWrapper<Attendance>().eq(Attendance::getStudentId, studentId));
        long present = attendances.stream().filter(a -> a.getStatus() == 1 || a.getStatus() == 2).count();
        double attRate = attendances.isEmpty() ? 0.0 : Math.round((double) present / attendances.size() * 100.0) / 100.0;

        // 2. 作业列表
        List<Homework> homeworks = homeworkMapper.selectList(
                new LambdaQueryWrapper<Homework>().orderByDesc(Homework::getCreateTime).last("LIMIT 50"));

        // 3. 学习记录（点评）
        List<LearningRecord> records = learningRecordMapper.selectList(
                new LambdaQueryWrapper<LearningRecord>()
                        .eq(LearningRecord::getStudentId, studentId)
                        .orderByDesc(LearningRecord::getCreateTime).last("LIMIT 50"));

        Map<String, Object> archive = new LinkedHashMap<>();
        archive.put("attendanceRate", attRate);
        archive.put("totalAttendance", attendances.size());
        archive.put("totalHomeworks", homeworks.size());
        archive.put("totalRecords", records.size());
        archive.put("homeworks", homeworks);
        archive.put("records", records);
        return archive;
    }
}
