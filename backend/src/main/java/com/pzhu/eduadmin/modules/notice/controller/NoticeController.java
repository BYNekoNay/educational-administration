package com.pzhu.eduadmin.modules.notice.controller;

import com.pzhu.eduadmin.common.PageQuery;
import com.pzhu.eduadmin.common.PageResult;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.notice.entity.Notice;
import com.pzhu.eduadmin.modules.notice.service.NoticeService;
import com.pzhu.eduadmin.security.RequireRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    // ---- 规范路径：/api/admin/notices ----

    @GetMapping("/api/admin/notices")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<PageResult<Notice>> list(PageQuery query) {
        return Result.success(PageResult.of(noticeService.page((int) query.getPageNum(), (int) query.getPageSize(),
                query.getKeyword(), query.getSortField(), query.getSortOrder())));
    }

    @GetMapping("/api/admin/notices/{id}")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Notice> get(@PathVariable Long id) {
        Notice notice = noticeService.getById(id);
        if (notice == null) {
            return Result.fail(404, "公告不存在");
        }
        return Result.success(notice);
    }

    @PostMapping("/api/admin/notices")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Notice> create(@Valid @RequestBody Notice notice) {
        // Mass assignment protection: strip server-controlled fields
        notice.setId(null);
        notice.setCreateTime(null);
        notice.setUpdateTime(null);
        return Result.success(noticeService.create(notice));
    }

    @PutMapping("/api/admin/notices/{id}")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Notice> update(@PathVariable Long id, @Valid @RequestBody Notice notice) {
        notice.setId(id);
        // L4 fix: 剥离服务端控制的审计字段，防止客户端覆盖 createTime/updateTime
        notice.setCreateTime(null);
        notice.setUpdateTime(null);
        return Result.success(noticeService.update(notice));
    }

    @DeleteMapping("/api/admin/notices/{id}")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Void> delete(@PathVariable Long id) {
        noticeService.delete(id);
        return Result.success();
    }

    // ---- 兼容旧路径：/api/notices（标记为 @Deprecated） ----

    @Deprecated
    @GetMapping("/api/notices")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<PageResult<Notice>> listLegacy(PageQuery query) {
        return list(query);
    }

    @Deprecated
    @GetMapping("/api/notices/{id}")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Notice> getLegacy(@PathVariable Long id) {
        return get(id);
    }

    @Deprecated
    @PostMapping("/api/notices")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Notice> createLegacy(@Valid @RequestBody Notice notice) {
        return create(notice);
    }

    @Deprecated
    @PutMapping("/api/notices/{id}")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Notice> updateLegacy(@PathVariable Long id, @Valid @RequestBody Notice notice) {
        return update(id, notice);
    }

    @Deprecated
    @DeleteMapping("/api/notices/{id}")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Void> deleteLegacy(@PathVariable Long id) {
        return delete(id);
    }
}
