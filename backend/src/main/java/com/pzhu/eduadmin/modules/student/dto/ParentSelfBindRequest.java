package com.pzhu.eduadmin.modules.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 家长自助绑定学员请求。
 *
 * <p>采用「学员姓名 + 报名联系电话」双要素校验：任一不匹配即拒绝，
 * 匹配到多名学员时也拒绝并要求联系教务人工绑定。
 * 不使用 {@code studentId} 直接绑定，避免家长越权绑定他人孩子。</p>
 */
@Data
public class ParentSelfBindRequest {

    @NotBlank(message = "学员姓名不能为空")
    @Size(max = 50, message = "学员姓名长度不能超过50位")
    private String studentName;

    /** 与注册/管理端口径一致的 11 位手机号 */
    @NotBlank(message = "联系电话不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "联系电话格式不正确")
    private String phone;

    @Size(max = 20, message = "关系长度不能超过20位")
    private String relation;
}
