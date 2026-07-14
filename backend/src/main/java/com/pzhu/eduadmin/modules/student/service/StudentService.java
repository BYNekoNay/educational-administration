package com.pzhu.eduadmin.modules.student.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.modules.student.entity.ParentStudent;
import com.pzhu.eduadmin.modules.student.entity.Student;

import java.util.Map;

public interface StudentService {

    Page<Student> pageStudents(int pageNum, int pageSize, String keyword, String sortField, String sortOrder);

    Student getStudentById(Long id);

    Student createStudent(Student student);

    Student updateStudent(Student student);

    boolean deleteStudent(Long id);

    boolean bindParent(ParentStudent parentStudent);

    /**
     * 学员转班：将学员从当前班级转移到目标班级，
     * 对应 docs/09-接口规范.md §5 POST /api/edu/students/{id}/transfer。
     */
    Map<String, Object> transferStudent(Long studentId, Long targetClassId);

    /**
     * 学员退班：将学员从班级中移除并生成退费申请，
     * 对应 docs/09-接口规范.md §5 POST /api/edu/students/{id}/withdraw。
     */
    Map<String, Object> withdrawStudent(Long studentId);
}
