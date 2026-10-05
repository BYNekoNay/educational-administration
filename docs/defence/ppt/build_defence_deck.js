// 艺培通毕业答辩 PPT 生成脚本（22 页；页序对齐 docs/defence/答辩讲稿.md 15 分钟版）
const pptxgen = require("pptxgenjs");
const pres = new pptxgen();
pres.layout = "LAYOUT_16x9";
const SLIDE_W = 10, SLIDE_H = 5.625;
const MARGIN = 0.5;
const CONTENT_X = MARGIN, CONTENT_Y = MARGIN;
const CONTENT_W = SLIDE_W - 2 * MARGIN, CONTENT_H = SLIDE_H - 2 * MARGIN;

// ============ 主题色 ============
const C = {
  navy: "1E2761", navy2: "2E3D7E", navy3: "3D5A8C",
  ice: "CADCFC", gold: "C9A227", goldLight: "F5EBD3",
  ink: "1F2937", gray: "6B7280", bg: "F7F9FC", white: "FFFFFF",
  border: "E5E9F2", light: "EEF1FB", red: "B03A2E", green: "2E7D32"
};
const F = { title: "SimSun", body: "Microsoft YaHei" };
const DIR = __dirname + "/assets/";
const OUT = process.argv[2] || "艺培通毕业答辩.pptx";

// ============ 容器系统（防溢出） ============
function parseImageDimensions(path) {
  const m = path.match(/_(\d+)x(\d+)\.(png|jpg|jpeg|gif|webp)$/i);
  return m ? { width: +m[1], height: +m[2] } : null;
}
function calculateScaledImageOpts(opts) {
  const { path, w: tw, h: th, x = 0, y = 0, ...rest } = opts;
  if (!path || !tw || !th) return opts;
  const d = parseImageDimensions(path);
  if (!d) return opts;
  const ia = d.width / d.height, ta = tw / th;
  let sw, sh, ox = 0, oy = 0;
  if (ia > ta) { sw = tw; sh = tw / ia; oy = (th - sh) / 2; }
  else { sh = th; sw = th * ia; ox = (tw - sw) / 2; }
  return { path, x: x + ox, y: y + oy, w: sw, h: sh, ...rest };
}
function createVirtualNode(type, data, px = 0, py = 0) {
  const opts = data.opts || {};
  const node = { type, data, absX: px + (opts.x || 0), absY: py + (opts.y || 0), w: opts.w || 0, h: opts.h || 0, children: [] };
  node.addShape = function (st, o = {}) { const c = createVirtualNode("shape", { shapeType: st, opts: o }, node.absX, node.absY); node.children.push(c); return c; };
  node.addText = function (t, o = {}) { const c = createVirtualNode("text", { text: t, opts: { fit: "shrink", ...o } }, node.absX, node.absY); node.children.push(c); return c; };
  node.addImage = function (o = {}) { const c = createVirtualNode("image", { opts: calculateScaledImageOpts(o) }, node.absX, node.absY); node.children.push(c); return c; };
  return node;
}
function flattenNode(node, slide, pres) {
  const abs = { ...node.data.opts, x: node.absX, y: node.absY };
  if (node.type === "shape") slide.addShape(node.data.shapeType, abs);
  else if (node.type === "text") slide.addText(node.data.text, abs);
  else if (node.type === "image") slide.addImage(abs);
  node.children.forEach(c => flattenNode(c, slide, pres));
}
const origAddSlide = pres.addSlide.bind(pres);
pres.addSlide = function (o) {
  const real = origAddSlide(o);
  const vs = { children: [], _real: real,
    set background(v) { real.background = v; }, get background() { return real.background; },
    addShape(st, o = {}) { const n = createVirtualNode("shape", { shapeType: st, opts: o }, 0, 0); this.children.push(n); return n; },
    addText(t, o = {}) { const n = createVirtualNode("text", { text: t, opts: { fit: "shrink", ...o } }, 0, 0); this.children.push(n); return n; },
    addImage(o = {}) { const n = createVirtualNode("image", { opts: calculateScaledImageOpts(o) }, 0, 0); this.children.push(n); return n; },
    render() { this.children.forEach(c => flattenNode(c, real, pres)); } };
  return vs;
};

// ============ 通用 helpers ============
function header(slide, kicker, title) {
  slide.addShape("rect", { x: CONTENT_X, y: 0.42, w: 0.07, h: 0.5, fill: { color: C.gold } });
  slide.addText(kicker, { x: CONTENT_X + 0.18, y: 0.44, w: 6.5, h: 0.28, fontFace: F.body, fontSize: 10.5, color: C.gold, bold: true, charSpacing: 1 });
  slide.addText(title, { x: CONTENT_X, y: 0.72, w: 8.2, h: 0.4, fontFace: F.title, fontSize: 25, bold: true, color: C.navy, charSpacing: 1.5 });
}
function footer(slide, n) {
  slide.addShape("rect", { x: CONTENT_X, y: 5.28, w: CONTENT_W, h: 0.01, fill: { color: C.border } });
  slide.addText("艺术培训机构全流程教务管理平台的设计与实现 · 毕业答辩", { x: CONTENT_X, y: 5.32, w: 6.5, h: 0.22, fontFace: F.body, fontSize: 8.5, color: C.gray });
  slide.addText(String(n).padStart(2, "0"), { x: 9.3, y: 5.32, w: 0.4, h: 0.22, fontFace: F.body, fontSize: 9, color: C.gray, align: "right" });
}
function chip(slide, x, y, w, h, text, o = {}) {
  slide.addShape("roundRect", { x, y, w, h, rectRadius: 0.09, fill: { color: o.fill || C.light }, line: { color: o.line || C.navy3, width: 0.75 } });
  slide.addText(text, { x, y, w, h, fontFace: F.body, fontSize: o.size || 11.5, bold: o.bold !== false, color: o.color || C.navy, align: "center", valign: "middle", charSpacing: 0.5 });
}
function card(slide, x, y, w, h, o = {}) {
  const sh = slide.addShape("roundRect", { x, y, w, h, rectRadius: 0.06, fill: { color: o.fill || C.white }, line: { color: o.line || C.border, width: 1 } });
  return sh;
}

