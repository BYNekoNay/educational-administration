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
import com.pzhu.eduadmin.modules.finance.entity.PaymentRecord;
import com.pzhu.eduadmin.modules.finance.mapper.PaymentRecordMapper;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import com.pzhu.eduadmin.common.EntityNameResolver;
import com.pzhu.eduadmin.modules.statistics.service.OperationLogService;
import com.pzhu.eduadmin.modules.student.mapper.StudentMapper;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import java.util.Collections;
import java.util.HashMap;
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
    private final OperationLogService operationLogService;
    private final EntityNameResolver nameResolver;
    private final ScheduleLessonMapper scheduleLessonMapper;
    private final PaymentRecordMapper paymentRecordMapper;

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
        Course existing = courseMapper.selectById(course.getId());
        if (existing == null) {
            throw new BusinessException(404, "课程不存在");
        }
        // Issue #20: 有缴费记录时禁止修改价格和课时数
        Long paymentCount = paymentRecordMapper.selectCount(
                new LambdaQueryWrapper<PaymentRecord>().eq(PaymentRecord::getCourseId, course.getId()));
        if (paymentCount > 0) {
            if (course.getPrice() != null && course.getPrice().compareTo(existing.getPrice()) != 0) {
                throw new BusinessException(409, "该课程已有缴费记录，不可修改价格");
            }
            if (course.getTotalLessons() != null && !course.getTotalLessons().equals(existing.getTotalLessons())) {
                throw new BusinessException(409, "该课程已有缴费记录，不可修改课时数");
            }
        }
        // 白名单更新：仅允许修改 name、price、totalLessons、status
        if (course.getName() != null) existing.setName(course.getName());
        if (course.getCategory() != null) existing.setCategory(course.getCategory());
        if (course.getPrice() != null) existing.setPrice(course.getPrice());
        if (course.getTotalLessons() != null) existing.setTotalLessons(course.getTotalLessons());
        if (course.getStatus() != null) existing.setStatus(course.getStatus());
        courseMapper.updateById(existing);
        return courseMapper.selectById(course.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteCourse(Long id) {
        Long classCount = classGroupMapper.selectCount(new LambdaQueryWrapper<ClassGroup>()
                .eq(ClassGroup::getCourseId, id)
                // status<>0 在 SQL 中不匹配 NULL，NULL 状态班级会漏检 → 视为活跃一并拦截
                .and(w -> w.ne(ClassGroup::getStatus, 0).or().isNull(ClassGroup::getStatus)));
        if (classCount > 0) {
            throw new BusinessException(409, "该课程下存在活跃班级，无法删除");
        }
        // Issue #21: 检查是否有未来的排课（ScheduleLesson 只有 classId，需先取课程下的班级ID）
        Set<Long> courseClassIds = classGroupMapper.selectList(
                        new LambdaQueryWrapper<ClassGroup>().eq(ClassGroup::getCourseId, id))
                .stream().map(ClassGroup::getId).collect(Collectors.toSet());
        if (!courseClassIds.isEmpty()) {
            Long futureLessonCount = scheduleLessonMapper.selectCount(
                    new LambdaQueryWrapper<ScheduleLesson>()
                            .in(ScheduleLesson::getClassId, courseClassIds)
                            .ge(ScheduleLesson::getLessonDate, LocalDate.now())
                            .in(ScheduleLesson::getStatus, 1, 2));
            if (futureLessonCount > 0) {
                throw new BusinessException(409, "该课程存在未来的排课记录，无法删除");
            }
        }
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new BusinessException(404, "课程不存在");
        }
        operationLogService.log("课程管理", "删除课程（课程=" + course.getName() + "）");
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
        // 查询课程名称（绕过 @TableLogic，包含已软删课程）
        Set<Long> courseIds = records.stream().map(ClassGroup::getCourseId).filter(id -> id != null).collect(Collectors.toSet());
        final Map<Long, String> courseNameMap;
        if (!courseIds.isEmpty()) {
            List<Map<String, Object>> rawCourses = courseMapper.selectNamesByIdsIncludeDeleted(courseIds);
            courseNameMap = rawCourses.stream().collect(Collectors.toMap(
                    m -> ((Number) m.get("id")).longValue(),
                    m -> (String) m.get("name") != null ? (String) m.get("name") : "未知课程",
                    (a, b) -> a));
        } else {
            courseNameMap = Collections.emptyMap();
        }
        // 查询教师姓名（绕过 @TableLogic，包含已软删教师）
        Set<Long> teacherIds = records.stream().map(ClassGroup::getTeacherId).filter(id -> id != null).collect(Collectors.toSet());
        final Map<Long, String> teacherNameMap;
        if (!teacherIds.isEmpty()) {
            List<Map<String, Object>> rawUsers = userMapper.selectNamesByIdsIncludeDeleted(teacherIds);
            teacherNameMap = rawUsers.stream().collect(Collectors.toMap(
                    m -> ((Number) m.get("id")).longValue(),
                    m -> {
                        String realName = (String) m.get("real_name");
                        String username = (String) m.get("username");
                        return realName != null && !realName.isBlank() ? realName : username;
                    },
                    (a, b) -> a));
        } else {
            teacherNameMap = Collections.emptyMap();
        }
        // 查询在班人数（status=1）
        Set<Long> classIds = records.stream().map(ClassGroup::getId).filter(id -> id != null).collect(Collectors.toSet());
        Map<Long, Integer> studentCountMap = new HashMap<>();
        if (!classIds.isEmpty()) {
            List<Map<String, Object>> rawCounts = classStudentMapper.countActiveStudentsByClassIds(classIds);
            for (Map<String, Object> row : rawCounts) {
                Object classIdObj = row.get("classId");
                Object cntObj = row.get("cnt");
                if (classIdObj != null && cntObj != null) {
                    studentCountMap.put(((Number) classIdObj).longValue(),
                                        ((Number) cntObj).intValue());
                }
            }
        }
        // 回填
        records.forEach(c -> {
            c.setCourseName(courseNameMap.getOrDefault(c.getCourseId(), "未知课程"));
            c.setTeacherName(teacherNameMap.getOrDefault(c.getTeacherId(), "未知教师"));
            c.setCurrentStudentCount(studentCountMap.getOrDefault(c.getId(), 0));
        });
    }

    @Override
    public ClassGroup getClassGroupById(Long id) {
        return classGroupMapper.selectById(id);
    }

    @Override
    public ClassGroup createClassGroup(ClassGroup classGroup) {
        if (classGroup.getClassName() == null || classGroup.getClassName().isBlank()) {
            throw new BusinessException(400, "班级名称不能为空");
        }
        // M fix: 校验 courseId 指向存在的课程，防止创建孤立班级（破坏课程→班级导航与统计）
        if (classGroup.getCourseId() == null || courseMapper.selectById(classGroup.getCourseId()) == null) {
            throw new BusinessException(404, "所选课程不存在");
        }
        if (classGroup.getMaxStudentCount() != null && classGroup.getMaxStudentCount() <= 0) {
            throw new BusinessException(400, "班级容量必须大于0");
        }
        classGroupMapper.insert(classGroup);
        return classGroup;
    }

    @Override
    public ClassGroup updateClassGroup(ClassGroup classGroup) {
        if (classGroup.getMaxStudentCount() != null && classGroup.getMaxStudentCount() <= 0) {
            throw new BusinessException(400, "班级容量必须大于0");
        }
        // M10 fix: 校验班级存在
        ClassGroup existing = classGroupMapper.selectById(classGroup.getId());
        if (existing == null) {
            throw new BusinessException(404, "班级不存在");
        }
        // M fix: 白名单字段拷贝，禁止 mass assignment。courseId 创建后不可变更（否则破坏与既有报名的引用完整性），
        // createTime/updateTime 等服务端字段一律不接收客户端值。
        if (classGroup.getClassName() != null) existing.setClassName(classGroup.getClassName());
        if (classGroup.getTeacherId() != null) existing.setTeacherId(classGroup.getTeacherId());
        if (classGroup.getMaxStudentCount() != null) existing.setMaxStudentCount(classGroup.getMaxStudentCount());
        if (classGroup.getStartDate() != null) existing.setStartDate(classGroup.getStartDate());
        if (classGroup.getStatus() != null) existing.setStatus(classGroup.getStatus());
        classGroupMapper.updateById(existing);
        return classGroupMapper.selectById(classGroup.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteClassGroup(Long id) {
        Long studentCount = classStudentMapper.selectCount(new LambdaQueryWrapper<ClassStudent>()
                .eq(ClassStudent::getClassId, id)
                .eq(ClassStudent::getStatus, 1));
        if (studentCount > 0) {
            throw new BusinessException(409, "该班级中存在学员，无法删除");
        }
        // Issue #21: 检查是否有未来的排课
        Long futureLessonCount = scheduleLessonMapper.selectCount(
                new LambdaQueryWrapper<ScheduleLesson>()
                        .eq(ScheduleLesson::getClassId, id)
                        .ge(ScheduleLesson::getLessonDate, LocalDate.now())
                        .in(ScheduleLesson::getStatus, 1, 2));
        if (futureLessonCount > 0) {
            throw new BusinessException(409, "该班级存在未来的排课记录，无法删除");
        }
        ClassGroup classGroup = classGroupMapper.selectById(id);
        if (classGroup == null) {
            throw new BusinessException(404, "班级不存在");
        }
        operationLogService.log("班级管理", "删除班级（班级=" + classGroup.getClassName() + "）");
        return classGroupMapper.deleteById(id) > 0;
    }

    @Override
    public Page<ClassStudent> pageClassStudents(Long classId, int pageNum, int pageSize) {
        Page<ClassStudent> page = classStudentMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<ClassStudent>()
                        .eq(ClassStudent::getClassId, classId)
                        .eq(ClassStudent::getStatus, 1));
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
            // 绕过 @TableLogic 查询学员姓名（含已软删学员，避免历史在班记录的学员名显示为空）
            List<Map<String, Object>> raw = studentMapper.selectNamesByIdsIncludeDeleted(studentIds);
            nameMap = raw.stream()
                    .collect(Collectors.toMap(
                            m -> ((Number) m.get("id")).longValue(),
                            m -> (String) m.get("name"),
                            (a, b) -> a));
        } else {
            nameMap = Collections.emptyMap();
        }
        records.forEach(s -> s.setStudentName(nameMap.getOrDefault(s.getStudentId(), "学员" + s.getStudentId())));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addStudentToClass(ClassStudent classStudent) {
        // 校验班级是否存在
        ClassGroup classGroup = classGroupMapper.selectById(classStudent.getClassId());
        if (classGroup == null) {
            throw new BusinessException(404, "班级不存在");
        }
        // M fix: 校验学员存在，防止插入引用不存在学员的孤立 ClassStudent 记录
        if (classStudent.getStudentId() == null) {
            throw new BusinessException(400, "学员ID不能为空");
        }
        if (studentMapper.selectById(classStudent.getStudentId()) == null) {
            throw new BusinessException(404, "学员不存在");
        }
        // 重复检查：该学员是否已在班级中（活跃状态）
        Long existCount = classStudentMapper.selectCount(new LambdaQueryWrapper<ClassStudent>()
                .eq(ClassStudent::getClassId, classStudent.getClassId())
                .eq(ClassStudent::getStudentId, classStudent.getStudentId())
                .eq(ClassStudent::getStatus, 1));
        if (existCount > 0) {
            throw new BusinessException(409, "该学员已在此班级中");
        }
        // 校验班级容量上限（仅统计活跃学员）
        Long currentCount = classStudentMapper.selectCount(
                new LambdaQueryWrapper<ClassStudent>()
                        .eq(ClassStudent::getClassId, classStudent.getClassId())
                        .eq(ClassStudent::getStatus, 1));
        int maxCount = classGroup.getMaxStudentCount() != null ? classGroup.getMaxStudentCount() : 0;
        if (maxCount > 0 && currentCount >= maxCount) {
            throw new BusinessException(409, "班级已满（容量" + maxCount + "），无法加入更多学员");
        }
        // H fix: class_student 唯一键 uk_class_student(class_id, student_id) 不含 status，
        // 学员被移除（status=3）或转出（status=2）后旧行仍物理存在，直接 insert 会触发 DuplicateKeyException
        // （全局异常处理转成误导性 409），导致学员永远无法重新加入。与 transferStudent 一致：
        // 优先复用已有行重新激活，无历史行才 insert 并 catch DKE 兜底软删除占用唯一键的边界。
        ClassStudent existingRow = classStudentMapper.selectOne(new LambdaQueryWrapper<ClassStudent>()
                .eq(ClassStudent::getClassId, classStudent.getClassId())
                .eq(ClassStudent::getStudentId, classStudent.getStudentId())
                .last("LIMIT 1"));
        boolean inserted;
        if (existingRow != null) {
            existingRow.setStatus(1);
            existingRow.setJoinTime(LocalDateTime.now());
            inserted = classStudentMapper.updateById(existingRow) > 0;
        } else {
            classStudent.setStatus(1);
            classStudent.setJoinTime(LocalDateTime.now());
            try {
                inserted = classStudentMapper.insert(classStudent) > 0;
            } catch (org.springframework.dao.DuplicateKeyException e) {
                throw new BusinessException(409, "该学员在此班级存在历史记录，无法重复添加");
            }
        }
        // C4 fix: 插入后二次校验容量（防止并发 TOCTOU 超员），超出则回滚
        if (inserted && maxCount > 0) {
            Long postCount = classStudentMapper.selectCount(
                    new LambdaQueryWrapper<ClassStudent>()
                            .eq(ClassStudent::getClassId, classStudent.getClassId())
                            .eq(ClassStudent::getStatus, 1));
            if (postCount > maxCount) {
                throw new BusinessException(409, "班级已满（容量" + maxCount + "），无法加入更多学员");
            }
        }
        return inserted;
    }

    @Override
    public boolean removeStudentFromClass(Long classId, Long studentId) {
        List<ClassStudent> records = classStudentMapper.selectList(new LambdaQueryWrapper<ClassStudent>()
                .eq(ClassStudent::getClassId, classId)
                .eq(ClassStudent::getStudentId, studentId)
                .eq(ClassStudent::getStatus, 1));
        if (records.isEmpty()) {
            throw new BusinessException(404, "该学员不在此班级中");
        }
        ClassStudent record = records.get(0);
        // Critical fix: 原实现用 deleteById（逻辑删除）会隐藏记录，导致流失统计（统计 status=3）永远为 0。
        // 改为置 status=3（已退出），保留记录用于统计。
        record.setStatus(3);
        boolean updated = classStudentMapper.updateById(record) > 0;
        if (updated) {
            operationLogService.log("班级学员管理", "移除班级学员（班级=" + nameResolver.getClassName(classId)
                    + "，学员=" + nameResolver.getStudentName(studentId) + "）");
        }
        return updated;
    }

}
