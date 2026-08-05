package com.pzhu.eduadmin.modules.enrollment.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.modules.enrollment.dto.ParentClassVO;
import com.pzhu.eduadmin.modules.enrollment.dto.ParentEnrollmentSnapshotVO;
import com.pzhu.eduadmin.modules.enrollment.entity.Enrollment;

import java.util.List;
import java.util.Map;

public interface EnrollmentService {

    Page<Enrollment> page(int pageNum, int pageSize, String sortField, String sortOrder);

    Enrollment getById(Long id);

    Enrollment create(Enrollment enrollment);

    Enrollment update(Enrollment enrollment);

    void updateClassId(Long id, Long classId);

    void validateClassBelongsToCourse(Long classId, Long courseId);

    boolean delete(Long id);

    Enrollment audit(Long id, Integer status, Long auditorId, String remark);

    Page<Enrollment> pageByParentUserId(Long parentUserId, int pageNum, int pageSize);

    /** 按学员过滤的分页查询（可选 studentId 为 null 时查全部） */
    Page<Enrollment> pageByParentUserId(Long parentUserId, Long studentId, int pageNum, int pageSize);

    /** 时间冲突检测：返回冲突详情 Map（{description, conflictClassName, conflictDetail}）或 null */
    Map<String, Object> detectTimeConflict(Long studentId, Long targetClassId);

    /** 时间冲突校验（用于报名准入），若有冲突则抛 BusinessException(409) */
    void checkTimeConflict(Long studentId, Long targetClassId);

    /** 家长报名选班展示所需的开放班级及实时容量。 */
    List<ParentClassVO> listParentCourseClasses(Long courseId);

    /** 当前家长和指定学员的一致性报名决策快照。 */
    ParentEnrollmentSnapshotVO getParentEnrollmentSnapshot(Long parentUserId, Long studentId);

    /** 校验快照仍有效且决策数据未变化后创建家长报名。 */
    Enrollment createParentEnrollmentFromSnapshot(
            Long parentUserId, Enrollment enrollment, String versionToken);
}
