package com.pzhu.eduadmin.modules.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.QueryHelper;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.entity.Course;
import com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.course.mapper.CourseMapper;
import com.pzhu.eduadmin.modules.statistics.entity.OperationLog;
import com.pzhu.eduadmin.modules.statistics.mapper.OperationLogMapper;
import com.pzhu.eduadmin.modules.student.entity.Student;
import com.pzhu.eduadmin.modules.student.mapper.StudentMapper;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private final CourseMapper courseMapper;
    private final ClassGroupMapper classGroupMapper;
    private final ClassStudentMapper classStudentMapper;
    private final UserMapper userMapper;
    private final StudentMapper studentMapper;
    private final OperationLogMapper operationLogMapper;

    private static final Map<String, SFunction<Course, ?>> COURSE_SORT_MAP = Map.of(
            "id", Course::getId,
            "name", Course::getName,
            "price", Course::getPrice,
            "totalLessons", Course::getTotalLessons
    );

    @Override
    public Page<Course> pageCourses(int pageNum, int pageSize, String keyword, String sortField, String sortOrder) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        QueryHelper.applyKeyword(wrapper, keyword, Course::getName);
        QueryHelper.applySort(wrapper, sortField, sortOrder, COURSE_SORT_MAP, () -> wrapper.orderByDesc(Course::getId));
        return courseMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public Course getCourseById(Long id) {
        return courseMapper.selectById(id);
    }

    @Override
    public Course createCourse(Course course) {
        if (course.getName() == null || course.getName().isBlank()) {
            throw new BusinessException(400, "课程名称不能为空");
        }
        if (course.getPrice() == null || course.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "课程价格必须大于0");
        }
        if (course.getTotalLessons() == null || course.getTotalLessons() <= 0) {
            throw new BusinessException(400, "课时总数必须大于0");
        }
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
        Page<ClassGroup> page = classGroupMapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<>());
        enrichClassGroupNames(page.getRecords());
        return page;
    }

    private void enrichClassGroupNames(List<ClassGroup> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        // 查询课程名称
        Set<Long> courseIds = records.stream().map(ClassGroup::getCourseId).filter(id -> id != null).collect(Collectors.toSet());
        final Map<Long, String> courseNameMap;
        if (!courseIds.isEmpty()) {
            List<Course> courses = courseMapper.selectList(
                    new LambdaQueryWrapper<Course>().in(Course::getId, courseIds));
            courseNameMap = courses.stream().collect(Collectors.toMap(Course::getId, c -> c.getName() != null ? c.getName() : "未知课程", (a, b) -> a));
        } else {
            courseNameMap = Collections.emptyMap();
        }
        // 查询教师姓名
        Set<Long> teacherIds = records.stream().map(ClassGroup::getTeacherId).filter(id -> id != null).collect(Collectors.toSet());
        final Map<Long, String> teacherNameMap;
        if (!teacherIds.isEmpty()) {
            List<User> users = userMapper.selectList(
                    new LambdaQueryWrapper<User>().in(User::getId, teacherIds));
            teacherNameMap = users.stream().collect(Collectors.toMap(User::getId,
                    u -> u.getRealName() != null && !u.getRealName().isBlank() ? u.getRealName() : u.getUsername(),
                    (a, b) -> a));
        } else {
            teacherNameMap = Collections.emptyMap();
        }
        // 回填
        records.forEach(c -> {
            c.setCourseName(courseNameMap.getOrDefault(c.getCourseId(), "未知课程"));
            c.setTeacherName(teacherNameMap.getOrDefault(c.getTeacherId(), "未知教师"));
        });
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
        Page<ClassStudent> page = classStudentMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<ClassStudent>().eq(ClassStudent::getClassId, classId));
        enrichClassStudentNames(page.getRecords());
        return page;
    }

    private void enrichClassStudentNames(List<ClassStudent> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> studentIds = records.stream().map(ClassStudent::getStudentId).filter(id -> id != null).collect(Collectors.toSet());
        final Map<Long, String> nameMap;
        if (!studentIds.isEmpty()) {
            List<Student> students = studentMapper.selectList(
                    new LambdaQueryWrapper<Student>().in(Student::getId, studentIds));
            nameMap = students.stream().collect(Collectors.toMap(Student::getId, Student::getName, (a, b) -> a));
        } else {
            nameMap = Collections.emptyMap();
        }
        records.forEach(s -> s.setStudentName(nameMap.getOrDefault(s.getStudentId(), "学员" + s.getStudentId())));
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
