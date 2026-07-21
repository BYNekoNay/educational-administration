package com.pzhu.eduadmin.modules.attendance.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.modules.attendance.entity.Attendance;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;

import java.util.List;

public interface AttendanceService {

    Page<Attendance> page(int pageNum, int pageSize, String sortField, String sortOrder);

    Attendance getById(Long id);

    /** 提交考勤（含课时扣减事务） */
    Attendance submit(Attendance attendance);

    /** 按课次查询学员列表 */
    List<ClassStudent> getLessonStudents(Long lessonId);

    /** 按课次查询已考勤记录 */
    List<Attendance> getByLessonId(Long lessonId);

    /** 按课次回冲所有已扣减的考勤课时（调课审批通过后调用） */
    void reverseDeductByLessonId(Long lessonId, Long operatorId);

    /** 批量提交考勤 */
    List<Attendance> batchSubmit(Long lessonId, List<Attendance> list);

    /** 家长查子女考勤 */
    Page<Attendance> pageByStudentId(Long studentId, int pageNum, int pageSize);

    /** 教师查询课次列表 */
    Page<ScheduleLesson> pageTeacherLessons(Long teacherId, int pageNum, int pageSize);

    /** 校验教师角色：当前课次是否属于当前登录教师（行级数据隔离） */
    void checkTeacherLessonOwnership(Long lessonId);

    /** 家长查询学员课表（根据 studentId 查询其班级课次） */
    List<ScheduleLesson> getStudentSchedules(Long studentId);

    /** 教师今日课表（含勤状态标记） */
    List<ScheduleLesson> getTodayLessons(Long teacherId);
}
