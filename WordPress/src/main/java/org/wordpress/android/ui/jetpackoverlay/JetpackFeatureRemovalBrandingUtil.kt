package org.wordpress.android.ui.jetpackoverlay

import org.wordpress.android.R
import org.wordpress.android.models.JetpackPoweredScreen
import org.wordpress.android.ui.jetpackoverlay.JetpackFeatureRemovalPhase.PhaseFour
import org.wordpress.android.ui.jetpackoverlay.JetpackFeatureRemovalPhase.PhaseOne
import org.wordpress.android.ui.jetpackoverlay.JetpackFeatureRemovalPhase.PhaseThree
import org.wordpress.android.ui.jetpackoverlay.JetpackFeatureRemovalPhase.PhaseTwo
import org.wordpress.android.ui.utils.UiString
import org.wordpress.android.ui.utils.UiString.UiStringRes
import org.wordpress.android.util.DateTimeUtilsWrapper
import org.wordpress.android.util.config.JPDeadlineConfig
import javax.inject.Inject

class JetpackFeatureRemovalBrandingUtil @Inject constructor(
    private val jetpackFeatureRemovalPhaseHelper: JetpackFeatureRemovalPhaseHelper,
    private val jpDeadlineConfig: JPDeadlineConfig,
    private val dateTimeUtilsWrapper: DateTimeUtilsWrapper
) {
    fun shouldShowPhaseOneBranding(): Boolean {
        return when (jetpackFeatureRemovalPhaseHelper.getCurrentPhase()) {
            PhaseOne,
            PhaseTwo,
            PhaseThree,
            PhaseFour -> true
            else -> false
        }
    }

    fun shouldShowPhaseTwoBranding(): Boolean {
        return when (jetpackFeatureRemovalPhaseHelper.getCurrentPhase()) {
            PhaseTwo,
            PhaseThree,
            PhaseFour -> true
            else -> false
        }
    }

    fun getBrandingTextByPhase(screen: JetpackPoweredScreen): UiString {
        return when (jetpackFeatureRemovalPhaseHelper.getCurrentPhase()) {
            null,
            PhaseOne -> UiStringRes(R.string.wp_jetpack_powered)
            PhaseTwo -> UiStringRes(R.string.wp_jetpack_powered_phase_2)
            PhaseThree -> when (screen) {
                is JetpackPoweredScreen.WithDynamicText -> {
                    getBrandingTextForPhaseThreeBasedOnDeadline(screen, jpDeadlineConfig.getValue())
                }
                else -> UiStringRes(R.string.wp_jetpack_powered)
            }
            PhaseFour -> UiStringRes(R.string.wp_jetpack_powered)
            else -> UiStringRes(R.string.wp_jetpack_powered)
        }
    }

    private fun getBrandingTextForPhaseThreeBasedOnDeadline(
        screen: JetpackPoweredScreen.WithDynamicText,
        jpDeadlineDate: String?,
    ): UiString {
        return when (jpDeadlineDate.isNullOrEmpty()) {
            true -> getPhaseThreeBrandingTextWithoutDeadline(screen)
            else -> getPhaseThreeBrandingTextWithDeadline(screen, countDaysUntilDeadlineOrZero(jpDeadlineDate))
        }
    }

    private fun getPhaseThreeBrandingTextWithoutDeadline(screen: JetpackPoweredScreen.WithDynamicText): UiString {
        return UiString.UiStringResWithParams(
            stringRes = R.string.wp_jetpack_powered_phase_3_without_deadline,
            params = screen.getBrandingTextParams()
        )
    }

    @Suppress("MagicNumber", "ForbiddenComment")
    private fun getPhaseThreeBrandingTextWithDeadline(
        screen: JetpackPoweredScreen.WithDynamicText,
        daysUntilDeadline: Int,
    ): UiString {
        return when {
            // Deadline is more than one month away
            daysUntilDeadline > 30 -> {
                UiString.UiStringResWithParams(
                    stringRes = R.string.wp_jetpack_powered_phase_3_with_deadline_months_away,
                    params = screen.getBrandingTextParams()
                )
            }
            // Deadline is more than one week away
            // TODO: Fix "1 weeks"
            daysUntilDeadline > 7 -> {
                val weeksUntilDeadline = daysUntilDeadline / 7
                UiString.UiStringResWithParams(
                    stringRes = R.string.wp_jetpack_powered_phase_3_with_deadline_weeks_away,
                    params = screen.getBrandingTextParams(weeksUntilDeadline)
                )
            }
            // Deadline is more than one day away
            daysUntilDeadline > 1 -> {
                UiString.UiStringResWithParams(
                    stringRes = R.string.wp_jetpack_powered_phase_3_with_deadline_days_away,
                    params = screen.getBrandingTextParams(daysUntilDeadline)
                )
            }
            // Deadline is one day away
            daysUntilDeadline == 1 -> UiString.UiStringResWithParams(
                stringRes = R.string.wp_jetpack_powered_phase_3_with_deadline_day_away,
                screen.getBrandingTextParams()
            )
            // Deadline is today or has passed
            else -> UiStringRes(R.string.wp_jetpack_powered)
        }
    }

    @Suppress("UNUSED_PARAMETER", "ForbiddenComment")
    private fun countDaysUntilDeadlineOrZero(jpDeadlineDate: String): Int {
        // TODO: verify logic is correct, otherwise use Calendar
        val startDate = dateTimeUtilsWrapper.getTodaysDate()
        val endDate = dateTimeUtilsWrapper.dateFromPattern(jpDeadlineDate, JETPACK_OVERLAY_ORIGINAL_DATE_FORMAT)
            ?: return 0
        return dateTimeUtilsWrapper.daysBetween(startDate, endDate)
    }

    private fun JetpackPoweredScreen.WithDynamicText.getBrandingTextParams(timeUntilDeadline: Int? = null) =
        listOfNotNull(
            featureName,
            featureVerb,
            timeUntilDeadline?.let { UiString.UiStringText("$it") },
        )

    private val JetpackPoweredScreen.WithDynamicText.featureVerb
        get() = UiStringRes(
            when (isFeatureNameSingular) {
                true -> R.string.wp_jetpack_powered_phase_3_feature_verb_singular_is
                else -> R.string.wp_jetpack_powered_phase_3_feature_verb_plural_are
            }
        )

}
