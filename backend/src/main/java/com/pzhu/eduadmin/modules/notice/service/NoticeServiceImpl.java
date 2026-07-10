package com.pzhu.eduadmin.modules.notice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.modules.notice.entity.Notice;
import com.pzhu.eduadmin.modules.notice.mapper.NoticeMapper;
import com.pzhu.eduadmin.modules.statistics.entity.OperationLog;
import com.pzhu.eduadmin.modules.statistics.mapper.OperationLogMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NoticeServiceImpl implements NoticeService {

    private final NoticeMapper noticeMapper;
    private final OperationLogMapper operationLogMapper;

    @Override
    public Page<Notice> page(int pageNum, int pageSize) {
        return noticeMapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<>());
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
