from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.enum.shapes import MSO_SHAPE, MSO_CONNECTOR
from pptx.dml.color import RGBColor
from pptx.enum.dml import MSO_THEME_COLOR


OUT = r"D:\codegitee\educational-administration\docs\defence\艺培通开题答辩_ER优化版.pptx"

NAVY = RGBColor(22, 43, 85)
NAVY_2 = RGBColor(36, 58, 111)
GOLD = RGBColor(211, 167, 44)
INK = RGBColor(24, 37, 61)
MUTED = RGBColor(92, 105, 124)
LIGHT = RGBColor(246, 248, 252)
WHITE = RGBColor(255, 255, 255)
LINE = RGBColor(220, 226, 235)
BLUE = RGBColor(56, 105, 170)
TEAL = RGBColor(42, 133, 128)
RED = RGBColor(189, 91, 75)

W, H = 13.333, 7.5
FONT = "Microsoft YaHei"


def rgb(hexv):
    hexv = hexv.lstrip('#')
    return RGBColor(int(hexv[0:2], 16), int(hexv[2:4], 16), int(hexv[4:6], 16))


def set_run_font(run, size, color=INK, bold=False):
    run.font.name = FONT
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.color.rgb = color


def add_text(slide, x, y, w, h, text, size=18, color=INK, bold=False,
             align=PP_ALIGN.LEFT, valign=MSO_ANCHOR.TOP, margin=0.04,
             font=FONT):
    box = slide.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    tf = box.text_frame
    tf.clear()
    tf.word_wrap = True
    tf.margin_left = Inches(margin)
    tf.margin_right = Inches(margin)
    tf.margin_top = Inches(margin)
    tf.margin_bottom = Inches(margin)
    tf.vertical_anchor = valign
    p = tf.paragraphs[0]
    p.alignment = align
    p.space_after = Pt(0)
    r = p.add_run()
    r.text = text
    r.font.name = font
    r.font.size = Pt(size)
    r.font.bold = bold
    r.font.color.rgb = color
    return box


def add_rich(slide, x, y, w, h, lines, size=17, color=INK, leading=2,
             margin=0.04, bullet=False):
    box = slide.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    tf = box.text_frame
    tf.clear()
    tf.word_wrap = True
    tf.margin_left = Inches(margin)
    tf.margin_right = Inches(margin)
    tf.margin_top = Inches(margin)
    tf.margin_bottom = Inches(margin)
    for i, item in enumerate(lines):
        if isinstance(item, tuple):
            text, col, bold = item
        else:
            text, col, bold = item, color, False
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.space_after = Pt(leading)
        p.level = 0
        if bullet:
            p.text = "• " + text
        else:
            p.text = text
        for r in p.runs:
            set_run_font(r, size, col, bold)
    return box


def rect(slide, x, y, w, h, fill, line=None, radius=False):
    shape = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE if radius else MSO_SHAPE.RECTANGLE,
                                   Inches(x), Inches(y), Inches(w), Inches(h))
    shape.fill.solid()
    shape.fill.fore_color.rgb = fill
    shape.line.color.rgb = line if line else fill
    shape.line.width = Pt(0.7)
    if radius:
        shape.adjustments[0] = 0.08
    return shape


def line(slide, x1, y1, x2, y2, color=LINE, width=1.2, dash=None):
    ln = slide.shapes.add_connector(MSO_CONNECTOR.STRAIGHT, Inches(x1), Inches(y1), Inches(x2), Inches(y2))
    ln.line.color.rgb = color
    ln.line.width = Pt(width)
    if dash:
        ln.line.dash_style = dash
    return ln


def circle(slide, x, y, d, fill, text=None, text_color=WHITE, size=14):
    sh = slide.shapes.add_shape(MSO_SHAPE.OVAL, Inches(x), Inches(y), Inches(d), Inches(d))
    sh.fill.solid(); sh.fill.fore_color.rgb = fill
    sh.line.color.rgb = fill
    if text is not None:
        add_text(slide, x, y + 0.01, d, d - 0.02, text, size, text_color, True,
                 PP_ALIGN.CENTER, MSO_ANCHOR.MIDDLE, margin=0)
    return sh


def base(slide, section, title, page, dark=False, kicker=None):
    slide.background.fill.solid()
    slide.background.fill.fore_color.rgb = NAVY if dark else LIGHT
    if dark:
        rect(slide, 0, 0, W, 0.1, GOLD)
        add_text(slide, 0.72, 0.42, 2.2, 0.25, section.upper(), 10, GOLD, True)
        add_text(slide, 0.72, 0.82, 11.4, 0.65, title, 28, WHITE, True)
        return
    rect(slide, 0, 0, W, 0.07, NAVY)
    rect(slide, 0.72, 0.42, 0.08, 0.52, GOLD)
    add_text(slide, 0.95, 0.41, 2.2, 0.26, section.upper(), 10, GOLD, True)
    add_text(slide, 0.95, 0.68, 11.3, 0.48, title, 24, NAVY, True)
    if kicker:
        add_text(slide, 0.98, 1.2, 11.2, 0.28, kicker, 10.5, MUTED)
    line(slide, 0.95, 1.55, 12.35, 1.55, LINE, 0.8)
    add_text(slide, 0.95, 7.16, 7.0, 0.16, "艺培通 · 艺体培训机构全流程教务管理平台", 8.5, MUTED)
    add_text(slide, 11.55, 7.14, 0.8, 0.18, f"{page:02d}", 9, NAVY, True, PP_ALIGN.RIGHT)


