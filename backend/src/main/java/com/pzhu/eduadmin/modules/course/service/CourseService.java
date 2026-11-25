package com.pzhu.eduadmin.modules.course.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.entity.Course;

public interface CourseService {

    // 课程
    Page<Course> pageCourses(int pageNum, int pageSize, String keyword, String sortField, String sortOrder);

    Course getCourseById(Long id);

    Course createCourse(Course course);

    Course updateCourse(Course course);

    boolean deleteCourse(Long id);

    // 班级
    /**
     * 班级分页。
     *
     * @param keyword   可选：按班级名模糊匹配（此前前端传了 keyword 但后端丢弃，属假搜索）
     * @param teacherId 可选：只看某教师任教的班级（教师详情页使用）
     */
    Page<ClassGroup> pageClassGroups(int pageNum, int pageSize, String keyword, Long teacherId);

    ClassGroup getClassGroupById(Long id);

    ClassGroup createClassGroup(ClassGroup classGroup);

    ClassGroup updateClassGroup(ClassGroup classGroup);

    boolean deleteClassGroup(Long id);

    /**
     * 换任课教师：只改班级与「未完成且未来」的课次。
     * <p><b>已完成课次（status=2）绝不改动</b>——薪资按 teacher_id + status=2 聚合，
     * 改了会静默重写历史账目。
     *
     * @return 受影响（被改派）的未来课次数；0 表示班级已改但无待上课次，或原本就是该教师
     */
    int transferTeacher(Long classId, Long teacherId);

    // 班级学员
    Page<ClassStudent> pageClassStudents(Long classId, int pageNum, int pageSize);

    boolean addStudentToClass(ClassStudent classStudent);

    boolean removeStudentFromClass(Long classId, Long studentId);
}
