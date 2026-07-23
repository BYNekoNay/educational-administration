package com.pzhu.eduadmin.modules.notice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.QueryHelper;
import com.pzhu.eduadmin.modules.notice.entity.Notice;
import com.pzhu.eduadmin.modules.notice.mapper.NoticeMapper;
import com.pzhu.eduadmin.modules.statistics.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
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
        if (notice.getTitle() == null || notice.getTitle().isBlank()) {
            throw new BusinessException(400, "公告标题不能为空");
        }
        if (notice.getContent() == null || notice.getContent().isBlank()) {
            throw new BusinessException(400, "公告内容不能为空");
        }
        noticeMapper.insert(notice);

        // 操作日志
        try {
            operationLogService.log("公告管理", "发布公告（标题=" + notice.getTitle() + "）");
        } catch (Exception e) {
            log.warn("操作日志记录失败: {}", e.getMessage());
        }

        return notice;
    }

    @Override
    public Notice update(Notice notice) {
        Notice existing = noticeMapper.selectById(notice.getId());
        if (existing == null) {
            throw new BusinessException(404, "公告不存在");
        }
        noticeMapper.updateById(notice);

        // 操作日志
        try {
            operationLogService.log("公告管理", "更新公告（标题=" + notice.getTitle() + "）");
        } catch (Exception e) {
            log.warn("操作日志记录失败: {}", e.getMessage());
        }

        return noticeMapper.selectById(notice.getId());
    }

    @Override
    public boolean delete(Long id) {
        Notice notice = noticeMapper.selectById(id);
        boolean deleted = noticeMapper.deleteById(id) > 0;
        if (deleted) {
            try {
                operationLogService.log("公告管理", "删除公告（标题=" + (notice != null ? notice.getTitle() : "id=" + id) + "）");
            } catch (Exception e) {
                log.warn("操作日志记录失败: {}", e.getMessage());
            }
        }
        return deleted;
    }
}