/* P1 封面 */
(() => {
  const s = pres.addSlide();
  s.background = { color: C.navy };
  s.addShape("rect", { x: 0, y: 0, w: SLIDE_W, h: SLIDE_H, fill: { color: C.navy2 } });
  s.addShape("rect", { x: 0, y: 0, w: SLIDE_W * 0.52, h: SLIDE_H, fill: { color: C.navy } });
  s.addShape("rect", { x: 0.5, y: 0, w: 0.012, h: SLIDE_H, fill: { color: C.gold } });
  s.addText("攀枝花学院 · 本科毕业设计（论文）", { x: 0.8, y: 0.85, w: 8.6, h: 0.4, fontFace: F.body, fontSize: 13, color: C.gold, align: "center", charSpacing: 2 });
  s.addText("艺术培训机构全流程教务管理平台的\n设计与实现", { x: 0.8, y: 1.5, w: 8.6, h: 1.6, fontFace: F.title, fontSize: 34, bold: true, color: C.white, align: "center", lineSpacingMultiple: 1.25 });
  s.addShape("rect", { x: 4.0, y: 3.25, w: 2.0, h: 0.02, fill: { color: C.gold } });
  s.addText([
    { text: "答辩人：罗斌洋", options: { fontSize: 15, color: C.ice } },
    { text: "专业：软件工程", options: { fontSize: 15, color: C.ice, breakLine: true } }
  ], { x: 2.6, y: 3.6, w: 5, h: 0.9, fontFace: F.body, align: "center", valign: "middle", lineSpacingMultiple: 1.35 });
  s.addText("毕业答辩", { x: 8.3, y: 4.9, w: 1.4, h: 0.4, fontFace: F.body, fontSize: 12, bold: true, color: C.gold, align: "center" });
  s.render();
})();

/* P2 一个真实的月底 */
(() => {
  const s = pres.addSlide();
  s.background = { color: C.bg };
  header(s, "01｜问题起点", "一个真实的月底");
  card(s, 0.5, 1.35, 4.3, 3.6);
  s.addText([
    { text: "月底，机构负责人在做什么？", options: { fontSize: 15, bold: true, color: C.navy } },
    { text: "· 翻点名表核对出勤", options: { fontSize: 12.5, color: C.ink, breakLine: true } },
    { text: "· 手工算每位学员剩余课时", options: { fontSize: 12.5, color: C.ink, breakLine: true } },
    { text: "· 按课时给兼职教师算工资", options: { fontSize: 12.5, color: C.ink, breakLine: true } },
    { text: "· 家长来问账单要翻聊天记录", options: { fontSize: 12.5, color: C.ink, breakLine: true } }
  ], { x: 0.75, y: 1.6, w: 3.8, h: 3.1, fontFace: F.body, valign: "top", lineSpacingMultiple: 1.5 });
  card(s, 5.15, 1.35, 4.3, 3.6, { fill: C.light, line: C.navy3 });
  s.addText([
    { text: "这些动作的共同点：", options: { fontSize: 15, bold: true, color: C.navy } },
    { text: "依赖人工、口径分散、出错后难以追溯。", options: { fontSize: 12.5, color: C.ink, breakLine: true } },
    { text: "", options: { fontSize: 8, breakLine: true } },
    { text: "本项目要解决的，是把这条链路整体搬进系统，并让它账目对得上、并发扛得住。", options: { fontSize: 12.5, color: C.ink } }
  ], { x: 5.4, y: 1.6, w: 3.8, h: 3.1, fontFace: F.body, valign: "top", lineSpacingMultiple: 1.45 });
  footer(s, 2);
  s.render();
})();

/* P3 五大痛点 */
(() => {
  const s = pres.addSlide();
  s.background = { color: C.bg };
  header(s, "01｜问题起点", "行业背景与五大痛点");
  const items = [
    ["数据分散", "统计滞后，难掌握招生/到课/营收/流失"],
    ["课时手工扣减", "多学员多课程组合下极易误扣漏扣"],
    ["排课靠经验", "教师/教室/班级冲突要等使用中才发现"],
    ["薪资人工汇总", "慢、易错、缺明细"],
    ["流失隐蔽", "等续费下滑才察觉，已错过干预窗口"]
  ];
  items.forEach((it, i) => {
    const y = 1.4 + i * 0.76;
    card(s, 0.5, y, 9.0, 0.66);
    chip(s, 0.7, y + 0.13, 2.3, 0.4, it[0]);
    s.addText(it[1], { x: 3.2, y: y + 0.08, w: 6.1, h: 0.5, fontFace: F.body, fontSize: 12.5, color: C.ink, valign: "middle" });
  });
  footer(s, 3);
  s.render();
})();

