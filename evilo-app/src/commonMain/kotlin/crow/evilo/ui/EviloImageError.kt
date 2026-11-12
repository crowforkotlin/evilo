package crow.evilo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import crow.evilo.app.resources.*
import crow.evilo.app.LocalAppDiagnostics
import crow.evilo.network.ktor.NetworkPolicyViolation
import crow.evilo.feature.reader.ImageFailure
import crow.evilo.feature.reader.ImageFailureKind
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun imageFailureLabel(failure: ImageFailure): String = when (failure.kind) {
    ImageFailureKind.HTTP -> failure.httpStatus?.let { stringResource(Res.string.image_http_failed, it) }
        ?: stringResource(Res.string.image_network_failed)
    else -> stringResource(when (failure.kind) {
        ImageFailureKind.INVALID_ADDRESS -> Res.string.image_invalid_address
        ImageFailureKind.NETWORK -> Res.string.image_network_failed
        ImageFailureKind.PERMISSION -> Res.string.image_permission_denied
        ImageFailureKind.FORMAT -> Res.string.image_unsupported
        ImageFailureKind.LIMIT -> Res.string.image_limit
        ImageFailureKind.SESSION_CLOSED -> Res.string.image_session_closed
        else -> Res.string.image_unknown_error
    })
}

@Composable
private fun imageFailureHint(failure: ImageFailure): String = stringResource(when {
    failure.redirectRejected -> Res.string.image_error_redirect_hint
    else -> when (failure.kind) {
        ImageFailureKind.INVALID_ADDRESS -> Res.string.image_error_address_hint
        ImageFailureKind.PERMISSION -> Res.string.image_error_permission_hint
        ImageFailureKind.FORMAT -> Res.string.image_error_format_hint
        ImageFailureKind.LIMIT -> Res.string.image_error_limit_hint
        ImageFailureKind.SESSION_CLOSED -> Res.string.image_error_session_hint
        else -> Res.string.image_error_retry_hint
    }
})

@Suppress("DEPRECATION")
@Composable
internal fun EviloImageErrorDetails(failure: ImageFailure, onDismiss: () -> Unit, onPermissions: (() -> Unit)? = null, onRetry: (() -> Unit)? = null) {
    val clipboard = LocalClipboardManager.current
    val category = stringResource(Res.string.image_error_category, imageFailureLabel(failure))
    val address = stringResource(Res.string.image_error_address, failure.redactedAddress ?: stringResource(Res.string.image_error_address_omitted))
    val status = failure.httpStatus?.let { stringResource(Res.string.image_error_http, it) }
    val bytes = failure.maxBytes?.let { stringResource(Res.string.image_error_byte_limit, it) }
    val dimensions = failure.maxDimension?.let { stringResource(Res.string.image_error_dimension_limit, it, failure.maxPixels ?: 0) }
    val hint = imageFailureHint(failure)
    val policy = if (LocalAppDiagnostics.current) failure.policyViolation?.let {
        stringResource(Res.string.image_error_policy, stringResource(when (it) {
            NetworkPolicyViolation.INVALID_URL -> Res.string.image_policy_invalid_url
            NetworkPolicyViolation.CLEARTEXT_NOT_APPROVED -> Res.string.image_policy_cleartext
            NetworkPolicyViolation.URL_CREDENTIALS -> Res.string.image_policy_credentials
            NetworkPolicyViolation.URL_PATH -> Res.string.image_policy_path
            NetworkPolicyViolation.ORIGIN_OR_PATH_NOT_APPROVED -> Res.string.image_policy_origin
            NetworkPolicyViolation.METHOD_NOT_APPROVED -> Res.string.image_policy_method
            NetworkPolicyViolation.REQUEST_BODY -> Res.string.image_policy_body
            NetworkPolicyViolation.HEADERS_NOT_APPROVED -> Res.string.image_policy_headers
        }))
    } else null
    val header = if (LocalAppDiagnostics.current) failure.rejectedHeader?.let {
        stringResource(Res.string.image_error_header, it)
    } else null
    val diagnostic = listOfNotNull(category, address, status, bytes, dimensions, policy, header, hint).joinToString("\n")
    AlertDialog(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(Res.string.image_error_details)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Medium)) {
                SelectionContainer { Text(diagnostic, style = MaterialTheme.typography.bodyMedium) }
                if (failure.canRetry && onRetry != null) TextButton(onClick = { onDismiss(); onRetry() }) { Text(stringResource(Res.string.source_retry)) }
                if (failure.kind == ImageFailureKind.PERMISSION && onPermissions != null) TextButton(onClick = {
                    onDismiss(); onPermissions()
                }) { Text(stringResource(Res.string.plugin_permissions)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.home_dismiss)) } },
        dismissButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(diagnostic)) }) { Text(stringResource(Res.string.image_error_copy)) } })
}
