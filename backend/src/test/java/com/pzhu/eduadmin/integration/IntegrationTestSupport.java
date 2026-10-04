package com.pzhu.eduadmin.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pzhu.eduadmin.modules.attendance.mapper.AttendanceMapper;
import com.pzhu.eduadmin.modules.attendance.service.AttendanceService;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.entity.Course;
import com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.course.mapper.CourseMapper;
import com.pzhu.eduadmin.modules.enrollment.entity.Enrollment;
import com.pzhu.eduadmin.modules.enrollment.mapper.EnrollmentMapper;
import com.pzhu.eduadmin.modules.enrollment.service.EnrollmentService;
import com.pzhu.eduadmin.modules.finance.entity.LessonAccount;
import com.pzhu.eduadmin.modules.finance.entity.LessonFlow;
import com.pzhu.eduadmin.modules.finance.entity.PaymentRecord;
import com.pzhu.eduadmin.modules.finance.mapper.LessonAccountMapper;
import com.pzhu.eduadmin.modules.finance.mapper.LessonFlowMapper;
import com.pzhu.eduadmin.modules.finance.mapper.PaymentRecordMapper;
import com.pzhu.eduadmin.modules.finance.mapper.RefundRecordMapper;
import com.pzhu.eduadmin.modules.finance.service.FinanceService;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import com.pzhu.eduadmin.modules.student.entity.Student;
import com.pzhu.eduadmin.modules.student.mapper.StudentMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 集成测试基类：真实 Spring 上下文 + H2（MySQL 模式）内存数据库。
 * <p>
 * 与 Mockito 单元测试互补：本层覆盖真实 SQL 执行、事务边界（回滚）、
 * 数据库唯一约束与乐观锁 CAS 等"只有真实数据库才能验证"的行为。
 * 建表脚本见 {@code src/test/resources/schema-h2.sql}（镜像 sql/schema.sql）。
 */
@SpringBootTest
@ActiveProfiles("test")
abstract class IntegrationTestSupport {

    protected static final Long TEACHER_ID = 9001L;
    protected static final Long AUDITOR_ID = 9002L;
    protected static final Long PARENT_ID = 9003L;

    @Autowired
    protected EnrollmentService enrollmentService;
    @Autowired
    protected FinanceService financeService;
    @Autowired
    protected AttendanceService attendanceService;

    @Autowired
    protected EnrollmentMapper enrollmentMapper;
    @Autowired
    protected StudentMapper studentMapper;
    @Autowired
    protected CourseMapper courseMapper;
    @Autowired
    protected ClassGroupMapper classGroupMapper;
    @Autowired
    protected ClassStudentMapper classStudentMapper;
    @Autowired
    protected ScheduleLessonMapper scheduleLessonMapper;
    @Autowired
    protected AttendanceMapper attendanceMapper;
    @Autowired
    protected LessonAccountMapper lessonAccountMapper;
    @Autowired
    protected LessonFlowMapper lessonFlowMapper;
    @Autowired
    protected PaymentRecordMapper paymentRecordMapper;
    @Autowired
    protected RefundRecordMapper refundRecordMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /** 与本层测试相关的表；每次用例前清空，保证用例相互独立 */
    private static final List<String> TABLES = List.of(
            "lesson_flow", "refund_record", "payment_record", "attendance", "lesson_account",
            "class_student", "schedule_lesson", "enrollment", "class_group", "course",
            "student", "parent_student", "user", "classroom", "room_booking", "period",
            "operation_log", "notification");

    @BeforeEach
    void resetDatabase() {
        TABLES.forEach(table -> jdbcTemplate.execute("DELETE FROM " + table));
    }

    @AfterEach
    void clearUserContext() {
        CurrentUserHolder.clear();
    }

    /** 写入登录用户上下文（考勤提交等行级校验依赖该上下文） */
    protected void loginAs(String roleCode) {
        CurrentUserHolder.set(new LoginUser(AUDITOR_ID, "integration-" + roleCode, roleCode));
    }

    // ==================== 数据夹具 ====================

    protected Long givenStudent(String name) {
        Student student = new Student();
        student.setName(name);
        student.setStatus(1);
        studentMapper.insert(student);
        return student.getId();
    }

    protected Course givenCourse(String name, int totalLessons, String price) {
        Course course = new Course();
        course.setName(name);
        course.setCategory("乐器");
        course.setTotalLessons(totalLessons);
        course.setLessonDuration(60);
        course.setPrice(new BigDecimal(price));
        course.setStatus(1);
        courseMapper.insert(course);
        return course;
    }

