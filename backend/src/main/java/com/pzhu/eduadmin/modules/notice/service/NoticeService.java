package com.pzhu.eduadmin.modules.notice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.modules.notice.entity.Notice;

public interface NoticeService {

    Page<Notice> page(int pageNum, int pageSize);

    Notice getById(Long id);

    Notice create(Notice notice);

    Notice update(Notice notice);

    boolean delete(Long id);
}
