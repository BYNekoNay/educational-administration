package com.pzhu.eduadmin.modules.statistics.service;

import com.pzhu.eduadmin.common.IpUtil;
import com.pzhu.eduadmin.modules.statistics.entity.OperationLog;
import com.pzhu.eduadmin.modules.statistics.mapper.OperationLogMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogService {

    private final OperationLogMapper operationLogMapper;

    @Override
    public void log(String module, String operation) {
        log(module, operation, null, null);
    }

    @Override
    public void log(String module, String operation, String targetType, Long targetId) {
        OperationLog record = new OperationLog();
        LoginUser operator = CurrentUserHolder.get();
        record.setOperatorId(operator != null ? operator.getUserId() : 0L);
        record.setModule(module);
        record.setOperation(operation);
        record.setTargetType(targetType);
        record.setTargetId(targetId);
        record.setHitCount(1);
        record.setIp(IpUtil.getCurrentIp());
        operationLogMapper.insert(record);
    }
}
