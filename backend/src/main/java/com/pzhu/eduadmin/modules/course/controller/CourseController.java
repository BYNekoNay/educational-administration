package com.pzhu.eduadmin.modules.course.controller;

import com.pzhu.eduadmin.common.PageQuery;
import com.pzhu.eduadmin.common.PageResult;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.entity.Course;
import com.pzhu.eduadmin.modules.course.service.CourseService;
import com.pzhu.eduadmin.security.RequireRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/edu")
@RequiredArgsConstructor
@RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
public class CourseController {

    private final CourseService courseService;

    // ========== 课程 ==========

    @GetMapping("/courses")
    public Result<PageResult<Course>> listCourses(PageQuery query) {
        return Result.success(PageResult.of(courseService.pageCourses((int) query.getPageNum(), (int) query.getPageSize(),
                query.getKeyword(), query.getSortField(), query.getSortOrder())));
    }

    @GetMapping("/courses/{id}")
    public Result<Course> getCourse(@PathVariable Long id) {
        return Result.success(courseService.getCourseById(id));
    }

    @PostMapping("/courses")
    public Result<Course> createCourse(@Valid @RequestBody Course course) {
        return Result.success(courseService.createCourse(course));
    }

    @PutMapping("/courses/{id}")
    public Result<Course> updateCourse(@PathVariable Long id, @RequestBody Course course) {
        course.setId(id);
        return Result.success(courseService.updateCourse(course));
    }

    @DeleteMapping("/courses/{id}")
    public Result<Void> deleteCourse(@PathVariable Long id) {
        courseService.deleteCourse(id);
        return Result.success();
    }

    // ========== 班级 ==========

    @GetMapping("/classes")
    public Result<PageResult<ClassGroup>> listClassGroups(PageQuery query) {
        return Result.success(PageResult.of(courseService.pageClassGroups((int) query.getPageNum(), (int) query.getPageSize())));
    }

    @GetMapping("/classes/{id}")
    public Result<ClassGroup> getClassGroup(@PathVariable Long id) {
        return Result.success(courseService.getClassGroupById(id));
    }

    @PostMapping("/classes")
    public Result<ClassGroup> createClassGroup(@Valid @RequestBody ClassGroup classGroup) {
        return Result.success(courseService.createClassGroup(classGroup));
    }

    @PutMapping("/classes/{id}")
    public Result<ClassGroup> updateClassGroup(@PathVariable Long id, @RequestBody ClassGroup classGroup) {
        classGroup.setId(id);
        return Result.success(courseService.updateClassGroup(classGroup));
    }

    @DeleteMapping("/classes/{id}")
    public Result<Void> deleteClassGroup(@PathVariable Long id) {
        courseService.deleteClassGroup(id);
        return Result.success();
    }

    // ========== 班级学员 ==========

    @GetMapping("/classes/{id}/students")
    public Result<PageResult<ClassStudent>> listClassStudents(@PathVariable Long id, PageQuery query) {
        return Result.success(PageResult.of(courseService.pageClassStudents(id, (int) query.getPageNum(), (int) query.getPageSize())));
    }

    @PostMapping("/classes/{id}/students")
    public Result<Void> addStudentToClass(@PathVariable Long id, @RequestBody ClassStudent classStudent) {
        classStudent.setClassId(id);
        courseService.addStudentToClass(classStudent);
        return Result.success();
    }

    @DeleteMapping("/classes/{id}/students/{studentId}")
    public Result<Void> removeStudentFromClass(@PathVariable Long id, @PathVariable Long studentId) {
        courseService.removeStudentFromClass(studentId);
        return Result.success();
    }
}
