package com.pzhu.eduadmin.modules.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.modules.user.dto.CreateUserRequest;
import com.pzhu.eduadmin.modules.user.dto.UpdateUserRequest;
import com.pzhu.eduadmin.modules.user.entity.User;

import java.util.List;

public interface UserService {

    Page<User> pageUsers(int pageNum, int pageSize, String keyword, String sortField, String sortOrder);

    User getUserById(Long id);

    User createUser(CreateUserRequest request);

    User updateUser(Long id, UpdateUserRequest request);

    void updateUserStatus(Long id, Integer status);

    /**
     * 解除账号的登录失败锁定（清 Redis 用户维度计数）。
     * @return 该账号此前是否处于锁定态
     */
    boolean unlockLoginLock(Long id);

    void resetPassword(Long id, String newPassword);

    /** 查询某教师的教学特长课程ID列表 */
    List<Long> getSpecialtyCourseIds(Long teacherId);

    /** 批量填充教师列表的 specialties 字段（用于 GET /api/edu/teachers） */
    void fillTeachersSpecialties(List<User> teachers);
}
