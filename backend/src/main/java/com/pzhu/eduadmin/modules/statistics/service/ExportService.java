package com.pzhu.eduadmin.modules.statistics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pzhu.eduadmin.modules.finance.entity.LessonFlow;
import com.pzhu.eduadmin.modules.finance.entity.PaymentRecord;
import com.pzhu.eduadmin.modules.finance.mapper.LessonFlowMapper;
import com.pzhu.eduadmin.modules.finance.mapper.PaymentRecordMapper;
import com.pzhu.eduadmin.modules.risk.dto.RiskStudentVO;
import com.pzhu.eduadmin.modules.risk.service.RiskWarningService;
import com.pzhu.eduadmin.modules.salary.entity.TeacherSalary;
import com.pzhu.eduadmin.modules.salary.mapper.TeacherSalaryMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExportService {

    private final PaymentRecordMapper paymentRecordMapper;
    private final LessonFlowMapper lessonFlowMapper;
    private final TeacherSalaryMapper teacherSalaryMapper;
    private final RiskWarningService riskWarningService;

    public void exportPayments(HttpServletResponse response, String startDate, String endDate) throws IOException {
        validateDateFormat(startDate, "startDate");
        validateDateFormat(endDate, "endDate");
        List<PaymentRecord> records = paymentRecordMapper.selectList(
                new LambdaQueryWrapper<PaymentRecord>()
                        .ge(startDate != null, PaymentRecord::getPayTime, startDate != null ? startDate + " 00:00:00" : null)
                        .lt(endDate != null, PaymentRecord::getPayTime, endDate != null ? java.time.LocalDate.parse(endDate).plusDays(1) + " 00:00:00" : null)
                        .orderByDesc(PaymentRecord::getPayTime));

        setResponseHeader(response, "收费台账.xlsx");
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100); OutputStream os = response.getOutputStream()) {
            Sheet sheet = wb.createSheet("收费台账");
            String[] headers = {"ID", "报名ID", "学员ID", "课程ID", "课时数", "金额", "支付方式", "缴费时间", "备注"};
            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = headerStyle(wb);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            for (int i = 0; i < records.size(); i++) {
                PaymentRecord r = records.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(r.getId());
                row.createCell(1).setCellValue(r.getEnrollmentId());
                row.createCell(2).setCellValue(r.getStudentId());
                row.createCell(3).setCellValue(r.getCourseId());
                row.createCell(4).setCellValue(r.getLessonCount() != null ? r.getLessonCount().doubleValue() : 0);
                row.createCell(5).setCellValue(r.getAmount() != null ? r.getAmount().doubleValue() : 0);
                // H15 fix: 防止 payType 为 null 时自动拆箱 NPE
                Integer pt = r.getPayType();
                row.createCell(6).setCellValue(pt != null && pt == 1 ? "现金" : pt != null && pt == 2 ? "模拟支付" : "其他");
                row.createCell(7).setCellValue(r.getPayTime() != null ? r.getPayTime().toString() : "");
                row.createCell(8).setCellValue(r.getRemark() != null ? r.getRemark() : "");
            }
            wb.write(os);
            wb.dispose();
        }
    }

    public void exportLessonFlows(HttpServletResponse response, String startDate, String endDate) throws IOException {
        validateDateFormat(startDate, "startDate");
        validateDateFormat(endDate, "endDate");
        List<LessonFlow> records = lessonFlowMapper.selectList(
                new LambdaQueryWrapper<LessonFlow>()
                        .ge(startDate != null, LessonFlow::getCreateTime, startDate != null ? startDate + " 00:00:00" : null)
                        .lt(endDate != null, LessonFlow::getCreateTime, endDate != null ? java.time.LocalDate.parse(endDate).plusDays(1) + " 00:00:00" : null)
                        .orderByDesc(LessonFlow::getCreateTime));

        setResponseHeader(response, "课时消耗报表.xlsx");
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100); OutputStream os = response.getOutputStream()) {
            Sheet sheet = wb.createSheet("课时消耗");
            String[] headers = {"ID", "学员ID", "来源类型", "变动课时", "变动前", "变动后", "备注", "时间"};
            Row headerRow = sheet.createRow(0);
            CellStyle hs = headerStyle(wb);
            for (int i = 0; i < headers.length; i++) { Cell c = headerRow.createCell(i); c.setCellValue(headers[i]); c.setCellStyle(hs); }

            String[] sourceTypes = {"", "充值", "消费", "回冲", "退费"};
            for (int i = 0; i < records.size(); i++) {
                LessonFlow r = records.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(r.getId());
                row.createCell(1).setCellValue(r.getStudentId());
                int st = r.getSourceType() != null ? r.getSourceType() : 0;
                row.createCell(2).setCellValue(st >= 1 && st <= 4 ? sourceTypes[st] : "其他");
                row.createCell(3).setCellValue(r.getChangeAmount() != null ? r.getChangeAmount().doubleValue() : 0);
                row.createCell(4).setCellValue(r.getBeforeBalance() != null ? r.getBeforeBalance().doubleValue() : 0);
                row.createCell(5).setCellValue(r.getAfterBalance() != null ? r.getAfterBalance().doubleValue() : 0);
                row.createCell(6).setCellValue(r.getRemark() != null ? r.getRemark() : "");
                row.createCell(7).setCellValue(r.getCreateTime() != null ? r.getCreateTime().toString() : "");
            }
            wb.write(os);
            wb.dispose();
        }
    }

    public void exportSalaries(HttpServletResponse response, String month) throws IOException {
        if (month != null && !month.isBlank()) {
            try {
                java.time.YearMonth.parse(month, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
            } catch (java.time.format.DateTimeParseException e) {
                throw new com.pzhu.eduadmin.common.BusinessException(400, "month 日期格式不正确，应为 yyyy-MM");
            }
        }
        // L1 fix: 空白 month 视为"全部月份"，避免查询条件变成 salary_month='' 导出空表
        boolean hasMonth = month != null && !month.isBlank();
        List<TeacherSalary> records = teacherSalaryMapper.selectList(
                new LambdaQueryWrapper<TeacherSalary>()
                        .eq(hasMonth, TeacherSalary::getSalaryMonth, month)
                        .orderByDesc(TeacherSalary::getSalaryMonth));

        setResponseHeader(response, "薪资结算单.xlsx");
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100); OutputStream os = response.getOutputStream()) {
            Sheet sheet = wb.createSheet("薪资结算");
            // M2 fix: 补充"代课金额"列，否则 基础工资+奖金≠应发工资，薪资单无法对账
            String[] headers = {"ID", "教师ID", "月份", "主讲课时", "代课课时", "基础工资", "代课金额", "奖金", "应发工资", "状态", "核算时间"};
            Row headerRow = sheet.createRow(0);
            CellStyle hs = headerStyle(wb);
            for (int i = 0; i < headers.length; i++) { Cell c = headerRow.createCell(i); c.setCellValue(headers[i]); c.setCellStyle(hs); }

            // M1 fix: status=3 为"已发放"，原数组该位置为空导致已发放薪资导出为空白单元格
            String[] statusLabels = {"", "待确认", "已确认", "已发放", "已撤销"};
            for (int i = 0; i < records.size(); i++) {
                TeacherSalary r = records.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(r.getId());
                row.createCell(1).setCellValue(r.getTeacherId());
                row.createCell(2).setCellValue(r.getSalaryMonth());
                row.createCell(3).setCellValue(r.getLessonCount() != null ? r.getLessonCount().doubleValue() : 0);
                row.createCell(4).setCellValue(r.getSubstituteCount() != null ? r.getSubstituteCount().doubleValue() : 0);
                row.createCell(5).setCellValue(r.getBaseAmount() != null ? r.getBaseAmount().doubleValue() : 0);
                row.createCell(6).setCellValue(r.getSubstituteAmount() != null ? r.getSubstituteAmount().doubleValue() : 0);
                row.createCell(7).setCellValue(r.getBonusAmount() != null ? r.getBonusAmount().doubleValue() : 0);
                row.createCell(8).setCellValue(r.getTotalAmount() != null ? r.getTotalAmount().doubleValue() : 0);
                int st = r.getStatus() != null ? r.getStatus() : 0;
                row.createCell(9).setCellValue(st >= 1 && st <= 4 ? statusLabels[st] : "");
                row.createCell(10).setCellValue(r.getCalcSnapshotTime() != null ? r.getCalcSnapshotTime().toString() : "");
            }
            wb.write(os);
            wb.dispose();
        }
    }

    public void exportRiskStudents(HttpServletResponse response, String level,
                                   Long classId, Long courseId) throws IOException {
        List<RiskStudentVO> records = riskWarningService.listRiskWarnings(level, classId, courseId, null, null);
        setResponseHeader(response, "流失预警名单.xlsx");
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100); OutputStream os = response.getOutputStream()) {
            Sheet sheet = wb.createSheet("流失预警名单");
            String[] headers = {"学员ID", "学员姓名", "班级", "课程", "风险分", "风险档位",
                    "最近到课", "28天缺勤/考勤", "缺勤率", "剩余课时", "总课时", "到期日",
                    "建议动作", "跟进状态"};
            Row headerRow = sheet.createRow(0);
            CellStyle hs = headerStyle(wb);
            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(hs);
            }
            String[] levels = {"", "低风险", "中风险", "高风险"};
            String[] followStates = {"待跟进", "已跟进", "暂不跟进"};
            for (int i = 0; i < records.size(); i++) {
                RiskStudentVO r = records.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(r.getStudentId() != null ? r.getStudentId() : 0L);
                row.createCell(1).setCellValue(r.getStudentName() != null ? r.getStudentName() : "");
                row.createCell(2).setCellValue(r.getClassName() != null ? r.getClassName() : "");
                row.createCell(3).setCellValue(r.getCourseName() != null ? r.getCourseName() : "");
                row.createCell(4).setCellValue(r.getRiskScore() != null ? r.getRiskScore() : 0);
                String lv = r.getRiskLevel();
                row.createCell(5).setCellValue("HIGH".equals(lv) ? levels[3]
                        : "MEDIUM".equals(lv) ? levels[2] : "LOW".equals(lv) ? levels[1] : "");
                row.createCell(6).setCellValue(r.getLastAttendDate() != null ? r.getLastAttendDate().toString() : "");
                row.createCell(7).setCellValue((r.getAbsentCount28d() != null ? r.getAbsentCount28d() : 0)
                        + "/" + (r.getScheduledCount28d() != null ? r.getScheduledCount28d() : 0));
                row.createCell(8).setCellValue(r.getAbsenceRate28d() != null ? r.getAbsenceRate28d() : 0.0);
                row.createCell(9).setCellValue(r.getRemainingLessons() != null ? r.getRemainingLessons().doubleValue() : 0);
                row.createCell(10).setCellValue(r.getTotalLessons() != null ? r.getTotalLessons().doubleValue() : 0);
                row.createCell(11).setCellValue(r.getExpireDate() != null ? r.getExpireDate().toString() : "");
                row.createCell(12).setCellValue(r.getSuggestedAction() != null ? r.getSuggestedAction() : "");
                Integer fs = r.getFollowUpStatus();
                row.createCell(13).setCellValue(fs != null && fs >= 1 && fs <= 2 ? followStates[fs] : followStates[0]);
            }
            wb.write(os);
            wb.dispose();
        }
    }

    private void setResponseHeader(HttpServletResponse response, String filename) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8));
    }

    private CellStyle headerStyle(SXSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    /** L4: 校验日期字符串格式是否为 yyyy-MM-dd */
    private void validateDateFormat(String dateStr, String paramName) {
        if (dateStr == null || dateStr.isBlank()) return;
        try {
            java.time.LocalDate.parse(dateStr, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (java.time.format.DateTimeParseException e) {
            throw new com.pzhu.eduadmin.common.BusinessException(400, paramName + " 日期格式不正确，应为 yyyy-MM-dd");
        }
    }
}
