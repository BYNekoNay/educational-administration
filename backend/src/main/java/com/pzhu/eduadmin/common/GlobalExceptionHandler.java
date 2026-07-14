package com.pzhu.eduadmin.common;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理：将各类异常统一转换为 Result 响应，提供用户可读的中文提示。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================== 业务异常 ====================

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    // ==================== 参数校验异常 ====================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        return Result.fail(400, message);
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        return Result.fail(400, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
        // 提取第一条用户可读的校验消息，避免暴露技术路径
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .filter(m -> m != null && !m.isBlank())
                .findFirst()
                .orElse("参数校验失败");
        return Result.fail(400, message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.fail(400, "缺少必要参数：" + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.fail(400, "参数类型错误：" + e.getName() + " 应为 " +
                (e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "合法值"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        return Result.fail(400, "请求体格式错误或为空，请检查数据格式");
    }

    // ==================== 请求方式异常 ====================

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return Result.fail(405, "请求方式不正确，请使用：" + e.getSupportedHttpMethods());
    }

    // ==================== 数据库异常 ====================

    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("数据重复", e);
        return Result.fail(409, "数据已存在，请勿重复提交");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<Void> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("数据完整性冲突", e);
        // 提取最有用的信息
        String msg = e.getMostSpecificCause().getMessage();
        if (msg != null && msg.contains("Duplicate entry")) {
            return Result.fail(409, "数据重复，该记录已存在");
        }
        if (msg != null && msg.contains("foreign key")) {
            return Result.fail(409, "该数据被其他记录引用，无法删除");
        }
        return Result.fail(409, "数据操作冲突，请检查数据是否合法");
    }

    // ==================== 通用兜底 ====================

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        // 提取根因消息，去掉包名等技术噪音，给用户一个可理解的提示
        String rootMsg = extractRootMessage(e);
        if (rootMsg != null && !rootMsg.isBlank()) {
            return Result.fail(500, rootMsg);
        }
        return Result.fail(500, "服务器内部错误，请稍后再试");
    }

    /**
     * 递归提取异常链中最有意义的根因消息，去掉包名等技术细节。
     */
    private String extractRootMessage(Throwable e) {
        if (e == null) return null;
        // 递归到最底层
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String raw = cause.getMessage();
        if (raw == null || raw.isBlank()) {
            raw = e.getMessage();
        }
        if (raw == null || raw.isBlank()) {
            return null;
        }
        // 常见技术文案替换为中文
        if (raw.contains("Connection refused") || raw.contains("Communications link failure")) {
            return "数据库连接失败，请检查数据库服务是否启动";
        }
        if (raw.contains("Access denied for user")) {
            return "数据库账号或密码错误，无法连接";
        }
        if (raw.contains("Unknown column") || raw.contains("doesn't exist")) {
            return "数据表结构异常：" + raw.replaceAll(".*Unknown column '([^']*)'.*", "字段 '$1' 不存在").replaceAll(".*Table '(.*)' doesn't exist.*", "数据表 '$1' 不存在");
        }
        if (raw.contains("Data too long")) {
            return "输入数据过长，请缩短后重试";
        }
        if (raw.contains("Out of range value")) {
            return "输入的数值超出允许范围";
        }
        if (raw.contains("NullPointerException") || (raw.contains("Cannot invoke") && raw.contains("null"))) {
            return "系统数据异常，请联系管理员";
        }
        // 去掉包名前缀（如 java.sql.SQLException: ...）
        String cleaned = raw.replaceAll("^[\\w.]+\\.([\\w]+Exception):\\s*", "$1: ");
        // 如果清洗后仍然是技术消息，返回简化版
        if (cleaned.length() > 100) {
            cleaned = cleaned.substring(0, 100) + "...";
        }
        return cleaned;
    }
}
