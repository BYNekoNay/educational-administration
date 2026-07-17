package com.pzhu.eduadmin;

import com.pzhu.eduadmin.common.GlobalExceptionHandler;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("全局异常处理单元测试")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("BusinessException — 返回对应 code 和 message")
    void handleBusinessException() {
        Result<Void> r = handler.handleBusinessException(new BusinessException(404, "资源不存在"));
        assertThat(r.getCode()).isEqualTo(404);
        assertThat(r.getMessage()).isEqualTo("资源不存在");
    }

    @Test
    @DisplayName("BusinessException 默认 code")
    void handleBusinessException_DefaultCode() {
        Result<Void> r = handler.handleBusinessException(new BusinessException("操作失败"));
        assertThat(r.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("MethodArgumentNotValidException — 提取 fieldError 消息")
    void handleValidException() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "obj");
        bindingResult.addError(new FieldError("obj", "name", "姓名不能为空"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        Result<Void> r = handler.handleValidException(ex);
        assertThat(r.getCode()).isEqualTo(400);
        assertThat(r.getMessage()).isEqualTo("姓名不能为空");
    }

    @Test
    @DisplayName("BindException — 提取 fieldError 消息")
    void handleBindException() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "obj");
        bindingResult.addError(new FieldError("obj", "age", "年龄不能为空"));
        BindException ex = new BindException(bindingResult);

        Result<Void> r = handler.handleBindException(ex);
        assertThat(r.getCode()).isEqualTo(400);
        assertThat(r.getMessage()).isEqualTo("年龄不能为空");
    }

    @Test
    @DisplayName("ConstraintViolationException — 提取第一条违规消息")
    @SuppressWarnings("unchecked")
    void handleConstraintViolationException() {
        ConstraintViolation<Object> cv = (ConstraintViolation<Object>)
                org.mockito.Mockito.mock(ConstraintViolation.class);
        org.mockito.Mockito.when(cv.getMessage()).thenReturn("手机号不能为空");
        ConstraintViolationException ex = new ConstraintViolationException("msg", Set.of(cv));

        Result<Void> r = handler.handleConstraintViolationException(ex);
        assertThat(r.getMessage()).isEqualTo("手机号不能为空");
    }

    @Test
    @DisplayName("MissingServletRequestParameterException — 参数缺失提示")
    void handleMissingParam() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("userId", "Long");

        Result<Void> r = handler.handleMissingParam(ex);
        assertThat(r.getMessage()).contains("userId");
    }

    @Test
    @DisplayName("MethodArgumentTypeMismatchException — 类型错误")
    void handleTypeMismatch() {
        MethodArgumentTypeMismatchException ex =
                new MethodArgumentTypeMismatchException("123abc", Long.class, "id", null, null);

        Result<Void> r = handler.handleTypeMismatch(ex);
        assertThat(r.getCode()).isEqualTo(400);
        assertThat(r.getMessage()).contains("参数类型错误");
    }

    @Test
    @DisplayName("HttpMessageNotReadableException — 格式错误")
    void handleNotReadable() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("bad json");

        Result<Void> r = handler.handleNotReadable(ex);
        assertThat(r.getMessage()).contains("格式错误");
    }

    @Test
    @DisplayName("HttpRequestMethodNotSupportedException — 405")
    void handleMethodNotSupported() {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("GET", List.of());

        Result<Void> r = handler.handleMethodNotSupported(ex);
        assertThat(r.getCode()).isEqualTo(405);
    }

    @Test
    @DisplayName("NoHandlerFoundException — 404")
    void handleNoHandlerFound() {
        org.springframework.web.servlet.NoHandlerFoundException ex =
                new org.springframework.web.servlet.NoHandlerFoundException("GET", "/api/nonexistent", null);

        Result<Void> r = handler.handleNoHandlerFound(ex);
        assertThat(r.getCode()).isEqualTo(404);
        assertThat(r.getMessage()).contains("不存在");
    }

    // ==================== 数据库异常 ====================

    @Test
    @DisplayName("DuplicateKeyException — 409")
    void handleDuplicateKey() {
        DuplicateKeyException ex = new DuplicateKeyException("Duplicate entry 'admin'");

        Result<Void> r = handler.handleDuplicateKey(ex);
        assertThat(r.getCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("DataIntegrityViolationException Duplicate entry — 409")
    void handleDataIntegrity_Duplicate() {
        SQLIntegrityConstraintViolationException cause =
                new SQLIntegrityConstraintViolationException("Duplicate entry 'x' for key 'uk_username'");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("msg", cause);

        Result<Void> r = handler.handleDataIntegrity(ex);
        assertThat(r.getMessage()).contains("重复");
    }

    @Test
    @DisplayName("DataIntegrityViolationException foreign key — 409")
    void handleDataIntegrity_ForeignKey() {
        SQLIntegrityConstraintViolationException cause =
                new SQLIntegrityConstraintViolationException("a foreign key constraint fails");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("msg", cause);

        Result<Void> r = handler.handleDataIntegrity(ex);
        assertThat(r.getMessage()).contains("被其他记录引用");
    }

    @Test
    @DisplayName("Exception 兜底 — 500")
    void handleException() {
        Result<Void> r = handler.handleException(new RuntimeException("未知错误"));
        assertThat(r.getCode()).isEqualTo(500);
        assertThat(r.getMessage()).contains("服务器内部错误");
    }
}
