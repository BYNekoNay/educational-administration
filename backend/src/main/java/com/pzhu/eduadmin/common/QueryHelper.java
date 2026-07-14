package com.pzhu.eduadmin.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;

import java.util.List;
import java.util.Map;

/**
 * 通用查询工具：为 LambdaQueryWrapper 添加关键词模糊搜索和动态排序。
 */
public final class QueryHelper {

    private QueryHelper() {}

    /**
     * 对多个字段应用关键词模糊搜索（OR 连接）。
     *
     * @param wrapper LambdaQueryWrapper
     * @param keyword 搜索关键词（null/空串时不应用）
     * @param columns 要搜索的字段列表
     */
    @SafeVarargs
    public static <T> void applyKeyword(LambdaQueryWrapper<T> wrapper, String keyword, SFunction<T, String>... columns) {
        if (keyword == null || keyword.isBlank() || columns == null || columns.length == 0) {
            return;
        }
        wrapper.and(w -> {
            for (int i = 0; i < columns.length; i++) {
                if (i == 0) {
                    w.like(columns[i], keyword);
                } else {
                    w.or().like(columns[i], keyword);
                }
            }
        });
    }

    /**
     * 应用动态排序。如果 sortField 为空或不在映射中，使用默认排序回调。
     *
     * @param wrapper    LambdaQueryWrapper
     * @param sortField  排序字段名
     * @param sortOrder  asc / desc
     * @param sortColumnMap 排序字段映射
     * @param <T>        实体类型
     */
    public static <T> void applySort(LambdaQueryWrapper<T> wrapper, String sortField, String sortOrder,
                                      Map<String, SFunction<T, ?>> sortColumnMap) {
        if (sortField != null && !sortField.isBlank() && sortColumnMap != null && sortColumnMap.containsKey(sortField)) {
            boolean asc = !"desc".equalsIgnoreCase(sortOrder);
            wrapper.orderBy(true, asc, sortColumnMap.get(sortField));
        }
    }

    /**
     * 应用动态排序（简化版，带默认排序回调）。
     *
     * @param wrapper         LambdaQueryWrapper
     * @param sortField       排序字段名
     * @param sortOrder       asc / desc
     * @param sortColumnMap   排序字段映射
     * @param defaultSort     无排序时的默认排序（可传 Runnable 或 null）
     * @param <T>             实体类型
     */
    public static <T> void applySort(LambdaQueryWrapper<T> wrapper, String sortField, String sortOrder,
                                      Map<String, SFunction<T, ?>> sortColumnMap, Runnable defaultSort) {
        if (sortField != null && !sortField.isBlank() && sortColumnMap != null && sortColumnMap.containsKey(sortField)) {
            boolean asc = !"desc".equalsIgnoreCase(sortOrder);
            wrapper.orderBy(true, asc, sortColumnMap.get(sortField));
        } else if (defaultSort != null) {
            defaultSort.run();
        }
    }
}
