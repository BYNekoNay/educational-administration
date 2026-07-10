package com.pzhu.eduadmin.modules.student.controller;

import com.pzhu.eduadmin.common.PageQuery;
import com.pzhu.eduadmin.common.PageResult;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.student.entity.ParentStudent;
import com.pzhu.eduadmin.modules.student.entity.Student;
import com.pzhu.eduadmin.modules.student.service.StudentService;
import com.pzhu.eduadmin.security.RequireRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/edu/students")
@RequiredArgsConstructor
@RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
public class StudentController {

    private final StudentService studentService;

    @GetMapping
    public Result<PageResult<Student>> list(PageQuery query) {
        return Result.success(PageResult.of(studentService.pageStudents((int) query.getPageNum(), (int) query.getPageSize())));
    }

    @GetMapping("/{id}")
    public Result<Student> get(@PathVariable Long id) {
        return Result.success(studentService.getStudentById(id));
    }

    @PostMapping
    public Result<Student> create(@Valid @RequestBody Student student) {
        return Result.success(studentService.createStudent(student));
    }

    @PutMapping("/{id}")
    public Result<Student> update(@PathVariable Long id, @RequestBody Student student) {
        student.setId(id);
        return Result.success(studentService.updateStudent(student));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        studentService.deleteStudent(id);
        return Result.success();
    }

    @PostMapping("/bind-parent")
    public Result<Void> bindParent(@RequestBody ParentStudent parentStudent) {
        studentService.bindParent(parentStudent);
        return Result.success();
    }

    @PostMapping("/{id}/transfer")
    public Result<java.util.Map<String, Object>> transfer(@PathVariable Long id, @RequestParam Long targetClassId) {
        return Result.success(studentService.transferStudent(id, targetClassId));
    }

    @PostMapping("/{id}/withdraw")
    public Result<java.util.Map<String, Object>> withdraw(@PathVariable Long id) {
        return Result.success(studentService.withdrawStudent(id));
    }
}
