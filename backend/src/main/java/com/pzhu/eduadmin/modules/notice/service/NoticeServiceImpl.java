package com.pzhu.eduadmin.modules.notice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.QueryHelper;
import com.pzhu.eduadmin.modules.notice.entity.Notice;
import com.pzhu.eduadmin.modules.notice.mapper.NoticeMapper;
import com.pzhu.eduadmin.modules.statistics.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class NoticeServiceImpl implements NoticeService {

    private final NoticeMapper noticeMapper;
    private final OperationLogService operationLogService;

    private static final Map<String, SFunction<Notice, ?>> NOTICE_SORT_MAP = Map.of(
            "id", Notice::getId,
            "title", Notice::getTitle,
            "publishTime", Notice::getPublishTime
    );

    @Override
    public Page<Notice> page(int pageNum, int pageSize, String keyword, String sortField, String sortOrder) {
        LambdaQueryWrapper<Notice> wrapper = new LambdaQueryWrapper<>();
        QueryHelper.applyKeyword(wrapper, keyword, Notice::getTitle);
        QueryHelper.applySort(wrapper, sortField, sortOrder, NOTICE_SORT_MAP, () -> wrapper.orderByDesc(Notice::getId));
        return noticeMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public Notice getById(Long id) {
        return noticeMapper.selectById(id);
    }

    @Override
    public Notice create(Notice notice) {
        noticeMapper.insert(notice);

        // 操作日志
        operationLogService.log("公告管理", "发布公告（标题=" + notice.getTitle() + "）");

        return notice;
    }

    @Override
    public Notice update(Notice notice) {
        noticeMapper.updateById(notice);

        // 操作日志
        operationLogService.log("公告管理", "更新公告（标题=" + notice.getTitle() + "）");

        return noticeMapper.selectById(notice.getId());
    }

    @Override
    public boolean delete(Long id) {
        Notice notice = noticeMapper.selectById(id);
        boolean deleted = noticeMapper.deleteById(id) > 0;
        if (deleted) {
            operationLogService.log("公告管理", "删除公告（标题=" + (notice != null ? notice.getTitle() : "id=" + id) + "）");
        }
        return deleted;
    }
}
