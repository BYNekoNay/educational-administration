package com.pzhu.eduadmin.modules.course.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.entity.Course;

public interface CourseService {

    // 课程
    Page<Course> pageCourses(int pageNum, int pageSize);

    Course getCourseById(Long id);

    Course createCourse(Course course);

    Course updateCourse(Course course);

    boolean deleteCourse(Long id);

    // 班级
    Page<ClassGroup> pageClassGroups(int pageNum, int pageSize);

    ClassGroup getClassGroupById(Long id);

    ClassGroup createClassGroup(ClassGroup classGroup);

    ClassGroup updateClassGroup(ClassGroup classGroup);

    boolean deleteClassGroup(Long id);

    // 班级学员
    Page<ClassStudent> pageClassStudents(Long classId, int pageNum, int pageSize);

    boolean addStudentToClass(ClassStudent classStudent);

    boolean removeStudentFromClass(Long id);
}
