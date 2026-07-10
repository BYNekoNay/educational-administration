package com.pzhu.eduadmin.modules.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pzhu.eduadmin.modules.finance.entity.PaymentRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecord> {

    @Select("SELECT COALESCE(SUM(amount), 0) FROM payment_record WHERE enrollment_id = #{enrollmentId} AND is_deleted = 0")
    BigDecimal sumByEnrollmentId(Long enrollmentId);
}
