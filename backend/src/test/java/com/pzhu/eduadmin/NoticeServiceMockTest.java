package com.pzhu.eduadmin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.QueryHelper;
import com.pzhu.eduadmin.modules.notice.entity.Notice;
import com.pzhu.eduadmin.modules.notice.mapper.NoticeMapper;
import com.pzhu.eduadmin.modules.notice.service.NoticeServiceImpl;
import com.pzhu.eduadmin.common.EntityNameResolver;
import com.pzhu.eduadmin.modules.statistics.service.OperationLogService;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.LoginUser;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("公告服务 Mock 单元测试")
class NoticeServiceMockTest {

    @Mock private NoticeMapper noticeMapper;
    @Mock private OperationLogService operationLogService;
    @Mock private EntityNameResolver nameResolver;

    @InjectMocks
    private NoticeServiceImpl noticeService;

    private MockedStatic<QueryHelper> queryHelperMock;

    @BeforeAll
    static void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, Notice.class);
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

        Notice notice = new Notice();
        notice.setTitle("新公告");
        notice.setContent("内容");

        Notice result = noticeService.create(notice);

        assertThat(result.getId()).isEqualTo(100L);
        verify(operationLogService).log(anyString(), anyString());
    }

    @Test
    @DisplayName("更新公告并记录日志 — 返回更新后的数据")
    void update_Success() {
        when(noticeMapper.updateById(any(Notice.class))).thenReturn(1);
        Notice updated = new Notice();
        updated.setId(50L);
        updated.setTitle("更新后的标题");
        when(noticeMapper.selectById(50L)).thenReturn(updated);

        Notice notice = new Notice();
        notice.setId(50L);
        notice.setTitle("新标题");

        Notice result = noticeService.update(notice);

        assertThat(result.getTitle()).isEqualTo("更新后的标题");
        verify(operationLogService).log(anyString(), anyString());
    }

    @Test
    @DisplayName("删除公告成功返回 true 并写日志")
    void delete_Success() {
        when(noticeMapper.deleteById(100L)).thenReturn(1);


        boolean result = noticeService.delete(100L);

        assertThat(result).isTrue();
        verify(operationLogService).log(anyString(), anyString());
    }

    @Test
    @DisplayName("删除不存在的公告返回 false，不写日志")
    void delete_NotFound() {
        when(noticeMapper.deleteById(999L)).thenReturn(0);

        boolean result = noticeService.delete(999L);

        assertThat(result).isFalse();
        verify(operationLogService, never()).log(anyString(), anyString());
    }
}
