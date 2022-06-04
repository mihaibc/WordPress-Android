package org.wordpress.android.ui.qrcodeauth

import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.wordpress.android.analytics.AnalyticsTracker.Stat
import org.wordpress.android.fluxc.network.rest.wpcom.qrcodeauth.QRCodeAuthError
import org.wordpress.android.fluxc.network.rest.wpcom.qrcodeauth.QRCodeAuthErrorType.API_ERROR
import org.wordpress.android.fluxc.network.rest.wpcom.qrcodeauth.QRCodeAuthErrorType.AUTHORIZATION_REQUIRED
import org.wordpress.android.fluxc.network.rest.wpcom.qrcodeauth.QRCodeAuthErrorType.DATA_INVALID
import org.wordpress.android.fluxc.network.rest.wpcom.qrcodeauth.QRCodeAuthErrorType.GENERIC_ERROR
import org.wordpress.android.fluxc.network.rest.wpcom.qrcodeauth.QRCodeAuthErrorType.INVALID_RESPONSE
import org.wordpress.android.fluxc.network.rest.wpcom.qrcodeauth.QRCodeAuthErrorType.NOT_AUTHORIZED
import org.wordpress.android.fluxc.network.rest.wpcom.qrcodeauth.QRCodeAuthErrorType.REST_INVALID_PARAM
import org.wordpress.android.fluxc.network.rest.wpcom.qrcodeauth.QRCodeAuthErrorType.TIMEOUT
import org.wordpress.android.fluxc.store.qrcodeauth.QRCodeAuthStore
import org.wordpress.android.fluxc.store.qrcodeauth.QRCodeAuthStore.QRCodeAuthResult
import org.wordpress.android.fluxc.store.qrcodeauth.QRCodeAuthStore.QRCodeAuthValidateResult
import org.wordpress.android.ui.posts.BasicDialogViewModel.DialogInteraction
import org.wordpress.android.ui.posts.BasicDialogViewModel.DialogInteraction.Dismissed
import org.wordpress.android.ui.posts.BasicDialogViewModel.DialogInteraction.Negative
import org.wordpress.android.ui.posts.BasicDialogViewModel.DialogInteraction.Positive
import org.wordpress.android.ui.qrcodeauth.QRCodeAuthActionEvent.FinishActivity
import org.wordpress.android.ui.qrcodeauth.QRCodeAuthActionEvent.LaunchDismissDialog
import org.wordpress.android.ui.qrcodeauth.QRCodeAuthActionEvent.LaunchScanner
import org.wordpress.android.ui.qrcodeauth.QRCodeAuthDialogModel.ShowDismissDialog
import org.wordpress.android.ui.qrcodeauth.QRCodeAuthUiState.Content.Validated
import org.wordpress.android.ui.qrcodeauth.QRCodeAuthUiState.Loading
import org.wordpress.android.ui.qrcodeauth.QRCodeAuthUiStateType.AUTHENTICATING
import org.wordpress.android.ui.qrcodeauth.QRCodeAuthUiStateType.AUTH_FAILED
import org.wordpress.android.ui.qrcodeauth.QRCodeAuthUiStateType.DONE
import org.wordpress.android.ui.qrcodeauth.QRCodeAuthUiStateType.EXPIRED
import org.wordpress.android.ui.qrcodeauth.QRCodeAuthUiStateType.INVALID_DATA
import org.wordpress.android.ui.qrcodeauth.QRCodeAuthUiStateType.LOADING
import org.wordpress.android.ui.qrcodeauth.QRCodeAuthUiStateType.NO_INTERNET
import org.wordpress.android.ui.qrcodeauth.QRCodeAuthUiStateType.SCANNING
import org.wordpress.android.ui.qrcodeauth.QRCodeAuthUiStateType.VALIDATED
import org.wordpress.android.util.NetworkUtilsWrapper
import org.wordpress.android.util.analytics.AnalyticsTrackerWrapper
import javax.inject.Inject

