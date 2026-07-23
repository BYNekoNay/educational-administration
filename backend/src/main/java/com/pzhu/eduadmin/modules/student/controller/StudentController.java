package com.pzhu.eduadmin.modules.student.controller;

import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.PageQuery;
import com.pzhu.eduadmin.common.PageResult;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.student.dto.ParentBindingVO;
import com.pzhu.eduadmin.modules.student.entity.ParentStudent;
import com.pzhu.eduadmin.modules.student.entity.Student;
import com.pzhu.eduadmin.modules.student.service.StudentService;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.security.RequireRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/edu/students")
@RequiredArgsConstructor
@RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
public class StudentController {

    private final StudentService studentService;

    @GetMapping
    public Result<PageResult<Student>> list(PageQuery query) {
        return Result.success(PageResult.of(studentService.pageStudents((int) query.getPageNum(), (int) query.getPageSize(),
                query.getKeyword(), query.getSortField(), query.getSortOrder())));
    }

    @GetMapping("/{id}")
    public Result<Student> get(@PathVariable Long id) {
        Student student = studentService.getStudentById(id);
        if (student == null) {
            throw new BusinessException(404, "学员不存在");
        }
        return Result.success(student);
    }

    @PostMapping
    public Result<Student> create(@Valid @RequestBody Student student) {
        // Mass assignment protection: strip server-controlled fields
        student.setId(null);
        student.setStatus(null);
        student.setIsDeleted(null);
        student.setCreateTime(null);
        student.setUpdateTime(null);
        return Result.success(studentService.createStudent(student));
    }

    @PutMapping("/{id}")
    public Result<Student> update(@PathVariable Long id, @RequestBody Student student) {
        student.setId(id);
        // Mass assignment protection: strip server-controlled fields
        student.setIsDeleted(null);
        student.setCreateTime(null);
        student.setUpdateTime(null);
        return Result.success(studentService.updateStudent(student));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        studentService.deleteStudent(id);
        return Result.success();
    }

    @GetMapping("/parent-options")
    public Result<List<User>> parentOptions() {
        return Result.success(studentService.listParentOptions());
    }

    @PostMapping("/bind-parent")
    public Result<Void> bindParent(@RequestBody ParentStudent parentStudent) {
        // Mass assignment protection: strip server-controlled fields
        parentStudent.setId(null);
        parentStudent.setCreateTime(null);
        studentService.bindParent(parentStudent);
        return Result.success();
    }

    @GetMapping("/{id}/parents")
    public Result<List<ParentBindingVO>> boundParents(@PathVariable Long id) {
        return Result.success(studentService.listBoundParents(id));
    }

    @DeleteMapping("/{id}/parents/{parentUserId}")
    public Result<Void> unbindParent(@PathVariable Long id, @PathVariable Long parentUserId) {
        studentService.unbindParent(id, parentUserId);
        return Result.success();
    }

    @PostMapping("/{id}/transfer")
    public Result<java.util.Map<String, Object>> transfer(@PathVariable Long id,
                                                           @RequestParam Long targetClassId,
                                                           @RequestParam(required = false) Long fromClassId) {
        return Result.success(studentService.transferStudent(id, targetClassId, fromClassId));
    }

    @PostMapping("/{id}/withdraw")
    public Result<java.util.Map<String, Object>> withdraw(@PathVariable Long id) {
        return Result.success(studentService.withdrawStudent(id));
    }
}