/* P4 国内外研究与缺口 */
(() => {
  const s = pres.addSlide();
  s.background = { color: C.bg };
  header(s, "02｜研究现状", "国内外研究与缺口");
  card(s, 0.5, 1.35, 4.35, 3.4);
  s.addText("国外", { x: 0.75, y: 1.5, w: 3.8, h: 0.4, fontFace: F.body, fontSize: 15, bold: true, color: C.navy });
  s.addText("排课优化与流失预测研究较深，算法成熟、数据驱动，但多为通用模型，缺少面向中小型培训机构的完整工程实现。", { x: 0.75, y: 2.0, w: 3.8, h: 2.5, fontFace: F.body, fontSize: 12.5, color: C.ink, lineSpacingMultiple: 1.45 });
  card(s, 5.15, 1.35, 4.35, 3.4);
  s.addText("国内", { x: 5.4, y: 1.5, w: 3.8, h: 0.4, fontFace: F.body, fontSize: 15, bold: true, color: C.navy });
  s.addText("点状成果多（排课、考勤、收费各有系统），但普遍只覆盖单一环节，缺少把招生—排课—考勤—课时—财务—学情串起来的完整链路。", { x: 5.4, y: 2.0, w: 3.8, h: 2.5, fontFace: F.body, fontSize: 12.5, color: C.ink, lineSpacingMultiple: 1.45 });
  s.addText("缺口：不是单点的算法不够好，而是拼不出一个完整、可上线、账目自洽的系统。", { x: 0.5, y: 4.9, w: 9.0, h: 0.35, fontFace: F.body, fontSize: 13, bold: true, color: C.red });
  footer(s, 4);
  s.render();
})();

/* P5 研究内容与范围 */
(() => {
  const s = pres.addSlide();
  s.background = { color: C.bg };
  header(s, "03｜研究内容", "研究内容与范围");
  const cells = [
    ["招生报名", "在线报名 · 审核 · 分班"],
    ["排课调课", "智能排课 · 冲突检测 · 调课审批"],
    ["考勤课时", "四态考勤 · 课时自动扣减"],
    ["财务薪资", "收费 · 退费 · 薪资核算"],
    ["学情考级", "作业点评 · 成长档案 · 考级"],
    ["运营统计", "多维统计 · 流失预警 · 导出"]
  ];
  cells.forEach((c, i) => {
    const x = 0.5 + (i % 3) * 3.05, y = 1.5 + Math.floor(i / 3) * 1.72;
    card(s, x, y, 2.85, 1.5);
    s.addText(c[0], { x: x + 0.2, y: y + 0.2, w: 2.45, h: 0.42, fontFace: F.body, fontSize: 14.5, bold: true, color: C.navy, align: "center" });
    s.addText(c[1], { x: x + 0.2, y: y + 0.72, w: 2.45, h: 0.6, fontFace: F.body, fontSize: 11, color: C.gray, align: "center", valign: "middle" });
  });
  s.addText("六类角色协同，共用一套数据模型，形成业务闭环。", { x: 0.5, y: 5.0, w: 9.0, h: 0.3, fontFace: F.body, fontSize: 12.5, bold: true, color: C.navy, align: "center" });
  footer(s, 5);
  s.render();
})();

/* P6 研究目标 */
(() => {
  const s = pres.addSlide();
  s.background = { color: C.bg };
  header(s, "03｜研究内容", "研究目标");
  const goals = [
    ["目标一", "五类角色业务闭环", "家长/教师/教务/财务/超管各司其职，数据互通"],
    ["目标二", "攻克四个技术难点", "课时一致性、排课冲突、调课并发、薪资正确性"],
    ["目标三", "可交付可运维", "容器化部署上线，迁移/备份/发布门禁/可观测齐备"]
  ];
  goals.forEach((g, i) => {
    const y = 1.5 + i * 1.25;
    card(s, 0.5, y, 9.0, 1.1);
    chip(s, 0.72, y + 0.32, 1.5, 0.44, g[0]);
    s.addText(g[1], { x: 2.45, y: y + 0.16, w: 6.8, h: 0.42, fontFace: F.body, fontSize: 15, bold: true, color: C.navy, valign: "middle" });
    s.addText(g[2], { x: 2.45, y: y + 0.6, w: 6.8, h: 0.38, fontFace: F.body, fontSize: 11.5, color: C.gray, valign: "middle" });
  });
  footer(s, 6);
  s.render();
})();

