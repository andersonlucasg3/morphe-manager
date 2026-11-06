/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.apkdownload

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.domain.installer.InstallerFileProvider
import app.morphe.manager.ui.screen.shared.DialogAction
import app.morphe.manager.ui.screen.shared.UserMessages
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Download
import org.koin.compose.koinInject

/**
 * Everything the in-app download needs from the screen that offers it, behind one call.
 *
 * The capture WebView, the download, the replay headers, the progress sheet and the hand-off to
 * the APK picker all live in this package. What a screen has to do is name the app and the page,
 * and say where a fetched file goes; keeping the rest here is also what keeps the diff in the
 * screens upstream owns down to a line or two, which is what decides whether this fork rebases
 * cleanly onto a new release.
 *
 * @param downloadUrl The page to open. Null while the redirect has not landed, because the link
 *     on hand until then is the unfollowed one, which is not a page.
 * @param appName Named in the capture screen, so the user knows which app they are fetching.
 * @param onApkReady Receives the fetched archive as a content URI, to go through the same intake
 *     a hand-picked APK does.
 * @return The action to offer, or null while there is no page worth opening.
 */
@Composable
fun rememberInAppApkDownload(
    downloadUrl: String?,
    appName: String,
    onApkReady: (Uri) -> Unit,
): (() -> Unit)? {
    val context = LocalContext.current
    val messages: UserMessages = koinInject()
    val scope = rememberCoroutineScope()

    return rememberApkDownloadCaptureAction(
        downloadUrl = downloadUrl,
        appName = appName,
    ) { capture ->
        ApkCaptureDownloader.start(
            context = context,
            scope = scope,
            capture = capture,
            appName = appName,
        ) { file ->
            if (file == null) {
                // Names the reason while this is being tested. The exception's own message says
                // more than "it failed" ever could, and reaching an adb console is not something
                // to ask of whoever is holding the phone
                val reason = ApkCaptureDownloader.lastError
                if (reason.isNullOrBlank()) {
                    messages.show(R.string.apk_capture_download_failed)
                } else {
                    messages.show(
                        context.getString(R.string.apk_capture_download_failed_reason, reason)
                    )
                }
                return@start
            }
            // Through the ordinary intake, so every check that guards a hand-picked APK applies.
            // Handed over as a content URI through the provider the installers already use, and
            // then dropped: the intake copies it into a file of its own, which would otherwise
            // leave the download behind as a second copy of the same archive.
            onApkReady(InstallerFileProvider.getUriForFile(context, file))
            ApkCaptureDownloader.release(file)
        }
    }
}

/**
 * The footer entry that starts an in-app download.
 *
 * Built here rather than at the call site so the screen upstream owns only has to place one
 * expression in its action list, which is the part that keeps rebasing onto a new release cheap.
 */
@Composable
fun inAppDownloadAction(onClick: () -> Unit, enabled: Boolean): DialogAction = DialogAction(
    text = stringResource(R.string.home_download_instructions_in_app),
    onClick = onClick,
    icon = MaterialSymbols.Rounded.Download,
    enabled = enabled,
)

/**
 * Draws the transfer while it runs, over whatever screen is showing.
 *
 * Rendered by the screen that hosts the dialogs rather than by the dialog that starts a
 * download, because the dialog closes as soon as the file leaves the page while the transfer it
 * began runs on for minutes afterwards.
 */
@Composable
fun ApkDownloadProgressHost() {
    ApkDownloadProgressOverlay(onCancel = { ApkCaptureDownloader.cancel() })
}