@Suppress("TooManyFunctions")
@HiltViewModel
class QRCodeAuthViewModel @Inject constructor(
    private val authStore: QRCodeAuthStore,
    private val uiStateMapper: QRCodeAuthUiStateMapper,
    private val networkUtilsWrapper: NetworkUtilsWrapper,
    private val validator: QRCodeAuthValidator,
    private val analyticsTrackerWrapper: AnalyticsTrackerWrapper
) : ViewModel() {
    private val _actionEvents = Channel<QRCodeAuthActionEvent>(Channel.BUFFERED)
    val actionEvents = _actionEvents.receiveAsFlow()

    private val _uiState = MutableStateFlow<QRCodeAuthUiState>(Loading)
    val uiState: StateFlow<QRCodeAuthUiState> = _uiState

    private var data: String? = null
    private var token: String? = null
    private var location: String? = null
    private var browser: String? = null
    private var lastState: QRCodeAuthUiStateType? = null
    private var isStarted = false

    fun start(savedInstanceState: Bundle? = null) {
        if (isStarted) return
        isStarted = true

        // track shown here
        extractSavedInstanceStateIfNeeded(savedInstanceState)
        startOrRestoreUiState()
    }

    private fun extractSavedInstanceStateIfNeeded(savedInstanceState: Bundle?) {
        savedInstanceState?.let {
            data = savedInstanceState.getString(DATA_KEY, null)
            token = savedInstanceState.getString(TOKEN_KEY, null)
            browser = savedInstanceState.getString(BROWSER_KEY, null)
            location = savedInstanceState.getString(LOCATION_KEY, null)
            lastState = QRCodeAuthUiStateType.fromString(savedInstanceState.getString(LAST_STATE_KEY, null))
        }
    }

    private fun startOrRestoreUiState() {
        when (lastState) {
            LOADING, SCANNING -> updateUiStateAndLaunchScanner()
            VALIDATED -> postUiState(
                    uiStateMapper.mapValidated(
                            location,
                            browser,
                            this::authenticateClicked,
                            this::authenticateCancelClicked
                    )
            )
            AUTHENTICATING -> postUiState(uiStateMapper.mapAuthenticating(location = location, browser = browser))
            DONE -> postUiState(uiStateMapper.mapDone(this::dismissClicked))
            // errors
            INVALID_DATA -> postUiState(uiStateMapper.mapInvalidData(this::scanAgainClicked, this::cancelClicked))
            AUTH_FAILED -> postUiState(uiStateMapper.mapAuthFailed(this::scanAgainClicked, this::cancelClicked))
            EXPIRED -> postUiState(uiStateMapper.mapExpired(this::scanAgainClicked, this::cancelClicked))
            NO_INTERNET -> {
                postUiState(uiStateMapper.mapNoInternet(this::scanAgainClicked, this::cancelClicked))
            }
            else -> updateUiStateAndLaunchScanner()
        }
    }

    //  https://apps.wordpress.com/get/?campaign=login-qr-code#qr-code-login?token=asdfadsfa&data=asdfasdf
    fun onScanSuccess(scannedValue: String?) {
        track(Stat.QRLOGIN_SCANNER_SCANNED_CODE)
        handleScan(scannedValue)
    }

    fun onScanFailure() {
        // Note: This is a result of the tap on "X" within the scanner view
        track(Stat.QRLOGIN_SCANNER_DISMISSED)
        postActionEvent(FinishActivity)
    }

    fun onBackPressed() {
        postActionEvent(LaunchDismissDialog(ShowDismissDialog))
    }

    private fun cancelClicked() {
        track(Stat.QRLOGIN_VERIFY_CANCELLED)
        postActionEvent(FinishActivity)
    }

    private fun scanAgainClicked() {
        postActionEvent(LaunchScanner)
    }

    private fun dismissClicked() {
        track(Stat.QRLOGIN_VERIFY_DISMISS)
        postActionEvent(FinishActivity)
    }

    private fun authenticateClicked() {
        track(Stat.QRLOGIN_VERIFY_APPROVED)
        postUiState(uiStateMapper.mapAuthenticating(_uiState.value as Validated))
        if (data.isNullOrEmpty() || token.isNullOrEmpty()) {
            postUiState(uiStateMapper.mapInvalidData(this::scanAgainClicked, this::cancelClicked))
        } else {
            authenticate(data = data.toString(), token = token.toString())
        }
    }

    private fun authenticateCancelClicked() {
        track(Stat.QRLOGIN_VERIFY_CANCELLED)
        postActionEvent(FinishActivity)
    }

    private fun handleScan(scannedValue: String?) {
        extractQueryParamsIfValid(scannedValue)

        if (data.isNullOrEmpty() || token.isNullOrEmpty()) {
            postUiState(uiStateMapper.mapInvalidData(this::scanAgainClicked, this::cancelClicked))
        } else {
            postUiState(uiStateMapper.mapLoading())
            track(Stat.QRLOGIN_VERIFY_DISPLAYED)
            validateScan(data = data.toString(), token = token.toString())
        }
    }

    private fun validateScan(data: String, token: String) {
        if (!networkUtilsWrapper.isNetworkAvailable()) {
            postUiState(uiStateMapper.mapNoInternet(this::scanAgainClicked, this::cancelClicked))
            return
        }

        viewModelScope.launch {
            val result = authStore.validate(data = data, token = token)
            if (result.isError) {
                postUiState(mapScanErrorToErrorState(result.error))
            } else {
                browser = result.model?.browser
                location = result.model?.location
                track(Stat.QRLOGIN_VERIFY_TOKEN_VALIDATED)
                postUiState(mapScanSuccessToValidatedState(result))
            }
        }
    }

    private fun mapScanSuccessToValidatedState(result: QRCodeAuthResult<QRCodeAuthValidateResult>) =
            uiStateMapper.mapValidated(
                    browser = result.model?.browser,
                    location = result.model?.location,
                    onAuthenticateClick = this::authenticateClicked,
                    onCancelClick = this::authenticateCancelClicked
            )

    private fun mapScanErrorToErrorState(error: QRCodeAuthError) = when (error.type) {
        DATA_INVALID -> uiStateMapper.mapExpired(this::scanAgainClicked, this::cancelClicked)
        NOT_AUTHORIZED -> uiStateMapper.mapAuthFailed(this::scanAgainClicked, this::cancelClicked)
        GENERIC_ERROR,
        INVALID_RESPONSE,
        REST_INVALID_PARAM,
        API_ERROR,
        AUTHORIZATION_REQUIRED,
        TIMEOUT -> uiStateMapper.mapInvalidData(this::scanAgainClicked, this::cancelClicked)
    }

    private fun extractQueryParamsIfValid(scannedValue: String?) {
        if (!validator.isValidUri(scannedValue)) return

        val queryParams = validator.extractQueryParams(scannedValue)
        if (queryParams.containsKey(DATA_KEY) && queryParams.containsKey(TOKEN_KEY)) {
            this.data = queryParams[DATA_KEY].toString()
            this.token = queryParams[TOKEN_KEY].toString()
        }
    }

    @Suppress("MagicNumber")
    private fun authenticate(data: String, token: String) {
        if (!networkUtilsWrapper.isNetworkAvailable()) {
            postUiState(uiStateMapper.mapNoInternet(this::scanAgainClicked, this::cancelClicked))
            return
        }

        viewModelScope.launch {
            val result = authStore.authenticate(data = data, token = token)
            if (result.isError) {
                postUiState(mapScanErrorToErrorState(result.error))
            } else {
                clearProperties()
                if (result.model?.authenticated == true) {
                    track(Stat.QRLOGIN_AUTHENTICATED)
                    postUiState(mapAuthenticateSuccessToDoneState())
                } else {
                    postUiState(uiStateMapper.mapAuthFailed(::scanAgainClicked, ::cancelClicked))
                }
            }
        }
    }

    private fun mapAuthenticateSuccessToDoneState() = uiStateMapper.mapDone(this::dismissClicked)

    private fun updateUiStateAndLaunchScanner() {
        postUiState(uiStateMapper.mapScanning())
        postActionEvent(LaunchScanner)
    }

    private fun postUiState(state: QRCodeAuthUiState) {
        viewModelScope.launch {
            _uiState.value = state
        }
    }
    private fun postActionEvent(actionEvent: QRCodeAuthActionEvent) {
        viewModelScope.launch {
            _actionEvents.send(actionEvent)
        }
    }

    fun onDialogInteraction(interaction: DialogInteraction) {
        when (interaction) {
            is Positive -> postActionEvent(FinishActivity)
            is Negative -> { } // NO OP
            is Dismissed -> { } // NO OP
        }
    }

    private fun clearProperties() {
        data = null
        token = null
        browser = null
        location = null
        lastState = null
    }

    fun writeToBundle(outState: Bundle) {
        outState.putString(DATA_KEY, data)
        outState.putString(TOKEN_KEY, data)
        outState.putString(BROWSER_KEY, browser)
        outState.putString(LOCATION_KEY, location)
        outState.putString(LAST_STATE_KEY, uiState.value.type?.label)
    }

    fun track(stat: Stat) {
        analyticsTrackerWrapper.track(stat, mapOf(ORIGIN to ORIGIN_MENU))
    }

    companion object {
        const val TAG_DISMISS_DIALOG = "TAG_DISMISS_DIALOG"
        const val TOKEN_KEY = "token"
        const val DATA_KEY = "data"
        const val BROWSER_KEY = "browser"
        const val LOCATION_KEY = "location"
        const val LAST_STATE_KEY = "last_state"
        const val ORIGIN = "origin"
        const val ORIGIN_MENU = "menu"
    }
}
