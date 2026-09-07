package com.pzhu.eduadmin.modules.risk.service;

import com.pzhu.eduadmin.common.PageResult;
import com.pzhu.eduadmin.modules.risk.dto.RiskStudentVO;
import com.pzhu.eduadmin.modules.risk.dto.RiskSummaryVO;
import com.pzhu.eduadmin.modules.risk.entity.StudentRiskFollowup;

import java.util.List;
import java.util.Map;

/**
 * 流失预警清单服务。
 */
public interface RiskWarningService {

    /**
     * 计算并返回风险名单（不设分页，供页面分页与 Excel 导出复用）。
     *
     * @param level           风险档位筛选：HIGH/MEDIUM/LOW（null=全部）
     * @param classId         班级筛选（null=全部）
     * @param courseId        课程筛选（null=全部）
     * @param keyword         学员姓名关键字（null=全部）
     * @param followUpStatus  跟进状态筛选：0/1/2（null=全部）
     */
    List<RiskStudentVO> listRiskWarnings(String level, Long classId, Long courseId,
                                         String keyword, Integer followUpStatus);

    /** 分页查询风险名单 */
    PageResult<RiskStudentVO> pageRiskWarnings(int pageNum, int pageSize, String level, Long classId,
                                               Long courseId, String keyword, Integer followUpStatus);

    /** 机构维度风险分布汇总（全量，不随筛选变化） */
    RiskSummaryVO summary();

    /**
     * 更新/新增学员跟进状态（单学员 upsert）。
     *
     * @return 落库后的跟进记录
     */
    StudentRiskFollowup updateFollowUp(Long studentId, Integer status, String remark, Long operatorId);

    /**
     * 一键站内触达选中学员的家长（按家长去重、当日幂等）。
     *
     * @return { parentCount, notifiedParentCount }
     */
    Map<String, Object> notifyParents(List<Long> studentIds, String message);
}
