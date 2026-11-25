package com.pzhu.eduadmin.modules.statistics.service;

/**
 * 操作日志统一写入服务。
 * 替代各模块 Service 中重复的 logOperation() 私有方法。
 */
public interface OperationLogService {

    /**
     * 记录一条操作日志。
     *
     * @param module    模块名（如 "学员管理"、"财务管理"）
     * @param operation 操作描述（可含业务名称）
     */
    void log(String module, String operation);

    /**
     * 记录一条带操作对象的结构化日志，用于"谁看过/改过哪个对象"的反查。
     * <p>当 targetType/targetId 为 null 时，等价于 {@link #log(String, String)}。
     *
     * @param targetType 对象类型，如 STUDENT / USER
     * @param targetId   对象ID
     */
    void log(String module, String operation, String targetType, Long targetId);
}
