package org.wordpress.android.viewmodel.posts

import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.wordpress.android.R
import org.wordpress.android.analytics.AnalyticsTracker
import org.wordpress.android.fluxc.model.PostModel
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.post.PostStatus
import org.wordpress.android.fluxc.store.MediaStore.MediaError
import org.wordpress.android.fluxc.store.MediaStore.MediaErrorType
import org.wordpress.android.fluxc.store.PostStore.PostError
import org.wordpress.android.fluxc.store.PostStore.PostErrorType
import org.wordpress.android.fluxc.store.UploadStore.UploadError
import org.wordpress.android.ui.blaze.BlazeFeatureUtils
import org.wordpress.android.ui.jetpackoverlay.JetpackFeatureRemovalPhaseHelper
import org.wordpress.android.ui.posts.AuthorFilterSelection
import org.wordpress.android.ui.posts.PostModelUploadStatusTracker
import org.wordpress.android.ui.prefs.AppPrefsWrapper
import org.wordpress.android.ui.utils.UiString.UiStringRes
import org.wordpress.android.ui.utils.UiString.UiStringText
import org.wordpress.android.viewmodel.pages.PostModelUploadUiStateUseCase
import org.wordpress.android.viewmodel.pages.PostModelUploadUiStateUseCase.PostUploadUiState.UploadFailed
import org.wordpress.android.viewmodel.pages.PostModelUploadUiStateUseCase.PostUploadUiState.UploadQueued
import org.wordpress.android.viewmodel.pages.PostModelUploadUiStateUseCase.PostUploadUiState.UploadWaitingForConnection
import org.wordpress.android.viewmodel.pages.PostModelUploadUiStateUseCase.PostUploadUiState.UploadingMedia
import org.wordpress.android.viewmodel.pages.PostModelUploadUiStateUseCase.PostUploadUiState.UploadingPost
import org.wordpress.android.viewmodel.pages.PostPageListLabelColorUseCase
import org.wordpress.android.viewmodel.posts.PostListItemAction.MoreItem
import org.wordpress.android.viewmodel.posts.PostListItemType.PostListItemUiState
import org.wordpress.android.viewmodel.uistate.ProgressBarUiState
import org.wordpress.android.widgets.PostListButtonType

private const val FORMATTER_DATE = "January 1st, 1:35pm"

private val POST_STATE_PUBLISH = PostStatus.PUBLISHED.toString()
private val POST_STATE_SCHEDULED = PostStatus.SCHEDULED.toString()
private val POST_STATE_PRIVATE = PostStatus.PRIVATE.toString()
private val POST_STATE_PENDING = PostStatus.PENDING.toString()
private val POST_STATE_DRAFT = PostStatus.DRAFT.toString()
private val POST_STATE_TRASHED = PostStatus.TRASHED.toString()

@Suppress("LargeClass")
@RunWith(MockitoJUnitRunner::class)
class PostListItemUiStateHelperTest {
    @Mock
    private lateinit var appPrefsWrapper: AppPrefsWrapper

    @Mock
    private lateinit var uploadUiStateUseCase: PostModelUploadUiStateUseCase

    @Mock
    private lateinit var uploadStatusTracker: PostModelUploadStatusTracker

    @Mock
    private lateinit var labelColorUseCase: PostPageListLabelColorUseCase

    @Mock
    private lateinit var jetpackFeatureRemovalPhaseHelper: JetpackFeatureRemovalPhaseHelper

    @Mock
    private lateinit var blazeFeatureUtils: BlazeFeatureUtils

    private lateinit var helper: PostListItemUiStateHelper

    @Before
    fun setup() {
        helper = PostListItemUiStateHelper(
            appPrefsWrapper,
            uploadUiStateUseCase,
            labelColorUseCase,
            jetpackFeatureRemovalPhaseHelper,
            blazeFeatureUtils
        )
        whenever(appPrefsWrapper.isAztecEditorEnabled).thenReturn(true)
    }

    @Test
    fun `featureImgUrl is propagated`() {
        val testUrl = "https://example.com"
        val state = createPostListItemUiState(featuredImageUrl = testUrl)
        assertThat(state.data.imageUrl).isEqualTo(testUrl)
    }

    @Test
    fun `verify draft actions`() {
        val state = createPostListItemUiState(
            post = createPostModel(status = POST_STATE_DRAFT)
        )

        assertThat(state.actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_EDIT)
        assertThat(state.actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_PUBLISH)
        assertThat(state.actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_MORE)
        assertThat(state.actions).hasSize(3)

        assertThat((state.actions[2] as MoreItem).actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_VIEW)
        assertThat((state.actions[2] as MoreItem).actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY)
        assertThat((state.actions[2] as MoreItem).actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY_URL)
        assertThat((state.actions[2] as MoreItem).actions[3].buttonType).isEqualTo(PostListButtonType.BUTTON_TRASH)
        assertThat((state.actions[2] as MoreItem).actions).hasSize(4)
    }

    @Test
    fun `verify local draft actions`() {
        val state = createPostListItemUiState(
            post = createPostModel(status = POST_STATE_DRAFT, isLocalDraft = true)
        )

        assertThat(state.actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_EDIT)
        assertThat(state.actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_PUBLISH)
        assertThat(state.actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_MORE)
        assertThat(state.actions).hasSize(3)

        assertThat((state.actions[2] as MoreItem).actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_PREVIEW)
        assertThat((state.actions[2] as MoreItem).actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY)
        assertThat((state.actions[2] as MoreItem).actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_DELETE)
        assertThat((state.actions[2] as MoreItem).actions).hasSize(3)
    }

