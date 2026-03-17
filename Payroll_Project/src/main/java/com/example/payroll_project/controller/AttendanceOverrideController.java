package com.example.payroll_project.controller;

import com.example.payroll_project.dao.AttendanceDAO;
import com.example.payroll_project.model.AttendanceRecord;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Manual Override Dialog Controller (F10 – Manual Attendance Correction)
 *
 * Three override modes selectable via RadioButtons:
 *   1. Leave   – mark the record as a specific leave type
 *   2. Time    – correct Time In / Time Out values
 *   3. Holiday – toggle regular/special holiday flag
 */
public class AttendanceOverrideController {

    private static final Logger logger = LoggerFactory.getLogger(AttendanceOverrideController.class);
    private static final DateTimeFormatter T_FMT = DateTimeFormatter.ofPattern("HH:mm");


    @FXML private RadioButton modeLeave;
    @FXML private RadioButton modeTime;
    @FXML private RadioButton modeHoliday;


    @FXML private VBox leavePane;
    @FXML private ComboBox<AttendanceRecord.LeaveType> leaveTypeCombo;
    @FXML private Label leaveInfoLabel;

    @FXML private VBox timePane;
    @FXML private TextField timeIn1Field;
    @FXML private TextField timeOut1Field;
    @FXML private TextField timeIn2Field;
    @FXML private TextField timeOut2Field;

    @FXML private VBox holidayPane;
    @FXML private RadioButton holidayNone;
    @FXML private RadioButton holidayRegular;
    @FXML private RadioButton holidaySpecial;
    @FXML private RadioButton holidayRestDay;

    @FXML private Label errorLabel;
    @FXML private Button applyButton;
    @FXML private Button cancelButton;

    private AttendanceRecord record;
    private boolean saved = false;
    private final AttendanceDAO dao = new AttendanceDAO();

    @FXML
    public void initialize() {

        leaveTypeCombo.getItems().addAll(
            AttendanceRecord.LeaveType.SICK_LEAVE,
            AttendanceRecord.LeaveType.VACATION_LEAVE,
            AttendanceRecord.LeaveType.EMERGENCY_LEAVE,
            AttendanceRecord.LeaveType.MATERNITY_LEAVE,
            AttendanceRecord.LeaveType.PATERNITY_LEAVE,
            AttendanceRecord.LeaveType.OTHER
        );
        leaveTypeCombo.setValue(AttendanceRecord.LeaveType.SICK_LEAVE);

        ToggleGroup modeGroup = new ToggleGroup();
        modeLeave.setToggleGroup(modeGroup);
        modeTime.setToggleGroup(modeGroup);
        modeHoliday.setToggleGroup(modeGroup);
        modeGroup.selectedToggleProperty().addListener((obs, o, n) -> refreshPanes());
        modeLeave.setSelected(true);

        ToggleGroup holGroup = new ToggleGroup();
        holidayNone.setToggleGroup(holGroup);
        holidayRegular.setToggleGroup(holGroup);
        holidaySpecial.setToggleGroup(holGroup);
        holidayRestDay.setToggleGroup(holGroup);

        errorLabel.setVisible(false);
    }

    public void setRecord(AttendanceRecord r) {
        this.record = r;
        populateFields();
    }

    private void populateFields() {
        if (record == null) return;

        if (record.isOnLeave()) {
            leaveTypeCombo.setValue(record.getLeaveType());
            modeLeave.setSelected(true);
        }

        timeIn1Field.setText(record.getTimeIn1()  != null ? record.getTimeIn1().format(T_FMT)  : "");
        timeOut1Field.setText(record.getTimeOut1() != null ? record.getTimeOut1().format(T_FMT) : "");
        timeIn2Field.setText(record.getTimeIn2()  != null ? record.getTimeIn2().format(T_FMT)  : "");
        timeOut2Field.setText(record.getTimeOut2() != null ? record.getTimeOut2().format(T_FMT) : "");

        if (record.isHoliday() && !record.isRestDay()) holidayRegular.setSelected(true);
        else if (record.isRestDay())                         holidayRestDay.setSelected(true);
        else holidayNone.setSelected(true);

        leaveInfoLabel.setText("Date: " + record.getAttendanceDate()
            + "  |  Current status: " + record.getTimeSummary());
    }

