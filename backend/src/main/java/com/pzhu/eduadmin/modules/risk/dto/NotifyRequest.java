package com.pzhu.eduadmin.modules.risk.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 一键站内触达家长请求。
 */
@Data
public class NotifyRequest {

    @NotEmpty(message = "请先选择学员")
    private List<Long> studentIds;

    /** 附加提醒文案（可选，缺省使用默认话术） */
    private String message;
}
