package jp.co.sss.lms.form;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 日次の勤怠フォーム
 * 
 * @author 東京ITスクール
 */
@Data
public class DailyAttendanceForm {

	/** 受講生勤怠ID */
	private Integer studentAttendanceId;
	/** 途中退校日 */
	private String leaveDate;
	/** 日付 */
	private String trainingDate;
	/** 出勤時間 */
	private String trainingStartTime;
	// 角田智哉 -task26 出勤時間の「時間」のみ切り分け（画面表示用）
	private String trainingStartTimeHourValue;
	// 角田智哉 -task26 出勤時間の「時間」のみ切り分け（画面表示用）
	private String trainingStartTimeMinuteValue;
	// 角田智哉 -task26 出勤時間の「時間」のみ切り分け
	private Integer trainingStartTimeHour;
	// 角田智哉 -task26 出勤時間の「時間」のみ切り分け
	private Integer trainingStartTimeMinute;
	/** 退勤時間 */
	private String trainingEndTime;
	// 角田智哉 -task26 退勤時間の「時間」のみ切り分け（画面表示用）
	private String trainingEndTimeHourValue;
	// 角田智哉 -task26 出勤時間の「分」のみ切り分け（画面表示用）
	private String trainingEndTimeMinuteValue;
	// 角田智哉 -task26 退勤時間の「時間」のみ切り分け
	private Integer trainingEndTimeHour;
	// 角田智哉 -task26 退勤時間の「分」のみ切り分け
	private Integer trainingEndTimeMinute;
	/** 中抜け時間 */
	private Integer blankTime;
	/** 中抜け時間（画面表示用） */
	private String blankTimeValue;
	/** ステータス */
	private String status;
	/** 備考 */
	// 角田 智哉 -Task.27
	@Size(max = 100)
	private String note;
	/** セクション名 */
	private String sectionName;
	/** 当日フラグ */
	private Boolean isToday;
	/** エラーフラグ */
	private Boolean isError;
	/** 日付（画面表示用） */
	private String dispTrainingDate;
	/** ステータス（画面表示用） */
	private String statusDispName;
	/** LMSユーザーID */
	private String lmsUserId;
	/** ユーザー名 */
	private String userName;
	/** コース名 */
	private String courseName;
	/** インデックス */
	private String index;

}
