package com.pzhu.eduadmin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.exam.entity.ExamSignup;
import com.pzhu.eduadmin.modules.exam.entity.ExamLevel;
import com.pzhu.eduadmin.modules.exam.mapper.ExamLevelMapper;
import com.pzhu.eduadmin.modules.exam.mapper.ExamSignupMapper;
import com.pzhu.eduadmin.modules.exam.service.ExamServiceImpl;
import com.pzhu.eduadmin.modules.student.mapper.StudentMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 考级报名状态机的并发正确性测试（第四轮 B-1 的回归门禁）。
 *
 * <p>背景：原实现 {@code ExamServiceImpl.updateExamSignup} 采用
 * 「selectById 读快照 → Java 侧校验 → updateById 裸更新」，UPDATE 只有主键条件。
 * 两名教务同时审批、或同一人用陈旧页面连点时，两次更新都会成功，先提交者的终态会被
 * 静默覆盖——甚至可以把已通过（2）改回未通过（3），而 1→2/3 only 的规则在数据库层
 * 没有任何强制力。</p>
 *
 * <p>本测试专门守护这条：写入必须带状态前置条件（CAS），陈旧快照必须被拒绝。</p>
 *
 * <p>承重要求：把 ExamServiceImpl 里 CAS 的 {@code .eq(ExamSignup::getStatus, ...)}
 * 去掉后，下面第二个用例必须变红（返回成功而非 409）。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("考级报名状态机并发正确性")
class ExamSignupConcurrencyTest {

    @Mock
    private ExamSignupMapper examSignupMapper;
    @Mock
    private ExamLevelMapper examLevelMapper;
    @Mock
    private StudentMapper studentMapper;

    @InjectMocks
    private ExamServiceImpl examService;

    @BeforeAll
    static void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, ExamSignup.class);
        TableInfoHelper.initTableInfo(assistant, ExamLevel.class);
    }

    private static ExamSignup signupWithStatus(Long id, Integer status) {
        ExamSignup s = new ExamSignup();
        s.setId(id);
        s.setStatus(status);
        return s;
    }

    @Test
    @DisplayName("正常审批：1(已报名) → 2(通过) 且写入带状态前置条件")
    void update_statusTransitionCarriesStatusPrecondition() {
        when(examSignupMapper.selectById(7L)).thenReturn(signupWithStatus(7L, 1));
        // CAS 命中：受影响行数 1
        when(examSignupMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(examSignupMapper.selectById(7L)).thenReturn(signupWithStatus(7L, 1))
                .thenReturn(signupWithStatus(7L, 2));

        ExamSignup req = signupWithStatus(7L, 2);
        examService.updateExamSignup(req);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<ExamSignup>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(examSignupMapper).update(isNull(), captor.capture());

        // 关键：UPDATE 语句必须包含 status 前置条件，否则等于没有并发保护
        String sqlSegment = captor.getValue().getTargetSql();
        assertThat(sqlSegment).contains("status");
        assertThat(captor.getValue().getSqlSet()).contains("status");
    }

    @Test
    @DisplayName("陈旧快照不可覆盖终态：第二个操作者拿到旧页面提交时必须 409")
    void update_staleSnapshotCannotOverwriteTerminalState() {
        // 两个操作者都读到 status=1（第二个是陈旧页面）
        when(examSignupMapper.selectById(7L)).thenReturn(signupWithStatus(7L, 1));

        // 第一个人：CAS 命中
        when(examSignupMapper.update(any(), any(LambdaUpdateWrapper.class)))
                .thenReturn(1)   // 第一次：status 仍为 1，命中
                .thenReturn(0);  // 第二次：库里已是 2，前置条件不匹配 → 0 行

        // 第一个人提交通过
        examService.updateExamSignup(signupWithStatus(7L, 2));

        // 第二个人用陈旧快照想把状态改成"未通过"——必须被拒绝，而不是静默覆盖
        assertThatThrownBy(() -> examService.updateExamSignup(signupWithStatus(7L, 3)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态已变更");
    }

    @Test
    @DisplayName("非法的终态回退（2 → 3）在校验阶段即被拒绝")
    void update_terminalStateCannotBeRolledBack() {
        when(examSignupMapper.selectById(7L)).thenReturn(signupWithStatus(7L, 2));

        assertThatThrownBy(() -> examService.updateExamSignup(signupWithStatus(7L, 3)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无效的状态变更");

        // 校验失败时不应产生任何写入
        verify(examSignupMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
    }
}
