package com.avitoohband.nutrun

enum class NotificationSettingsSaveStage {
    INDIVIDUAL_SUPPLEMENTS,
    HYDRATION,
    TRAINING,
    SUPPLEMENT_MASTER
}

sealed interface NotificationSettingsSaveResult {
    val allowsNavigation: Boolean
        get() = this is Success

    data class Success(val accountId: String) : NotificationSettingsSaveResult

    data class NotReady(val expectedAccountId: String?) : NotificationSettingsSaveResult

    data class AccountChanged(
        val expectedAccountId: String,
        val actualAccountId: String?,
        val stage: NotificationSettingsSaveStage
    ) : NotificationSettingsSaveResult

    data class Failed(
        val expectedAccountId: String,
        val stage: NotificationSettingsSaveStage,
        val message: String
    ) : NotificationSettingsSaveResult
}

internal suspend fun orchestrateNotificationSettingsSave(
    persistIndividuals: suspend () -> NotificationSettingsSaveResult,
    persistRemainingSettings: suspend () -> NotificationSettingsSaveResult
): NotificationSettingsSaveResult {
    val individualResult = persistIndividuals()
    return if (individualResult is NotificationSettingsSaveResult.Success) {
        persistRemainingSettings()
    } else {
        individualResult
    }
}

internal fun NotificationSettingsSaveResult.errorMessage(): String? = when (this) {
    is NotificationSettingsSaveResult.Success -> null
    is NotificationSettingsSaveResult.NotReady ->
        "Supplement reminders are still loading. Try again in a moment."
    is NotificationSettingsSaveResult.AccountChanged ->
        "The active account changed. Review these settings before saving again."
    is NotificationSettingsSaveResult.Failed -> when (stage) {
        NotificationSettingsSaveStage.INDIVIDUAL_SUPPLEMENTS ->
            "Could not save supplement reminders. No other notification settings were saved. $message"
        NotificationSettingsSaveStage.HYDRATION ->
            "Supplement reminders were saved, but hydration settings could not be saved. " +
                "Training and master settings were not started. $message"
        NotificationSettingsSaveStage.TRAINING ->
            "Supplement and hydration settings were saved, but training settings could not " +
                "be saved. Master settings were not started. $message"
        NotificationSettingsSaveStage.SUPPLEMENT_MASTER ->
            "Individual, hydration, and training settings were saved, but the supplement " +
                "master setting could not be saved. $message"
    }
}
