package com.pzhu.eduadmin;

import com.pzhu.eduadmin.modules.attendance.entity.Attendance;
import com.pzhu.eduadmin.modules.attendance.entity.LeaveRequest;
import com.pzhu.eduadmin.modules.enrollment.entity.Enrollment;
import com.pzhu.eduadmin.modules.finance.dto.LessonAccountAdjustRequest;
import com.pzhu.eduadmin.modules.learning.entity.Homework;
import com.pzhu.eduadmin.modules.learning.entity.LearningRecord;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleAdjustRequest;
import com.pzhu.eduadmin.modules.statistics.entity.Organization;
import com.pzhu.eduadmin.modules.student.dto.ParentSelfBindRequest;
import com.pzhu.eduadmin.modules.user.entity.Menu;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

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

    @Test
    @DisplayName("课时调整：方向仅 1/2，课时数须为正且 ≤2 位小数，原因与流水号必填")
    void lessonAdjustRequestConstraints() {
        LessonAccountAdjustRequest req = new LessonAccountAdjustRequest();
        assertThat(VALIDATOR.validate(req)).hasSize(4);

        req.setDirection(1);
        req.setAmount(BigDecimal.ZERO);
        req.setRemark("   ");
        req.setBizNo("biz-1");
        assertThat(VALIDATOR.validate(req)).hasSize(2);

        req.setDirection(3);
        req.setAmount(new BigDecimal("1.00"));
        req.setRemark("停课补偿");
        assertThat(VALIDATOR.validate(req)).hasSize(1);

        req.setDirection(2);
        assertThat(VALIDATOR.validate(req)).isEmpty();

        req.setAmount(new BigDecimal("1.234"));
        assertThat(VALIDATOR.validate(req)).hasSize(1);
    }

    @Test
    @DisplayName("家长自助绑定：姓名与电话必填，电话须为 11 位手机号，关系 ≤20 字")
    void parentSelfBindRequestConstraints() {
        ParentSelfBindRequest req = new ParentSelfBindRequest();
        assertThat(VALIDATOR.validate(req)).hasSize(2);

        req.setStudentName("张三");
        req.setPhone("12345");
        assertThat(VALIDATOR.validate(req)).hasSize(1);

        req.setPhone("13800138000");
        assertThat(VALIDATOR.validate(req)).isEmpty();

        req.setRelation("x".repeat(21));
        assertThat(VALIDATOR.validate(req)).hasSize(1);
    }
}