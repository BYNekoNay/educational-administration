from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.enum.shapes import MSO_SHAPE
from pptx.dml.color import RGBColor


SOURCE = r"D:\codegitee\educational-administration\docs\defence\艺培通开题答辩_优化版.pptx"
IMAGE = r"G:\er-diagram-20261011-1329.png"
OUTPUT = r"D:\codegitee\educational-administration\docs\defence\艺培通开题答辩_优化版_数据库后含ER图.pptx"

NAVY = RGBColor(22, 43, 85)
GOLD = RGBColor(211, 167, 44)
MUTED = RGBColor(92, 105, 124)
WHITE = RGBColor(255, 255, 255)
LINE = RGBColor(220, 226, 235)
FONT = "Microsoft YaHei"


def add_text(slide, x, y, w, h, text, size, color, bold=False,
             align=PP_ALIGN.LEFT, valign=MSO_ANCHOR.TOP):
    box = slide.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    tf = box.text_frame
    tf.clear()
    tf.word_wrap = True
    tf.margin_left = Inches(0.03)
    tf.margin_right = Inches(0.03)
    tf.margin_top = Inches(0.02)
    tf.margin_bottom = Inches(0.02)
    tf.vertical_anchor = valign
    p = tf.paragraphs[0]
    p.alignment = align
    run = p.add_run()
    run.text = text
    run.font.name = FONT
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.color.rgb = color
    return box


def rect(slide, x, y, w, h, fill, line_color=None):
    shape = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(x), Inches(y), Inches(w), Inches(h))
    shape.fill.solid()
    shape.fill.fore_color.rgb = fill
    shape.line.color.rgb = line_color or fill
    shape.line.width = Pt(0.8)
    return shape


def append_er_slide():
    prs = Presentation(SOURCE)
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    slide.background.fill.solid()
    slide.background.fill.fore_color.rgb = RGBColor(248, 249, 252)

    # A compact title strip keeps the generated diagram as the visual focus.
    rect(slide, 0, 0, 13.333, 0.52, NAVY)
    rect(slide, 0, 0.52, 13.333, 0.05, GOLD)
    add_text(slide, 0.72, 0.09, 5.5, 0.28, "数据库设计 · 核心实体关系图（ER）", 17, WHITE, True, valign=MSO_ANCHOR.MIDDLE)
    add_text(slide, 10.25, 0.1, 2.35, 0.25, "精简核心链路 · 8 张核心表", 9.5, RGBColor(215, 224, 239), False, PP_ALIGN.RIGHT, MSO_ANCHOR.MIDDLE)

    # The source image is nearly square, so center it vertically and leave a clean margin.
    rect(slide, 3.02, 0.75, 7.3, 6.56, WHITE, LINE)
    slide.shapes.add_picture(IMAGE, Inches(3.08), Inches(0.8), width=Inches(7.18), height=Inches(6.45))
    add_text(slide, 0.72, 7.16, 7.0, 0.16, "艺培通 · 艺体培训机构全流程教务管理平台", 8.5, MUTED)
    add_text(slide, 11.55, 7.14, 0.8, 0.18, "14", 9, NAVY, True, PP_ALIGN.RIGHT)

    # Put the generated ER slide immediately after the original database design slide (slide 13).
    slide_ids = prs.slides._sldIdLst
    new_slide_id = slide_ids[-1]
    slide_ids.remove(new_slide_id)
    slide_ids.insert(13, new_slide_id)

    # Shift the existing footer page numbers after inserting the ER page.
    for index, current_slide in enumerate(prs.slides, start=1):
        for shape in current_slide.shapes:
            if not hasattr(shape, "text") or not shape.text.strip():
                continue
            if shape.left / 914400 > 11.0 and shape.top / 914400 > 6.8:
                if shape.text.strip().isdigit():
                    shape.text_frame.paragraphs[0].runs[0].text = str(index)
    prs.save(OUTPUT)
    print(OUTPUT)


if __name__ == "__main__":
    append_er_slide()