    @Test
    fun `verify draft actions without publishing rights`() {
        val state = createPostListItemUiState(
            post = createPostModel(status = POST_STATE_DRAFT),
            capabilitiesToPublish = false
        )

        assertThat(state.actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_EDIT)
        assertThat(state.actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_SUBMIT)
        assertThat(state.actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_MORE)
        assertThat(state.actions).hasSize(3)

        assertThat((state.actions[2] as MoreItem).actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_VIEW)
        assertThat((state.actions[2] as MoreItem).actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY)
        assertThat((state.actions[2] as MoreItem).actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY_URL)
        assertThat((state.actions[2] as MoreItem).actions[3].buttonType).isEqualTo(PostListButtonType.BUTTON_TRASH)
        assertThat((state.actions[2] as MoreItem).actions).hasSize(4)
    }

    @Test
    fun `verify local draft actions without publishing rights`() {
        val state = createPostListItemUiState(
            post = createPostModel(status = POST_STATE_DRAFT, isLocalDraft = true),
            capabilitiesToPublish = false
        )

        assertThat(state.actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_EDIT)
        assertThat(state.actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_SUBMIT)
        assertThat(state.actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_MORE)
        assertThat(state.actions).hasSize(3)

        assertThat((state.actions[2] as MoreItem).actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_PREVIEW)
        assertThat((state.actions[2] as MoreItem).actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY)
        assertThat((state.actions[2] as MoreItem).actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_DELETE)
        assertThat((state.actions[2] as MoreItem).actions).hasSize(3)
    }

    @Test
    fun `verify draft actions on failed upload`() {
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            createFailedUploadUiState()
        )
        val state = createPostListItemUiState(
            post = createPostModel(status = POST_STATE_DRAFT)
        )

        assertThat(state.actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_EDIT)
        assertThat(state.actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_RETRY)
        assertThat(state.actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_MORE)
        assertThat(state.actions).hasSize(3)

