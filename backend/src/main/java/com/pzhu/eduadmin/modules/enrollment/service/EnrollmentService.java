package com.pzhu.eduadmin.modules.enrollment.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.modules.enrollment.entity.Enrollment;

public interface EnrollmentService {

    Page<Enrollment> page(int pageNum, int pageSize);

    Enrollment getById(Long id);

    Enrollment create(Enrollment enrollment);

    Enrollment update(Enrollment enrollment);

    boolean delete(Long id);

    Enrollment audit(Long id, Integer status, Long auditorId, String remark);

    Page<Enrollment> pageByParentUserId(Long parentUserId, int pageNum, int pageSize);
}