    protected ClassGroup givenClassGroup(Long courseId, int capacity) {
        ClassGroup classGroup = new ClassGroup();
        classGroup.setCourseId(courseId);
        classGroup.setClassName("集成测试班-" + courseId);
        classGroup.setTeacherId(TEACHER_ID);
        classGroup.setMaxStudentCount(capacity);
        classGroup.setStartDate(LocalDate.now());
        classGroup.setStatus(1);
        classGroupMapper.insert(classGroup);
        return classGroup;
    }

    protected ScheduleLesson givenLesson(Long classId) {
        ScheduleLesson lesson = new ScheduleLesson();
        lesson.setClassId(classId);
        lesson.setTeacherId(TEACHER_ID);
        lesson.setClassroomId(1L);
        lesson.setLessonDate(LocalDate.now().plusDays(1));
        lesson.setStartTime(LocalTime.of(10, 0));
        lesson.setEndTime(LocalTime.of(11, 0));
        lesson.setStatus(1);
        scheduleLessonMapper.insert(lesson);
        return lesson;
    }

    protected ClassStudent givenClassStudent(Long classId, Long studentId, int status) {
        ClassStudent classStudent = new ClassStudent();
        classStudent.setClassId(classId);
        classStudent.setStudentId(studentId);
        classStudent.setStatus(status);
        classStudentMapper.insert(classStudent);
        return classStudent;
    }

    /** 直接建课时账户（可指定初始余额与乐观锁版本号） */
    protected LessonAccount givenAccount(Long studentId, Long courseId, String remaining, int version) {
        LessonAccount account = new LessonAccount();
        account.setStudentId(studentId);
        account.setCourseId(courseId);
        account.setTotalLessons(new BigDecimal(remaining));
        account.setRemainingLessons(new BigDecimal(remaining));
        account.setExpireDate(LocalDate.now().plusYears(1));
        account.setVersion(version);
        lessonAccountMapper.insert(account);
        return account;
    }

    // ==================== 场景组合 ====================

    protected Enrollment newEnrollment(Long studentId, Long courseId, Long classId) {
        Enrollment enrollment = new Enrollment();
        enrollment.setStudentId(studentId);
        enrollment.setParentUserId(PARENT_ID);
        enrollment.setCourseId(courseId);
        enrollment.setClassId(classId);
        enrollment.setStatus(1);
        return enrollment;
    }

    /** 走真实链路：报名 → 审核通过 → 缴费，返回缴费记录 */
    protected PaymentRecord givenPaidEnrollment(Long studentId, Course course, ClassGroup classGroup,
                                                int lessonCount, String amount) {
        Enrollment created = enrollmentService.create(
                newEnrollment(studentId, course.getId(), classGroup.getId()));
        enrollmentService.audit(created.getId(), 2, AUDITOR_ID, "集成测试审核通过");
        return pay(created.getId(), lessonCount, amount);
    }

    protected PaymentRecord pay(Long enrollmentId, int lessonCount, String amount) {
        PaymentRecord payment = new PaymentRecord();
        payment.setEnrollmentId(enrollmentId);
        payment.setLessonCount(new BigDecimal(lessonCount));
        payment.setAmount(new BigDecimal(amount));
        payment.setPayType(2);
        payment.setPayTime(LocalDateTime.now());
        payment.setOperatorId(AUDITOR_ID);
        payment.setOperatorRole("FINANCE");
        return financeService.createPayment(payment);
    }

    // ==================== 查询辅助 ====================

    protected LessonAccount accountOf(Long studentId, Long courseId) {
        return lessonAccountMapper.selectOne(new LambdaQueryWrapper<LessonAccount>()
                .eq(LessonAccount::getStudentId, studentId)
                .eq(LessonAccount::getCourseId, courseId));
    }

    protected List<LessonFlow> flowsOf(Long accountId) {
        return lessonFlowMapper.selectList(new LambdaQueryWrapper<LessonFlow>()
                .eq(LessonFlow::getAccountId, accountId)
                .orderByAsc(LessonFlow::getId));
    }

    protected List<ClassStudent> classStudentsOf(Long classId, Long studentId) {
        return classStudentMapper.selectList(new LambdaQueryWrapper<ClassStudent>()
                .eq(ClassStudent::getClassId, classId)
                .eq(ClassStudent::getStudentId, studentId));
    }

    protected long attendanceCountOf(Long lessonId, Long studentId) {
        return attendanceMapper.selectCount(new LambdaQueryWrapper<com.pzhu.eduadmin.modules.attendance.entity.Attendance>()
                .eq(com.pzhu.eduadmin.modules.attendance.entity.Attendance::getLessonId, lessonId)
                .eq(com.pzhu.eduadmin.modules.attendance.entity.Attendance::getStudentId, studentId));
    }
}