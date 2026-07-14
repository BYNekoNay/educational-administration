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
}