    private void refreshPanes() {
        leavePane.setVisible(modeLeave.isSelected());
        leavePane.setManaged(modeLeave.isSelected());
        timePane.setVisible(modeTime.isSelected());
        timePane.setManaged(modeTime.isSelected());
        holidayPane.setVisible(modeHoliday.isSelected());
        holidayPane.setManaged(modeHoliday.isSelected());
        errorLabel.setVisible(false);
    }


    @FXML
    private void handleApply() {
        if (record == null) return;
        errorLabel.setVisible(false);

        try {
            if (modeLeave.isSelected()) {
                applyLeaveOverride();
            } else if (modeTime.isSelected()) {
                applyTimeOverride();
            } else if (modeHoliday.isSelected()) {
                applyHolidayOverride();
            }

            record.setManuallyEdited(true);
            dao.update(record);
            saved = true;
            close();
            logger.info("Manual override saved for attendance_id={}", record.getAttendanceId());

        } catch (Exception e) {
            logger.error("Override save failed", e);
            showError("Save failed: " + e.getMessage());
        }
    }

    private void applyLeaveOverride() {
        AttendanceRecord.LeaveType lt = leaveTypeCombo.getValue();
        if (lt == null) { showError("Please select a leave type."); throw new IllegalStateException(); }
        record.setLeaveType(lt);
        record.setAbsent(false);          // on leave ≠ absent (leave pays)
        record.setHasAnomaly(false);
        record.setAnomalyDescription(null);
        // Clear time entries – leave has no clock-in/out
        record.setTimeIn1(null); record.setTimeOut1(null);
        record.setTimeIn2(null); record.setTimeOut2(null);
    }

    private void applyTimeOverride() {
        LocalTime ti1 = parseTime(timeIn1Field.getText(),  "Time In");
        LocalTime to2 = parseTime(timeOut2Field.getText(), "Time Out");
        LocalTime to1 = parseTime(timeOut1Field.getText(), null);
        LocalTime ti2 = parseTime(timeIn2Field.getText(),  null);

        record.setTimeIn1(ti1);
        record.setTimeOut1(to1);
        record.setTimeIn2(ti2);
        record.setTimeOut2(to2);
        record.setAbsent(ti1 == null && to2 == null);
        record.setLeaveType(AttendanceRecord.LeaveType.NONE);

        // Re-run basic anomaly check after correction
        if (ti1 != null && to2 != null) {
            record.setHasAnomaly(false);
            record.setAnomalyDescription(null);
        }
    }

    private void applyHolidayOverride() {
        if (holidayRegular.isSelected()) {
            record.setHoliday(true);
            record.setRestDay(false);
        } else if (holidaySpecial.isSelected()) {
            record.setHoliday(true);
            record.setRestDay(false);
            record.setAnomalyDescription("Special Holiday");
        } else if (holidayRestDay.isSelected()) {
            record.setRestDay(true);
            record.setHoliday(false);
        } else {
            record.setHoliday(false);
            record.setRestDay(false);
        }
        record.setLeaveType(AttendanceRecord.LeaveType.NONE);
    }

    /** Returns null for empty/blank strings; throws for invalid format. */
    private LocalTime parseTime(String text, String fieldName) {
        if (text == null || text.isBlank()) return null;
        try {
            String t = text.trim();
            if (t.length() == 4 && !t.contains(":")) t = t.substring(0,2) + ":" + t.substring(2);
            return LocalTime.parse(t, T_FMT);
        } catch (Exception e) {
            if (fieldName != null) {
                showError(fieldName + " must be in HH:mm format (e.g. 08:30).");
                throw new IllegalArgumentException("Invalid time: " + text);
            }
            return null;
        }
    }

    @FXML
    private void handleCancel() { close(); }

    private void close() {
        Stage s = (Stage) applyButton.getScene().getWindow();
        s.close();
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
    }

    public boolean isSaved() { return saved; }
}
