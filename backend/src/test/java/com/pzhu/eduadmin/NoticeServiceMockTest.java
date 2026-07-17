package com.pzhu.eduadmin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.QueryHelper;
import com.pzhu.eduadmin.modules.notice.entity.Notice;
import com.pzhu.eduadmin.modules.notice.mapper.NoticeMapper;
import com.pzhu.eduadmin.modules.notice.service.NoticeServiceImpl;
import com.pzhu.eduadmin.modules.statistics.entity.OperationLog;
import com.pzhu.eduadmin.modules.statistics.mapper.OperationLogMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("公告服务 Mock 单元测试")
class NoticeServiceMockTest {

    @Mock private NoticeMapper noticeMapper;
    @Mock private OperationLogMapper operationLogMapper;

    @InjectMocks
    private NoticeServiceImpl noticeService;

    private MockedStatic<QueryHelper> queryHelperMock;

    @BeforeAll
    static void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, Notice.class);
        TableInfoHelper.initTableInfo(assistant, OperationLog.class);
    }

    @BeforeEach
    void setUp() {
        CurrentUserHolder.set(new LoginUser(1L, "admin", "SUPER_ADMIN"));
        queryHelperMock = mockStatic(QueryHelper.class);
    }

    @AfterEach
    void tearDown() {
        CurrentUserHolder.clear();
        if (queryHelperMock != null) queryHelperMock.close();
    }

    @Test
    @DisplayName("分页查询无关键词")
    void page_NoKeyword() {
        Notice notice = new Notice();
        notice.setId(1L);
        notice.setTitle("测试公告");
        Page<Notice> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(notice));
        when(noticeMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(mockPage);

        Page<Notice> result = noticeService.page(1, 10, null, null, null);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getTitle()).isEqualTo("测试公告");
    }

    @Test
    @DisplayName("按关键词搜索公告")
    void page_WithKeyword() {
        Notice notice = new Notice();
        notice.setId(1L);
        notice.setTitle("重要通知");
        Page<Notice> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(notice));
        when(noticeMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(mockPage);

        Page<Notice> result = noticeService.page(1, 10, "重要", null, null);

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("按 ID 查询公告")
    void getById_Found() {
        Notice notice = new Notice();
        notice.setId(1L);
        notice.setTitle("测试公告");
        when(noticeMapper.selectById(1L)).thenReturn(notice);

        Notice result = noticeService.getById(1L);

        assertThat(result.getTitle()).isEqualTo("测试公告");
    }

    @Test
    @DisplayName("创建公告并写操作日志")
    void create_Success() {
        doAnswer(inv -> { inv.getArgument(0, Notice.class).setId(100L); return 1; })
                .when(noticeMapper).insert(any(Notice.class));
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);

        Notice notice = new Notice();
        notice.setTitle("新公告");
        notice.setContent("内容");

        Notice result = noticeService.create(notice);

        assertThat(result.getId()).isEqualTo(100L);
        verify(operationLogMapper).insert(any(OperationLog.class));
    }

    @Test
    @DisplayName("更新公告并记录日志 — 返回更新后的数据")
    void update_Success() {
        when(noticeMapper.updateById(any(Notice.class))).thenReturn(1);
        Notice updated = new Notice();
        updated.setId(50L);
        updated.setTitle("更新后的标题");
        when(noticeMapper.selectById(50L)).thenReturn(updated);
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);

        Notice notice = new Notice();
        notice.setId(50L);
        notice.setTitle("新标题");

        Notice result = noticeService.update(notice);

        assertThat(result.getTitle()).isEqualTo("更新后的标题");
        verify(operationLogMapper).insert(any(OperationLog.class));
    }

    @Test
    @DisplayName("删除公告成功返回 true 并写日志")
    void delete_Success() {
        when(noticeMapper.deleteById(100L)).thenReturn(1);
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);

        boolean result = noticeService.delete(100L);

        assertThat(result).isTrue();
        verify(operationLogMapper).insert(any(OperationLog.class));
    }

    @Test
    @DisplayName("删除不存在的公告返回 false，不写日志")
    void delete_NotFound() {
        when(noticeMapper.deleteById(999L)).thenReturn(0);

        boolean result = noticeService.delete(999L);

        assertThat(result).isFalse();
        verify(operationLogMapper, never()).insert(any(OperationLog.class));
    }

    @Test
    @DisplayName("日志记录 — IP 取自 X-Forwarded-For")
    void logOperation_IPFromXForwardedFor() {
        doAnswer(inv -> { inv.getArgument(0, Notice.class).setId(100L); return 1; })
                .when(noticeMapper).insert(any(Notice.class));
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);

        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1");
        ServletRequestAttributes attrs = new ServletRequestAttributes(mockRequest);

        try (MockedStatic<RequestContextHolder> holderMock = mockStatic(RequestContextHolder.class)) {
            holderMock.when(RequestContextHolder::getRequestAttributes).thenReturn(attrs);

            Notice notice = new Notice();
            notice.setTitle("测试");
            noticeService.create(notice);
        }

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getIp()).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("日志记录 — X-Forwarded-For 未知时回退到 X-Real-IP")
    void logOperation_FallbackToXRealIP() {
        doAnswer(inv -> { inv.getArgument(0, Notice.class).setId(100L); return 1; })
                .when(noticeMapper).insert(any(Notice.class));
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);

        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getHeader("X-Forwarded-For")).thenReturn("unknown");
        when(mockRequest.getHeader("X-Real-IP")).thenReturn("192.168.1.1");
        ServletRequestAttributes attrs = new ServletRequestAttributes(mockRequest);

        try (MockedStatic<RequestContextHolder> holderMock = mockStatic(RequestContextHolder.class)) {
            holderMock.when(RequestContextHolder::getRequestAttributes).thenReturn(attrs);

            Notice notice = new Notice();
            notice.setTitle("测试");
            noticeService.create(notice);
        }

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getIp()).isEqualTo("192.168.1.1");
    }

    @Test
    @DisplayName("日志记录 — 两级回退到 remoteAddr")
    void logOperation_FallbackToRemoteAddr() {
        doAnswer(inv -> { inv.getArgument(0, Notice.class).setId(100L); return 1; })
                .when(noticeMapper).insert(any(Notice.class));
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);

        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(mockRequest.getHeader("X-Real-IP")).thenReturn(null);
        when(mockRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        ServletRequestAttributes attrs = new ServletRequestAttributes(mockRequest);

        try (MockedStatic<RequestContextHolder> holderMock = mockStatic(RequestContextHolder.class)) {
            holderMock.when(RequestContextHolder::getRequestAttributes).thenReturn(attrs);

            Notice notice = new Notice();
            notice.setTitle("测试");
            noticeService.create(notice);
        }

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getIp()).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("日志记录 — X-Forwarded-For 多 IP 取第一个")
    void logOperation_XForwardedForChain() {
        doAnswer(inv -> { inv.getArgument(0, Notice.class).setId(100L); return 1; })
                .when(noticeMapper).insert(any(Notice.class));
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);

        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 10.0.0.2, 10.0.0.3");
        ServletRequestAttributes attrs = new ServletRequestAttributes(mockRequest);

        try (MockedStatic<RequestContextHolder> holderMock = mockStatic(RequestContextHolder.class)) {
            holderMock.when(RequestContextHolder::getRequestAttributes).thenReturn(attrs);

            Notice notice = new Notice();
            notice.setTitle("测试");
            noticeService.create(notice);
        }

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getIp()).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("日志记录 — RequestContextHolder 为 null 时 IP 为 unknown")
    void logOperation_RequestContextHolderNull() {
        doAnswer(inv -> { inv.getArgument(0, Notice.class).setId(100L); return 1; })
                .when(noticeMapper).insert(any(Notice.class));
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);

        try (MockedStatic<RequestContextHolder> holderMock = mockStatic(RequestContextHolder.class)) {
            holderMock.when(RequestContextHolder::getRequestAttributes).thenReturn(null);

            Notice notice = new Notice();
            notice.setTitle("测试");
            noticeService.create(notice);
        }

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getIp()).isEqualTo("unknown");
    }

    @Test
    @DisplayName("日志记录 — X-Forwarded-For 为空字符串时回退")
    void logOperation_EmptyXForwardedFor() {
        doAnswer(inv -> { inv.getArgument(0, Notice.class).setId(100L); return 1; })
                .when(noticeMapper).insert(any(Notice.class));
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);

        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getHeader("X-Forwarded-For")).thenReturn("");
        when(mockRequest.getRemoteAddr()).thenReturn("10.0.0.99");
        ServletRequestAttributes attrs = new ServletRequestAttributes(mockRequest);

        try (MockedStatic<RequestContextHolder> holderMock = mockStatic(RequestContextHolder.class)) {
            holderMock.when(RequestContextHolder::getRequestAttributes).thenReturn(attrs);

            Notice notice = new Notice();
            notice.setTitle("测试");
            noticeService.create(notice);
        }

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getIp()).isEqualTo("10.0.0.99");
    }
}
