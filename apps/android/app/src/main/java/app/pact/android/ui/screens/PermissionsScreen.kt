package app.pact.android.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import app.pact.android.shield.ShieldService
import app.pact.android.ui.PrimaryAction
import app.pact.android.ui.ScreenTitle
import app.pact.android.ui.theme.PactColors

/**
 * Onboarding for the three permissions the shield needs, each with a plain-spoken
 * rationale (Play prominent-disclosure-friendly) and a button to the exact
 * Settings surface. Grant state refreshes on resume.
 */
@Composable
fun PermissionsScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Bump to force a re-read of permission state when we come back from Settings.
    var refresh by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val hasUsage = remember(refresh) { ShieldService.hasUsageAccess(context) }
    val hasOverlay = remember(refresh) { ShieldService.hasOverlayPermission(context) }
    val hasNotif = remember(refresh) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) true
        else context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh++ }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            ScreenTitle(
                "Set up the shield",
                "Pact needs three things to replace your distracting apps with the table while a pact is locked. You can always leave a pact in two taps.",
            )
            Spacer(Modifier.height(24.dp))

            PermissionCard(
                title = "Usage access",
                granted = hasUsage,
                rationale = "Pact reads only which app is in the foreground, so it knows when to show the table instead of a feed. It never logs your history and nothing leaves your phone.",
                buttonLabel = "Open usage access settings",
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )
            Spacer(Modifier.height(12.dp))

            PermissionCard(
                title = "Draw over other apps",
                granted = hasOverlay,
                rationale = "Lets Pact show the night screen on top of a distracting app. The emergency dialer and calls are never covered.",
                buttonLabel = "Open overlay settings",
                onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )
            Spacer(Modifier.height(12.dp))

            PermissionCard(
                title = "Notifications",
                granted = hasNotif,
                rationale = "So an ask from the table reaches you with Allow / Not now buttons, even with the screen off.",
                buttonLabel = "Allow notifications",
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )

            Spacer(Modifier.height(28.dp))
            PrimaryAction(label = "Done", onClick = onDone)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    granted: Boolean,
    rationale: String,
    buttonLabel: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PactColors.OffWhite),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (granted) Icons.Filled.CheckCircle
                    else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = if (granted) "Granted" else "Not granted",
                    tint = if (granted) PactColors.Allow else PactColors.MutedOnLight,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.size(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = PactColors.InkOnLight)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                rationale,
                style = MaterialTheme.typography.bodyMedium,
                color = PactColors.MutedOnLight,
            )
            if (!granted) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                    Text(buttonLabel)
                }
            }
        }
    }
}
