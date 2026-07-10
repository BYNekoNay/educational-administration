package com.pzhu.eduadmin.modules.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pzhu.eduadmin.modules.finance.entity.RefundRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface RefundRecordMapper extends BaseMapper<RefundRecord> {

    @Select("SELECT COALESCE(SUM(amount), 0) FROM refund_record WHERE enrollment_id = #{enrollmentId} AND status = 2 AND is_deleted = 0")
    BigDecimal sumApprovedByEnrollmentId(Long enrollmentId);
}
