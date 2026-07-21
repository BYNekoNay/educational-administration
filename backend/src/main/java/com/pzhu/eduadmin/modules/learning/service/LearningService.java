package com.pzhu.eduadmin.modules.learning.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.modules.learning.entity.Homework;
import com.pzhu.eduadmin.modules.learning.entity.LearningRecord;

import java.util.List;
import java.util.Map;

public interface LearningService {

    Page<Homework> pageHomeworks(int pageNum, int pageSize);

    Homework createHomework(Homework homework);

    List<Homework> getHomeworksByLessonId(Long lessonId);

    /** 按学员当前所在班级查询全部作业 */
    List<Homework> getHomeworksByStudentId(Long studentId);

    Page<LearningRecord> pageLearningRecords(int pageNum, int pageSize);

    LearningRecord createLearningRecord(LearningRecord record);

    List<LearningRecord> getRecordsByLessonIdAndStudentId(Long lessonId, Long studentId);

    List<LearningRecord> getRecordsByLessonId(Long lessonId);

    /** 按学员ID查询所有学习记录 */
    List<LearningRecord> getRecordsByStudentId(Long studentId);

    /** 批量保存学情记录 */
    List<LearningRecord> batchCreateRecords(List<LearningRecord> records);

    /** 学员成长档案（出勤率+作业列表+老师点评总汇） */
    Map<String, Object> getStudentArchive(Long studentId);

    /** H13 fix: 校验教师是否有权访问指定学员（学员在教师所教班级中） */
    void verifyTeacherStudentAccess(Long teacherId, Long studentId);
}
