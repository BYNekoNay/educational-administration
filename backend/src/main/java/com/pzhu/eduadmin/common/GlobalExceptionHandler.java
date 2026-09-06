package com.pzhu.eduadmin.common;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import com.pzhu.eduadmin.observability.BusinessMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 全局异常处理：将各类异常统一转换为 Result 响应，提供用户可读的中文提示。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Autowired(required = false)
    private BusinessMetrics businessMetrics;

    // ==================== 业务异常 ====================

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        if (businessMetrics != null) {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            String requestPath = attributes == null ? null : attributes.getRequest().getRequestURI();
            businessMetrics.recordBusinessFailure(requestPath, e.getCode(), e.getMessage());
        }
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

    // M9 fix: Spring 6.1 起，@Validated 控制器方法参数（@RequestParam 上的约束）校验失败
    // 抛 HandlerMethodValidationException 而非 ConstraintViolationException，
    // 缺少此处理器时所有非法提交会落入通用兜底返回 500（如家长请假原因校验）
    @ExceptionHandler(HandlerMethodValidationException.class)
    public Result<Void> handleHandlerMethodValidation(HandlerMethodValidationException e) {
        String message = e.getAllValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream())
                .map(MessageSourceResolvable::getDefaultMessage)
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
        return Result.fail(400, "参数类型错误，请检查参数格式");
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

    // L8 fix: Content-Type 不受支持语义上是 415，缺少处理器会落入通用兜底返回 500
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public Result<Void> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return Result.fail(415, "不支持的请求内容类型：" + e.getContentType());
    }

    // L8 fix: 上传文件超过大小限制应返回友好提示，而非通用 500
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return Result.fail(400, "上传文件大小超过限制");
    }

    // ==================== 404 处理 ====================

    @ExceptionHandler(NoHandlerFoundException.class)
    public Result<Void> handleNoHandlerFound(NoHandlerFoundException e) {
        return Result.fail(404, "请求的资源不存在");
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
        return Result.fail(500, "服务器内部错误，请稍后再试");
    }
}
