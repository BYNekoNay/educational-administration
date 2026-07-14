package com.pzhu.eduadmin.modules.notice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.QueryHelper;
import com.pzhu.eduadmin.modules.notice.entity.Notice;
import com.pzhu.eduadmin.modules.notice.mapper.NoticeMapper;
import com.pzhu.eduadmin.modules.statistics.entity.OperationLog;
import com.pzhu.eduadmin.modules.statistics.mapper.OperationLogMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class NoticeServiceImpl implements NoticeService {

    private final NoticeMapper noticeMapper;
    private final OperationLogMapper operationLogMapper;

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
        logOperation("公告管理", "发布公告(id=" + notice.getId() + ",标题=" + notice.getTitle() + ")");

        return notice;
    }

    @Override
    public Notice update(Notice notice) {
        noticeMapper.updateById(notice);

        // 操作日志
        logOperation("公告管理", "更新公告(id=" + notice.getId() + ")");

        return noticeMapper.selectById(notice.getId());
    }

    @Override
    public boolean delete(Long id) {
        logOperation("公告管理", "删除公告(id=" + id + ")");
        return noticeMapper.deleteById(id) > 0;
    }

    private void logOperation(String module, String operation) {
        OperationLog log = new OperationLog();
        log.setOperatorId(CurrentUserHolder.get().getUserId());
        log.setModule(module);
        log.setOperation(operation);
        log.setIp("0.0.0.0");
        operationLogMapper.insert(log);
    }
}