def card(slide, x, y, w, h, title, body=None, accent=BLUE, num=None, title_size=15, body_size=11.5):
    rect(slide, x, y, w, h, WHITE, LINE, radius=True)
    rect(slide, x, y, 0.06, h, accent)
    if num is not None:
        circle(slide, x + 0.22, y + 0.2, 0.33, accent, str(num), WHITE, 10)
        tx = x + 0.67
    else:
        tx = x + 0.22
    add_text(slide, tx, y + 0.18, w - (tx - x) - 0.2, 0.34, title, title_size, NAVY, True)
    if body:
        add_rich(slide, x + 0.22, y + 0.68, w - 0.42, h - 0.83, body if isinstance(body, list) else [body], body_size, MUTED, leading=2.5, bullet=False)


def pill(slide, x, y, w, text, fill=NAVY_2, color=WHITE):
    rect(slide, x, y, w, 0.33, fill, fill, radius=True)
    add_text(slide, x, y + 0.01, w, 0.28, text, 10.5, color, True, PP_ALIGN.CENTER, MSO_ANCHOR.MIDDLE, margin=0)


def add_title_slide(prs):
    s = prs.slides.add_slide(prs.slide_layouts[6])
    s.background.fill.solid(); s.background.fill.fore_color.rgb = NAVY
    rect(s, 0, 0, W, 0.1, GOLD)
    rect(s, 0, 0, 4.18, H, NAVY_2)
    rect(s, 4.18, 0, 0.08, H, GOLD)
    add_text(s, 0.75, 0.82, 3.0, 0.24, "攀枝花学院 · 本科毕业设计（论文）", 11, RGBColor(222, 228, 241), True)
    add_text(s, 4.85, 1.25, 7.6, 0.72, "艺体培训机构全流程教务管理平台", 29, WHITE, True)
    add_text(s, 4.85, 2.05, 7.4, 0.62, "的设计与实现", 27, GOLD, True)
    line(s, 4.88, 2.95, 11.85, 2.95, RGBColor(96, 116, 163), 1.1)
    add_text(s, 4.88, 3.22, 7.4, 0.35, "艺培通：面向中小型艺体培训机构的全链路教务管理平台", 14, RGBColor(210, 219, 235))
    rect(s, 4.88, 4.45, 6.35, 1.05, RGBColor(31, 54, 102), RGBColor(77, 99, 150), radius=True)
    add_text(s, 5.18, 4.68, 5.65, 0.28, "答辩人：罗斌洋    指导教师：张会", 13, WHITE, True)
    add_text(s, 5.18, 5.08, 5.65, 0.25, "专业：软件工程    ｜    2026 年 10 月", 11, RGBColor(204, 215, 232))
    add_text(s, 0.75, 6.77, 3.0, 0.2, "开题答辩", 11, GOLD, True)
    add_text(s, 4.88, 6.78, 7.5, 0.2, "Research proposal defense", 9, RGBColor(161, 177, 207))


