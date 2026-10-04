package com.pzhu.eduadmin.integration;

import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.attendance.entity.Attendance;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.Course;
import com.pzhu.eduadmin.modules.finance.entity.LessonAccount;
import com.pzhu.eduadmin.modules.finance.entity.LessonFlow;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 集成测试：课堂考勤 → 课时扣减（真实事务 + 乐观锁 CAS + 回冲）。
 * 验证"考勤记录、账户余额、课时流水"三者在同一事务中的一致性落库。
 */
@DisplayName("集成测试：考勤扣课时（真实 DB）")
class AttendanceDeductionIntegrationTest extends IntegrationTestSupport {

    private record DeductContext(Long studentId, Course course, ClassGroup classGroup,
                                 ScheduleLesson lesson, LessonAccount account) {
    }

    /** 学员在班 + 账户余额 5 课时（版本号从 2 开始，便于断言 CAS 递增） */
    private DeductContext givenDeductibleContext(String remaining) {
        Long studentId = givenStudent("集成学员-考勤");
        Course course = givenCourse("声乐启蒙", 10, "1500.00");
        ClassGroup classGroup = givenClassGroup(course.getId(), 10);
        ScheduleLesson lesson = givenLesson(classGroup.getId());
        givenClassStudent(classGroup.getId(), studentId, 1);
        LessonAccount account = givenAccount(studentId, course.getId(), remaining, 2);
        return new DeductContext(studentId, course, classGroup, lesson, account);
    }

    private Attendance submit(Long lessonId, Long studentId, int status, String deductLessons) {
        Attendance attendance = new Attendance();
        attendance.setLessonId(lessonId);
        attendance.setStudentId(studentId);
        attendance.setStatus(status);
        if (deductLessons != null) {
            attendance.setDeductLessons(new BigDecimal(deductLessons));
        }
        return attendanceService.submit(attendance);
    }

    @Test
    @DisplayName("到课考勤：账户 CAS 扣减 1 课时、版本号递增、写消费流水")
    void attendanceDeductsLesson_writesFlowAndBumpsVersion() {
        DeductContext ctx = givenDeductibleContext("5");
        loginAs("SUPER_ADMIN");

        Attendance saved = submit(ctx.lesson().getId(), ctx.studentId(), 1, "1");

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getDeductLessons()).isEqualByComparingTo("1");

        LessonAccount account = accountOf(ctx.studentId(), ctx.course().getId());
        assertThat(account.getRemainingLessons()).isEqualByComparingTo("4");
        assertThat(account.getVersion()).isEqualTo(3);

        List<LessonFlow> flows = flowsOf(account.getId());
        assertThat(flows).hasSize(1);
        LessonFlow flow = flows.get(0);
        assertThat(flow.getSourceType()).isEqualTo(2);
        assertThat(flow.getChangeType()).isEqualTo(2);
        assertThat(flow.getChangeAmount()).isEqualByComparingTo("-1");
        assertThat(flow.getBeforeBalance()).isEqualByComparingTo("5");
        assertThat(flow.getAfterBalance()).isEqualByComparingTo("4");
    }

    @Test
    @DisplayName("改判缺勤：回冲已扣课时（净额还原）且考勤扣减数归零")
    void changingToAbsent_reversesDeductionAndZeroesDeduct() {
        DeductContext ctx = givenDeductibleContext("5");
        loginAs("SUPER_ADMIN");

        submit(ctx.lesson().getId(), ctx.studentId(), 1, "1");

        // 同一课次同一学员再次提交：先回冲旧扣减，再按缺勤（不扣课时）落库
        Attendance updated = submit(ctx.lesson().getId(), ctx.studentId(), 4, null);

        assertThat(updated.getStatus()).isEqualTo(4);
        assertThat(updated.getDeductLessons()).isEqualByComparingTo("0");

        LessonAccount account = accountOf(ctx.studentId(), ctx.course().getId());
        assertThat(account.getRemainingLessons()).isEqualByComparingTo("5");

        List<LessonFlow> flows = flowsOf(account.getId());
        assertThat(flows).hasSize(2);
        assertThat(flows.get(0).getChangeAmount()).isEqualByComparingTo("-1");
        assertThat(flows.get(0).getSourceType()).isEqualTo(2);
        assertThat(flows.get(1).getChangeAmount()).isEqualByComparingTo("1");
        assertThat(flows.get(1).getSourceType()).isEqualTo(3);
        assertThat(flows.get(1).getAfterBalance()).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("余额不足时扣至 0：实际扣减数回写考勤记录，流水余额不为负")
    void insufficientBalance_deductsToZeroAndSyncsAttendance() {
        DeductContext ctx = givenDeductibleContext("0.50");
        loginAs("SUPER_ADMIN");

        Attendance saved = submit(ctx.lesson().getId(), ctx.studentId(), 1, "1");

        // 实际只扣 0.5，并把实际值回写考勤（防止回冲时按 1 多退）
        assertThat(saved.getDeductLessons()).isEqualByComparingTo("0.50");
        Attendance persisted = attendanceMapper.selectById(saved.getId());
        assertThat(persisted.getDeductLessons()).isEqualByComparingTo("0.50");

        LessonAccount account = accountOf(ctx.studentId(), ctx.course().getId());
        assertThat(account.getRemainingLessons()).isEqualByComparingTo("0");

        List<LessonFlow> flows = flowsOf(account.getId());
        assertThat(flows).hasSize(1);
        assertThat(flows.get(0).getChangeAmount()).isEqualByComparingTo("-0.50");
        assertThat(flows.get(0).getAfterBalance()).isEqualByComparingTo("0");
        assertThat(flows.get(0).getRemark()).contains("课时不足");
    }

    @Test
    @DisplayName("学员无课时账户：考勤提交抛业务异常并整体回滚（考勤行不落库）")
    void missingAccount_rollsBackAttendanceInsert() {
        Long studentId = givenStudent("集成学员-无账户");
        Course course = givenCourse("美术涂鸦", 8, "800.00");
        ClassGroup classGroup = givenClassGroup(course.getId(), 10);
        ScheduleLesson lesson = givenLesson(classGroup.getId());
        givenClassStudent(classGroup.getId(), studentId, 1);
        loginAs("SUPER_ADMIN");

        assertThatThrownBy(() -> submit(lesson.getId(), studentId, 1, "1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无课时账户");

        // 事务回滚：先落库的考勤记录被一并撤销
        assertThat(attendanceCountOf(lesson.getId(), studentId)).isZero();
    }
}