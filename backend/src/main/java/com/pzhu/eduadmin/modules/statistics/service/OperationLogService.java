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
}