/* P7 总体架构 */
(() => {
  const s = pres.addSlide();
  s.background = { color: C.bg };
  header(s, "04｜架构与选型", "总体架构");
  s.addImage({ path: DIR + "arch_1204x1110.png", x: 0.6, y: 1.4, w: 4.2, h: 3.6 });
  card(s, 5.1, 1.4, 4.4, 3.6);
  s.addText([
    { text: "前后端分离 B/S 架构", options: { fontSize: 14, bold: true, color: C.navy } },
    { text: "· 管理后台：Vue 3 单页应用", options: { fontSize: 12, color: C.ink, breakLine: true } },
    { text: "· 移动端：uni-app（H5）", options: { fontSize: 12, color: C.ink, breakLine: true } },
    { text: "· 后端：统一 RESTful 接口 + RBAC 鉴权", options: { fontSize: 12, color: C.ink, breakLine: true } },
    { text: "· 数据：MySQL 8.0，结构变更由 Flyway 版本化管理", options: { fontSize: 12, color: C.ink, breakLine: true } },
    { text: "· 部署：Docker Compose 编排，Nginx/Caddy 反代", options: { fontSize: 12, color: C.ink, breakLine: true } }
  ], { x: 5.35, y: 1.62, w: 3.9, h: 3.2, fontFace: F.body, valign: "top", lineSpacingMultiple: 1.42 });
  footer(s, 7);
  s.render();
})();

/* P8 技术选型 */
(() => {
  const s = pres.addSlide();
  s.background = { color: C.bg };
  header(s, "04｜架构与选型", "技术选型");
  const rows = [
    ["层次", "选型", "理由"],
    ["管理后台", "Vue 3 + Vite 5 + Element Plus", "组件成熟、生态完善，适配数据密集页面"],
    ["移动端", "uni-app（Vue 3）", "一套代码编译多端，本次交付 H5"],
    ["后端", "Spring Boot 3.2.5 + MyBatis-Plus 3.5.7", "分层清晰，复杂统计 SQL 易表达"],
    ["数据库", "MySQL 8.0 + Flyway", "强一致场景可靠，结构变更可版本化"],
    ["认证", "JWT(HS256) + RBAC", "无状态鉴权，五类角色接口级隔离"]
  ];
  rows.forEach((r, i) => {
    const y = 1.35 + i * 0.66;
    const fill = i === 0 ? C.navy : (i % 2 === 0 ? C.light : C.white);
    s.addShape("rect", { x: 0.5, y, w: 9.0, h: 0.62, fill: { color: fill }, line: { color: C.border, width: 0.5 } });
    const col = i === 0 ? C.white : C.ink;
    s.addText(r[0], { x: 0.7, y, w: 1.7, h: 0.62, fontFace: F.body, fontSize: i === 0 ? 12.5 : 12, bold: i === 0, color: col, valign: "middle" });
    s.addText(r[1], { x: 2.5, y, w: 3.6, h: 0.62, fontFace: F.body, fontSize: i === 0 ? 12.5 : 12, bold: i === 0, color: col, valign: "middle" });
    s.addText(r[2], { x: 6.2, y, w: 3.1, h: 0.62, fontFace: F.body, fontSize: i === 0 ? 12.5 : 11.5, bold: i === 0, color: col, valign: "middle" });
  });
  footer(s, 8);
  s.render();
})();

/* P9 数据模型 */
(() => {
  const s = pres.addSlide();
  s.background = { color: C.bg };
  header(s, "04｜架构与选型", "数据模型");
  s.addImage({ path: DIR + "er_1568x954.png", x: 0.6, y: 1.55, w: 5.2, h: 3.2 });
  card(s, 6.05, 1.45, 3.45, 3.5);
  s.addText([
    { text: "37 张业务表", options: { fontSize: 15, bold: true, color: C.navy } },
    { text: "· 8 个业务域", options: { fontSize: 12, color: C.ink, breakLine: true } },
    { text: "· 后端 16 个模块", options: { fontSize: 12, color: C.ink, breakLine: true } },
    { text: "· 0 外键：一致性由关联表与唯一约束承担", options: { fontSize: 12, color: C.ink, breakLine: true } },
    { text: "· 逻辑删除 + 唯一键冲突有统一处理策略", options: { fontSize: 12, color: C.ink, breakLine: true } }
  ], { x: 6.3, y: 1.68, w: 3.0, h: 3.05, fontFace: F.body, valign: "top", lineSpacingMultiple: 1.45 });
  footer(s, 9);
  s.render();
})();

/* P10 难点一：课时 */
(() => {
  const s = pres.addSlide();
  s.background = { color: C.bg };
  header(s, "05｜核心难点（得分重点）", "难点一：课时不重扣、不漏扣");
  const steps = [
    ["1", "同一事务", "考勤 + 账户 + 流水一次提交"],
    ["2", "乐观锁", "版本号比对，更新 0 行即判冲突"],
    ["3", "幂等与回冲", "重复提交不重复扣，退费按实际扣减量归还"],
    ["4", "请假保护", "已有请假记录不允许被覆盖（409）"]
  ];
  steps.forEach((st, i) => {
    const x = 0.5 + i * 2.32;
    card(s, x, 1.5, 2.12, 2.0);
    chip(s, x + 0.72, 1.72, 0.68, 0.44, st[0]);
    s.addText(st[1], { x: x + 0.16, y: 2.32, w: 1.8, h: 0.42, fontFace: F.body, fontSize: 13.5, bold: true, color: C.navy, align: "center" });
    s.addText(st[2], { x: x + 0.16, y: 2.78, w: 1.8, h: 0.6, fontFace: F.body, fontSize: 10.5, color: C.gray, align: "center", valign: "middle" });
  });
  s.addText("结果：账目对得上——界面与数据库逐条对账一致（收费 24 课时：22→46；退费 2 课时：46→44）。", { x: 0.5, y: 3.75, w: 9.0, h: 0.5, fontFace: F.body, fontSize: 13, bold: true, color: C.green, align: "center" });
  s.addShape("rect", { x: 0.5, y: 4.45, w: 9.0, h: 0.62, fill: { color: C.light }, line: { color: C.navy3, width: 0.75 } });
  s.addText("并发一致性测试：多条路径的乐观并发控制均有效，冲突请求被拒绝而非静默覆盖。", { x: 0.7, y: 4.45, w: 8.6, h: 0.62, fontFace: F.body, fontSize: 12.5, color: C.navy, valign: "middle" });
  footer(s, 10);
  s.render();
})();

