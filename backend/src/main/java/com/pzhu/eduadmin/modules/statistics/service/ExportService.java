package com.pzhu.eduadmin.modules.statistics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pzhu.eduadmin.modules.finance.entity.LessonFlow;
import com.pzhu.eduadmin.modules.finance.entity.PaymentRecord;
import com.pzhu.eduadmin.modules.finance.mapper.LessonFlowMapper;
import com.pzhu.eduadmin.modules.finance.mapper.PaymentRecordMapper;
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

    public void exportPayments(HttpServletResponse response, String startDate, String endDate) throws IOException {
        List<PaymentRecord> records = paymentRecordMapper.selectList(
                new LambdaQueryWrapper<PaymentRecord>()
                        .ge(startDate != null, PaymentRecord::getPayTime, startDate != null ? startDate + " 00:00:00" : null)
                        .le(endDate != null, PaymentRecord::getPayTime, endDate != null ? endDate + " 23:59:59" : null)
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
                row.createCell(6).setCellValue(r.getPayType() == 1 ? "现金" : r.getPayType() == 2 ? "模拟支付" : "其他");
                row.createCell(7).setCellValue(r.getPayTime() != null ? r.getPayTime().toString() : "");
                row.createCell(8).setCellValue(r.getRemark() != null ? r.getRemark() : "");
            }
            wb.write(os);
            wb.dispose();
        }
    }

    public void exportLessonFlows(HttpServletResponse response, String startDate, String endDate) throws IOException {
        List<LessonFlow> records = lessonFlowMapper.selectList(
                new LambdaQueryWrapper<LessonFlow>()
                        .ge(startDate != null, LessonFlow::getCreateTime, startDate != null ? startDate + " 00:00:00" : null)
                        .le(endDate != null, LessonFlow::getCreateTime, endDate != null ? endDate + " 23:59:59" : null)
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
        List<TeacherSalary> records = teacherSalaryMapper.selectList(
                new LambdaQueryWrapper<TeacherSalary>()
                        .eq(month != null, TeacherSalary::getSalaryMonth, month)
                        .orderByDesc(TeacherSalary::getSalaryMonth));

        setResponseHeader(response, "薪资结算单.xlsx");
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100); OutputStream os = response.getOutputStream()) {
            Sheet sheet = wb.createSheet("薪资结算");
            String[] headers = {"ID", "教师ID", "月份", "主讲课时", "代课课时", "基础工资", "奖金", "应发工资", "状态", "核算时间"};
            Row headerRow = sheet.createRow(0);
            CellStyle hs = headerStyle(wb);
            for (int i = 0; i < headers.length; i++) { Cell c = headerRow.createCell(i); c.setCellValue(headers[i]); c.setCellStyle(hs); }

            String[] statusLabels = {"", "待确认", "已确认", "", "已撤销"};
            for (int i = 0; i < records.size(); i++) {
                TeacherSalary r = records.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(r.getId());
                row.createCell(1).setCellValue(r.getTeacherId());
                row.createCell(2).setCellValue(r.getSalaryMonth());
                row.createCell(3).setCellValue(r.getLessonCount() != null ? r.getLessonCount().doubleValue() : 0);
                row.createCell(4).setCellValue(r.getSubstituteCount() != null ? r.getSubstituteCount().doubleValue() : 0);
                row.createCell(5).setCellValue(r.getBaseAmount() != null ? r.getBaseAmount().doubleValue() : 0);
                row.createCell(6).setCellValue(r.getBonusAmount() != null ? r.getBonusAmount().doubleValue() : 0);
                row.createCell(7).setCellValue(r.getTotalAmount() != null ? r.getTotalAmount().doubleValue() : 0);
                int st = r.getStatus() != null ? r.getStatus() : 0;
                row.createCell(8).setCellValue(st >= 1 && st <= 4 ? statusLabels[st] : "");
                row.createCell(9).setCellValue(r.getCalcSnapshotTime() != null ? r.getCalcSnapshotTime().toString() : "");
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
}
