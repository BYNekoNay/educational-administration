package com.pzhu.eduadmin.modules.learning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.learning.entity.Homework;
import com.pzhu.eduadmin.modules.learning.entity.LearningRecord;
import com.pzhu.eduadmin.modules.learning.mapper.HomeworkMapper;
import com.pzhu.eduadmin.modules.learning.mapper.LearningRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LearningServiceImpl implements LearningService {

    private final HomeworkMapper homeworkMapper;
    private final LearningRecordMapper learningRecordMapper;

    @Override
    public Page<Homework> pageHomeworks(int pageNum, int pageSize) {
        return homeworkMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Homework>().orderByDesc(Homework::getCreateTime));
    }

    @Override
    public Homework createHomework(Homework homework) {
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
    public List<LearningRecord> batchCreateRecords(List<LearningRecord> records) {
        List<LearningRecord> result = new ArrayList<>();
        for (LearningRecord r : records) {
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
}