/* P11 难点二：排课冲突 */
(() => {
  const s = pres.addSlide();
  s.background = { color: C.bg };
  header(s, "05｜核心难点（得分重点）", "难点二：排课冲突检测要检得全");
  s.addImage({ path: DIR + "schedule_1920x1080.png", x: 0.6, y: 1.45, w: 4.3, h: 2.5 });
  card(s, 5.1, 1.45, 4.4, 2.5);
  s.addText([
    { text: "五维冲突检测", options: { fontSize: 14, bold: true, color: C.navy } },
    { text: "· 教师时间冲突", options: { fontSize: 12, color: C.ink, breakLine: true } },
    { text: "· 教室占用冲突", options: { fontSize: 12, color: C.ink, breakLine: true } },
    { text: "· 教室预约（非常规占用）", options: { fontSize: 12, color: C.ink, breakLine: true } },
    { text: "· 班级已排课", options: { fontSize: 12, color: C.ink, breakLine: true } },
    { text: "· 学员跨班时间冲突", options: { fontSize: 12, color: C.ink, breakLine: true } }
  ], { x: 5.35, y: 1.65, w: 3.9, h: 2.1, fontFace: F.body, valign: "top", lineSpacingMultiple: 1.35 });
  s.addShape("rect", { x: 0.5, y: 4.15, w: 9.0, h: 0.9, fill: { color: C.light }, line: { color: C.navy3, width: 0.75 } });
  s.addText("智能排课按容量升序取首个无冲突教室；已取消、已调课的旧课次不参与冲突判定，避免“幽灵占用”。", { x: 0.7, y: 4.15, w: 8.6, h: 0.9, fontFace: F.body, fontSize: 12.5, color: C.navy, valign: "middle" });
  footer(s, 11);
  s.render();
})();

/* P12 难点三：调课并发 */
(() => {
  const s = pres.addSlide();
  s.background = { color: C.bg };
  header(s, "05｜核心难点（得分重点）", "难点三：拖拽调课的并发覆盖");
  card(s, 0.5, 1.4, 4.35, 2.4);
  s.addText([
    { text: "问题", options: { fontSize: 14, bold: true, color: C.red } },
    { text: "拖拽调课前后状态同为“待上课”，仅以状态做条件时恒真——两笔请求都能写入，先提交者的修改被静默覆盖。", options: { fontSize: 12, color: C.ink } }
  ], { x: 0.75, y: 1.6, w: 3.85, h: 2.0, fontFace: F.body, valign: "top", lineSpacingMultiple: 1.35 });
  card(s, 5.15, 1.4, 4.35, 2.4, { fill: C.light, line: C.navy3 });
  s.addText([
    { text: "解法", options: { fontSize: 14, bold: true, color: C.green } },
    { text: "在乐观条件里追加“原日期 + 原起止时间”。第二笔更新影响行数为 0 → 返回冲突提示。", options: { fontSize: 12, color: C.ink } }
  ], { x: 5.4, y: 1.6, w: 3.85, h: 2.0, fontFace: F.body, valign: "top", lineSpacingMultiple: 1.35 });
  s.addShape("rect", { x: 0.5, y: 4.0, w: 9.0, h: 1.05, fill: { color: C.white }, line: { color: C.border, width: 1 } });
  s.addText([
    { text: "这个过程值得说：缺陷由测试发现 → 定位到“状态条件恒真” → 改条件 → 补回归测试防回退。", options: { fontSize: 13, bold: true, color: C.navy } },
    { text: "同类问题此后在全库状态机做了横向排查（报名审核、退费、薪资、考级），统一为带前置条件的原子更新。", options: { fontSize: 12, color: C.gray, breakLine: true } }
  ], { x: 0.75, y: 4.0, w: 8.5, h: 1.05, fontFace: F.body, valign: "middle", lineSpacingMultiple: 1.35 });
  footer(s, 12);
  s.render();
})();

