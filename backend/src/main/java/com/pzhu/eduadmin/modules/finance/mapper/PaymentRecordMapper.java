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

    /** Bug #29 fix: 查询该报名累计购买的课时总数（不随退费变化），作为退费单价计算的稳定分母 */
    @Select("SELECT COALESCE(SUM(lesson_count), 0) FROM payment_record WHERE enrollment_id = #{enrollmentId} AND is_deleted = 0")
    BigDecimal sumLessonCountByEnrollmentId(Long enrollmentId);
}
