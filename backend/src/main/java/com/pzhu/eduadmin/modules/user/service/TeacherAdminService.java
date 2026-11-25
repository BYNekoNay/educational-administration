package com.pzhu.eduadmin.modules.user.service;

import com.pzhu.eduadmin.common.PageResult;
import com.pzhu.eduadmin.modules.user.dto.TeacherDetailVO;
import com.pzhu.eduadmin.modules.user.dto.TeacherListItemVO;

import java.time.LocalDate;

/**
 * 教师管理（教务视角）。
 * <p>与 {@link UserService} 的区别：这里只处理 role_code = TEACHER 的账号，
 * 且不包含密码、角色变更等敏感操作。
 */
public interface TeacherAdminService {

    /**
     * 教师分页列表。
     *
     * @param statusFilter 可选：1-在职（含已排定离职日）；0-已停用/已离职
     */
    PageResult<TeacherListItemVO> pageTeachers(int pageNum, int pageSize, String keyword, Integer statusFilter);

    /**
     * 教师详情：档案 + 当前任教班级 + 历史任教班级。
     */
    TeacherDetailVO getDetail(Long teacherId);

    /**
     * 办理离职：一次完成「写离职日 + 停用账号 + 递增 version」，
     * 使用户已签发的 token 立即失效。
     */
    void resign(Long teacherId, LocalDate leaveDate);
}
