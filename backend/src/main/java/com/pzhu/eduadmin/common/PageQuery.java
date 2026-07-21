package com.pzhu.eduadmin.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 通用分页请求参数：pageNum、pageSize、keyword（可选搜索关键词）、sortField/sortOrder（可选排序），与 docs/08-开发指南.md §5 联调约定保持一致。
 */
@Data
public class PageQuery implements Serializable {

    private int pageNum = 1;
    private int pageSize = 10;
    private String keyword;
    private String sortField;
    private String sortOrder;

    /**
     * Clamp pageSize to [1, 200] to prevent excessive database load or invalid pagination.
     */
    public int getPageSize() {
        // L5 fix: 增加下界，防止 pageSize<=0 导致分页异常
        return Math.max(1, Math.min(pageSize, 200));
    }
}
