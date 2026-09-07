package com.pzhu.eduadmin.modules.risk.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 跟进状态更新请求。
 */
@Data
public class FollowUpRequest {

    /** 0-待跟进，1-已跟进，2-暂不跟进 */
    @NotNull(message = "跟进状态不能为空")
    private Integer status;

    /** 跟进备注（可选） */
    private String remark;
}
