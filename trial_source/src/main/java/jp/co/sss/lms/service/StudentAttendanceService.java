package jp.co.sss.lms.service;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import jp.co.sss.lms.dto.AttendanceManagementDto;
import jp.co.sss.lms.dto.LoginUserDto;
import jp.co.sss.lms.entity.TStudentAttendance;
import jp.co.sss.lms.enums.AttendanceStatusEnum;
import jp.co.sss.lms.form.AttendanceForm;
import jp.co.sss.lms.form.DailyAttendanceForm;
import jp.co.sss.lms.mapper.TStudentAttendanceMapper;
import jp.co.sss.lms.util.AttendanceUtil;
import jp.co.sss.lms.util.Constants;
import jp.co.sss.lms.util.DateUtil;
import jp.co.sss.lms.util.LoginUserUtil;
import jp.co.sss.lms.util.MessageUtil;
import jp.co.sss.lms.util.TrainingTime;

/**
 * 勤怠情報（受講生入力）サービス
 *
 * @author 東京ITスクール
 */
@Service
public class StudentAttendanceService {

	@Autowired
	private DateUtil dateUtil;
	@Autowired
	private AttendanceUtil attendanceUtil;
	@Autowired
	private MessageUtil messageUtil;
	@Autowired
	private LoginUserUtil loginUserUtil;
	@Autowired
	private LoginUserDto loginUserDto;
	@Autowired
	private TStudentAttendanceMapper tStudentAttendanceMapper;

	/**
	 * 勤怠一覧情報取得
	 *
	 * @param courseId
	 * @param lmsUserId
	 * @return 勤怠管理画面用DTOリスト
	 */
	public List<AttendanceManagementDto> getAttendanceManagement(Integer courseId, Integer lmsUserId) {

		// 勤怠管理リストの取得
		List<AttendanceManagementDto> attendanceManagementDtoList = tStudentAttendanceMapper
				.getAttendanceManagement(courseId, lmsUserId, Constants.DB_FLG_FALSE);
		for (AttendanceManagementDto dto : attendanceManagementDtoList) {
			// 中抜け時間を設定
			if (dto.getBlankTime() != null) {
				TrainingTime blankTime = attendanceUtil.calcBlankTime(dto.getBlankTime());
				dto.setBlankTimeValue(String.valueOf(blankTime));
			}
			// 遅刻早退区分判定
			AttendanceStatusEnum statusEnum = AttendanceStatusEnum.getEnum(dto.getStatus());
			if (statusEnum != null) {
				dto.setStatusDispName(statusEnum.name);
			}
		}

		return attendanceManagementDtoList;
	}