/* P13 难点四：薪资 */
(() => {
  const s = pres.addSlide();
  s.background = { color: C.bg };
  header(s, "05｜核心难点（得分重点）", "难点四：薪资算得对且不可篡改");
  s.addImage({ path: DIR + "salary_1920x1080.png", x: 0.6, y: 1.45, w: 4.3, h: 2.45 });
  card(s, 5.1, 1.45, 4.4, 2.45);
  s.addText([
    { text: "自动核算与状态机", options: { fontSize: 14, bold: true, color: C.navy } },
    { text: "· 课次 → 班级 → 课程 → 薪资规则自动映射", options: { fontSize: 11.8, color: C.ink, breakLine: true } },
    { text: "· 仅“已完成且有到课/迟到”计薪", options: { fontSize: 11.8, color: C.ink, breakLine: true } },
    { text: "· 核算 → 确认 → 发放 → 撤销 四态受 CAS 保护", options: { fontSize: 11.8, color: C.ink, breakLine: true } },
    { text: "· (教师, 月份) 唯一约束防重复", options: { fontSize: 11.8, color: C.ink, breakLine: true } }
  ], { x: 5.35, y: 1.65, w: 3.9, h: 2.05, fontFace: F.body, valign: "top", lineSpacingMultiple: 1.35 });
  s.addShape("rect", { x: 0.5, y: 4.1, w: 9.0, h: 0.95, fill: { color: C.light }, line: { color: C.navy3, width: 0.75 } });
  s.addText("已确认的薪资单若被追溯修改，写入的是调整记录而非覆盖原金额——改动有痕、金额可追溯。", { x: 0.7, y: 4.1, w: 8.6, h: 0.95, fontFace: F.body, fontSize: 13, bold: true, color: C.navy, valign: "middle" });
  footer(s, 13);
  s.render();
})();

/* P14 流失预警 */
(() => {
  const s = pres.addSlide();
  s.background = { color: C.bg };
  header(s, "05｜核心难点（得分重点）", "从统计升级为决策：流失预警");
  s.addImage({ path: DIR + "churn_1568x426.png", x: 0.6, y: 1.4, w: 8.8, h: 2.1 });
  card(s, 0.5, 3.65, 9.0, 1.35);
  s.addText([
    { text: "五因子：到课沉寂度 · 缺勤倾向 · 课时余量 · 到期紧迫度 · 异常出勤（默认关闭）", options: { fontSize: 12.5, bold: true, color: C.navy } },
    { text: "输出高/中/低三档，并给出命中因子说明——可解释，便于教务据此跟进，而不是给一个黑箱分数。", options: { fontSize: 12, color: C.ink, breakLine: true } }
  ], { x: 0.75, y: 3.65, w: 8.5, h: 1.35, fontFace: F.body, valign: "middle", lineSpacingMultiple: 1.4 });
  footer(s, 14);
  s.render();
})();

/* P15 运营看板 */
(() => {
  const s = pres.addSlide();
  s.background = { color: C.bg };
  header(s, "05｜核心难点（得分重点）", "运营看板与统计");
  s.addImage({ path: DIR + "dashboard_1920x1080.png", x: 0.6, y: 1.4, w: 5.4, h: 3.05 });
  card(s, 6.2, 1.4, 3.3, 3.05);
  s.addText([
    { text: "多维统计", options: { fontSize: 14, bold: true, color: C.navy } },
    { text: "· 在册学员（按学员去重）", options: { fontSize: 11.8, color: C.ink, breakLine: true } },
    { text: "· 本月营收（净额）", options: { fontSize: 11.8, color: C.ink, breakLine: true } },
    { text: "· 本月课次 / 到课率", options: { fontSize: 11.8, color: C.ink, breakLine: true } },
    { text: "· 近 6 月趋势（ECharts）", options: { fontSize: 11.8, color: C.ink, breakLine: true } },
    { text: "· 报表导出（Excel）", options: { fontSize: 11.8, color: C.ink, breakLine: true } },
    { text: "按角色隔离数据可见范围。", options: { fontSize: 11.8, color: C.gray, breakLine: true } }
  ], { x: 6.45, y: 1.6, w: 2.85, h: 2.7, fontFace: F.body, valign: "top", lineSpacingMultiple: 1.32 });
  footer(s, 15);
  s.render();
})();

/* P16 移动端交付 */
(() => {
  const s = pres.addSlide();
  s.background = { color: C.bg };
  header(s, "05｜核心难点（得分重点）", "移动端交付");
  card(s, 0.5, 1.4, 4.35, 3.35);
  s.addText("学员家长", { x: 0.75, y: 1.55, w: 3.85, h: 0.4, fontFace: F.body, fontSize: 15, bold: true, color: C.navy });
  s.addText("在线报名（含决策快照）· 周课表 · 学情查看 · 课时账户与流水 · 请假申请 · 退费申请 · 消息中心", { x: 0.75, y: 2.05, w: 3.85, h: 2.5, fontFace: F.body, fontSize: 12.5, color: C.ink, lineSpacingMultiple: 1.45 });
  card(s, 5.15, 1.4, 4.35, 3.35);
  s.addText("授课教师", { x: 5.4, y: 1.55, w: 3.85, h: 0.4, fontFace: F.body, fontSize: 15, bold: true, color: C.navy });
  s.addText("我的课表 · 课堂考勤（四态）· 考勤记录 · 学情管理 · 课时统计 · 调课申请 · 请假审批", { x: 5.4, y: 2.05, w: 3.85, h: 2.5, fontFace: F.body, fontSize: 12.5, color: C.ink, lineSpacingMultiple: 1.45 });
  s.addText("交付形态：uni-app 编译的 H5（手机浏览器访问）；微信小程序因平台要求 HTTPS 与备案域名，未纳入本次交付。", { x: 0.5, y: 4.9, w: 9.0, h: 0.35, fontFace: F.body, fontSize: 11.5, color: C.gray, align: "center" });
  footer(s, 16);
  s.render();
})();

