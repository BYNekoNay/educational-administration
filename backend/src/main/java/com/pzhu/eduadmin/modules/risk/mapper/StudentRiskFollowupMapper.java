package com.pzhu.eduadmin.modules.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pzhu.eduadmin.modules.risk.entity.StudentRiskFollowup;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流失预警跟进状态 Mapper。
 */
@Mapper
public interface StudentRiskFollowupMapper extends BaseMapper<StudentRiskFollowup> {
}
