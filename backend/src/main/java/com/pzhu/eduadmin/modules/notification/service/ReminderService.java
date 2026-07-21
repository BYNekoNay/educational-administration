package com.pzhu.eduadmin.modules.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.finance.entity.LessonAccount;
import com.pzhu.eduadmin.modules.finance.mapper.LessonAccountMapper;
import com.pzhu.eduadmin.modules.notification.entity.Notification;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import com.pzhu.eduadmin.modules.student.entity.ParentStudent;
import com.pzhu.eduadmin.modules.student.mapper.ParentStudentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    private final ScheduleLessonMapper scheduleLessonMapper;
    private final ClassStudentMapper classStudentMapper;
    private final ParentStudentMapper parentStudentMapper;
    private final LessonAccountMapper lessonAccountMapper;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 */10 * * * ?")
    public void sendLessonReminders() {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime deadline = now.plusHours(2);
            List<ScheduleLesson> lessons = scheduleLessonMapper.selectList(
                    new LambdaQueryWrapper<ScheduleLesson>()
                            .eq(ScheduleLesson::getLessonDate, now.toLocalDate())
                            .eq(ScheduleLesson::getStatus, 1));

            for (ScheduleLesson lesson : lessons) {
                try {
                    if (lesson.getStartTime() == null) continue;
                    LocalDateTime start = LocalDateTime.of(lesson.getLessonDate(), lesson.getStartTime());
                    if (start.isBefore(now.plusMinutes(30)) || start.isAfter(deadline)) continue;

                    String content = lesson.getLessonDate() + " " + lesson.getStartTime() + " 即将上课";
                    if (lesson.getTeacherId() != null) {
                        sendOnce(lesson.getTeacherId(), "CLASS_REMINDER", "上课提醒", content, lesson.getId());
                    }

                    Set<Long> studentIds = classStudentMapper.selectList(
                                    new LambdaQueryWrapper<ClassStudent>()
                                            .eq(ClassStudent::getClassId, lesson.getClassId())
                                            .eq(ClassStudent::getStatus, 1))
                            .stream().map(ClassStudent::getStudentId).collect(Collectors.toSet());
                    for (Long parentId : parentUserIds(studentIds)) {
                        sendOnce(parentId, "CLASS_REMINDER", "上课提醒", content, lesson.getId());
                    }
                } catch (Exception e) {
                    log.error("上课提醒发送失败, lessonId={}", lesson.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("定时任务 sendLessonReminders 执行异常", e);
        }
    }

    @Scheduled(cron = "0 0 9 * * ?")
    public void sendLessonExpiryReminders() {
        try {
            LocalDate today = LocalDate.now();
            // Bug#44: 修正查询逻辑，明确分离"即将到期"和"课时不足"两种提醒条件
            // 条件: 剩余课时>0 且 ((有到期日且在7天内到期且未过期) 或 剩余课时<=3)
            List<LessonAccount> accounts = lessonAccountMapper.selectList(
                    new LambdaQueryWrapper<LessonAccount>()
                            .gt(LessonAccount::getRemainingLessons, BigDecimal.ZERO)
                            .and(w -> w
                                    .and(inner -> inner
                                            .isNotNull(LessonAccount::getExpireDate)
                                            .le(LessonAccount::getExpireDate, today.plusDays(7))
                                            .ge(LessonAccount::getExpireDate, today))
                                    .or()
                                    .le(LessonAccount::getRemainingLessons, new BigDecimal("3"))
                            ));

            for (LessonAccount account : accounts) {
                try {
                    // M30: 不再跳过已过期但有剩余课时的账户，家长仍需收到通知
                    String content = "剩余课时 " + account.getRemainingLessons()
                            + (account.getExpireDate() != null ? "，有效期至 " + account.getExpireDate() : "");
                    for (Long parentId : parentUserIds(Set.of(account.getStudentId()))) {
                        sendOnce(parentId, "LESSON_EXPIRY", "课时到期提醒", content, account.getId());
                    }
                } catch (Exception e) {
                    log.error("课时到期提醒发送失败, accountId={}", account.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("定时任务 sendLessonExpiryReminders 执行异常", e);
        }
    }

    private Set<Long> parentUserIds(Set<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) return Collections.emptySet();
        return parentStudentMapper.selectList(
                        new LambdaQueryWrapper<ParentStudent>().in(ParentStudent::getStudentId, studentIds))
                .stream().map(ParentStudent::getParentUserId).collect(Collectors.toSet());
    }

    private void sendOnce(Long userId, String type, String title, String content, Long relatedId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setRelatedId(relatedId);
        // M25: 去重键含日期，到期提醒每天发送一次而非整个窗口仅一次
        notificationService.sendOnce(userId, notification, type + ":" + userId + ":" + relatedId + ":" + LocalDate.now());
    }
}
