package com.pzhu.eduadmin;

import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.enrollment.controller.EnrollmentController;
import com.pzhu.eduadmin.modules.finance.controller.FinanceController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 第六轮验收修复：审核类接口的"驳回强制填原因"校验（Controller 层入口拦截）。
 * 校验位于 CurrentUserHolder 解析之前，可直接实例化 Controller 测拒绝分支。
 */
@DisplayName("驳回强制原因校验（接口层）")
class AuditRejectRemarkGuardTest {

    @Test
    @DisplayName("退费驳回不填原因 — 400")
    void refundRejectWithoutRemark() {
        FinanceController controller = new FinanceController(null, null, null, null);
        Map<String, Object> body = new HashMap<>();
        body.put("status", 3);
        assertThatThrownBy(() -> controller.auditRefund(1L, body))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("驳回时必须填写原因");
    }

    @Test
    @DisplayName("退费驳回仅空白原因 — 400")
    void refundRejectWithBlankRemark() {
        FinanceController controller = new FinanceController(null, null, null, null);
        Map<String, Object> body = new HashMap<>();
        body.put("status", 3);
        body.put("remark", "   ");
        assertThatThrownBy(() -> controller.auditRefund(1L, body))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("驳回时必须填写原因");
    }

    @Test
    @DisplayName("报名驳回不填备注 — 400")
    void enrollmentRejectWithoutRemark() {
        EnrollmentController controller = new EnrollmentController(null);
        assertThatThrownBy(() -> controller.audit(1L, 4, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("驳回时必须填写备注");
    }

    @Test
    @DisplayName("报名驳回仅空白备注 — 400")
    void enrollmentRejectWithBlankRemark() {
        EnrollmentController controller = new EnrollmentController(null);
        assertThatThrownBy(() -> controller.audit(1L, 4, "   "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("驳回时必须填写备注");
    }
}
