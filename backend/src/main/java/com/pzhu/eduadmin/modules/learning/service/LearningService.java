package com.pzhu.eduadmin.modules.learning.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.modules.learning.entity.Homework;
import com.pzhu.eduadmin.modules.learning.entity.LearningRecord;

import java.util.List;

public interface LearningService {

    Page<Homework> pageHomeworks(int pageNum, int pageSize);

    Homework createHomework(Homework homework);

    List<Homework> getHomeworksByLessonId(Long lessonId);

    Page<LearningRecord> pageLearningRecords(int pageNum, int pageSize);

    LearningRecord createLearningRecord(LearningRecord record);

    List<LearningRecord> getRecordsByLessonIdAndStudentId(Long lessonId, Long studentId);

    List<LearningRecord> getRecordsByLessonId(Long lessonId);

    /** 批量保存学情记录 */
    List<LearningRecord> batchCreateRecords(List<LearningRecord> records);
}
