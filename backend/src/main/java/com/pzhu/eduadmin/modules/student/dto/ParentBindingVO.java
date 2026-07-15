package com.pzhu.eduadmin.modules.student.dto;

import lombok.Data;

/**
 * 学员已绑定家长的展示对象。用于「绑定家长」弹窗中列出已绑定家长并支持解绑。
 */
@Data
public class ParentBindingVO {

    /** parent_student 关系记录主键 */
    private Long id;

    /** 家长用户 ID */
    private Long parentUserId;

    /** 家长真实姓名 */
    private String realName;

    /** 家长登录用户名 */
    private String username;

    /** 家长手机号 */
    private String phone;

    /** 与学员的关系（父亲/母亲等） */
    private String relation;
}
