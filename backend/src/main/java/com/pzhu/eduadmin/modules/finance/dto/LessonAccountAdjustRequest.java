package com.pzhu.eduadmin.modules.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 课时人工调整请求。
 *
 * <p>用于财务/教务手动补课时（赠送、停课补偿）或扣课时（误扣回补、纠错）。
 * 对应课时流水 {@code source_type=5}、{@code change_type=4}。</p>
 */
@Data
public class LessonAccountAdjustRequest {

    /** 1-增加课时，2-扣减课时。不使用带符号金额，避免前端传 0 或双负号导致方向歧义 */
    @NotNull(message = "调整方向不能为空")
    @Min(value = 1, message = "调整方向仅支持 1-增加 2-扣减")
    @Max(value = 2, message = "调整方向仅支持 1-增加 2-扣减")
    private Integer direction;

    /** 恒为正数，方向由 direction 决定；lesson_account.remaining_lessons 为 DECIMAL(6,2) */
    @NotNull(message = "调整课时数不能为空")
    @DecimalMin(value = "0.01", message = "调整课时数必须大于 0")
    @Digits(integer = 4, fraction = 2, message = "调整课时数最多 4 位整数、2 位小数")
    private BigDecimal amount;

    /** 人工调账属审计事件，原因必填 */
    @NotBlank(message = "调整原因不能为空")
    @Size(max = 200, message = "调整原因不能超过 200 字")
    private String remark;

    /** 幂等键，客户端每次打开调整弹窗生成一次，重试复用同一值 */
    @NotBlank(message = "请求流水号不能为空")
    @Size(max = 64, message = "请求流水号不能超过 64 位")
    private String bizNo;
}
