package com.pzhu.eduadmin.modules.student.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.modules.student.dto.ParentBindingVO;
import com.pzhu.eduadmin.modules.student.entity.ParentStudent;
import com.pzhu.eduadmin.modules.student.entity.Student;
import com.pzhu.eduadmin.modules.user.entity.User;

import java.util.List;
import java.util.Map;

public interface StudentService {

    Page<Student> pageStudents(int pageNum, int pageSize, String keyword, String sortField, String sortOrder);

    /**
     * 查询可用于绑定学员的家长用户列表（角色为 PARENT 且状态正常）。
     */
    List<User> listParentOptions();

    Student getStudentById(Long id);

    Student createStudent(Student student);

    Student updateStudent(Student student);

    boolean deleteStudent(Long id);

    boolean bindParent(ParentStudent parentStudent);

    /**
     * 查询指定学员已绑定的家长列表（含家长姓名、用户名、关系），用于绑定弹窗展示与解绑。
     */
    List<ParentBindingVO> listBoundParents(Long studentId);

    /**
     * 解除指定学员与指定家长的绑定关系（物理删除，允许后续重新绑定）。
     */
    boolean unbindParent(Long studentId, Long parentUserId);

    /**
     * 学员转班：将学员从当前班级转移到目标班级，
     * 对应 docs/09-接口规范.md §5 POST /api/edu/students/{id}/transfer。
     */
    Map<String, Object> transferStudent(Long studentId, Long targetClassId, Long fromClassId);

    /**
     * 学员退班：将学员从班级中移除并生成退费申请，
     * 对应 docs/09-接口规范.md §5 POST /api/edu/students/{id}/withdraw。
     */
    Map<String, Object> withdrawStudent(Long studentId);
}