/* P17 成果总览 */
(() => {
  const s = pres.addSlide();
  s.background = { color: C.bg };
  header(s, "06｜成果与验证", "实现成果总览");
  const rows = [
    ["角色", "核心功能"],
    ["超级管理员", "用户/角色/菜单权限、机构配置、公告、全局看板与流失预警、操作日志"],
    ["教务管理员", "课程班级、学员档案与分班转班、报名审核、智能排课与大课表、调课审核、考勤、考级"],
    ["财务管理员", "收费续费、退费审核、课时账户与流水、薪资规则与核算发放、营收统计与导出"],
    ["授课教师", "课表、课堂考勤、学情点评、课时统计、调课申请、请假审批"],
    ["学员家长", "在线报名、课表、学情、课时账户、请假、退费、消息"]
  ];
  rows.forEach((r, i) => {
    const y = 1.35 + i * 0.63;
    const fill = i === 0 ? C.navy : (i % 2 === 0 ? C.light : C.white);
    s.addShape("rect", { x: 0.5, y, w: 9.0, h: 0.59, fill: { color: fill }, line: { color: C.border, width: 0.5 } });
    const col = i === 0 ? C.white : C.ink;
    s.addText(r[0], { x: 0.7, y, w: 1.9, h: 0.59, fontFace: F.body, fontSize: i === 0 ? 12.5 : 12, bold: i === 0, color: col, valign: "middle" });
    s.addText(r[1], { x: 2.7, y, w: 6.6, h: 0.59, fontFace: F.body, fontSize: i === 0 ? 12.5 : 11.5, bold: i === 0, color: col, valign: "middle" });
  });
  footer(s, 17);
  s.render();
})();

/* P18 测试体系 */
(() => {
  const s = pres.addSlide();
  s.background = { color: C.bg };
  header(s, "06｜成果与验证", "测试体系与结果");
  const rows = [
    ["层次", "用例数", "结果"],
    ["后端单元测试", "490", "全通过"],
    ["后端集成测试", "12", "全通过"],
    ["管理后台组件测试", "118", "全通过"],
    ["移动端逻辑测试", "25", "全通过"],
    ["五角色授权矩阵", "38", "全通过"],
    ["浏览器 UI 验收", "69", "全通过"],
    ["合计", "752", "0 失败"]
  ];
  rows.forEach((r, i) => {
    const y = 1.3 + i * 0.55;
    const head = i === 0, last = i === rows.length - 1;
    const fill = head ? C.navy : (last ? C.light : (i % 2 === 0 ? C.light : C.white));
    s.addShape("rect", { x: 0.5, y, w: 9.0, h: 0.51, fill: { color: fill }, line: { color: C.border, width: 0.5 } });
    const col = head ? C.white : C.ink;
    s.addText(r[0], { x: 0.7, y, w: 5.4, h: 0.51, fontFace: F.body, fontSize: head ? 12.5 : 12, bold: head || last, color: col, valign: "middle" });
    s.addText(r[1], { x: 6.2, y, w: 1.5, h: 0.51, fontFace: F.body, fontSize: head ? 12.5 : 12, bold: head || last, color: col, align: "center", valign: "middle" });
    s.addText(r[2], { x: 7.8, y, w: 1.5, h: 0.51, fontFace: F.body, fontSize: head ? 12.5 : 12, bold: head || last, color: col, align: "center", valign: "middle" });
  });
  footer(s, 18);
  s.render();
})();

/* P19 最有价值的一次缺陷 */
(() => {
  const s = pres.addSlide();
  s.background = { color: C.bg };
  header(s, "06｜成果与验证", "最有价值的一次缺陷");
  const steps = [
    ["现象", "线上移动端缺少 v1.3 该有的功能"],
    ["根因", "首次部署复用了本地七月底构建的旧产物，未校验产物新鲜度"],
    ["牵连", "顺线发现构建产物缺静态资源目录，底部导航图标全部 404"]
  ];
  steps.forEach((st, i) => {
    const x = 0.5 + i * 3.05;
    card(s, x, 1.45, 2.85, 2.1);
    chip(s, x + 0.95, 1.62, 0.95, 0.4, st[0]);
    s.addText(st[1], { x: x + 0.2, y: 2.2, w: 2.45, h: 1.2, fontFace: F.body, fontSize: 12, color: C.ink, valign: "middle", lineSpacingMultiple: 1.4 });
  });
  s.addShape("rect", { x: 0.5, y: 3.75, w: 9.0, h: 1.2, fill: { color: C.light }, line: { color: C.navy3, width: 0.75 } });
  s.addText([
    { text: "收获：测试不只是验证功能对不对，更会暴露流程与工程上的漏洞。", options: { fontSize: 13.5, bold: true, color: C.navy } },
    { text: "修复后重新构建部署，功能补全、图标恢复，接口验收 38 项复验全过。", options: { fontSize: 12.5, color: C.ink, breakLine: true } }
  ], { x: 0.75, y: 3.75, w: 8.5, h: 1.2, fontFace: F.body, valign: "middle", lineSpacingMultiple: 1.4 });
  footer(s, 19);
  s.render();
})();

