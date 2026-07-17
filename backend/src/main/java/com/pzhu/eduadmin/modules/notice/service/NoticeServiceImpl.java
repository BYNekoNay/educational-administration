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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
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
        boolean deleted = noticeMapper.deleteById(id) > 0;
        if (deleted) {
            logOperation("公告管理", "删除公告(id=" + id + ")");
        }
        return deleted;
    }

    private void logOperation(String module, String operation) {
        OperationLog log = new OperationLog();
        com.pzhu.eduadmin.security.LoginUser operator = CurrentUserHolder.get();
        log.setOperatorId(operator != null ? operator.getUserId() : 0L);
        log.setModule(module);
        log.setOperation(operation);

        String ip = "unknown";
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("X-Real-IP");
            }
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
            // Take first IP if multiple (X-Forwarded-For can have chain)
            if (ip != null && ip.contains(",")) {
                ip = ip.split(",")[0].trim();
            }
        }
        log.setIp(ip);
        operationLogMapper.insert(log);
    }
}
