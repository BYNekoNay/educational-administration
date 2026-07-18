package com.pzhu.eduadmin.modules.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** SCHEDULE_CHANGE | CLASS_REMINDER | EXAM_NOTICE | ANNOUNCEMENT */
    private String type;

    private String title;

    private String content;

    private Long relatedId;

    private Integer isRead;

    private LocalDateTime createTime;
}
