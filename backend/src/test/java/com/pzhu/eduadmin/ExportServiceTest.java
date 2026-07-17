package com.pzhu.eduadmin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.finance.entity.LessonFlow;
import com.pzhu.eduadmin.modules.finance.entity.PaymentRecord;
import com.pzhu.eduadmin.modules.finance.mapper.LessonFlowMapper;
import com.pzhu.eduadmin.modules.finance.mapper.PaymentRecordMapper;
import com.pzhu.eduadmin.modules.salary.entity.TeacherSalary;
import com.pzhu.eduadmin.modules.salary.mapper.TeacherSalaryMapper;
import com.pzhu.eduadmin.modules.statistics.service.ExportService;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("导出服务 Mock 单元测试")
class ExportServiceTest {

    @Mock private PaymentRecordMapper paymentRecordMapper;
    @Mock private LessonFlowMapper lessonFlowMapper;
    @Mock private TeacherSalaryMapper teacherSalaryMapper;

    @InjectMocks
    private ExportService exportService;

    private ByteArrayOutputStream baos;

    @BeforeAll
    static void initLambdaCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant asst = new MapperBuilderAssistant(cfg, "");
        TableInfoHelper.initTableInfo(asst, PaymentRecord.class);
        TableInfoHelper.initTableInfo(asst, LessonFlow.class);
        TableInfoHelper.initTableInfo(asst, TeacherSalary.class);
    }

    @BeforeEach
    void setUp() {
        baos = new ByteArrayOutputStream();
    }

    @Test
    @DisplayName("导出收费台账 — 响应头包含正确编码")
    void exportPayments_ResponseHeaders() throws IOException {
        when(paymentRecordMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        HttpServletResponse response = mockResponse();
        exportService.exportPayments(response, "2026-01-01", "2026-12-31");

        verify(response).setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        verify(response).setCharacterEncoding("UTF-8");
        verify(response).setHeader(eq("Content-Disposition"), contains("filename*=UTF-8''"));
    }

    @Test
    @DisplayName("导出课时消耗 — 响应头正确")
    void exportLessonFlows_ResponseHeaders() throws IOException {
        LessonFlow lf = new LessonFlow();
        lf.setId(1L);
        lf.setStudentId(10L);
        lf.setSourceType(1);
        when(lessonFlowMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(lf));

        HttpServletResponse response = mockResponse();
        exportService.exportLessonFlows(response, "2026-01-01", "2026-12-31");

        verify(response).setHeader(eq("Content-Disposition"), contains("filename*=UTF-8''"));
    }

    @Test
    @DisplayName("导出薪资 — 响应头正确")
    void exportSalaries_ResponseHeaders() throws IOException {
        TeacherSalary ts = new TeacherSalary();
        ts.setId(1L);
        ts.setTeacherId(10L);
        ts.setSalaryMonth("2026-07");
        when(teacherSalaryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(ts));

        HttpServletResponse response = mockResponse();
        exportService.exportSalaries(response, "2026-07");

        verify(response).setHeader(eq("Content-Disposition"), contains("filename*=UTF-8''"));
    }

    private HttpServletResponse mockResponse() throws IOException {
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream sos = new ServletOutputStream() {
            @Override public void write(int b) { baos.write(b); }
            @Override public boolean isReady() { return true; }
            @Override public void setWriteListener(jakarta.servlet.WriteListener listener) {}
        };
        when(response.getOutputStream()).thenReturn(sos);
        return response;
    }

    // ============ exportPayments ============

    @Test
    @DisplayName("导出收费台账正常")
    void exportPayments_Success() throws IOException {
        PaymentRecord pr = new PaymentRecord();
        pr.setId(1L);
        pr.setEnrollmentId(10L);
        pr.setStudentId(20L);
        pr.setCourseId(30L);
        pr.setLessonCount(new BigDecimal("10"));
        pr.setAmount(new BigDecimal("1000"));
        pr.setPayType(1);
        pr.setPayTime(LocalDateTime.of(2026, 7, 1, 10, 0));
        when(paymentRecordMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(pr));

        HttpServletResponse response = mockResponse();
        exportService.exportPayments(response, "2026-06-01", "2026-07-31");

        verify(response).setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(baos.size()).isGreaterThan(0);
    }

    @Test
    @DisplayName("导出收费台账日期格式错误 — 抛出 BusinessException")
    void exportPayments_InvalidDate() {
        HttpServletResponse response = mock(HttpServletResponse.class);

        assertThatThrownBy(() -> exportService.exportPayments(response, "2026/07/01", "2026-07-31"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("日期格式不正确");
    }

    @Test
    @DisplayName("导出收费台账无日期限制(startDate 和 endDate 均为 null)")
    void exportPayments_NoDates() throws IOException {
        when(paymentRecordMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        HttpServletResponse response = mockResponse();
        exportService.exportPayments(response, null, null);

        assertThat(baos.size()).isGreaterThan(0);
    }

    @Test
    @DisplayName("导出收费台账空结果")
    void exportPayments_EmptyResult() throws IOException {
        when(paymentRecordMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        HttpServletResponse response = mockResponse();
        exportService.exportPayments(response, null, null);

        assertThat(baos.size()).isGreaterThan(0);
    }

    // ============ exportLessonFlows ============

    @Test
    @DisplayName("导出课时消耗报表正常")
    void exportLessonFlows_Success() throws IOException {
        LessonFlow lf = new LessonFlow();
        lf.setId(1L);
        lf.setStudentId(10L);
        lf.setSourceType(2);
        lf.setChangeAmount(new BigDecimal("1"));
        lf.setBeforeBalance(new BigDecimal("10"));
        lf.setAfterBalance(new BigDecimal("9"));
        lf.setCreateTime(LocalDateTime.now());
        when(lessonFlowMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(lf));

        HttpServletResponse response = mockResponse();
        exportService.exportLessonFlows(response, "2026-01-01", "2026-12-31");

        assertThat(baos.size()).isGreaterThan(0);
    }

    @Test
    @DisplayName("导出课时消耗日期格式错误")
    void exportLessonFlows_InvalidDate() {
        HttpServletResponse response = mock(HttpServletResponse.class);

        assertThatThrownBy(() -> exportService.exportLessonFlows(response, "bad-date", "2026-07-31"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("日期格式不正确");
    }

    // ============ exportSalaries ============

    @Test
    @DisplayName("导出薪资结算单正常")
    void exportSalaries_Success() throws IOException {
        TeacherSalary ts = new TeacherSalary();
        ts.setId(1L);
        ts.setTeacherId(10L);
        ts.setSalaryMonth("2026-07");
        ts.setLessonCount(new BigDecimal("20"));
        ts.setSubstituteCount(BigDecimal.ZERO);
        ts.setBaseAmount(new BigDecimal("4000"));
        ts.setBonusAmount(new BigDecimal("500"));
        ts.setTotalAmount(new BigDecimal("4500"));
        ts.setStatus(2);
        when(teacherSalaryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(ts));

        HttpServletResponse response = mockResponse();
        exportService.exportSalaries(response, "2026-07");

        assertThat(baos.size()).isGreaterThan(0);
    }

    @Test
    @DisplayName("导出薪资月份格式错误 — 抛出 BusinessException")
    void exportSalaries_InvalidMonth() {
        HttpServletResponse response = mock(HttpServletResponse.class);

        assertThatThrownBy(() -> exportService.exportSalaries(response, "2026/07"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("日期格式不正确");
    }

    @Test
    @DisplayName("导出薪资无月份过滤")
    void exportSalaries_NoMonth() throws IOException {
        when(teacherSalaryMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        HttpServletResponse response = mockResponse();
        exportService.exportSalaries(response, null);

        assertThat(baos.size()).isGreaterThan(0);
    }
}
