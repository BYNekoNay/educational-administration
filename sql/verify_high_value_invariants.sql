-- Returns no rows when the high-value business data is internally consistent.
-- Deployment and restore-drill scripts treat any returned row as a hard gate.

SELECT 'orphan_payment_enrollment' AS invariant_name, COUNT(*) AS violation_count
FROM payment_record p
LEFT JOIN enrollment e ON e.id = p.enrollment_id
WHERE e.id IS NULL
HAVING COUNT(*) > 0

UNION ALL

SELECT 'orphan_refund_payment', COUNT(*)
FROM refund_record r
LEFT JOIN payment_record p ON p.id = r.payment_record_id
WHERE p.id IS NULL
HAVING COUNT(*) > 0

UNION ALL

SELECT 'orphan_refund_enrollment', COUNT(*)
FROM refund_record r
LEFT JOIN enrollment e ON e.id = r.enrollment_id
WHERE e.id IS NULL
HAVING COUNT(*) > 0

UNION ALL

SELECT 'orphan_lesson_flow_account', COUNT(*)
FROM lesson_flow f
LEFT JOIN lesson_account a ON a.id = f.account_id
WHERE a.id IS NULL
HAVING COUNT(*) > 0

UNION ALL

SELECT 'negative_lesson_balance', COUNT(*)
FROM lesson_account
WHERE remaining_lessons < 0 OR total_lessons < 0
HAVING COUNT(*) > 0

UNION ALL

SELECT 'remaining_lessons_exceeds_total', COUNT(*)
FROM lesson_account
WHERE remaining_lessons > total_lessons
HAVING COUNT(*) > 0

UNION ALL

SELECT 'duplicate_pending_refund', COUNT(*)
FROM (
  SELECT enrollment_id
  FROM refund_record
  WHERE status = 1 AND is_deleted = 0
  GROUP BY enrollment_id
  HAVING COUNT(*) > 1
) duplicate_refunds
HAVING COUNT(*) > 0;