def add_agenda(prs):
    s = prs.slides.add_slide(prs.slide_layouts[6]); base(s, "开题答辩", "从问题出发，走到可交付方案", 2, kicker="汇报结构：背景与缺口 → 研究内容 → 技术方案 → 验证与交付")
    items = [("01", "选题背景与意义", "行业特征 · 五类痛点 · 工程价值"), ("02", "研究现状与趋势", "点状成果 · 三个研究缺口"), ("03", "研究内容与目标", "五类角色 · 四类关键技术"), ("04", "技术路线与方案", "技术选型 · 架构 · 数据库 · 算法"), ("05", "研究方法与可行性", "核心链路 · 分层测试 · 三性分析"), ("06", "进度与预期成果", "五阶段计划 · 六项交付物")]
    for i,(n,t,b) in enumerate(items):
        x = 0.95 + (i%2)*6.0; y = 1.95 + (i//2)*1.38
        rect(s,x,y,5.38,1.05,WHITE,LINE,radius=True); circle(s,x+0.23,y+0.23,0.48,GOLD,n,WHITE,11)
        add_text(s,x+0.88,y+0.18,4.2,0.28,t,16,NAVY,True); add_text(s,x+0.88,y+0.54,4.15,0.22,b,10.5,MUTED)
    rect(s,0.95,6.28,11.38,0.45,NAVY,NAVY,radius=True)
    add_text(s,1.15,6.34,10.95,0.24,"答辩主线：把分散的教务环节，组织成一条可验证、可上线的业务闭环",11,WHITE,True,PP_ALIGN.CENTER,MSO_ANCHOR.MIDDLE)


def add_background(prs):
    s=prs.slides.add_slide(prs.slide_layouts[6]); base(s,"01 选题背景与意义","教务管理为何是机构最容易出错的环节？",3,kicker="艺术培训的运营特征相互叠加，让教务成为信息密度最高、最易出错的环节")
    traits=[("课程类型多","体验课、长期课、考级课并存"),("上课频次高","以周为单位常态化排课"),("兼职老师多","排班与场地调度更复杂"),("学员未成年","请假、调课、补课频繁"),("收入靠课时包","课时、考勤与薪资强关联")]
    for i,(t,b) in enumerate(traits):
        x=0.95+i*2.28; rect(s,x,2.0,2.05,1.05,WHITE,LINE,radius=True); circle(s,x+0.18,2.18,0.34,GOLD,str(i+1),WHITE,10); add_text(s,x+0.62,2.14,1.2,0.25,t,13,NAVY,True); add_text(s,x+0.2,2.58,1.65,0.25,b,9.5,MUTED)
    rect(s,0.95,3.55,7.45,2.28,NAVY,NAVY,radius=True); add_text(s,1.28,3.85,6.75,0.3,"传统管理模式",12,GOLD,True); add_text(s,1.28,4.23,6.7,0.45,"电子表格 + 纸质登记本",24,WHITE,True); add_text(s,1.28,4.88,6.35,0.5,"记录学员信息、课表、考勤与收费\n信息被拆散在多个表格与人员手中",14,RGBColor(215,224,239));
    rect(s,8.72,3.55,3.61,2.28,RGBColor(255,250,238),RGBColor(236,219,166),radius=True); add_text(s,9.02,3.86,3.0,0.3,"核心判断",11,GOLD,True); add_text(s,9.02,4.32,2.95,0.98,"规模扩大后，\n人力成本与差错成本\n呈非线性上升",19,NAVY,True); add_text(s,9.02,5.45,2.9,0.2,"需要一套统一、可追溯的系统",10,MUTED)


def add_pain(prs):
    s=prs.slides.add_slide(prs.slide_layouts[6]); base(s,"01 选题背景与意义","五类痛点：传统教务管理的核心问题",4,kicker="问题不是单点故障，而是数据、流程与决策同时断裂")
    ps=[("数据分散","统计滞后","多表多人各管一段，管理者难以及时掌握招生、到课、营收与流失状况",BLUE),("课时误扣","容易漏扣","多学员、多课程组合下，手工扣减极易出错",TEAL),("冲突后知后觉","排课靠试错","教师、教室、班级的时间冲突无法一次暴露",RED),("薪资慢且易错","缺逐项明细","以课时为基础人工汇总，缺乏可追溯构成",GOLD),("流失隐蔽","干预滞后","续费下滑才察觉，往往错过干预窗口",NAVY_2)]
    for i,(a,b,c,col) in enumerate(ps):
        x=0.95+i*2.28; rect(s,x,2.0,2.05,3.55,WHITE,LINE,radius=True); rect(s,x,2.0,2.05,0.12,col); circle(s,x+0.24,2.33,0.5,col,f"0{i+1}",WHITE,10); add_text(s,x+0.24,3.05,1.5,0.32,a,14,NAVY,True); add_text(s,x+0.24,3.47,1.55,0.22,b,11,col,True); add_text(s,x+0.24,4.02,1.55,1.0,c,10.5,MUTED)
    rect(s,0.95,6.03,11.38,0.5,NAVY,NAVY,radius=True); add_text(s,1.15,6.13,10.98,0.25,"结论：手工模式已成为机构规模化、规范化运营的瓶颈",12,WHITE,True,PP_ALIGN.CENTER,MSO_ANCHOR.MIDDLE)


def add_significance(prs):
    s=prs.slides.add_slide(prs.slide_layouts[6]); base(s,"01 选题背景与意义","研究意义：以工程化手段解决实际管理难题",5,kicker="服务对象：中小型艺体培训机构")
    cards=[("Ⅰ","统一数据模型","招生、排课、考勤、课时、财务、学情进入同一系统，流程标准化、口径一致化，消除信息孤岛",BLUE),("Ⅱ","自动化降错提效","课时自动扣减、冲突检测与薪资自动核算，减少人工重复与误差",TEAL),("Ⅲ","数据驱动决策","沉淀业务数据支撑运营看板与流失预警，从事后统计走向事前干预",GOLD)]
    for i,(n,t,b,c) in enumerate(cards):
        x=0.95+i*3.78; rect(s,x,2.0,3.42,3.35,WHITE,LINE,radius=True); circle(s,x+0.28,2.32,0.56,c,n,WHITE,14); add_text(s,x+1.0,2.34,2.0,0.28,t,16,NAVY,True); add_text(s,x+0.28,3.18,2.82,1.35,b,13,MUTED); line(s,x+0.28,4.78,x+2.95,4.78,LINE,0.8); add_text(s,x+0.28,4.96,2.82,0.22,["消除信息孤岛","降低人工差错","支撑事前干预"][i],10.5,c,True)
    rect(s,0.95,5.85,11.38,0.58,RGBColor(255,250,238),RGBColor(236,219,166),radius=True); add_text(s,1.2,6.01,10.88,0.24,"现实意义：教务管理信息化，是艺术素质教育数字化的基础工程",12,NAVY,True,PP_ALIGN.CENTER,MSO_ANCHOR.MIDDLE)


def add_status(prs):
    s=prs.slides.add_slide(prs.slide_layouts[6]); base(s,"02 研究现状与趋势","国内外研究现状：点状成果丰富，整体系统缺失",6,kicker="已有研究为算法与单模块提供基础，但缺少面向中小型艺术培训机构的工程化落地")
    rect(s,0.95,1.93,5.55,3.95,WHITE,LINE,radius=True); add_text(s,1.25,2.2,4.7,0.3,"国外研究",16,NAVY,True); pill(s,1.25,2.67,1.7,"智能排课",BLUE); add_text(s,1.25,3.12,4.5,0.64,"组合优化与启发式\n遗传算法 / 动态规划 / 禁忌搜索",12,MUTED); pill(s,1.25,4.05,1.7,"流失预测",TEAL); add_text(s,1.25,4.5,4.5,0.64,"机器学习与生存分析\n从多源行为因子刻画风险",12,MUTED); add_text(s,1.25,5.36,4.55,0.25,"启示：到课行为与流失风险存在稳定关联",10.5,GOLD,True)
    rect(s,6.75,1.93,5.58,3.95,WHITE,LINE,radius=True); add_text(s,7.05,2.2,4.8,0.3,"国内研究",16,NAVY,True); add_rich(s,7.05,2.72,4.9,2.6,["排课与冲突检测：B/S 系统、时间区间重叠","可视化与学情：ECharts、行为预警模型","核算与移动端：工资核算、uni-app 学生端","系统与档案：教务管理、数字化存储"],12,MUTED,leading=7,bullet=True)
    rect(s,0.95,6.15,11.38,0.43,NAVY,NAVY,radius=True); add_text(s,1.15,6.24,10.98,0.2,"共性短板：单一算法点或单一业务环节，难以拼装为可用、可靠、可维护的整体系统",11,WHITE,True,PP_ALIGN.CENTER,MSO_ANCHOR.MIDDLE)


def add_gap(prs):
    s=prs.slides.add_slide(prs.slide_layouts[6]); base(s,"02 研究现状与趋势","发展趋势与本研究缺口",7,kicker="本研究定位：完整系统 + 统一数据模型 + 工程质量保障")
    trends=["全流程一体化","事前预警干预","移动优先多端协同","工程质量成底线","容器化持续交付"]
    for i,t in enumerate(trends):
        x=0.95+i*2.28; rect(s,x,1.98,2.04,0.8,WHITE,LINE,radius=True); circle(s,x+0.18,2.19,0.36,GOLD,str(i+1),WHITE,10); add_text(s,x+0.67,2.18,1.16,0.28,t,11.5,NAVY,True)
        if i<4: line(s,x+2.04,2.38,x+2.27,2.38,GOLD,1.2)
    gaps=[("缺口 1","缺少全链条工程化落地系统","面向中小型艺体机构，覆盖招生、排课、考勤、课时、财务、学情"),("缺口 2","缺少统一数据模型闭环","以同一数据模型串联各环节，形成可追溯的业务闭环"),("缺口 3","缺少工程质量保障","并发一致性、权限隔离与可维护性缺少系统性设计")]
    for i,(n,t,b) in enumerate(gaps):
        x=0.95+i*3.78; rect(s,x,3.45,3.42,2.3,WHITE,LINE,radius=True); add_text(s,x+0.28,3.72,1.2,0.22,n,10,GOLD,True); add_text(s,x+0.28,4.08,2.82,0.38,t,15,NAVY,True); add_text(s,x+0.28,4.7,2.82,0.62,b,11,MUTED)
    rect(s,0.95,6.15,11.38,0.43,NAVY,NAVY,radius=True); add_text(s,1.15,6.24,10.98,0.2,"研究回答：如何把业务闭环做完整、把规则做一致、把工程质量做扎实？",11,WHITE,True,PP_ALIGN.CENTER,MSO_ANCHOR.MIDDLE)


def add_roles(prs):
    s=prs.slides.add_slide(prs.slide_layouts[6]); base(s,"03 研究内容与目标","五类角色，共同组成一条业务闭环",8,kicker="统一数据模型：学员 / 课程 / 课次 / 考勤 / 课时 / 财务 / 薪资 / 通知")
    roles=[("学员家长端","报名缴费 · 课表学情\n课时流水 · 消息提醒",BLUE),("授课教师端","课表提醒 · 调课申请\n四态考勤 · 课时统计",TEAL),("教务管理端","课程班级 · 智能排课\n报名审核 · 考级管理",NAVY_2),("财务管理端","收费续费 · 退费审核\n薪资结算 · 营收报表",GOLD),("超级管理员","人员权限 · 机构配置\n公告管理 · 运营看板",RED)]
    for i,(t,b,c) in enumerate(roles):
        x=0.95+i*2.28; rect(s,x,2.0,2.04,2.22,WHITE,LINE,radius=True); rect(s,x,2.0,2.04,0.11,c); circle(s,x+0.76,2.31,0.5,c,str(i+1),WHITE,11); add_text(s,x+0.15,3.03,1.72,0.28,t,13,NAVY,True,PP_ALIGN.CENTER); add_text(s,x+0.18,3.52,1.66,0.46,b,10.5,MUTED,align=PP_ALIGN.CENTER)
    line(s,1.55,4.7,11.72,4.7,GOLD,2.0)
    for i in range(5):
        circle(s,1.42+i*2.28,4.54,0.32,GOLD,str(i+1),WHITE,9)
    add_text(s,1.0,5.18,11.1,0.35,"招生",17,NAVY,True,PP_ALIGN.CENTER); add_text(s,1.0,5.55,11.1,0.35,"报名 → 排课 → 考勤 → 课时 / 财务 → 学情与预警",18,GOLD,True,PP_ALIGN.CENTER)
    rect(s,0.95,6.28,11.38,0.4,NAVY,NAVY,radius=True); add_text(s,1.15,6.36,10.98,0.2,"五类角色经统一数据模型相互衔接，覆盖“招生 → 学情”全流程",10.5,WHITE,True,PP_ALIGN.CENTER,MSO_ANCHOR.MIDDLE)


def add_function_arch(prs):
    s=prs.slides.add_slide(prs.slide_layouts[6]); base(s,"03 研究内容与目标","功能边界：21 项功能，覆盖全流程",9,kicker="以角色为入口，以统一业务规则为底座")
    groups=[("学员家长","在线报名缴费\n课表 / 学情查看\n课时余额流水\n到期消息提醒",BLUE),("授课教师","授课课表提醒\n调课申请\n四态课堂考勤\n学情课时统计",TEAL),("教务管理","课程班级管理\n智能排课调课\n报名审核分班\n考级管理",NAVY_2),("财务管理","收费与续费\n退费审核\n薪资自动结算\n营收统计报表",GOLD),("超级管理员","人员权限管理\n机构配置\n公告管理\n全局运营看板",RED)]
    for i,(t,b,c) in enumerate(groups):
        x=0.95+i*2.28; rect(s,x,2.0,2.04,3.4,WHITE,LINE,radius=True); rect(s,x,2.0,2.04,0.42,c); add_text(s,x,2.06,2.04,0.25,t,12,WHITE,True,PP_ALIGN.CENTER); add_rich(s,x+0.22,2.72,1.6,2.2,b.split('\n'),11,MUTED,leading=10,bullet=True)
    rect(s,0.95,5.85,11.38,0.58,RGBColor(255,250,238),RGBColor(236,219,166),radius=True); add_text(s,1.15,6.0,10.98,0.25,"统一数据模型把五类角色串成一条闭环：每个动作都有来源、状态和可追溯结果",11.5,NAVY,True,PP_ALIGN.CENTER,MSO_ANCHOR.MIDDLE)


def add_keytech(prs):
    s=prs.slides.add_slide(prs.slide_layouts[6]); base(s,"03 研究内容与目标","四类关键技术问题：把复杂规则做成可验证机制",10,kicker="研究目标：完成开发、分层测试与容器化上线，形成完整交付")
    tech=[("报名决策一致性","报名决策快照 + 版本校验\n保证名额、冲突与状态读取一致",BLUE),("排课易用与并发冲突","五维时间交叉检测 + 拖拽调课\n复合乐观并发条件防覆盖",TEAL),("薪资正确与不可篡改","状态机 + 并发控制\n已确认单不覆盖，变更留调整记录",GOLD),("运营决策能力","多因子规则评分\n把事后统计升级为事前流失干预",RED)]
    for i,(t,b,c) in enumerate(tech):
        x=0.95+(i%2)*5.9; y=2.0+(i//2)*1.8; rect(s,x,y,5.38,1.46,WHITE,LINE,radius=True); circle(s,x+0.26,y+0.29,0.52,c,str(i+1),WHITE,12); add_text(s,x+0.98,y+0.25,4.0,0.28,t,15,NAVY,True); add_text(s,x+0.98,y+0.73,4.05,0.44,b,11,MUTED)
    rect(s,0.95,6.0,11.38,0.55,NAVY,NAVY,radius=True); add_text(s,1.15,6.14,10.98,0.22,"需求分析 → 设计实现 → 测试验收 → 生产上线",14,WHITE,True,PP_ALIGN.CENTER,MSO_ANCHOR.MIDDLE)


def add_techstack(prs):
    s=prs.slides.add_slide(prs.slide_layouts[6]); base(s,"04 技术路线与总体方案","技术路线：B/S 前后端分离，按场景选择终端",11,kicker="选型依据：团队熟悉度、生态成熟度与部署成本")
    left=[("管理后台","Vue 3 · Vite · Pinia · Element Plus · ECharts","承载大课表、薪资核算与运营看板",BLUE),("移动端","uni-app（Vue 3）· uView Plus","面向家长与教师的高频轻量场景",TEAL),("后端","Spring Boot 3.2.5 · MyBatis-Plus 3.5.7","统一 RESTful 接口，承载事务与核心规则",NAVY_2)]
    for i,(t,a,b,c) in enumerate(left):
        y=1.95+i*1.28; rect(s,0.95,y,7.0,1.02,WHITE,LINE,radius=True); rect(s,0.95,y,0.12,1.02,c); add_text(s,1.25,y+0.18,1.25,0.24,t,13,NAVY,True); add_text(s,2.55,y+0.17,4.95,0.25,a,12,c,True); add_text(s,2.55,y+0.54,4.95,0.22,b,10.5,MUTED)
    rect(s,8.27,1.95,4.06,3.58,NAVY,NAVY,radius=True); add_text(s,8.62,2.23,3.25,0.25,"数据与交付",12,GOLD,True); add_rich(s,8.62,2.72,3.1,2.1,["MySQL 8.0 + Flyway","JWT + RBAC 权限隔离","Redis 依赖预留，多实例启用","容器化编排 + 反向代理","数据卷持久化，可回滚交付"],12,WHITE,leading=8,bullet=True)
    rect(s,0.95,6.0,11.38,0.5,RGBColor(255,250,238),RGBColor(236,219,166),radius=True); add_text(s,1.15,6.13,10.98,0.22,"两类终端共用同一套后端业务规则，避免规则在两端重复实现",11.5,NAVY,True,PP_ALIGN.CENTER,MSO_ANCHOR.MIDDLE)


def add_layers(prs):
    s=prs.slides.add_slide(prs.slide_layouts[6]); base(s,"04 技术路线与总体方案","总体架构：自顶向下五层分层",12,kicker="各层只依赖其下层，职责单一、边界清晰")
    layers=[("客户端层","Web 管理后台 ｜ 移动端 H5",BLUE),("接口与鉴权层","请求接入 ｜ JWT 认证 ｜ RBAC 校验",TEAL),("业务服务层","报名 / 排课 / 考勤 / 课时 / 财务 / 薪资 / 统计",NAVY_2),("数据访问层","数据库读写封装 ｜ 聚合查询",GOLD),("基础设施层","MySQL ｜ 缓存 ｜ 文件存储 ｜ Flyway ｜ 可观测性",RED)]
    for i,(t,b,c) in enumerate(layers):
        y=1.92+i*0.86; rect(s,1.2,y,10.95,0.62,WHITE,LINE,radius=True); rect(s,1.2,y,0.16,0.62,c); add_text(s,1.62,y+0.15,2.0,0.25,t,14,NAVY,True); add_text(s,3.85,y+0.15,7.7,0.25,b,12,MUTED)
        if i<4: line(s,6.65,y+0.62,6.65,y+0.86,GOLD,1.2)
    rect(s,0.95,6.35,11.38,0.34,NAVY,NAVY,radius=True); add_text(s,1.15,6.41,10.98,0.18,"部署链路：HTTPS / 反向代理 → Web 容器 → 业务接口 → 数据库容器",10.5,WHITE,True,PP_ALIGN.CENTER,MSO_ANCHOR.MIDDLE)


def add_db(prs):
    s=prs.slides.add_slide(prs.slide_layouts[6]); base(s,"04 技术路线与总体方案","数据库设计：核心实体关系总览",13,kicker="全库共 37 张表；下图聚焦核心业务链路，采用概念 ER 图表达实体、关系与基数")

    entity_fill = RGBColor(205, 235, 215)
    entity_line = RGBColor(42, 116, 76)
    rel_line = RGBColor(42, 87, 220)
    attr_line = RGBColor(0, 145, 185)

    def entity(x, y, w, title):
        sh = rect(s, x, y, w, 0.48, entity_fill, entity_line, radius=True)
        add_text(s, x + 0.04, y + 0.08, w - 0.08, 0.24, title, 12, RGBColor(28, 94, 58), True, PP_ALIGN.CENTER, MSO_ANCHOR.MIDDLE, margin=0)
        return sh

    def relation(x, y, w, h, title):
        sh = s.shapes.add_shape(MSO_SHAPE.DIAMOND, Inches(x), Inches(y), Inches(w), Inches(h))
        sh.fill.solid(); sh.fill.fore_color.rgb = WHITE
        sh.line.color.rgb = rel_line; sh.line.width = Pt(1.3)
        add_text(s, x + 0.02, y + 0.08, w - 0.04, h - 0.12, title, 10.5, rel_line, True, PP_ALIGN.CENTER, MSO_ANCHOR.MIDDLE, margin=0)
        return sh

    def attr(x, y, w, text):
        sh = s.shapes.add_shape(MSO_SHAPE.OVAL, Inches(x), Inches(y), Inches(w), Inches(0.3))
        sh.fill.solid(); sh.fill.fore_color.rgb = WHITE
        sh.line.color.rgb = attr_line; sh.line.width = Pt(1.05)
        add_text(s, x + 0.02, y + 0.04, w - 0.04, 0.2, text, 8.8, attr_line, False, PP_ALIGN.CENTER, MSO_ANCHOR.MIDDLE, margin=0)
        return sh

    def connect(x1, y1, x2, y2, card1=None, card2=None):
        line(s, x1, y1, x2, y2, RGBColor(45, 60, 87), 1.2)
        # Keep cardinalities above horizontal relationships; vertical links stay uncluttered.
        horizontal = abs(y1 - y2) < 0.16
        if horizontal and card1:
            add_text(s, x1 - 0.16, y1 - 0.37, 0.32, 0.18, card1, 9.5, INK, True, PP_ALIGN.CENTER, MSO_ANCHOR.MIDDLE, margin=0)
        if horizontal and card2:
            add_text(s, x2 - 0.16, y2 - 0.37, 0.32, 0.18, card2, 9.5, INK, True, PP_ALIGN.CENTER, MSO_ANCHOR.MIDDLE, margin=0)

    # Core teaching chain: 学员 -- 报名 -- 课程 -- 开班 -- 班级 -- 生成 -- 课次
    entity(1.0, 2.38, 1.5, "学员 student")
    entity(4.22, 2.38, 1.5, "课程 course")
    entity(7.38, 2.38, 1.72, "班级 class_group")
    entity(10.65, 2.38, 1.55, "课次 schedule_lesson")
    relation(2.77, 2.36, 0.78, 0.52, "报名")
    relation(5.98, 2.36, 0.78, 0.52, "开班")
    relation(9.43, 2.36, 0.78, 0.52, "生成")
    connect(2.5, 2.62, 2.77, 2.62, "1", "n")
    connect(3.55, 2.62, 4.22, 2.62, "n", "1")
    connect(5.72, 2.62, 5.98, 2.62, "1", "n")
    connect(6.76, 2.62, 7.38, 2.62, "n", "1")
    connect(9.1, 2.62, 9.43, 2.62, "1", "n")
    connect(10.21, 2.62, 10.65, 2.62, "n", "1")

    # Key attributes around the core entities.
    attr(0.92, 1.8, 0.9, "student_id"); attr(1.9, 1.8, 0.72, "姓名")
    connect(1.44, 2.38, 1.37, 2.1); connect(1.72, 2.38, 2.22, 2.1)
    attr(4.08, 1.8, 0.78, "课程名"); attr(5.0, 1.8, 0.72, "价格")
    connect(4.82, 2.38, 4.47, 2.1); connect(5.02, 2.38, 5.34, 2.1)
    attr(7.22, 1.8, 0.92, "teacher_id"); attr(8.26, 1.8, 0.82, "容量")
    connect(7.82, 2.38, 7.68, 2.1); connect(8.35, 2.38, 8.67, 2.1)
    attr(10.42, 1.8, 0.82, "日期"); attr(11.38, 1.8, 0.8, "时段")
    connect(11.04, 2.38, 10.83, 2.1); connect(11.5, 2.38, 11.78, 2.1)

    # Learning / attendance branch.
    entity(7.28, 4.12, 1.72, "考勤 attendance")
    relation(5.18, 4.1, 0.78, 0.52, "参与")
    connect(4.22, 2.86, 5.18, 4.36)
    connect(5.96, 4.36, 7.28, 4.36)
    connect(8.24, 2.86, 8.14, 4.12)
    attr(7.05, 4.82, 0.85, "状态"); attr(8.0, 4.82, 0.98, "扣减课时")
    connect(7.82, 4.6, 7.48, 4.82); connect(8.42, 4.6, 8.48, 4.82)

    # Finance / lesson ledger branch.
    entity(1.05, 5.02, 1.72, "课时账户 lesson_account")
    entity(4.08, 5.02, 1.62, "课时流水 lesson_flow")
    entity(7.04, 5.02, 1.72, "收费 payment_record")
    entity(10.05, 5.02, 1.72, "退费 refund_record")
    relation(2.97, 5.0, 0.78, 0.52, "产生")
    relation(8.98, 5.0, 0.78, 0.52, "退费")
    connect(2.77, 5.26, 2.97, 5.26)
    connect(3.75, 5.26, 4.08, 5.26)
    connect(8.76, 5.26, 8.98, 5.26)
    connect(9.96, 5.26, 10.05, 5.26)
    connect(1.75, 2.86, 1.75, 5.02)
    connect(7.82, 2.86, 7.82, 5.02)
    add_text(s, 0.98, 4.63, 1.52, 0.18, "按学员 + 课程", 8.6, BLUE, True, PP_ALIGN.CENTER, MSO_ANCHOR.MIDDLE, margin=0)
    add_text(s, 7.03, 4.63, 1.74, 0.18, "报名缴费后生成", 8.6, TEAL, True, PP_ALIGN.CENTER, MSO_ANCHOR.MIDDLE, margin=0)

    rect(s, 0.95, 6.02, 11.38, 0.48, RGBColor(255,250,238), RGBColor(236,219,166), radius=True)
    add_text(s, 1.16, 6.14, 1.15, 0.2, "读图重点", 10.5, GOLD, True)
    add_text(s, 2.28, 6.12, 9.82, 0.22, "报名与收费沉淀为课时账户；课次与考勤共同驱动课时流水；所有引用关系由应用层事务与业务校验保证。", 10.2, NAVY, True)
    add_text(s, 0.95, 6.68, 11.4, 0.18, "图示为核心概念 ER 图；权限、通知、考级、日志等辅助表仍纳入全库 37 张表。", 9.0, MUTED)


def add_algorithms(prs):
    s=prs.slides.add_slide(prs.slide_layouts[6]); base(s,"04 技术路线与总体方案","核心算法：可解释、可实现、可测试",14,kicker="算法直接服务业务规则，并且能在测试中被逐条验证")
    algs=[("五维冲突检测","教师 / 教室 / 教室预约 / 班级 / 跨班学员\n冲突即时提示并不予保存",BLUE),("课时自动扣减","考勤状态输入：到课、迟到扣减；请假经审批不扣\n余额、记录、流水同一事务",TEAL),("薪资自动核算","课次 → 班级 → 课程 → 薪资规则\n确认单不覆盖，变更叠加调整记录",GOLD),("学员流失预警","到课沉寂度 + 缺勤倾向 + 课时余量 + 到期紧迫\n评分分档，输出跟进清单",RED)]
    for i,(t,b,c) in enumerate(algs):
        x=0.95+(i%2)*5.9; y=1.98+(i//2)*1.8; rect(s,x,y,5.38,1.45,WHITE,LINE,radius=True); circle(s,x+0.25,y+0.28,0.5,c,str(i+1),WHITE,11); add_text(s,x+0.96,y+0.23,4.0,0.27,t,15,NAVY,True); add_text(s,x+0.96,y+0.72,4.05,0.43,b,11,MUTED)
    rect(s,0.95,6.0,11.38,0.5,NAVY,NAVY,radius=True); add_text(s,1.15,6.13,10.98,0.22,"规则的价值：让每一次排课、扣课时、结算和预警都能解释、能追溯",11.5,WHITE,True,PP_ALIGN.CENTER,MSO_ANCHOR.MIDDLE)


def add_method(prs):
    s=prs.slides.add_slide(prs.slide_layouts[6]); base(s,"05 研究方法与可行性","研究方法与验证：文档驱动，核心链路优先",15,kicker="先定义边界与口径，再围绕关键业务链路逐步实现")
    rect(s,0.95,1.98,5.25,3.74,NAVY,NAVY,radius=True); add_text(s,1.28,2.25,4.5,0.25,"研究方法",12,GOLD,True); steps=[("STEP 1","需求与设计文档先行"),("STEP 2","按核心链路逐步实现"),("STEP 3","模块联调与问题闭环")]
    for i,(n,t) in enumerate(steps):
        y=2.86+i*0.82; circle(s,1.3,y,0.42,GOLD,n[-1],NAVY,11); add_text(s,1.92,y+0.03,3.85,0.25,t,14,WHITE,True); add_text(s,1.92,y+0.35,3.9,0.2,["明确边界、状态和算法口径","报名退费 → 排课调课 → 考勤课时","薪资核算 → 运营预警"][i],10.5,RGBColor(210,220,237))
    rect(s,6.55,1.98,5.78,3.74,WHITE,LINE,radius=True); add_text(s,6.88,2.25,4.8,0.25,"分层验证",12,NAVY,True); tests=["单元测试：后端核心纯逻辑","组件测试：前端关键组件","接口验收：契约与业务链路","浏览器 UI：真实部署验收","五角色权限：授权 / 越权矩阵","容器化上线：生产稳定运行"]
    for i,t in enumerate(tests):
        x=6.88+(i%2)*2.58; y=2.82+(i//2)*0.78; rect(s,x,y,2.32,0.48,RGBColor(245,247,251),LINE,radius=True); add_text(s,x+0.1,y+0.1,2.1,0.2,t,9.8,NAVY,True,PP_ALIGN.CENTER,MSO_ANCHOR.MIDDLE)
    rect(s,0.95,6.05,11.38,0.48,RGBColor(255,250,238),RGBColor(236,219,166),radius=True); add_text(s,1.15,6.17,10.98,0.2,"验证目标：从“功能可用”走到“规则正确、权限可靠、上线可复现”",11.5,NAVY,True,PP_ALIGN.CENTER,MSO_ANCHOR.MIDDLE)


def add_feasible(prs):
    s=prs.slides.add_slide(prs.slide_layouts[6]); base(s,"05 研究方法与可行性","可行性分析：技术、经济、操作三方面均可行",16,kicker="成熟技术栈 + 清晰业务边界 + 分层验证方案")
    items=[("技","技术可行性","框架与组件成熟开源；团队具备开发经验；四类关键问题有工程机制可落地",BLUE),("经","经济可行性","开源技术栈与容器化部署，不依赖商业授权，成本可控",TEAL),("操","操作可行性","后台承载复杂管理，移动端承载高频轻量场景，终端形态匹配岗位习惯",GOLD)]
    for i,(n,t,b,c) in enumerate(items):
        x=0.95+i*3.78; rect(s,x,2.0,3.42,3.28,WHITE,LINE,radius=True); circle(s,x+0.28,2.32,0.6,c,n,WHITE,16); add_text(s,x+1.05,2.39,2.0,0.28,t,16,NAVY,True); add_text(s,x+0.28,3.22,2.8,1.05,b,12,MUTED)
    rect(s,0.95,5.85,11.38,0.58,NAVY,NAVY,radius=True); add_text(s,1.15,6.01,10.98,0.25,"结论：具备进入设计与开发阶段的条件",14,WHITE,True,PP_ALIGN.CENTER,MSO_ANCHOR.MIDDLE)


def add_schedule(prs):
    s=prs.slides.add_slide(prs.slide_layouts[6]); base(s,"06 进度安排","五个阶段：从定题到毕业答辩",17,kicker="自 2026.07.02 定题；当前编码联调主体完成，后续进入测试与论文阶段")
    stages=[("01","选题定题与可行性研究","07.02 — 07.31",BLUE),("02","需求分析与系统设计","08.01 — 08.31",TEAL),("03","编码实现与模块联调","09.01 — 10.31",NAVY_2),("04","系统测试与部署上线","11.01 — 12.31",GOLD),("05","论文撰写与毕业答辩","2027.01 — 05.31",RED)]
    line(s,1.45,3.55,11.87,3.55,LINE,2.5)
    for i,(n,t,d,c) in enumerate(stages):
        x=1.0+i*2.45; circle(s,x,3.24,0.62,c,n,WHITE,11); add_text(s,x-0.27,2.27,1.18,0.4,t,12,NAVY,True,PP_ALIGN.CENTER); add_text(s,x-0.38,4.08,1.4,0.3,d,10,c,True,PP_ALIGN.CENTER); add_text(s,x-0.4,4.63,1.45,0.56,["明确边界\n完成开题","确定表结构\n接口与算法","核心模块\n逐步联调","分层测试\n容器上线","论文定稿\n准备答辩"][i],10,MUTED,align=PP_ALIGN.CENTER)
    rect(s,0.95,6.05,11.38,0.45,RGBColor(255,250,238),RGBColor(236,219,166),radius=True); add_text(s,1.15,6.16,10.98,0.2,"毕业答辩时间以学院安排为准",10.5,NAVY,True,PP_ALIGN.CENTER,MSO_ANCHOR.MIDDLE)


def add_deliverables(prs):
    s=prs.slides.add_slide(prs.slide_layouts[6]); base(s,"07 预期成果","预期成果：对照任务书逐项交付",18,kicker="交付目标不是一份演示稿，而是一套可运行、可复现、可验收的成果包")
    ds=[("完整可运行系统","管理后台 + 移动端 H5 + 后端 + 数据库",BLUE),("数据库脚本","建表脚本 / 演示数据 / 一键重置",TEAL),("系统设计文档","可行性、需求、概要 / 详细设计",NAVY_2),("测试报告","单元、接口、权限与界面验收记录",GOLD),("毕业论文","符合学院本科毕业设计规范",RED),("答辩材料","使用说明书、PPT、演示动线与数据",BLUE)]
    for i,(t,b,c) in enumerate(ds):
        x=0.95+(i%3)*3.78; y=1.98+(i//3)*1.78; rect(s,x,y,3.42,1.45,WHITE,LINE,radius=True); circle(s,x+0.25,y+0.27,0.45,c,f"0{i+1}",WHITE,10); add_text(s,x+0.88,y+0.24,2.18,0.28,t,14,NAVY,True); add_text(s,x+0.25,y+0.82,2.82,0.34,b,10.5,MUTED)
    rect(s,0.95,5.95,11.38,0.55,NAVY,NAVY,radius=True); add_text(s,1.15,6.1,10.98,0.22,"最终目标：从需求分析、设计实现、测试验收，到生产上线的完整交付",11.5,WHITE,True,PP_ALIGN.CENTER,MSO_ANCHOR.MIDDLE)


def add_closing(prs):
    s=prs.slides.add_slide(prs.slide_layouts[6]); s.background.fill.solid(); s.background.fill.fore_color.rgb=NAVY; rect(s,0,0,4.35,H,NAVY_2); rect(s,4.35,0,0.08,H,GOLD); rect(s,0,0,W,0.1,GOLD)
    add_text(s,0.82,0.82,3.1,0.24,"开题答辩 · 汇报结束",11,GOLD,True)
    add_text(s,4.92,2.08,6.9,0.42,"以上是我的开题汇报",17,RGBColor(209,219,236),True)
    add_text(s,4.92,2.78,6.9,0.82,"恳请各位老师\n批评指正",30,WHITE,True)
    line(s,4.95,4.24,11.65,4.24,RGBColor(89,110,159),1.1)
    add_text(s,4.95,4.52,6.5,0.3,"谢谢聆听 · 敬请指导",16,GOLD,True)
    add_text(s,0.82,6.78,3.1,0.2,"艺培通：让教务管理回到业务本身",10,RGBColor(201,214,235))


def build():
    prs = Presentation()
    prs.slide_width = Inches(W); prs.slide_height = Inches(H)
    add_title_slide(prs); add_agenda(prs); add_background(prs); add_pain(prs); add_significance(prs)
    add_status(prs); add_gap(prs); add_roles(prs); add_function_arch(prs); add_keytech(prs)
    add_techstack(prs); add_layers(prs); add_db(prs); add_algorithms(prs); add_method(prs)
    add_feasible(prs); add_schedule(prs); add_deliverables(prs); add_closing(prs)
    prs.save(OUT)
    print(OUT)


if __name__ == "__main__":
    build()
