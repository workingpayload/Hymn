package com.example.musicsm.ui.support

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.musicsm.R
import com.example.musicsm.ui.theme.Coral
import com.example.musicsm.ui.theme.OnDark
import com.example.musicsm.ui.theme.OnDarkVariant
import com.example.musicsm.ui.theme.OverlayTint

/**
 * The UPI "buy me a coffee" chooser, mirroring the Vercel landing page: pick an app (which
 * deep-links straight into it) or copy the UPI ID. Each app has its own URL scheme; if the chosen
 * app isn't installed we fall back to a generic `upi://` chooser so any UPI app can handle it.
 *
 * Opened from Settings, and every couple of days on its own (see [SupportPromptViewModel]).
 */
@Composable
fun SupportDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val params = "pa=${Uri.encode(UPI_VPA)}&pn=${Uri.encode(UPI_NAME)}&cu=INR"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${stringResource(R.string.settings_coffee_title)} 🥤") },
        text = {
            Column {
                Text(
                    stringResource(R.string.coffee_lede),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnDarkVariant,
                )
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    UpiAppButton(stringResource(R.string.coffee_gpay)) { launchUpi(context, "tez://upi/pay?$params", params) }
                    UpiAppButton(stringResource(R.string.coffee_phonepe)) { launchUpi(context, "phonepe://pay?$params", params) }
                    UpiAppButton(stringResource(R.string.coffee_paytm)) { launchUpi(context, "paytmmp://pay?$params", params) }
                    UpiAppButton(stringResource(R.string.coffee_any)) { launchUpi(context, "upi://pay?$params", params) }
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.coffee_upi_label), style = MaterialTheme.typography.labelMedium, color = Coral)
                Text(UPI_VPA, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = OnDark)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.coffee_kofi_hint), style = MaterialTheme.typography.bodyMedium, color = OnDarkVariant)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.coffee_kofi),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(Coral)
                        .clickable { openKofi(context) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        },
        // Copy leaves the dialog open (you may still want to pick an app); Close dismisses it.
        dismissButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(UPI_VPA))
                Toast.makeText(context, context.getString(R.string.coffee_copied), Toast.LENGTH_SHORT).show()
            }) { Text(stringResource(R.string.coffee_copy)) }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun UpiAppButton(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = OnDark,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(OverlayTint.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** Deep-links into [primary]'s UPI app; falls back to a generic `upi://` chooser, then a toast. */
private fun launchUpi(context: Context, primary: String, params: String) {
    val opened = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(primary)))
    }.isSuccess
    if (opened) return
    val chooser = runCatching {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay?$params")),
                context.getString(R.string.coffee_pay_with),
            ),
        )
    }.isSuccess
    if (!chooser) {
        Toast.makeText(context, context.getString(R.string.coffee_no_upi), Toast.LENGTH_LONG).show()
    }
}

// Same payee as the Vercel landing page's support dialog.
private const val UPI_VPA = "rs91963@pingpay"
private const val UPI_NAME = "Raj"
private const val KOFI_URL = "https://ko-fi.com/workingpayload"

/** Ko-fi takes cards and PayPal, for supporters without UPI. */
private fun openKofi(context: Context) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(KOFI_URL)))
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.coffee_no_browser), Toast.LENGTH_SHORT).show()
    }
}
