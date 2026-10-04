package com.pzhu.eduadmin;

import com.pzhu.eduadmin.modules.attendance.entity.Attendance;
import com.pzhu.eduadmin.modules.attendance.entity.LeaveRequest;
import com.pzhu.eduadmin.modules.enrollment.entity.Enrollment;
import com.pzhu.eduadmin.modules.learning.entity.Homework;
import com.pzhu.eduadmin.modules.learning.entity.LearningRecord;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleAdjustRequest;
import com.pzhu.eduadmin.modules.statistics.entity.Organization;
import com.pzhu.eduadmin.modules.user.entity.Menu;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static jakarta.validation.Validation.buildDefaultValidatorFactory;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 参数校验约束测试：验证声明式约束（@NotNull/@NotBlank/@Size）与数据库列约束一致，
 * 保证非法请求在进入业务逻辑前即被全局异常处理器转为 400 友好提示。
 */
@DisplayName("参数校验约束（Bean Validation）")
class ValidationConstraintsTest {

    private static final Validator VALIDATOR = buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("报名：学员ID/课程ID 必填")
    void enrollmentRequiresStudentAndCourse() {
        Enrollment enrollment = new Enrollment();
        assertThat(VALIDATOR.validate(enrollment)).hasSize(2);

        enrollment.setStudentId(1L);
        enrollment.setCourseId(2L);
        assertThat(VALIDATOR.validate(enrollment)).isEmpty();
    }

    @Test
    @DisplayName("考勤：课次ID/学员ID 必填，备注长度与列一致（255）")
    void attendanceRequiresLessonAndStudent() {
        Attendance attendance = new Attendance();
        assertThat(VALIDATOR.validate(attendance)).hasSize(2);

        attendance.setLessonId(1L);
        attendance.setStudentId(2L);
        assertThat(VALIDATOR.validate(attendance)).isEmpty();

        attendance.setRemark("超长备注".repeat(100));
        assertThat(VALIDATOR.validate(attendance)).hasSize(1);
    }

    @Test
    @DisplayName("菜单/机构：名称必填（@NotBlank 覆盖空白串）")
    void menuAndOrganizationRequireName() {
        Menu menu = new Menu();
        menu.setMenuName("   ");
        assertThat(VALIDATOR.validate(menu)).hasSize(1);
        menu.setMenuName("角色管理");
        assertThat(VALIDATOR.validate(menu)).isEmpty();

        Organization organization = new Organization();
        assertThat(VALIDATOR.validate(organization)).hasSize(1);
        organization.setOrgName("艺培通艺术中心");
        assertThat(VALIDATOR.validate(organization)).isEmpty();
    }

    @Test
    @DisplayName("作业/学情/调课/请假：文本长度上限与库表列宽一致")
    void textLengthLimitsMatchSchema() {
        Homework homework = new Homework();
        homework.setContent("x".repeat(1001));
        assertThat(VALIDATOR.validate(homework)).hasSize(1);
        homework.setContent("x".repeat(1000));
        assertThat(VALIDATOR.validate(homework)).isEmpty();

        LearningRecord record = new LearningRecord();
        record.setTeacherComment("评语".repeat(300));
        record.setGrowthTag("标签".repeat(60));
        assertThat(VALIDATOR.validate(record)).hasSize(2);

        ScheduleAdjustRequest adjust = new ScheduleAdjustRequest();
        adjust.setReason("原因".repeat(200));
        assertThat(VALIDATOR.validate(adjust)).hasSize(1);

        LeaveRequest leave = new LeaveRequest();
        leave.setReason("请假".repeat(200));
        leave.setAuditRemark("备注".repeat(200));
        assertThat(VALIDATOR.validate(leave)).hasSize(2);
    }
}