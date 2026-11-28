#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
补学员数据（每班 8 人 · 完整链路）

用法：
    python3 seed_students_2026_11.py --dry-run    # 审计 + 预估，不写库
    python3 seed_students_2026_11.py --apply      # 写入（阶段事务）
    python3 seed_students_2026_11.py --verify     # 只读校验

约束（来自方案 docs/plans/radiant-aurora-darwin-7qGo_QhC.md）：
- 目标人数取 min(8, class_group.max_student_count)，班 6 容量 6 → 只补到 6
- 课时按课程实际总课时发放（旧版固定 48 曾造成 remaining>total 存量脏数据，V15 已订正）
  保证 (总课时-最多消耗20)/总课时 > 50%，F3=0；否则 91 人全进风险
- 缴费分 7/8/9 月三批（40/20/19），避免单月营收畸高
- 考勤 80/5/10/5，扣减前做余额控制，杜绝负余额
- 流水按代码口径：source_type=2 考勤消费 / 1 充值 / 4 退费 / 5 人工调整
- 幂等：自然键 + biz_no（SEED26:RC:{sid} / SEED26:AT:{aid}）
"""
import subprocess
import sys
import random
import json
from datetime import date, datetime, timedelta
from collections import defaultdict

CONTAINER = "educational-administration-mysql-1"
AS_OF = date(2026, 11, 25)
TARGET = 8


def lesson_pack(course):
    """按课程实际总课时发放课时包。

    原先固定 LESSON_PACK=48，导致 16 课时课程也发 48（3 倍超发），
    出现「剩余课时 47 > 总课时 46」这类账实不符。
    """
    return int(course["total"])

BATCH = 200
SEED = 20261125

P_ATTEND, P_LATE, P_LEAVE = 80, 85, 95
DEDUCT = {1: 1.00, 2: 0.50, 3: 0.00, 4: 1.00}
REMARK = {1: "正常到课", 2: "迟到15分钟，扣半课时", 3: "家长请假", 4: "缺勤，未请假"}

SURNAMES = ["王", "李", "张", "刘", "陈", "杨", "黄", "赵", "周", "吴",
            "徐", "孙", "马", "朱", "胡", "郭", "何", "高", "林", "罗"]
GIVEN = ["雨萱", "子轩", "梓涵", "浩宇", "欣怡", "思远", "嘉怡", "俊杰", "诗涵", "天佑",
         "若曦", "明轩", "欣妍", "宇航", "梦琪", "皓轩", "念安", "清越", "书瑶", "沐辰"]
SCHOOLS = ["示例小学", "阳光小学", "蓝天小学", "实验二小", "百花小学"]

# 现有演示账号的 BCrypt hash（实测对应密码 123456，直接复用）
PWD_HASH = "$2b$10$ZWjPz0H2FscQDKbdG1CO0OtFX91epUXwabKSLffP3zUmnLhAPDJpu"


def mysql(sql, timeout=180):
    r = subprocess.run(
        ["docker", "exec", CONTAINER, "sh", "-c",
         f'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -N -B -e "{sql}"'],
        capture_output=True, text=True, timeout=timeout)
    if r.returncode != 0:
        raise RuntimeError(r.stderr.strip()[:500])
    return [ln.split("\t") for ln in r.stdout.strip().split("\n") if ln]


def run_script(statements, wrap_tx=True):
    body = "SET NAMES utf8mb4;\nSET time_zone='+08:00';\n"
    if wrap_tx:
        body += "START TRANSACTION;\n"
    body += "\n".join(statements) + "\n"
    if wrap_tx:
        body += "COMMIT;\n"
    r = subprocess.run(
        ["docker", "exec", "-i", CONTAINER, "sh", "-c",
         'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'],
        input=body, capture_output=True, text=True, timeout=600)
    if r.returncode != 0:
        raise RuntimeError(r.stderr.strip()[:800])
    return r.stdout


def q(v):
    """SQL 字面量转义"""
    if v is None:
        return "NULL"
    if isinstance(v, (int, float)):
        return str(v)
    return "'" + str(v).replace("\\", "\\\\").replace("'", "''") + "'"


def audit():
    rows = mysql(
        "SELECT g.id, g.class_name, g.teacher_id, g.course_id, g.max_student_count, "
        "  (SELECT COUNT(*) FROM class_student cs WHERE cs.class_id=g.id AND cs.status=1 AND cs.is_deleted=0), "
        "  (SELECT COUNT(*) FROM course c WHERE c.id=g.course_id AND c.is_deleted=0) "
        "FROM class_group g WHERE g.is_deleted=0 ORDER BY g.id")
    classes = []
    for r in rows:
        classes.append({
            "id": int(r[0]), "name": r[1], "teacher_id": int(r[2]),
            "course_id": int(r[3]), "capacity": int(r[4]), "cur": int(r[5]),
        })
    return classes


def next_id(table):
    v = mysql(f"SELECT COALESCE(MAX(id),0)+1 FROM {table}")
    return int(v[0][0])


def main():
    mode = "apply" if "--apply" in sys.argv else ("verify" if "--verify" in sys.argv else "dry")
    random.seed(SEED)
    print("=" * 64)
    print("模式: %s   基准日: %s" % (mode.upper(), AS_OF))
    print("=" * 64)

    classes = audit()
    need_total = 0
    print("\n[班级容量与缺口]")
    for c in classes:
        tgt = min(TARGET, c["capacity"])
        gap = max(0, tgt - c["cur"])
        c["target"] = tgt
        c["gap"] = gap
        need_total += gap
        flag = "" if c["capacity"] >= TARGET else "  ← 容量不足，取上限"
        print("  %2d %-16s 容量%2d 现有%2d → 目标%2d (+%d)%s"
              % (c["id"], c["name"], c["capacity"], c["cur"], tgt, gap, flag))
    print("  合计新增: %d 人" % need_total)

    if mode == "verify":
        return do_verify()

    # ---------- 生成计划 ----------
    # 取课程信息（课时总数、价格）
    course = {}
    for r in mysql("SELECT id, name, total_lessons, price FROM course WHERE is_deleted=0"):
        course[int(r[0])] = {"name": r[1], "total": int(r[2]), "price": float(r[3])}

    # 缴费批次：40 / 20 / 19
    months = ["2026-07"] * 40 + ["2026-08"] * 20 + ["2026-09"] * 19
    months = months[:need_total]
    random.shuffle(months)

    plan = []
    idx = 0
    for c in classes:
        for _ in range(c["gap"]):
            plan.append({"class": c, "pay_month": months[idx]})
            idx += 1

    # 账号与档案
    used_names = set()
    base_parent = next_id("user")
    base_student = next_id("student")
    base_pwd_phone = 1
    # 已占用家长手机号
    used_phone = {r[0] for r in mysql("SELECT contact_phone FROM student WHERE contact_phone IS NOT NULL")}

    for i, p in enumerate(plan):
        while True:
            nm = random.choice(SURNAMES) + random.choice(GIVEN)
            if nm not in used_names:
                used_names.add(nm)
                break
        p["name"] = nm
        p["gender"] = 1 if i % 2 == 0 else 2
        p["birthday"] = "20%02d-%02d-%02d" % (random.randint(14, 19), random.randint(1, 12), random.randint(1, 28))
        p["school"] = SCHOOLS[i % len(SCHOOLS)]
        # 手机号：1391000 + 4 位递增，跳过已占用
        while True:
            ph = "1391000%04d" % base_pwd_phone
            base_pwd_phone += 1
            if ph not in used_phone:
                used_phone.add(ph)
                break
        p["phone"] = ph
        p["username"] = "parent%d" % (base_parent + i)
        p["parent_id"] = base_parent + i
        p["student_id"] = base_student + i
        # 缴费日：批次月份内随机一天
        y, m = p["pay_month"].split("-")
        p["pay_date"] = "%s-%02d" % (p["pay_month"], random.randint(3, 22))
        # 报名日：缴费前 3-7 天
        p["enroll_date"] = (datetime.strptime(p["pay_date"], "%Y-%m-%d") - timedelta(days=random.randint(3, 7))).strftime("%Y-%m-%d")
        p["join_date"] = p["enroll_date"]

    # ---------- 考勤计划 ----------
    lessons = mysql(
        "SELECT id, class_id, lesson_date, start_time FROM schedule_lesson "
        "WHERE lesson_date BETWEEN '2026-08-01' AND '%s' AND status=2 AND is_deleted=0 "
        "ORDER BY class_id, lesson_date, start_time" % AS_OF)
    by_class = defaultdict(list)
    for r in lessons:
        by_class[int(r[1])].append({"id": int(r[0]), "date": r[2], "start": r[3]})

    att_plan = []
    flow_plan = []  # 扣减流水
    for p in plan:
        bal = lesson_pack(course[p["class"]["course_id"]])
        seq = []
        for l in by_class.get(p["class"]["id"], []):
            if l["date"] < p["join_date"]:
                continue
            r = random.randint(0, 99)
            st = 1 if r < P_ATTEND else (2 if r < P_LATE else (3 if r < P_LEAVE else 4))
            dd = DEDUCT[st]
            if dd > 0 and bal < dd:
                st, dd = 3, 0.0
            bal -= dd
            check_time = "%s %s" % (l["date"], l["start"])
            seq.append({"lesson_id": l["id"], "student_id": p["student_id"], "status": st,
                        "deduct": dd, "check_time": check_time, "date": l["date"]})
            if dd > 0:
                flow_plan.append({"student_id": p["student_id"], "lesson_id": l["id"],
                                  "check_time": check_time, "change": -dd,
                                  "seq_in_student": len([s for s in seq if s["deduct"] > 0])})
        att_plan.extend(seq)
        p["att_count"] = len(seq)
        p["final_balance"] = bal

    cnt = defaultdict(int)
    for a in att_plan:
        cnt[a["status"]] += 1
    print("\n[考勤计划]")
    print("  总计 %d 条：到课 %d / 迟到 %d / 请假 %d / 缺勤 %d"
          % (len(att_plan), cnt[1], cnt[2], cnt[3], cnt[4]))
    print("  扣减流水 %d 条" % len(flow_plan))
    print("  学员余额区间: %.1f ~ %.1f" % (min(p["final_balance"] for p in plan), max(p["final_balance"] for p in plan)))

    print("\n[预计各表新增]")
    est = {"user": len(plan), "student": len(plan), "parent_student": len(plan),
           "enrollment": len(plan), "payment_record": len(plan), "lesson_account": len(plan),
           "lesson_flow(充值)": len(plan), "class_student": len(plan),
           "attendance": len(att_plan), "lesson_flow(扣减)": len(flow_plan),
           "leave_request": cnt[3], "learning_record": len(plan) * 3,
           "homework": len(classes) * 4, "student_risk_followup": 10}
    for k, v in est.items():
        print("  %-22s %5d" % (k, v))
    print("  %-22s %5d" % ("合计", sum(est.values())))

    # 重跑防护：本脚本为纯 INSERT、无防重保护，重复执行必然重复插入。
    # 以充值流水 biz_no（SEED26:RC:{sid}，内部幂等键，前端不可见）为存在性锚点
    existing_seed = mysql("SELECT COUNT(*) FROM lesson_flow WHERE biz_no LIKE 'SEED26:RC:%' AND is_deleted=0")
    existing_count = int(existing_seed[0][0]) if existing_seed else 0
    if existing_count > 0:
        if mode == "apply":
            raise SystemExit(
                "[拒绝执行] 检测到 %d 条种子充值流水（biz_no LIKE 'SEED26:RC:%%'）。"
                "本脚本不幂等，重复执行会重复插入 79 名学员及全部关联数据。"
                "如确需重跑，请先清理：attendance/lesson_flow/lesson_account/refund_record/"
                "payment_record/class_student/enrollment/parent_student/student/user（按此顺序）" % existing_count)
        print("[警告] 检测到 %d 条已有种子数据，dry-run 结果可能与首次运行不一致" % existing_count)

    if mode != "apply":
        print("\n[DRY-RUN] 未写入。确认后加 --apply。")
        return

    # ---------- 写入 ----------
    print("\n[执行] 修复存量流水口径 ...")
    run_script([
        "UPDATE lesson_flow SET source_type=5 WHERE source_type=4 AND change_type=2 AND is_deleted=0;",
        "UPDATE lesson_flow SET source_type=4, change_type=2 WHERE source_type=2 AND change_type=3 AND is_deleted=0;",
        "UPDATE lesson_flow SET source_type=2 WHERE source_type=3 AND change_type=2 AND is_deleted=0;",
    ])

    print("[执行] 主体 8 张表 ...")
    stmts = []
    for p in plan:
        c = p["class"]
        stmts.append(
            "INSERT INTO user (id, username, password, real_name, phone, role_code, status, must_change_password, create_time) "
            "VALUES (%d, %s, %s, %s, %s, 'PARENT', 1, 0, %s);"
            % (p["parent_id"], q(p["username"]), q(PWD_HASH), q(p["name"][0] + "家长"), q(p["phone"]), q(p["enroll_date"])))
        stmts.append(
            "INSERT INTO student (id, name, gender, birthday, school, contact_phone, status, create_time, update_time) "
            "VALUES (%d, %s, %d, %s, %s, %s, 1, %s, %s);"
            % (p["student_id"], q(p["name"]), p["gender"], q(p["birthday"]), q(p["school"]),
               q(p["phone"]), q(p["enroll_date"]), q(p["enroll_date"])))
        stmts.append(
            "INSERT INTO parent_student (parent_user_id, student_id, relation, create_time) "
            "VALUES (%d, %d, %s, %s);"
            % (p["parent_id"], p["student_id"], q("父亲" if p["gender"] == 1 else "母亲"), q(p["enroll_date"])))
        stmts.append(
            "INSERT INTO enrollment (student_id, parent_user_id, course_id, class_id, status, auditor_id, audit_remark, create_time, update_time) "
            "VALUES (%d, %d, %d, %d, 3, 2, %s, %s, %s);"
            % (p["student_id"], p["parent_id"], c["course_id"], c["id"],
               q("资料齐全，审核通过"), q(p["enroll_date"]), q(p["enroll_date"])))
        stmts.append(
            "INSERT INTO class_student (class_id, student_id, join_time, status) "
            "VALUES (%d, %d, %s, 1);" % (c["id"], p["student_id"], q(p["join_date"])))

    run_script(stmts)

    # 回查 enrollment / payment 的对应关系（按 student_id）
    print("[执行] 缴费 + 课时账户 + 充值流水 ...")
    stmts = []
    for p in plan:
        co = course[p["class"]["course_id"]]
        pack = lesson_pack(co)
        amount = round(co["price"] / co["total"] * pack, 2)
        stmts.append(
            "INSERT INTO payment_record (enrollment_id, student_id, course_id, lesson_count, amount, pay_type, "
            "pay_time, operator_id, operator_role, remark, create_time, update_time) "
            "SELECT e.id, %d, %d, %d, %.2f, 2, %s, 3, 'FINANCE', %s, %s, %s "
            "FROM enrollment e WHERE e.student_id=%d AND e.is_deleted=0 LIMIT 1;"
            % (p["student_id"], p["class"]["course_id"], pack, amount,
               q(p["pay_date"] + " 10:30:00"), q("入学缴费"), q(p["pay_date"]), q(p["pay_date"]), p["student_id"]))
        stmts.append(
            "INSERT INTO lesson_account (student_id, course_id, total_lessons, remaining_lessons, expire_date, version) "
            "VALUES (%d, %d, %d, %d, '2027-07-31', 0);"
            % (p["student_id"], p["class"]["course_id"], pack, pack))
        stmts.append(
            "INSERT INTO lesson_flow (account_id, student_id, lesson_id, source_type, source_id, change_amount, "
            "change_type, before_balance, after_balance, remark, biz_no, create_time) "
            "SELECT a.id, %d, NULL, 1, pr.id, %d, 1, 0, %d, %s, %s, %s "
            "FROM lesson_account a JOIN payment_record pr ON pr.student_id=a.student_id AND pr.is_deleted=0 "
            "WHERE a.student_id=%d AND a.is_deleted=0 LIMIT 1;"
            % (p["student_id"], pack, pack, q("缴费开通-%d课时" % pack),
               q("SEED26:RC:%d" % p["student_id"]), q(p["pay_date"]), p["student_id"]))
    for i in range(0, len(stmts), BATCH):
        run_script(stmts[i:i + BATCH])

    print("[执行] 考勤（%d 条）..." % len(att_plan))
    stmts = []
    for a in att_plan:
        stmts.append(
            "INSERT INTO attendance (lesson_id, student_id, status, deduct_lessons, check_time, remark, create_time, update_time) "
            "VALUES (%d, %d, %d, %.2f, %s, %s, %s, %s);"
            % (a["lesson_id"], a["student_id"], a["status"], a["deduct"],
               q(a["check_time"]), q(REMARK[a["status"]]), q(a["check_time"]), q(a["check_time"])))
    for i in range(0, len(stmts), BATCH):
        run_script(stmts[i:i + BATCH])

    print("[执行] 扣减流水（%d 条）..." % len(flow_plan))
    stmts = []
    for p in plan:
        bal = lesson_pack(course[p["class"]["course_id"]])
        for f in [x for x in flow_plan if x["student_id"] == p["student_id"]]:
            before = bal
            bal += f["change"]
            stmts.append(
                "INSERT INTO lesson_flow (account_id, student_id, lesson_id, source_type, source_id, change_amount, "
                "change_type, before_balance, after_balance, remark, biz_no, create_time) "
                "SELECT a.id, %d, %d, 2, att.id, %.2f, 2, %.2f, %.2f, %s, %s, %s "
                "FROM attendance att JOIN lesson_account a ON a.student_id=att.student_id AND a.is_deleted=0 "
                "WHERE att.lesson_id=%d AND att.student_id=%d AND att.is_deleted=0 LIMIT 1;"
                % (p["student_id"], f["lesson_id"], f["change"], before, bal,
                   q("考勤扣减"), q("SEED26:AT:%d-%d" % (p["student_id"], f["lesson_id"])),
                   q(f["check_time"]), f["lesson_id"], p["student_id"]))
    for i in range(0, len(stmts), BATCH):
        run_script(stmts[i:i + BATCH])

    print("[执行] 同步账户余额 ...")
    # 只同步种子学员账户：
    # ① 全表更新会碰原有账户，它们的流水汇总恰好为负（-16.5/-14/-13.5），
    #    直接违反 lesson_account 的 CHECK(remaining_lessons >= 0) —— 已踩
    # ② 种子账户自身的汇总也加 f.s>=0 兜底，避免个别学员扣成负数
    run_script([
        "UPDATE lesson_account a JOIN (SELECT account_id, SUM(change_amount) s FROM lesson_flow "
        "WHERE is_deleted=0 GROUP BY account_id) f ON f.account_id=a.id "
        "SET a.remaining_lessons=f.s, a.version=a.version+1 "
        # 圈定改靠充值流水 biz_no（SEED26:RC:{sid}，稳定唯一锚点）——
        # payment_record.remark 已改中文「入学缴费」，若再按 remark 匹配会误圈运行时同名义缴费
        "WHERE a.student_id IN (SELECT student_id FROM lesson_flow "
        "WHERE biz_no LIKE 'SEED26:RC:%' AND is_deleted=0) AND f.s>=0;"
    ])

    print("[执行] 请假 / 学情 / 作业 / 风险跟进 ...")
    stmts = []
    # 请假：每个 status=3 的考勤配一条已通过的请假单
    for a in att_plan:
        if a["status"] != 3:
            continue
        p = next(x for x in plan if x["student_id"] == a["student_id"])
        stmts.append(
            "INSERT INTO leave_request (student_id, parent_user_id, lesson_date, schedule_id, reason, status, "
            "audit_user_id, audit_remark, create_time, update_time) "
            "SELECT %d, %d, %s, %d, %s, 2, 2, %s, %s, %s FROM DUAL "
            "WHERE NOT EXISTS (SELECT 1 FROM leave_request lr WHERE lr.student_id=%d AND lr.schedule_id=%d);"
            % (a["student_id"], p["parent_id"], q(a["date"]), a["lesson_id"], q("家长请假，身体不适"),
               q("同意请假"), q(a["check_time"]), q(a["check_time"]), a["student_id"], a["lesson_id"]))
    run_script(stmts) if stmts else None

    # 学情：每人 3 条（首/中/末次到课）
    stmts = []
    COMMENTS = ["课堂专注度高，进步明显", "基础扎实，需加强细节处理", "表现积极，作品完成度好"]
    TAGS = ["进步明显", "稳定发挥", "需加强练习"]
    for p in plan:
        docs = [a for a in att_plan if a["student_id"] == p["student_id"] and a["status"] in (1, 2)]
        if not docs:
            continue
        picks = [docs[0], docs[len(docs) // 2], docs[-1]]
        for k, a in enumerate(picks):
            stmts.append(
                "INSERT INTO learning_record (lesson_id, student_id, teacher_comment, growth_tag, create_time, update_time) "
                "SELECT %d, %d, %s, %s, %s, %s FROM DUAL "
                "WHERE NOT EXISTS (SELECT 1 FROM learning_record lr WHERE lr.lesson_id=%d AND lr.student_id=%d AND lr.is_deleted=0);"
                % (a["lesson_id"], p["student_id"], q(COMMENTS[k]), q(TAGS[k]),
                   q(a["check_time"]), q(a["check_time"]), a["lesson_id"], p["student_id"]))
    for i in range(0, len(stmts), BATCH):
        run_script(stmts[i:i + BATCH])

    # 作业：每班最后 4 个课次
    stmts = []
    for c in classes:
        ls = by_class.get(c["id"], [])[-4:]
        for a in ls:
            stmts.append(
                "INSERT INTO homework (lesson_id, teacher_id, content, create_time, update_time) "
                "SELECT %d, %d, %s, %s, %s FROM DUAL "
                "WHERE NOT EXISTS (SELECT 1 FROM homework h WHERE h.lesson_id=%d AND h.is_deleted=0);"
                % (a["id"], c["teacher_id"], q("完成课堂练习并提交作品照片"), q(a["date"] + " 18:00:00"),
                   q(a["date"] + " 18:00:00"), a["id"]))
    run_script(stmts) if stmts else None

    # 风险跟进：给 5 个 10 月底结课班的前 10 名新学员（这些学员 F1=35，必然入榜）
    print("[执行] 风险跟进 ...")
    late_classes = {4, 6, 8, 10, 12}
    candidates = [p for p in plan if p["class"]["id"] in late_classes][:10]
    follow_status = [1, 1, 1, 1, 0, 0, 0, 0, 2, 2]
    follow_remark = ["已电话沟通，家长计划本周续费", "已微信联系，家长表示会安排补课",
                     "已沟通，家长确认继续跟读", "已预约面谈",
                     "待联系", "待联系", "待联系", "待联系",
                     "家长暂不考虑续费", "家长要求暂停"]
    stmts = []
    for k, p in enumerate(candidates):
        stt = follow_status[k]
        ft = q(AS_OF.strftime("%Y-%m-%d") + " 10:00:00") if stt == 1 else "NULL"
        stmts.append(
            "INSERT INTO student_risk_followup (student_id, status, remark, operator_id, followup_time, create_time, update_time) "
            "VALUES (%d, %d, %s, 2, %s, NOW(), NOW()) "
            "ON DUPLICATE KEY UPDATE status=VALUES(status), remark=VALUES(remark), "
            "followup_time=VALUES(followup_time), update_time=NOW();"
            % (p["student_id"], stt, q(follow_remark[k]), ft))
    run_script(stmts) if stmts else None

    print("\n✓ 写入完成")
    do_verify()


def do_verify():
    print("\n[校验]")
    checks = [
        ("班级人数符合目标", "SELECT COUNT(*) FROM (SELECT g.id, COUNT(cs.id) cnt, LEAST(%d,g.max_student_count) tgt "
                          "FROM class_group g LEFT JOIN class_student cs ON cs.class_id=g.id AND cs.status=1 AND cs.is_deleted=0 "
                          "WHERE g.is_deleted=0 GROUP BY g.id, g.max_student_count HAVING cnt<>tgt) x" % TARGET),
        ("学员数", "SELECT COUNT(*) FROM student WHERE is_deleted=0"),
        ("班级人次", "SELECT COUNT(*) FROM class_student WHERE status=1 AND is_deleted=0"),
        ("PARENT 账号", "SELECT COUNT(*) FROM user WHERE role_code='PARENT' AND is_deleted=0"),
        ("余额为负", "SELECT COUNT(*) FROM lesson_account WHERE remaining_lessons<0 AND is_deleted=0"),
        ("剩余>总课时", "SELECT COUNT(*) FROM lesson_account WHERE remaining_lessons>total_lessons AND is_deleted=0"),
        ("余额≠流水汇总", "SELECT COUNT(*) FROM lesson_account a LEFT JOIN (SELECT account_id,SUM(change_amount) s "
                    "FROM lesson_flow WHERE is_deleted=0 GROUP BY account_id) f ON f.account_id=a.id "
                    "WHERE a.is_deleted=0 AND a.remaining_lessons<>COALESCE(f.s,0)"),
        ("流水口径异常", "SELECT COUNT(*) FROM lesson_flow WHERE is_deleted=0 AND source_type NOT IN (1,2,4,5)"),
        ("请假缺失（status=3 无单据）", "SELECT COUNT(*) FROM attendance a LEFT JOIN leave_request lr "
                                "ON lr.student_id=a.student_id AND lr.schedule_id=a.lesson_id AND lr.status=2 "
                                "WHERE a.status=3 AND a.is_deleted=0 AND lr.id IS NULL"),
        ("考勤重复", "SELECT COUNT(*) FROM (SELECT lesson_id,student_id FROM attendance WHERE is_deleted=0 "
                 "GROUP BY 1,2 HAVING COUNT(*)>1) x"),
    ]
    for name, sql in checks:
        v = mysql(sql)[0][0]
        print("  %-28s %s" % (name, v))


if __name__ == "__main__":
    main()