/* P20 创新点 */
(() => {
  const s = pres.addSlide();
  s.background = { color: C.bg };
  header(s, "07｜创新与不足", "创新点");
  const items = [
    ["全链条完整工程系统", "招生—排课—考勤—课时—财务—学情统一数据模型，而非单点工具"],
    ["并发一致性做成纪律", "全库状态机统一为带前置条件的原子更新，并配回归测试防回退"],
    ["可解释的多因子预警", "给档位也给出命中原因，便于教务据此行动"],
    ["健壮性做进交付", "版本化迁移、加密备份与演练、发布门禁、可观测性一并落地"]
  ];
  items.forEach((it, i) => {
    const x = 0.5 + (i % 2) * 4.65, y = 1.45 + Math.floor(i / 2) * 1.75;
    card(s, x, y, 4.4, 1.55);
    s.addText(it[0], { x: x + 0.22, y: y + 0.2, w: 3.96, h: 0.42, fontFace: F.body, fontSize: 14, bold: true, color: C.navy });
    s.addText(it[1], { x: x + 0.22, y: y + 0.68, w: 3.96, h: 0.72, fontFace: F.body, fontSize: 11.5, color: C.gray, valign: "middle" });
  });
  footer(s, 20);
  s.render();
})();

/* P21 不足与展望 */
(() => {
  const s = pres.addSlide();
  s.background = { color: C.bg };
  header(s, "07｜创新与不足", "不足与展望");
  card(s, 0.5, 1.4, 4.35, 3.4);
  s.addText("不足", { x: 0.75, y: 1.55, w: 3.85, h: 0.4, fontFace: F.body, fontSize: 15, bold: true, color: C.red });
  s.addText([
    { text: "· 支付与短信为模拟实现，未接真实网关", options: { fontSize: 12, color: C.ink, breakLine: true } },
    { text: "· 单机部署，快照令牌仍在内存", options: { fontSize: 12, color: C.ink, breakLine: true } },
    { text: "· 未做性能压测，缺少容量基线", options: { fontSize: 12, color: C.ink, breakLine: true } },
    { text: "· 预警为规则引擎，非机器学习模型", options: { fontSize: 12, color: C.ink, breakLine: true } }
  ], { x: 0.75, y: 2.05, w: 3.85, h: 2.6, fontFace: F.body, valign: "top", lineSpacingMultiple: 1.45 });
  card(s, 5.15, 1.4, 4.35, 3.4, { fill: C.light, line: C.navy3 });
  s.addText("展望", { x: 5.4, y: 1.55, w: 3.85, h: 0.4, fontFace: F.body, fontSize: 15, bold: true, color: C.green });
  s.addText([
    { text: "· 接入真实支付与短信通道", options: { fontSize: 12, color: C.ink, breakLine: true } },
    { text: "· 迁至分布式缓存与会话，支撑多实例", options: { fontSize: 12, color: C.ink, breakLine: true } },
    { text: "· 补充压测，明确并发拐点", options: { fontSize: 12, color: C.ink, breakLine: true } },
    { text: "· 引入数据驱动模型，保留规则兜底", options: { fontSize: 12, color: C.ink, breakLine: true } }
  ], { x: 5.4, y: 2.05, w: 3.85, h: 2.6, fontFace: F.body, valign: "top", lineSpacingMultiple: 1.45 });
  footer(s, 21);
  s.render();
})();

/* P22 结语 */
(() => {
  const s = pres.addSlide();
  s.background = { color: C.navy };
  s.addShape("rect", { x: 0, y: 0, w: SLIDE_W, h: SLIDE_H, fill: { color: C.navy2 } });
  s.addShape("rect", { x: 0, y: 0, w: SLIDE_W * 0.55, h: SLIDE_H, fill: { color: C.navy } });
  s.addShape("rect", { x: 0.55, y: 0, w: 0.012, h: SLIDE_H, fill: { color: C.gold } });
  s.addText("账目对得上 · 并发扛得住 · 坏了查得到", { x: 0.7, y: 1.5, w: 8.6, h: 0.7, fontFace: F.title, fontSize: 26, bold: true, color: C.white, align: "center", charSpacing: 1.5 });
  s.addShape("rect", { x: 3.9, y: 2.45, w: 2.2, h: 0.02, fill: { color: C.gold } });
  s.addText("系统已完成容器化部署并稳定运行，核心业务闭环经数据库逐项对账一致。", { x: 0.7, y: 2.75, w: 8.6, h: 0.5, fontFace: F.body, fontSize: 14, color: C.ice, align: "center" });
  s.addText("以上是我的毕业设计汇报", { x: 0.7, y: 3.45, w: 8.6, h: 0.45, fontFace: F.body, fontSize: 15, color: C.ice, align: "center", charSpacing: 2 });
  s.addText("恳请各位老师批评指正", { x: 0.7, y: 4.0, w: 8.6, h: 0.7, fontFace: F.title, fontSize: 30, bold: true, color: C.white, align: "center", charSpacing: 2 });
  s.render();
})();

pres.writeFile({ fileName: OUT }).then(f => console.log("OK: " + f));