        assertThat((state.actions[2] as MoreItem).actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY)
        assertThat((state.actions[2] as MoreItem).actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY_URL)
        assertThat((state.actions[2] as MoreItem).actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_TRASH)
        assertThat((state.actions[2] as MoreItem).actions).hasSize(3)
    }

    @Test
    fun `verify local draft actions on failed upload`() {
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            createFailedUploadUiState()
        )
        val state = createPostListItemUiState(
            post = createPostModel(status = POST_STATE_DRAFT, isLocalDraft = true)
        )

        assertThat(state.actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_EDIT)
        assertThat(state.actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_RETRY)
        assertThat(state.actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_MORE)
        assertThat(state.actions).hasSize(3)

        assertThat((state.actions[2] as MoreItem).actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY)
        assertThat((state.actions[2] as MoreItem).actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_DELETE)
        assertThat((state.actions[2] as MoreItem).actions).hasSize(2)
    }

    @Test
    fun `verify draft actions on failed upload without publishing rights`() {
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            createFailedUploadUiState()
        )
        val state = createPostListItemUiState(
            post = createPostModel(status = POST_STATE_DRAFT),
            capabilitiesToPublish = false
        )

        assertThat(state.actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_EDIT)
        assertThat(state.actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_RETRY)
        assertThat(state.actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_MORE)
        assertThat(state.actions).hasSize(3)

        assertThat((state.actions[2] as MoreItem).actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY)
        assertThat((state.actions[2] as MoreItem).actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY_URL)
        assertThat((state.actions[2] as MoreItem).actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_TRASH)
        assertThat((state.actions[2] as MoreItem).actions).hasSize(3)
    }

    @Test
    fun `verify local draft actions on failed upload without publishing rights`() {
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            createFailedUploadUiState()
        )

        val state = createPostListItemUiState(
            post = createPostModel(status = POST_STATE_DRAFT, isLocalDraft = true),
            capabilitiesToPublish = false
        )

        assertThat(state.actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_EDIT)
        assertThat(state.actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_RETRY)
        assertThat(state.actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_MORE)
        assertThat(state.actions).hasSize(3)

        assertThat((state.actions[2] as MoreItem).actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY)
        assertThat((state.actions[2] as MoreItem).actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_DELETE)
        assertThat((state.actions[2] as MoreItem).actions).hasSize(2)
    }

    @Test
    fun `verify published post actions`() {
        whenever(jetpackFeatureRemovalPhaseHelper.shouldShowPublishedPostStatsButton()).thenReturn(true)
        val state = createPostListItemUiState(
            post = createPostModel(status = POST_STATE_PUBLISH)
        )

        assertThat(state.actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_EDIT)
        assertThat(state.actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_VIEW)
        assertThat(state.actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_MORE)
        assertThat(state.actions).hasSize(3)

        val moreActions = (state.actions[2] as MoreItem).actions
        assertThat(moreActions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_STATS)
        assertThat(moreActions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY)
        assertThat(moreActions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_MOVE_TO_DRAFT)
        assertThat(moreActions[3].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY_URL)
        assertThat(moreActions[4].buttonType).isEqualTo(PostListButtonType.BUTTON_TRASH)
        assertThat(moreActions).hasSize(5)
    }

    @Test
    fun `verify published post actions when stats are not supported`() {
        val state = createPostListItemUiState(
            post = createPostModel(status = POST_STATE_PUBLISH),
            statsSupported = false
        )

        assertThat(state.actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_EDIT)
        assertThat(state.actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_VIEW)
        assertThat(state.actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_MORE)
        assertThat(state.actions).hasSize(3)

        val moreActions = (state.actions[2] as MoreItem).actions
        assertThat(moreActions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY)
        assertThat(moreActions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_MOVE_TO_DRAFT)
        assertThat(moreActions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY_URL)
        assertThat(moreActions[3].buttonType).isEqualTo(PostListButtonType.BUTTON_TRASH)
        assertThat(moreActions).hasSize(4)
    }

    @Test
    fun `given published post with stats access when jetpack removal phase then stats is in menu`() {
        whenever(jetpackFeatureRemovalPhaseHelper.shouldShowPublishedPostStatsButton()).thenReturn(true)
        val state = createPostListItemUiState(
            post = createPostModel(status = POST_STATE_PUBLISH)
        )

        assertThat(state.actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_EDIT)
        assertThat(state.actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_VIEW)
        assertThat(state.actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_MORE)
        assertThat(state.actions).hasSize(3)

        val moreActions = (state.actions[2] as MoreItem).actions
        assertThat(moreActions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_STATS)
        assertThat(moreActions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY)
        assertThat(moreActions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_MOVE_TO_DRAFT)
        assertThat(moreActions[3].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY_URL)
        assertThat(moreActions[4].buttonType).isEqualTo(PostListButtonType.BUTTON_TRASH)
        assertThat(moreActions).hasSize(5)
    }

    @Test
    fun `given published post with stats access when not jetpack removal phase then stats is not in menu`() {
        whenever(jetpackFeatureRemovalPhaseHelper.shouldShowPublishedPostStatsButton()).thenReturn(false)
        val state = createPostListItemUiState(
            post = createPostModel(status = POST_STATE_PUBLISH)
        )

        assertThat(state.actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_EDIT)
        assertThat(state.actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_VIEW)
        assertThat(state.actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_MORE)
        assertThat(state.actions).hasSize(3)

        val moreActions = (state.actions[2] as MoreItem).actions
        assertThat(moreActions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY)
        assertThat(moreActions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_MOVE_TO_DRAFT)
        assertThat(moreActions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY_URL)
        assertThat(moreActions[3].buttonType).isEqualTo(PostListButtonType.BUTTON_TRASH)
        assertThat(moreActions).hasSize(4)
    }

    @Test
    fun `verify published post with changes actions`() {
        val state = createPostListItemUiState(
            post = createPostModel(status = POST_STATE_PUBLISH, isLocallyChanged = true)
        )

        assertThat(state.actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_EDIT)
        assertThat(state.actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_PUBLISH)
        assertThat(state.actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_MORE)
        assertThat(state.actions).hasSize(3)

        val moreActions = (state.actions[2] as MoreItem).actions
        assertThat(moreActions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_PREVIEW)
        assertThat(moreActions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY)
        assertThat(moreActions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_MOVE_TO_DRAFT)
        assertThat(moreActions[3].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY_URL)
        assertThat(moreActions[4].buttonType).isEqualTo(PostListButtonType.BUTTON_TRASH)
        assertThat(moreActions).hasSize(5)
    }

    @Test
    fun `verify published post with failed upload actions`() {
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            createFailedUploadUiState()
        )
        val state = createPostListItemUiState(
            post = createPostModel(status = POST_STATE_PUBLISH, isLocallyChanged = true)
        )

        assertThat(state.actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_EDIT)
        assertThat(state.actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_RETRY)
        assertThat(state.actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_MORE)
        assertThat(state.actions).hasSize(3)

        val moreActions = (state.actions[2] as MoreItem).actions
        assertThat(moreActions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY)
        assertThat(moreActions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_MOVE_TO_DRAFT)
        assertThat(moreActions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY_URL)
        assertThat(moreActions[3].buttonType).isEqualTo(PostListButtonType.BUTTON_TRASH)
        assertThat(moreActions).hasSize(4)
    }

    @Test
    fun `verify trashed post actions`() {
        val state = createPostListItemUiState(
            post = createPostModel(status = POST_STATE_TRASHED)
        )

        assertThat(state.actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_MOVE_TO_DRAFT)
        assertThat(state.actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_DELETE_PERMANENTLY)
        assertThat(state.actions).hasSize(2)
    }

    @Test
    fun `verify scheduled post actions`() {
        val state = createPostListItemUiState(
            post = createPostModel(status = POST_STATE_SCHEDULED)
        )

        assertThat(state.actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_EDIT)
        assertThat(state.actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_VIEW)
        assertThat(state.actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_MORE)

        val moreActions = (state.actions[2] as MoreItem).actions
        assertThat(moreActions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY_URL)
        assertThat(moreActions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_TRASH)
        assertThat(moreActions).hasSize(2)
    }

    @Test
    fun `verify scheduled post with changes actions`() {
        val state = createPostListItemUiState(
            post = createPostModel(status = POST_STATE_SCHEDULED, isLocallyChanged = true)
        )

        assertThat(state.actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_EDIT)
        assertThat(state.actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_SYNC)
        assertThat(state.actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_MORE)
        assertThat(state.actions).hasSize(3)

        assertThat((state.actions[2] as MoreItem).actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_PREVIEW)
        assertThat((state.actions[2] as MoreItem).actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY_URL)
        assertThat((state.actions[2] as MoreItem).actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_TRASH)
        assertThat((state.actions[2] as MoreItem).actions).hasSize(3)
    }

    @Test
    fun `verify scheduled post with failed upload actions`() {
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            createFailedUploadUiState()
        )
        val state = createPostListItemUiState(
            post = createPostModel(status = POST_STATE_SCHEDULED, isLocallyChanged = true)
        )

        assertThat(state.actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_EDIT)
        assertThat(state.actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_RETRY)
        assertThat(state.actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_MORE)
        assertThat(state.actions).hasSize(3)

        val moreActions = (state.actions[2] as MoreItem).actions
        assertThat(moreActions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY_URL)
        assertThat(moreActions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_TRASH)
        assertThat(moreActions).hasSize(2)
    }

    @Test
    fun `verify post pending review with publishing rights actions`() {
        val state = createPostListItemUiState(
            post = createPostModel(status = POST_STATE_PENDING)
        )

        assertThat(state.actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_EDIT)
        assertThat(state.actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_PUBLISH)
        assertThat(state.actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_MORE)
        assertThat(state.actions).hasSize(3)
        assertThat((state.actions[2] as MoreItem).actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_VIEW)
        assertThat((state.actions[2] as MoreItem).actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY_URL)
        assertThat((state.actions[2] as MoreItem).actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_TRASH)
        assertThat((state.actions[2] as MoreItem).actions).hasSize(3)
    }

    @Test
    fun `verify post pending review without publishing rights`() {
        val state = createPostListItemUiState(
            post = createPostModel(status = POST_STATE_PENDING),
            capabilitiesToPublish = false
        )

        assertThat(state.actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_EDIT)
        assertThat(state.actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_VIEW)
        assertThat(state.actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_MORE)
        assertThat(state.actions).hasSize(3)

        val moreActions = (state.actions[2] as MoreItem).actions
        assertThat(moreActions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY_URL)
        assertThat(moreActions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_TRASH)
        assertThat(moreActions).hasSize(2)
    }

    @Test
    fun `verify published post with local changes eligible for auto upload`() {
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            UploadWaitingForConnection(PostStatus.PUBLISHED)
        )
        val state = createPostListItemUiState(
            post = createPostModel(status = POST_STATE_PUBLISH, isLocallyChanged = true)
        )

        assertThat(state.actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_EDIT)
        assertThat(state.actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_CANCEL_PENDING_AUTO_UPLOAD)
        assertThat(state.actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_MORE)
        assertThat(state.actions).hasSize(3)
        val moreActions = (state.actions[2] as MoreItem).actions
        assertThat(moreActions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_PREVIEW)
        assertThat(moreActions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY)
        assertThat(moreActions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_MOVE_TO_DRAFT)
        assertThat(moreActions[3].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY_URL)
        assertThat(moreActions[4].buttonType).isEqualTo(PostListButtonType.BUTTON_TRASH)
        assertThat(moreActions).hasSize(5)
    }

    @Test
    fun `verify scheduled post with local changes eligible for auto upload`() {
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            UploadWaitingForConnection(PostStatus.SCHEDULED)
        )
        val state = createPostListItemUiState(
            post = createPostModel(status = POST_STATE_SCHEDULED, isLocallyChanged = true)
        )

        assertThat(state.actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_EDIT)
        assertThat(state.actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_CANCEL_PENDING_AUTO_UPLOAD)
        assertThat(state.actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_MORE)
        assertThat(state.actions).hasSize(3)
        assertThat((state.actions[2] as MoreItem).actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_PREVIEW)
        assertThat((state.actions[2] as MoreItem).actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY_URL)
        assertThat((state.actions[2] as MoreItem).actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_TRASH)
        assertThat((state.actions[2] as MoreItem).actions).hasSize(3)
    }

    @Test
    fun `verify published private post with local changes eligible for auto upload`() {
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            UploadWaitingForConnection(PostStatus.PRIVATE)
        )
        val state = createPostListItemUiState(
            post = createPostModel(status = POST_STATE_PRIVATE, isLocallyChanged = true)
        )

        assertThat(state.actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_EDIT)
        assertThat(state.actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_CANCEL_PENDING_AUTO_UPLOAD)
        assertThat(state.actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_MORE)
        assertThat(state.actions).hasSize(3)
        assertThat((state.actions[2] as MoreItem).actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_PREVIEW)
        assertThat((state.actions[2] as MoreItem).actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY_URL)
        assertThat((state.actions[2] as MoreItem).actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_TRASH)
        assertThat((state.actions[2] as MoreItem).actions).hasSize(3)
    }

    @Test
    fun `verify draft with local changes eligible for auto upload`() {
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            UploadWaitingForConnection(PostStatus.DRAFT)
        )
        val state = createPostListItemUiState(
            post = createPostModel(status = POST_STATE_DRAFT, isLocallyChanged = true)
        )

        assertThat(state.actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_EDIT)
        assertThat(state.actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_CANCEL_PENDING_AUTO_UPLOAD)
        assertThat(state.actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_MORE)
        assertThat(state.actions).hasSize(3)

        assertThat((state.actions[2] as MoreItem).actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_PREVIEW)
        assertThat((state.actions[2] as MoreItem).actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY)
        assertThat((state.actions[2] as MoreItem).actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY_URL)
        assertThat((state.actions[2] as MoreItem).actions[3].buttonType).isEqualTo(PostListButtonType.BUTTON_TRASH)
        assertThat((state.actions[2] as MoreItem).actions).hasSize(4)
    }

    @Test
    fun `verify published with local changes eligible for auto upload after a failed upload`() {
        whenever(
            uploadUiStateUseCase.createUploadUiState(
                anyOrNull(),
                anyOrNull(),
                anyOrNull()
            )
        ).thenReturn(
            createFailedUploadUiState(
                UploadError(MediaError(MediaErrorType.AUTHORIZATION_REQUIRED)),
                isEligibleForAutoUpload = true
            )
        )
        val state = createPostListItemUiState(
            post = createPostModel(status = POST_STATE_PUBLISH, isLocallyChanged = true)
        )

        assertThat(state.actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_EDIT)
        assertThat(state.actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_CANCEL_PENDING_AUTO_UPLOAD)
        assertThat(state.actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_MORE)
        assertThat(state.actions).hasSize(3)

        val moreActions = (state.actions[2] as MoreItem).actions
        assertThat(moreActions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_RETRY)
        assertThat(moreActions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY)
        assertThat(moreActions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_MOVE_TO_DRAFT)
        assertThat(moreActions[3].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY_URL)
        assertThat(moreActions[4].buttonType).isEqualTo(PostListButtonType.BUTTON_TRASH)
        assertThat(moreActions).hasSize(5)
    }

    @Test
    fun `private label shown for private posts`() {
        val state = createPostListItemUiState(post = createPostModel(status = POST_STATE_PRIVATE))
        assertThat(state.data.statuses).contains(UiStringRes(R.string.post_status_post_private))
    }

    @Test
    fun `pending review label shown for posts pending review`() {
        val state = createPostListItemUiState(post = createPostModel(status = POST_STATE_PENDING))
        assertThat(state.data.statuses).contains(UiStringRes(R.string.post_status_pending_review))
    }

    @Test
    fun `local draft label shown for local posts`() {
        val state = createPostListItemUiState(post = createPostModel(isLocalDraft = true))
        assertThat(state.data.statuses).contains(UiStringRes(R.string.local_draft))
    }

    @Test
    fun `locally changed label shown for locally changed posts`() {
        val state = createPostListItemUiState(post = createPostModel(isLocallyChanged = true))
        assertThat(state.data.statuses).contains(UiStringRes(R.string.local_changes))
    }

    @Test
    fun `version conflict label shown for posts with version conflict`() {
        val state = createPostListItemUiState(unhandledConflicts = true)
        assertThat(state.data.statuses).contains(UiStringRes(R.string.local_post_is_conflicted))
    }

    @Test
    fun `unhandled auto-save label shown for posts with existing auto-save`() {
        val state = createPostListItemUiState(hasAutoSave = true)
        assertThat(state.data.statuses).contains(UiStringRes(R.string.local_post_autosave_revision_available))
    }

    @Test
    fun `uploading post label shown when the post is being uploaded`() {
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            UploadingPost(false)
        )
        val state = createPostListItemUiState()
        assertThat(state.data.statuses).contains(UiStringRes(R.string.post_uploading))
    }

    @Test
    fun `uploading draft label shown when the draft is being uploaded`() {
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            UploadingPost(true)
        )
        val state = createPostListItemUiState(
            post = createPostModel(status = POST_STATE_DRAFT)
        )
        assertThat(state.data.statuses).contains(UiStringRes(R.string.post_uploading_draft))
    }

    @Test
    fun `uploading media label shown when the post's media is being uploaded`() {
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            UploadingMedia(0)
        )
        val state = createPostListItemUiState()
        assertThat(state.data.statuses).contains(UiStringRes(R.string.uploading_media))
    }

    @Test
    fun `queued post label shown when the post has pending media uploads`() {
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            UploadQueued
        )
        val state = createPostListItemUiState()
        assertThat(state.data.statuses).contains(UiStringRes(R.string.post_queued))
    }

    @Test
    fun `queued post label shown when the post is queued for upload`() {
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            UploadQueued
        )
        val state = createPostListItemUiState()
        assertThat(state.data.statuses).contains(UiStringRes(R.string.post_queued))
    }

    @Test
    fun `error uploading media label shown when the media upload fails`() {
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            createFailedUploadUiState(uploadError = UploadError(MediaError(MediaErrorType.AUTHORIZATION_REQUIRED)))
        )
        val state = createPostListItemUiState()
        assertThat(state.data.statuses).contains(UiStringRes(R.string.error_media_recover_post))
    }

    @Test
    fun `generic error message shown when upload fails from unknown reason`() {
        val errorMsg = "testing error message"
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            createFailedUploadUiState(uploadError = UploadError(PostError(PostErrorType.GENERIC_ERROR, errorMsg)))
        )
        val state = createPostListItemUiState()
        assertThat(state.data.statuses).contains(UiStringRes(R.string.error_generic_error))
    }

    @Test
    fun `given a mix of info and error statuses, only the error status is shown`() {
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            createFailedUploadUiState(uploadError = UploadError(MediaError(MediaErrorType.AUTHORIZATION_REQUIRED)))
        )
        val state = createPostListItemUiState(
            post = createPostModel(isLocallyChanged = true, status = POST_STATE_PRIVATE)
        )
        assertThat(state.data.statuses).containsOnly(UiStringRes(R.string.error_media_recover_post))
    }

    @Test
    fun `media upload error shown with specific message for pending post eligible for auto-upload`() {
        whenever(
            uploadUiStateUseCase.createUploadUiState(
                anyOrNull(),
                anyOrNull(),
                anyOrNull()
            )
        ).thenReturn(
            createFailedUploadUiState(
                uploadError = UploadError(MediaError(MediaErrorType.AUTHORIZATION_REQUIRED)),
                isEligibleForAutoUpload = true
            )
        )
        val state = createPostListItemUiState(
            post = createPostModel(isLocallyChanged = true, status = POST_STATE_PENDING)
        )
        assertThat(state.data.statuses)
            .containsOnly(UiStringRes(R.string.error_media_recover_post_not_submitted_retrying))
    }

    @Test
    fun `media upload error shown with specific message for pending post not eligible for auto-upload`() {
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            createFailedUploadUiState(
                uploadError = UploadError(MediaError(MediaErrorType.AUTHORIZATION_REQUIRED)),
                isEligibleForAutoUpload = false,
                retryWillPushChanges = true
            )
        )
        val state = createPostListItemUiState(
            post = createPostModel(isLocallyChanged = true, status = POST_STATE_PENDING)
        )
        assertThat(state.data.statuses).containsOnly(UiStringRes(R.string.error_media_recover_post_not_submitted))
    }

    @Test
    fun `media upload error shown with specific message for scheduled post eligible for auto-upload`() {
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            createFailedUploadUiState(
                uploadError = UploadError(MediaError(MediaErrorType.AUTHORIZATION_REQUIRED)),
                isEligibleForAutoUpload = true
            )
        )
        val state = createPostListItemUiState(
            post = createPostModel(isLocallyChanged = true, status = POST_STATE_SCHEDULED)
        )
        assertThat(state.data.statuses)
            .containsOnly(UiStringRes(R.string.error_media_recover_post_not_scheduled_retrying))
    }

    @Test
    fun `media upload error shown with specific message for scheduled post not eligible for auto-upload`() {
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            createFailedUploadUiState(
                uploadError = UploadError(MediaError(MediaErrorType.AUTHORIZATION_REQUIRED)),
                isEligibleForAutoUpload = false,
                retryWillPushChanges = true
            )
        )
        val state = createPostListItemUiState(
            post = createPostModel(isLocallyChanged = true, status = POST_STATE_SCHEDULED)
        )
        assertThat(state.data.statuses).containsOnly(UiStringRes(R.string.error_media_recover_post_not_scheduled))
    }

    @Test
    fun `retrying media upload shown for draft eligible for auto-upload`() {
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            createFailedUploadUiState(
                uploadError = UploadError(MediaError(MediaErrorType.AUTHORIZATION_REQUIRED)),
                isEligibleForAutoUpload = true
            )
        )
        val state = createPostListItemUiState(
            post = createPostModel(isLocallyChanged = true, status = POST_STATE_DRAFT)
        )
        assertThat(state.data.statuses).containsOnly(UiStringRes(R.string.error_generic_error_retrying))
    }

    @Test
    fun `base media upload error shown for draft not eligible for auto-upload`() {
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            createFailedUploadUiState(
                uploadError = UploadError(MediaError(MediaErrorType.AUTHORIZATION_REQUIRED)),
                isEligibleForAutoUpload = false,
                retryWillPushChanges = false
            )
        )
        val state = createPostListItemUiState(
            post = createPostModel(isLocallyChanged = true, status = POST_STATE_DRAFT)
        )
        assertThat(state.data.statuses).containsOnly(UiStringRes(R.string.error_media_recover_post))
    }

    @Test
    fun `base upload error shown on GENERIC ERROR and not eligible for auto upload`() {
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            createFailedUploadUiState(
                uploadError = UploadError(PostError(PostErrorType.GENERIC_ERROR)),
                isEligibleForAutoUpload = false,
                retryWillPushChanges = false
            )
        )
        val state = createPostListItemUiState(
            post = createPostModel(isLocallyChanged = true, status = POST_STATE_DRAFT)
        )
        assertThat(state.data.statuses).containsOnly(UiStringRes(R.string.error_generic_error))
    }

    @Test
    fun `retrying upload shown for draft eligible for auto-upload`() {
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            createFailedUploadUiState(
                uploadError = UploadError(PostError(PostErrorType.GENERIC_ERROR)),
                isEligibleForAutoUpload = true,
                retryWillPushChanges = true
            )
        )
        val state = createPostListItemUiState(
            post = createPostModel(isLocallyChanged = true, status = POST_STATE_DRAFT)
        )
        assertThat(state.data.statuses).containsOnly(UiStringRes(R.string.error_generic_error_retrying))
    }

    @Test
    fun `multiple info labels are being shown together`() {
        val state = createPostListItemUiState(
            post = createPostModel(isLocallyChanged = true, status = POST_STATE_PRIVATE)
        )
        assertThat(state.data.statuses).contains(UiStringRes(R.string.local_changes))
        assertThat(state.data.statuses).contains(UiStringRes(R.string.post_status_post_private))
    }

    @Test
    fun `show progress when performing critical action`() {
        val state = createPostListItemUiState(performingCriticalAction = true)
        assertThat(state.data.progressBarUiState).isEqualTo(ProgressBarUiState.Indeterminate)
    }

    @Test
    fun `show progress when post is uploading or queued`() {
        whenever(
            uploadUiStateUseCase.createUploadUiState(
                anyOrNull(),
                anyOrNull(),
                anyOrNull()
            )
        ).thenReturn(UploadQueued)
        val state = createPostListItemUiState()
        assertThat(state.data.progressBarUiState).isEqualTo(ProgressBarUiState.Indeterminate)
    }

    @Test
    fun `show progress when uploading media`() {
        whenever(
            uploadUiStateUseCase.createUploadUiState(
                anyOrNull(),
                anyOrNull(),
                anyOrNull()
            )
        ).thenReturn(UploadingMedia(0))
        val state = createPostListItemUiState()
        assertThat(state.data.progressBarUiState).isInstanceOf(ProgressBarUiState.Determinate::class.java)
    }

    @Test
    fun `do not show progress when upload failed`() {
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            createFailedUploadUiState()
        )
        val state = createPostListItemUiState()
        assertThat(state.data.progressBarUiState).isEqualTo(ProgressBarUiState.Hidden)
    }

    @Test
    fun `show progress when upload failed and retrying`() {
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            UploadingMedia(0)
        )
        val state = createPostListItemUiState()
        assertThat(state.data.progressBarUiState).isInstanceOf(ProgressBarUiState.Determinate::class.java)
    }

    @Test
    fun `show overlay when performing critical action`() {
        val state = createPostListItemUiState(performingCriticalAction = true)
        assertThat(state.data.showOverlay).isTrue
    }

    @Test
    fun `show overlay when uploading post`() {
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            UploadingPost(false)
        )
        val state = createPostListItemUiState()
        assertThat(state.data.showOverlay).isTrue
    }

    @Test
    fun `show only delete and move to draft buttons on trashed posts`() {
        val state = createPostListItemUiState(post = createPostModel(POST_STATE_TRASHED))
        assertThat(state.actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_MOVE_TO_DRAFT)
        assertThat(state.actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_DELETE_PERMANENTLY)
        assertThat(state.actions).hasSize(2)
    }

    @Test
    fun `show delete button on local draft with a media upload error`() {
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            createFailedUploadUiState(uploadError = UploadError(MediaError(MediaErrorType.GENERIC_ERROR)))
        )
        val state = createPostListItemUiState(
            post = createPostModel(isLocalDraft = true, isLocallyChanged = true)
        )
        assertThat(state.actions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_EDIT)
        assertThat(state.actions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_RETRY)
        assertThat(state.actions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_MORE)
        assertThat(state.actions).hasSize(3)

        val moreActions = (state.actions[2] as MoreItem).actions
        assertThat(moreActions[0].buttonType).isEqualTo(PostListButtonType.BUTTON_COPY)
        assertThat(moreActions[1].buttonType).isEqualTo(PostListButtonType.BUTTON_MOVE_TO_DRAFT)
        assertThat(moreActions[2].buttonType).isEqualTo(PostListButtonType.BUTTON_DELETE)
        assertThat(moreActions).hasSize(3)
    }

    @Test
    fun `pending publish post label shown when post eligible for auto-upload`() {
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            UploadWaitingForConnection(PostStatus.PUBLISHED)
        )
        val state = createPostListItemUiState(
            post = createPostModel(isLocallyChanged = true, status = POST_STATE_PUBLISH)
        )
        assertThat((state.data.statuses[0] as UiStringRes).stringRes)
            .isEqualTo(R.string.post_waiting_for_connection_publish)
    }

    @Test
    fun `pending schedule label shown when post eligible for auto-upload`() {
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            UploadWaitingForConnection(PostStatus.SCHEDULED)
        )
        val state = createPostListItemUiState(
            post = createPostModel(isLocallyChanged = true, status = POST_STATE_SCHEDULED)
        )

        assertThat((state.data.statuses[0] as UiStringRes).stringRes)
            .isEqualTo(R.string.post_waiting_for_connection_scheduled)
    }

    @Test
    fun `pending publish private post label shown when post eligible for auto-upload`() {
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            UploadWaitingForConnection(PostStatus.PRIVATE)
        )
        val state = createPostListItemUiState(
            post = createPostModel(isLocallyChanged = true, status = POST_STATE_PRIVATE)
        )

        assertThat((state.data.statuses[0] as UiStringRes).stringRes)
            .isEqualTo(R.string.post_waiting_for_connection_private)
    }

    @Test
    fun `pending submit post label shown when post eligible for auto-upload`() {
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            UploadWaitingForConnection(PostStatus.PENDING)
        )
        val state = createPostListItemUiState(
            post = createPostModel(isLocallyChanged = true, status = POST_STATE_PENDING)
        )

        assertThat((state.data.statuses[0] as UiStringRes).stringRes)
            .isEqualTo(R.string.post_waiting_for_connection_pending)
    }

    @Test
    fun `local changes post label shown when draft eligible for auto-upload`() {
        whenever(uploadUiStateUseCase.createUploadUiState(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(
            UploadWaitingForConnection(PostStatus.DRAFT)
        )
        val state = createPostListItemUiState(
            post = createPostModel(isLocallyChanged = true, status = POST_STATE_DRAFT)
        )

        assertThat((state.data.statuses[0] as UiStringRes).stringRes)
            .isEqualTo(R.string.post_waiting_for_connection_draft)
    }

    @Test
    fun `date and author label contains both date and authorDisplayName`() {
        // Arrange
        val authorDisplayName = "John Novak"
        val state = createPostListItemUiState(
            post = createPostModel(
                authorDisplayName = authorDisplayName
            ), formattedDate = FORMATTER_DATE
        )

        // Assert
        assertThat(state.data.postInfo!!.size).isEqualTo(2)
        assertThat(state.data.postInfo!![0]).isEqualTo(UiStringText(FORMATTER_DATE))
        assertThat(state.data.postInfo!![1]).isEqualTo(UiStringText(authorDisplayName))
    }

    @Test
    fun `date and author label contains only date when author name is null`() {
        // Arrange
        val state = createPostListItemUiState(
            post = createPostModel(
                authorDisplayName = null
            ), formattedDate = FORMATTER_DATE
        )

        // Assert
        assertThat(state.data.postInfo!!.size).isEqualTo(1)
        assertThat(state.data.postInfo!![0]).isEqualTo(UiStringText(FORMATTER_DATE))
    }

    @Test
    fun `date and author label contains only date when author name is empty`() {
        // Arrange
        val state = createPostListItemUiState(
            post = createPostModel(
                authorDisplayName = ""
            ), formattedDate = FORMATTER_DATE
        )

        // Assert
        assertThat(state.data.postInfo!!.size).isEqualTo(1)
        assertThat(state.data.postInfo!![0]).isEqualTo(UiStringText(FORMATTER_DATE))
    }

    @Test
    fun `author name is displayed when author filter is EVERYONE`() {
        // Arrange
        val authorDisplayName = "John Novak"
        val state = createPostListItemUiState(
            authorFilterSelection = AuthorFilterSelection.EVERYONE,
            post = createPostModel(
                authorDisplayName = authorDisplayName
            ), formattedDate = FORMATTER_DATE
        )

        // Assert
        assertThat(state.data.postInfo!!.size).isEqualTo(2)
        assertThat(state.data.postInfo!![0]).isEqualTo(UiStringText(FORMATTER_DATE))
        assertThat(state.data.postInfo!![1]).isEqualTo(UiStringText(authorDisplayName))
    }

    @Test
    fun `author name is NOT displayed when author filter is ME`() {
        // Arrange
        val authorDisplayName = "John Novak"
        val state = createPostListItemUiState(
            authorFilterSelection = AuthorFilterSelection.ME,
            post = createPostModel(
                authorDisplayName = authorDisplayName
            ), formattedDate = FORMATTER_DATE
        )

        // Assert
        assertThat(state.data.postInfo!!.size).isEqualTo(1)
        assertThat(state.data.postInfo!![0]).isEqualTo(UiStringText(FORMATTER_DATE))
    }

    @Test
    fun `post status is displayed when isSearch == true`() {
        // Arrange
        val state = createPostListItemUiState(
            post = createPostModel(status = POST_STATE_PRIVATE),
            isSearch = true,
            formattedDate = FORMATTER_DATE
        )

        // Assert
        assertThat(state.data.postInfo!!.size).isEqualTo(2)
        assertThat(state.data.postInfo!![0]).isEqualTo(UiStringText(FORMATTER_DATE))
        assertThat(state.data.postInfo!![1]).isEqualTo(UiStringRes(R.string.post_status_post_private))
    }

    @Test
    fun `when a post is locally changed and is local draft only 'Local draft' label is displayed`() {
        // Arrange
        val state = createPostListItemUiState(
            post = createPostModel(isLocallyChanged = true, isLocalDraft = true)
        )

        // Assert
        assertThat(state.data.statuses).containsOnly(UiStringRes(R.string.local_draft))
    }

    @Test
    fun `when a post is sticky and no errors occurred, the 'Sticky' label is displayed`() {
        // Arrange
        val state = createPostListItemUiState(
            post = createPostModel(sticky = true)
        )

        // Assert
        assertThat(state.data.statuses).containsOnly(UiStringRes(R.string.post_status_sticky))
    }

    @Test
    fun `when a post is sticky and private, the labels 'Private' and 'Sticky' are displayed`() {
        // Arrange
        val state = createPostListItemUiState(
            post = createPostModel(status = POST_STATE_PRIVATE, sticky = true)
        )

        // Assert
        assertThat(state.data.statuses).contains(UiStringRes(R.string.post_status_post_private))
        assertThat(state.data.statuses).contains(UiStringRes(R.string.post_status_sticky))
    }

    private fun createPostModel(
        status: String = POST_STATE_PUBLISH,
        isLocalDraft: Boolean = false,
        isLocallyChanged: Boolean = false,
        authorDisplayName: String? = null,
        sticky: Boolean = false
    ): PostModel {
        val post = PostModel()
        post.setStatus(status)
        post.setIsLocalDraft(isLocalDraft)
        post.setIsLocallyChanged(isLocallyChanged)
        post.setAuthorDisplayName(authorDisplayName)
        post.setSticky(sticky)
        return post
    }

    private fun createPostListItemUiState(
        authorFilterSelection: AuthorFilterSelection = AuthorFilterSelection.EVERYONE,
        post: PostModel = PostModel(),
        site: SiteModel = SiteModel(),
        unhandledConflicts: Boolean = false,
        hasAutoSave: Boolean = false,
        capabilitiesToPublish: Boolean = true,
        statsSupported: Boolean = true,
        featuredImageUrl: String? = null,
        formattedDate: String = FORMATTER_DATE,
        performingCriticalAction: Boolean = false,
        isSearch: Boolean = false,
        onAction: (PostModel, PostListButtonType, AnalyticsTracker.Stat) -> Unit = { _, _, _ -> }
    ): PostListItemUiState = helper.createPostListItemUiState(
        authorFilterSelection,
        post = post,
        site = site,
        unhandledConflicts = unhandledConflicts,
        hasAutoSave = hasAutoSave,
        capabilitiesToPublish = capabilitiesToPublish,
        statsSupported = statsSupported,
        featuredImageUrl = featuredImageUrl,
        formattedDate = formattedDate,
        onAction = onAction,
        performingCriticalAction = performingCriticalAction,
        uploadStatusTracker = uploadStatusTracker,
        isSearch = isSearch,
        isSiteBlazeEligible = false
    )

    private fun createFailedUploadUiState(
        uploadError: UploadError = createGenericError(),
        isEligibleForAutoUpload: Boolean = false,
        retryWillPushChanges: Boolean = false
    ): UploadFailed {
        return UploadFailed(
            uploadError,
            isEligibleForAutoUpload = isEligibleForAutoUpload,
            retryWillPushChanges = retryWillPushChanges
        )
    }

    private fun createGenericError(): UploadError = UploadError(PostError(PostErrorType.GENERIC_ERROR))
}
