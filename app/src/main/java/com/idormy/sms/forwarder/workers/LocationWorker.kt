package com.idormy.sms.forwarder.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.idormy.sms.forwarder.App
import com.idormy.sms.forwarder.database.AppDatabase
import com.idormy.sms.forwarder.entity.MsgInfo
import com.idormy.sms.forwarder.entity.task.BatterySetting
import com.idormy.sms.forwarder.entity.task.ChargeSetting
import com.idormy.sms.forwarder.entity.task.TaskSetting
import com.idormy.sms.forwarder.utils.TASK_CONDITION_BATTERY
import com.idormy.sms.forwarder.utils.TASK_CONDITION_CHARGE
import com.idormy.sms.forwarder.utils.TaskWorker
import com.idormy.sms.forwarder.utils.task.TaskUtils
import java.util.Date

@Suppress("PrivatePropertyName", "DEPRECATION")
class LocationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val TAG: String = LocationWorker::class.java.simpleName

    override suspend fun doWork(): Result {

        when (val conditionType = inputData.getInt(TaskWorker.conditionType, -1)) {

            TASK_CONDITION_BATTERY -> {
                val status = inputData.getInt("status", -1)
                val levelNew = inputData.getInt("level_new", -1)
                val levelOld = inputData.getInt("level_old", -1)
                Log.d(TAG, "levelNew: $levelNew, levelOld: $levelOld")
                if (levelNew == -1 || levelOld == -1) {
                    Log.d(TAG, "levelNew or levelOld is -1")
                    return Result.failure()
                }

                val taskList = AppDatabase.getInstance(App.context).taskDao().getByType(conditionType)
                for (task in taskList) {
                    Log.d(TAG, "task = $task")

                    // 根据任务信息执行相应操作
                    val conditionList = Gson().fromJson(task.conditions, Array<TaskSetting>::class.java).toMutableList()
                    if (conditionList.isEmpty()) {
                        Log.d(TAG, "任务${task.id}：conditionList is empty")
                        continue
                    }
                    val firstCondition = conditionList.firstOrNull()
                    if (firstCondition == null) {
                        Log.d(TAG, "任务${task.id}：firstCondition is null")
                        continue
                    }

                    val batterySetting = Gson().fromJson(firstCondition.setting, BatterySetting::class.java)
                    if (batterySetting == null) {
                        Log.d(TAG, "任务${task.id}：batterySetting is null")
                        continue
                    }

                    val msg = batterySetting.getMsg(status, levelNew, levelOld, TaskUtils.batteryInfo)
                    if (msg.isEmpty()) {
                        Log.d(TAG, "任务${task.id}：msg is empty, batterySetting = $batterySetting, status = $status, levelNew = $levelNew, levelOld = $levelOld")
                        continue
                    }

                    //TODO：判断其他条件是否满足

                    //TODO: 组装消息体 && 执行具体任务
                    val msgInfo = MsgInfo("task", task.name, msg, Date(), task.name)
                    val actionData = Data.Builder()
                        .putLong(TaskWorker.taskId, task.id)
                        .putString(TaskWorker.taskActions, task.actions)
                        .putString(TaskWorker.msgInfo, Gson().toJson(msgInfo))
                        .build()
                    val actionRequest = OneTimeWorkRequestBuilder<ActionWorker>().setInputData(actionData).build()
                    WorkManager.getInstance().enqueue(actionRequest)
                }

                return Result.success()
            }

            TASK_CONDITION_CHARGE -> {
                val statusNew = inputData.getInt("status_new", -1)
                val statusOld = inputData.getInt("status_old", -1)
                val pluggedNew = inputData.getInt("plugged_new", -1)
                val pluggedOld = inputData.getInt("plugged_old", -1)
                Log.d(TAG, "statusNew: $statusNew, statusOld: $statusOld, pluggedNew: $pluggedNew, pluggedOld: $pluggedOld")
                if (statusNew == -1 || statusOld == -1 || pluggedNew == -1 || pluggedOld == -1) {
                    Log.d(TAG, "statusNew or statusOld or pluggedNew or pluggedOld is -1")
                    return Result.failure()
                }

                val taskList = AppDatabase.getInstance(App.context).taskDao().getByType(conditionType)
                for (task in taskList) {
                    Log.d(TAG, "task = $task")

                    // 根据任务信息执行相应操作
                    val conditionList = Gson().fromJson(task.conditions, Array<TaskSetting>::class.java).toMutableList()
                    if (conditionList.isEmpty()) {
                        Log.d(TAG, "任务${task.id}：conditionList is empty")
                        continue
                    }
                    val firstCondition = conditionList.firstOrNull()
                    if (firstCondition == null) {
                        Log.d(TAG, "任务${task.id}：firstCondition is null")
                        continue
                    }

                    val chargeSetting = Gson().fromJson(firstCondition.setting, ChargeSetting::class.java)
                    if (chargeSetting == null) {
                        Log.d(TAG, "任务${task.id}：chargeSetting is null")
                        continue
                    }

                    val msg = chargeSetting.getMsg(statusNew, statusOld, pluggedNew, pluggedOld, TaskUtils.batteryInfo)
                    if (msg.isEmpty()) {
                        Log.d(TAG, "任务${task.id}：msg is empty, chargeSetting = $chargeSetting, statusNew = $statusNew, statusOld = $statusOld, pluggedNew = $pluggedNew, pluggedOld = $pluggedOld")
                        continue
                    }

                    //TODO：判断其他条件是否满足

                    //TODO: 组装消息体 && 执行具体任务
                    val msgInfo = MsgInfo("task", task.name, msg, Date(), task.description)
                    val actionData = Data.Builder()
                        .putLong(TaskWorker.taskId, task.id)
                        .putString(TaskWorker.taskActions, task.actions)
                        .putString(TaskWorker.msgInfo, Gson().toJson(msgInfo))
                        .build()
                    val actionRequest = OneTimeWorkRequestBuilder<ActionWorker>().setInputData(actionData).build()
                    WorkManager.getInstance().enqueue(actionRequest)
                }

                return Result.success()
            }

            else -> {
                Log.d(TAG, "conditionType is $conditionType")
                return Result.failure()
            }
        }

    }

}