package com.pzhu.eduadmin.modules.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 办理教师离职请求。
 */
@Data
public class ResignTeacherRequest {

    /**
     * 离职生效日。
     * <p>允许填写未来日期（例如今天办理、月底离职）；
     * 账号停用与 token 失效在提交时立即生效，不受该日期影响。
     */
    @NotNull(message = "离职生效日不能为空")
    private LocalDate leaveDate;
}
