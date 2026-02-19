package com.example.payroll_project.service;

import com.example.payroll_project.model.Employee;
import com.example.payroll_project.model.PayPeriod;
import com.example.payroll_project.model.PayrollRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Payslip Generator Service (CR6, F5)
 *
 * Generates individual CSV payslips and an optional consolidated summary report.
 * Output directory: payslips/<periodName>/
 */
public class PayslipGeneratorService {

    private static final Logger logger = LoggerFactory.getLogger(PayslipGeneratorService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy");

    // -----------------------------------------------------------------------

    /**
     * Generate a single employee payslip CSV.
     *
     * @param employee      employee data
     * @param payPeriod     the pay period
     * @param payroll       computed payroll record
     * @param outputDir     folder to write to (created if absent)
     * @return path of the generated file
     */
    public String generatePayslip(Employee employee, PayPeriod payPeriod,
                                   PayrollRecord payroll, String outputDir) throws IOException {

        Files.createDirectories(Paths.get(outputDir));

        String filename = sanitize(employee.getEmployeeCode()) + "_"
                        + sanitize(payPeriod.getPeriodName()) + "_payslip.csv";
        Path out = Paths.get(outputDir, filename);

        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(out.toFile()), StandardCharsets.UTF_8))) {

            // Header block
            pw.println("KC-02N PAYROLL SYSTEM - PAYSLIP");
            pw.println(",,");
            row(pw, "Employee Code",  employee.getEmployeeCode());
            row(pw, "Employee Name",  employee.getFullName());
            row(pw, "Position",       employee.getPosition());
            row(pw, "Department",     nvl(employee.getDepartment()));
            pw.println(",,");
            row(pw, "Pay Period",     payPeriod.getPeriodName());
            row(pw, "Period Start",   fmt(payPeriod.getStartDate()));
            row(pw, "Period End",     fmt(payPeriod.getEndDate()));
            row(pw, "Pay Date",       payPeriod.getPayDate() != null
                                         ? fmt(payPeriod.getPayDate()) : "TBD");
            pw.println(",,");

            // Attendance summary
            pw.println("--- ATTENDANCE SUMMARY ---,,");
            row(pw, "Days Worked",    payroll.getDaysWorked());
            row(pw, "Days Absent",    payroll.getDaysAbsent());
            row(pw, "Regular Hours",  fmt2(payroll.getTotalRegularHours()));
            row(pw, "Overtime Hours", fmt2(payroll.getTotalOvertimeHours()));
            row(pw, "Night Diff Hrs", fmt2(payroll.getTotalNightDiffHours()));
            row(pw, "Holiday Hours",  fmt2(payroll.getTotalHolidayHours()));
            row(pw, "Late (minutes)", payroll.getTotalLateMinutes());
            row(pw, "Undertime (min)",payroll.getTotalUndertimeMinutes());
            pw.println(",,");

            // Earnings
            pw.println("--- EARNINGS ---,,");
            row(pw, "Basic Pay",       money(payroll.getBasicPay()));
            row(pw, "Overtime Pay",    money(payroll.getOvertimePay()));
            row(pw, "Night Diff Pay",  money(payroll.getNightDiffPay()));
            row(pw, "Holiday Pay",     money(payroll.getHolidayPay()));
            if (payroll.getTotalAllowances().compareTo(BigDecimal.ZERO) > 0) {
                row(pw, "Total Allowances", money(payroll.getTotalAllowances()));
            }
            row(pw, "GROSS PAY",      money(payroll.getGrossPay()));
            pw.println(",,");

            // Deductions
            pw.println("--- DEDUCTIONS ---,,");
            row(pw, "SSS Contribution",      money(payroll.getSssContribution()));
            row(pw, "PhilHealth Contribution",money(payroll.getPhilhealthContribution()));
            row(pw, "Pag-IBIG Contribution", money(payroll.getPagibigContribution()));
            row(pw, "Withholding Tax (BIR)", money(payroll.getWithholdingTax()));
            if (payroll.getTotalOtherDeductions().compareTo(BigDecimal.ZERO) > 0) {
                row(pw, "Other Deductions",  money(payroll.getTotalOtherDeductions()));
            }
            row(pw, "TOTAL DEDUCTIONS",      money(payroll.getTotalDeductions()));
            pw.println(",,");

            // Net pay
            pw.println("--- NET PAY ---,,");
            row(pw, "NET PAY", money(payroll.getNetPay()));
            pw.println(",,");

            // Government IDs
            pw.println("--- GOVERNMENT IDs ---,,");
            row(pw, "SSS Number",      nvl(employee.getSssNumber()));
            row(pw, "PhilHealth No.",  nvl(employee.getPhilhealthNumber()));
            row(pw, "Pag-IBIG No.",    nvl(employee.getPagibigNumber()));
            row(pw, "TIN",             nvl(employee.getTin()));
            pw.println(",,");
            pw.println("Generated by KC-02N Payroll System,,");
        }

        logger.info("Payslip generated: {}", out);
        return out.toString();
    }

    /**
     * Generate payslips for all employees and a consolidated summary CSV.
     *
     * @return list of generated file paths
     */
    public List<String> generateBatch(List<Employee> employees,
                                       PayPeriod payPeriod,
                                       List<PayrollRecord> payrolls,
                                       String outputDir) throws IOException {

        List<String> generated = new java.util.ArrayList<>();

        // Individual payslips
        for (int i = 0; i < employees.size(); i++) {
            Employee e = employees.get(i);
            PayrollRecord pr = payrolls.stream()
                    .filter(p -> e.getEmployeeId().equals(p.getEmployeeId()))
                    .findFirst().orElse(null);
            if (pr != null) {
                generated.add(generatePayslip(e, payPeriod, pr, outputDir));
            }
        }

        // Consolidated summary
        generated.add(generateSummary(employees, payPeriod, payrolls, outputDir));

        return generated;
    }

    // -----------------------------------------------------------------------

    private String generateSummary(List<Employee> employees,
                                    PayPeriod payPeriod,
                                    List<PayrollRecord> payrolls,
                                    String outputDir) throws IOException {

        String filename = "PAYROLL_SUMMARY_" + sanitize(payPeriod.getPeriodName()) + ".csv";
        Path out = Paths.get(outputDir, filename);

        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(out.toFile()), StandardCharsets.UTF_8))) {

            pw.println("KC-02N PAYROLL SYSTEM - PAYROLL SUMMARY");
            pw.printf("Pay Period: %s (%s to %s)%n",
                    payPeriod.getPeriodName(),
                    fmt(payPeriod.getStartDate()),
                    fmt(payPeriod.getEndDate()));
            pw.println();
            pw.println("Emp Code,Employee Name,Position,Days Worked,Regular Hrs,OT Hrs,"
                     + "Basic Pay,OT Pay,Night Diff,Holiday Pay,Gross Pay,"
                     + "SSS,PhilHealth,Pag-IBIG,Tax,Total Deductions,NET PAY");

            BigDecimal totalGross = BigDecimal.ZERO;
            BigDecimal totalNet   = BigDecimal.ZERO;

            for (Employee e : employees) {
                PayrollRecord pr = payrolls.stream()
                        .filter(p -> e.getEmployeeId().equals(p.getEmployeeId()))
                        .findFirst().orElse(null);
                if (pr == null) continue;

                pw.printf("%s,%s,%s,%d,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                        csv(e.getEmployeeCode()), csv(e.getFullName()), csv(e.getPosition()),
                        pr.getDaysWorked(),
                        fmt2(pr.getTotalRegularHours()), fmt2(pr.getTotalOvertimeHours()),
                        money(pr.getBasicPay()), money(pr.getOvertimePay()),
                        money(pr.getNightDiffPay()), money(pr.getHolidayPay()),
                        money(pr.getGrossPay()),
                        money(pr.getSssContribution()), money(pr.getPhilhealthContribution()),
                        money(pr.getPagibigContribution()), money(pr.getWithholdingTax()),
                        money(pr.getTotalDeductions()), money(pr.getNetPay()));

                totalGross = totalGross.add(pr.getGrossPay());
                totalNet   = totalNet.add(pr.getNetPay());
            }

            pw.printf(",,,,,,,,,,TOTAL GROSS: %s,,,,,TOTAL NET: %s%n",
                    money(totalGross), money(totalNet));
        }

        logger.info("Summary report generated: {}", out);
        return out.toString();
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    private void row(PrintWriter pw, String label, Object value) {
        pw.printf("%s,%s,%n", csv(label), csv(String.valueOf(value)));
    }

    private String fmt(LocalDate d)     { return d != null ? d.format(DATE_FMT) : ""; }
    private String fmt2(BigDecimal bd)  { return bd != null ? bd.toPlainString() : "0.00"; }
    private String money(BigDecimal bd) { return bd != null ? String.format("%.2f", bd) : "0.00"; }
    private String nvl(String s)        { return s != null ? s : ""; }

    private String csv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private String sanitize(String name) {
        if (name == null) return "unknown";
        return name.replaceAll("[^A-Za-z0-9_\\-]", "_");
    }
}