	/**
	 * 出退勤更新前のチェック
	 *
	 * @param attendanceType
	 * @return エラーメッセージ
	 */
	public String punchCheck(Short attendanceType) {
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 権限チェック
		if (!loginUserUtil.isStudent()) {
			return messageUtil.getMessage(Constants.VALID_KEY_AUTHORIZATION);
		}
		// 研修日チェック
		if (!attendanceUtil.isWorkDay(loginUserDto.getCourseId(), trainingDate)) {
			return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_NOTWORKDAY);
		}
		// 登録情報チェック
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate, Constants.DB_FLG_FALSE);
		switch (attendanceType) {
		case Constants.CODE_VAL_ATWORK:
			if (tStudentAttendance != null && !tStudentAttendance.getTrainingStartTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			break;
		case Constants.CODE_VAL_LEAVING:
			if (tStudentAttendance == null || tStudentAttendance.getTrainingStartTime().equals("")) {
				// 出勤情報がないため退勤情報を入力出来ません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY);
			}
			if (!tStudentAttendance.getTrainingEndTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			TrainingTime trainingStartTime = new TrainingTime(tStudentAttendance.getTrainingStartTime());
			TrainingTime trainingEndTime = new TrainingTime();
			if (trainingStartTime.compareTo(trainingEndTime) > 0) {
				// 退勤時刻は出勤時刻より後でなければいけません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE);
			}
			break;
		}
		return null;
	}

	/**
	 * 出勤ボタン処理
	 *
	 * @return 完了メッセージ
	 */
	public String setPunchIn() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 現在の研修時刻
		TrainingTime trainingStartTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime, null);
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate, Constants.DB_FLG_FALSE);
		if (tStudentAttendance == null) {
			// 登録処理
			tStudentAttendance = new TStudentAttendance();
			tStudentAttendance.setLmsUserId(loginUserDto.getLmsUserId());
			tStudentAttendance.setTrainingDate(trainingDate);
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setTrainingEndTime("");
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setNote("");
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setFirstCreateDate(date);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendance.setBlankTime(null);
			tStudentAttendanceMapper.insert(tStudentAttendance);
		} else {
			// 更新処理
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendanceMapper.update(tStudentAttendance);
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 退勤ボタン処理
	 *
	 * @return 完了メッセージ
	 */
	public String setPunchOut() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate, Constants.DB_FLG_FALSE);
		// 出退勤時刻
		TrainingTime trainingStartTime = new TrainingTime(tStudentAttendance.getTrainingStartTime());
		TrainingTime trainingEndTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime, trainingEndTime);
		// 更新処理
		tStudentAttendance.setTrainingEndTime(trainingEndTime.toString());
		tStudentAttendance.setStatus(attendanceStatusEnum.code);
		tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
		tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
		tStudentAttendance.setLastModifiedDate(date);
		tStudentAttendanceMapper.update(tStudentAttendance);
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 勤怠フォームへ設定
	 * 
	 * @param attendanceManagementDtoList
	 * @return 勤怠編集フォーム
	 */
	public AttendanceForm setAttendanceForm(List<AttendanceManagementDto> attendanceManagementDtoList) {

		AttendanceForm attendanceForm = new AttendanceForm();
		attendanceForm.setAttendanceList(new ArrayList<DailyAttendanceForm>());
		attendanceForm.setLmsUserId(loginUserDto.getLmsUserId());
		attendanceForm.setUserName(loginUserDto.getUserName());
		attendanceForm.setLeaveFlg(loginUserDto.getLeaveFlg());
		attendanceForm.setBlankTimes(attendanceUtil.setBlankTime());
		// 角田 智哉 Task.26
		attendanceForm.setTrainingTimeHour(attendanceUtil.setTrainingTimeHour());
		attendanceForm.setTrainingTimeMinute(attendanceUtil.setTrainingTimeMinute());

		// 途中退校している場合のみ設定
		if (loginUserDto.getLeaveDate() != null) {
			attendanceForm.setLeaveDate(dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy-MM-dd"));
			attendanceForm.setDispLeaveDate(dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy年M月d日"));
		}

		// 勤怠管理リストの件数分、日次の勤怠フォームに移し替え
		for (AttendanceManagementDto attendanceManagementDto : attendanceManagementDtoList) {
			DailyAttendanceForm dailyAttendanceForm = new DailyAttendanceForm();
			dailyAttendanceForm.setStudentAttendanceId(attendanceManagementDto.getStudentAttendanceId());
			dailyAttendanceForm.setTrainingDate(dateUtil.toString(attendanceManagementDto.getTrainingDate()));
			dailyAttendanceForm.setTrainingStartTime(attendanceManagementDto.getTrainingStartTime());
			dailyAttendanceForm.setTrainingEndTime(attendanceManagementDto.getTrainingEndTime());
			// 角田 智哉 Task.26
			String trainingStartTime = attendanceManagementDto.getTrainingStartTime();
			String trainingEndTime = attendanceManagementDto.getTrainingEndTime();
			// 文字列型、数値型の出勤時をフォームにセット
			dailyAttendanceForm.setTrainingStartTimeHour(attendanceUtil.getTrainingTimeHourInteger(trainingStartTime));
			dailyAttendanceForm.setTrainingStartTimeHourValue(
					String.valueOf(attendanceUtil.getTrainingTimeHourInteger(trainingStartTime)));
			// 文字列型、数値型の出勤分をフォームにセット
			dailyAttendanceForm
					.setTrainingStartTimeMinute(attendanceUtil.getTrainingTimeMinuteInteger(trainingStartTime));
			dailyAttendanceForm.setTrainingStartTimeMinuteValue(
					String.valueOf(attendanceUtil.getTrainingTimeMinuteInteger(trainingStartTime)));
			// 文字列型、数値型の退勤時をフォームにセット
			dailyAttendanceForm.setTrainingEndTimeHour(attendanceUtil.getTrainingTimeHourInteger(trainingEndTime));
			dailyAttendanceForm.setTrainingEndTimeHourValue(
					String.valueOf(attendanceUtil.getTrainingTimeHourInteger(trainingEndTime)));
			// 文字列型、数値型の退勤分をフォームにセット
			dailyAttendanceForm.setTrainingEndTimeMinute(attendanceUtil.getTrainingTimeMinuteInteger(trainingEndTime));
			dailyAttendanceForm.setTrainingEndTimeMinuteValue(
					String.valueOf(attendanceUtil.getTrainingTimeMinuteInteger(trainingEndTime)));

			if (attendanceManagementDto.getBlankTime() != null) {
				dailyAttendanceForm.setBlankTime(attendanceManagementDto.getBlankTime());
				dailyAttendanceForm.setBlankTimeValue(
						String.valueOf(attendanceUtil.calcBlankTime(attendanceManagementDto.getBlankTime())));
			}
			dailyAttendanceForm.setStatus(String.valueOf(attendanceManagementDto.getStatus()));
			dailyAttendanceForm.setNote(attendanceManagementDto.getNote());
			dailyAttendanceForm.setSectionName(attendanceManagementDto.getSectionName());
			dailyAttendanceForm.setIsToday(attendanceManagementDto.getIsToday());
			dailyAttendanceForm.setDispTrainingDate(
					dateUtil.dateToString(attendanceManagementDto.getTrainingDate(), "yyyy年M月d日(E)"));
			dailyAttendanceForm.setStatusDispName(attendanceManagementDto.getStatusDispName());

			attendanceForm.getAttendanceList().add(dailyAttendanceForm);
		}

		return attendanceForm;
	}

	/**
	 * 勤怠登録・更新処理
	 *
	 * @param attendanceForm
	 * @return 完了メッセージ
	 * @throws ParseException
	 */
	public String update(AttendanceForm attendanceForm, BindingResult result) throws ParseException {

		Integer lmsUserId = loginUserUtil.isStudent() ? loginUserDto.getLmsUserId() : attendanceForm.getLmsUserId();

		// 現在の勤怠情報（受講生入力）リストを取得
		List<TStudentAttendance> tStudentAttendanceList = tStudentAttendanceMapper.findByLmsUserId(lmsUserId,
				Constants.DB_FLG_FALSE);
		// 入力された情報を更新用のエンティティに移し替え
		Date date = new Date();
		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {

			// 更新用エンティティ作成
			TStudentAttendance tStudentAttendance = new TStudentAttendance();
			// 日次勤怠フォームから更新用のエンティティにコピー
			BeanUtils.copyProperties(dailyAttendanceForm, tStudentAttendance);
			// 研修日付
			tStudentAttendance.setTrainingDate(dateUtil.parse(dailyAttendanceForm.getTrainingDate()));
			// 現在の勤怠情報リストのうち、研修日が同じものを更新用エンティティで上書き
			for (TStudentAttendance entity : tStudentAttendanceList) {
				if (entity.getTrainingDate().equals(tStudentAttendance.getTrainingDate())) {
					tStudentAttendance = entity;
					break;
				}
			}
			tStudentAttendance.setLmsUserId(lmsUserId);
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			// 出勤時刻整形
			TrainingTime trainingStartTime = null;
			// 角田 智哉 Task.26
			Integer trainingStartTimeHour = dailyAttendanceForm.getTrainingStartTimeHour();
			Integer trainingStartTimeMinute = dailyAttendanceForm.getTrainingStartTimeMinute();
			// 出勤「時」もしくは「分」が未入力の場合は、空文字を挿入。それ以外は入力パラメータをセット
			if (trainingStartTimeHour == null || trainingStartTimeMinute == null) {
				String startTime = "";
				tStudentAttendance.setTrainingStartTime(startTime);
			} else {
				String trainingStartTimeStr = (trainingStartTimeHour + ":" + trainingStartTimeMinute);
				trainingStartTime = new TrainingTime(trainingStartTimeStr);
				tStudentAttendance.setTrainingStartTime(trainingStartTime.getFormattedString());
			}
			// 角田 智哉 Task.27
			// 出勤時間（時）、出勤時間（分）の一方が入力有り＆もう一方が入力なしの場合、エラーメッセージを追加
			if ((trainingStartTimeHour == null && trainingStartTimeMinute != null)
					|| (trainingStartTimeHour != null && trainingStartTimeMinute == null)) {
				result.addError(new FieldError(result.getObjectName(), "DailyAttendanceForm",
						messageUtil.getMessage(Constants.INPUT_INVALID, new String[] { "出勤時間" })));
			}

			// 退勤時刻整形
			TrainingTime trainingEndTime = null;

			// 角田 智哉 Task.26
			Integer trainingEndTimeHour = dailyAttendanceForm.getTrainingEndTimeHour();
			Integer trainingEndTimeMinute = dailyAttendanceForm.getTrainingEndTimeMinute();
			// 退勤「時」もしくは「分」が未入力の場合は、空文字を挿入。それ以外は入力パラメータをセット
			if (trainingEndTimeHour == null || trainingEndTimeMinute == null) {
				String endTime = "";
				tStudentAttendance.setTrainingEndTime(endTime);
			} else {
				String trainingEndTimeStr = (trainingEndTimeHour + ":" + trainingEndTimeMinute);
				trainingEndTime = new TrainingTime(trainingEndTimeStr);
				tStudentAttendance.setTrainingEndTime(trainingEndTime.getFormattedString());
			}
			// 角田 智哉 Task.27
			// 退勤時間（時）、退勤時間（分）の一方が入力有り＆もう一方が入力なしの場合、エラーメッセージを追加
			if ((trainingEndTimeHour == null && trainingEndTimeMinute != null)
					|| (trainingEndTimeHour != null && trainingEndTimeMinute == null)) {
				result.addError(new FieldError(result.getObjectName(), "DailyAttendanceForm",
						messageUtil.getMessage(Constants.INPUT_INVALID, new String[] { "退勤時間" })));

			}
			// 角田 智哉 Task.27
			// 出勤時間に入力なし＆退勤時間に入力ありの場合、エラーメッセージを追加
			if (tStudentAttendance.getTrainingStartTime() == "" && tStudentAttendance.getTrainingEndTime() != "") {
				result.addError(new FieldError(result.getObjectName(), "DailyAttendanceForm",
						messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY)));
			}
			// 角田 智哉 Task.27
			// 未入力の場合は処理を続行
			if (trainingStartTime == null || trainingEndTime == null) {
				// 出勤時間 ＞ 退勤時間 の場合、エラーメッセージを追加
			} else if (((trainingStartTimeHour - trainingEndTimeHour) > 0)
					|| ((trainingStartTimeHour == trainingEndTimeHour)
							&& (trainingStartTimeMinute > trainingEndTimeMinute))) {
				result.addError(new FieldError(result.getObjectName(), "DailyAttendanceForm",
						messageUtil.getMessage(messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE,
								new String[] { tStudentAttendance.getTrainingEndTime(),
										tStudentAttendance.getTrainingStartTime() }))));
			} else {

			}

			// 中抜け時間
			tStudentAttendance.setBlankTime(dailyAttendanceForm.getBlankTime());
			// 遅刻早退ステータス
			if ((trainingStartTime != null || trainingEndTime != null)
					&& !dailyAttendanceForm.getStatusDispName().equals("欠席")) {
				AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
						trainingEndTime);
				tStudentAttendance.setStatus(attendanceStatusEnum.code);
			}
			// 角田 智哉 Task.27
			// 未入力の場合は処理を続行
			if (tStudentAttendance.getTrainingStartTime() == "" || tStudentAttendance.getTrainingEndTime() == ""
					|| tStudentAttendance.getBlankTime() == null) {
				// 出勤時間、退勤時間、中抜け時間のいずれも入力されている場合に実行
			} else {
				// 中抜け時間を取得
				Integer blankTime = tStudentAttendance.getBlankTime();
				// 比較用の出勤時間、退勤時間をリストから取得
				TrainingTime compareTrainingStartTime = new TrainingTime(tStudentAttendance.getTrainingStartTime());
				TrainingTime compareTrainingEndTime = new TrainingTime(tStudentAttendance.getTrainingEndTime());
				// 比較用の出勤時間、退勤時間を分に変換し、勤務合計時間を算出
				Integer compareTotalTainingMinute = (compareTrainingEndTime.getHour() * 60)
						+ compareTrainingEndTime.getMinute() - (compareTrainingStartTime.getHour() * 60)
						+ compareTrainingStartTime.getMinute();
				// 勤務合計時間よりも中抜け時間のほうが大きい場合は、エラーメッセージを格納
				if (blankTime > compareTotalTainingMinute) {
					result.addError(new FieldError(result.getObjectName(), "DailyAttendanceForm",
							messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_BLANKTIMEERROR)));
				}
			}

			// 備考
			tStudentAttendance.setNote(dailyAttendanceForm.getNote());
			// 角田 智哉 Task.27
			// 100文字以上入力された場合はエラーメッセージを追加
			if (tStudentAttendance.getNote().length() > 100) {
				result.addError(new FieldError(result.getObjectName(), "DailyAttendanceForm",
						messageUtil.getMessage(Constants.VALID_KEY_MAXLENGTH, new String[] { "備考", "100" })));
			}
			// 更新者と更新日時
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			// 削除フラグ
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			// 登録用Listへ追加
			tStudentAttendanceList.add(tStudentAttendance);
		}

		String completeMessage = null;

		if (result.hasErrors()) {
			return completeMessage;
		}
		// 登録・更新処理
		for (TStudentAttendance tStudentAttendance : tStudentAttendanceList) {
			if (tStudentAttendance.getStudentAttendanceId() == null) {
				tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
				tStudentAttendance.setFirstCreateDate(date);
				tStudentAttendanceMapper.insert(tStudentAttendance);
			} else {
				tStudentAttendanceMapper.update(tStudentAttendance);
			}
		}
		// 角田 智哉 Task.27
		// 完了メッセージを返却
		completeMessage = messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
		return completeMessage;
	}

	/**
	 * Mapper実行結果として未入力カウント数が0より大きいかどうか判定
	 * 
	 * @author 角田智哉 -Task.25
	 * @param lmsUserId ログイン時のユーザーID
	 * @return 未入力件数判定結果
	 */
	public Boolean check(Integer lmsUserId) {

		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		short deleteFlg = Constants.DB_FLG_FALSE;
		Integer count = tStudentAttendanceMapper.notEnterCount(lmsUserId, deleteFlg, trainingDate);
		// 未入力カウント数が0より大きいなら”true”を返す
		return count > 0;
	}

}
