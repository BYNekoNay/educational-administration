SET NAMES utf8mb4;

-- A2 报名待审核样本：student 93 徐浩宇（parent 23 / class 1 / course 1 少儿美术启蒙班）
-- 业务语义：已完成周期后的续报（enrollment 无 student+course 唯一键，允许）
INSERT INTO enrollment
  (student_id, parent_user_id, class_id, course_id, status,
   auditor_id, audit_remark, hold_expire_time, create_time)
VALUES
  (93, 23, 1, 1, 1, NULL, NULL, NULL, NOW());

-- 验证
SELECT CONCAT('enrollment 总数=', COUNT(*)) AS info FROM enrollment WHERE is_deleted=0;
SELECT CONCAT('待审核样本: id=', id, ' student=', student_id, ' status=', status,
              ' auditor=', IFNULL(auditor_id,'NULL')) AS info
FROM enrollment WHERE status=1 AND is_deleted=0;
