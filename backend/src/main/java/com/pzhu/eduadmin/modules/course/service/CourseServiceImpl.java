package com.pzhu.eduadmin.modules.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.entity.Course;
import com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.course.mapper.CourseMapper;
import com.pzhu.eduadmin.modules.statistics.entity.OperationLog;
import com.pzhu.eduadmin.modules.statistics.mapper.OperationLogMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private final CourseMapper courseMapper;
    private final ClassGroupMapper classGroupMapper;
    private final ClassStudentMapper classStudentMapper;
    private final OperationLogMapper operationLogMapper;

    @Override
    public Page<Course> pageCourses(int pageNum, int pageSize) {
        return courseMapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<>());
    }

    @Override
    public Course getCourseById(Long id) {
        return courseMapper.selectById(id);
    }

    @Override
    public Course createCourse(Course course) {
        courseMapper.insert(course);
        return course;
    }

    @Override
    public Course updateCourse(Course course) {
        courseMapper.updateById(course);
        return courseMapper.selectById(course.getId());
    }

    @Override
    public boolean deleteCourse(Long id) {
        logOperation("课程管理", "删除课程(id=" + id + ")");
        return courseMapper.deleteById(id) > 0;
    }

    @Override
    public Page<ClassGroup> pageClassGroups(int pageNum, int pageSize) {
        return classGroupMapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<>());
    }

    @Override
    public ClassGroup getClassGroupById(Long id) {
        return classGroupMapper.selectById(id);
    }

    @Override
    public ClassGroup createClassGroup(ClassGroup classGroup) {
        classGroupMapper.insert(classGroup);
        return classGroup;
    }

    @Override
    public ClassGroup updateClassGroup(ClassGroup classGroup) {
        classGroupMapper.updateById(classGroup);
        return classGroupMapper.selectById(classGroup.getId());
    }

    @Override
    public boolean deleteClassGroup(Long id) {
        logOperation("班级管理", "删除班级(id=" + id + ")");
        return classGroupMapper.deleteById(id) > 0;
    }

    @Override
    public Page<ClassStudent> pageClassStudents(Long classId, int pageNum, int pageSize) {
        return classStudentMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<ClassStudent>().eq(ClassStudent::getClassId, classId));
    }

    @Override
    public boolean addStudentToClass(ClassStudent classStudent) {
        // 校验班级容量上限
        ClassGroup classGroup = classGroupMapper.selectById(classStudent.getClassId());
        if (classGroup == null) {
            throw new BusinessException(404, "班级不存在");
        }
        Long currentCount = classStudentMapper.selectCount(
                new LambdaQueryWrapper<ClassStudent>().eq(ClassStudent::getClassId, classStudent.getClassId()));
        if (currentCount >= classGroup.getMaxStudentCount()) {
            throw new BusinessException(409, "班级已满（容量" + classGroup.getMaxStudentCount() + "），无法加入更多学员");
        }
        return classStudentMapper.insert(classStudent) > 0;
    }

    @Override
    public boolean removeStudentFromClass(Long id) {
        logOperation("班级学员管理", "移除班级学员(id=" + id + ")");
        return classStudentMapper.deleteById(id) > 0;
    }

    private void logOperation(String module, String operation) {
        OperationLog log = new OperationLog();
        log.setOperatorId(CurrentUserHolder.get().getUserId());
        log.setModule(module);
        log.setOperation(operation);
        log.setIp("0.0.0.0");
        operationLogMapper.insert(log);
    }
}
