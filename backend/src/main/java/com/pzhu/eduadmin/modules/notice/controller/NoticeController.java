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
    public Result<PageResult<Notice>> list(PageQuery query) {
        return Result.success(PageResult.of(noticeService.page((int) query.getPageNum(), (int) query.getPageSize())));
    }

    @GetMapping("/api/admin/notices/{id}")
    public Result<Notice> get(@PathVariable Long id) {
        return Result.success(noticeService.getById(id));
    }

    @PostMapping("/api/admin/notices")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Notice> create(@Valid @RequestBody Notice notice) {
        return Result.success(noticeService.create(notice));
    }

    @PutMapping("/api/admin/notices/{id}")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Notice> update(@PathVariable Long id, @RequestBody Notice notice) {
        notice.setId(id);
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
    public Result<PageResult<Notice>> listLegacy(PageQuery query) {
        return list(query);
    }

    @Deprecated
    @GetMapping("/api/notices/{id}")
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
    public Result<Notice> updateLegacy(@PathVariable Long id, @RequestBody Notice notice) {
        return update(id, notice);
    }

    @Deprecated
    @DeleteMapping("/api/notices/{id}")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Void> deleteLegacy(@PathVariable Long id) {
        return delete(id);
    }
}
