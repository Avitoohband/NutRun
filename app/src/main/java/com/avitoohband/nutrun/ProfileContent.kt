package com.avitoohband.nutrun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.avitoohband.nutrun.billing.BillingUiState
import com.avitoohband.nutrun.domain.UserProfile

@Composable
fun ProfileOverviewContent(
    profile: UserProfile,
    entitlementLabel: String,
    darkMode: Boolean,
    authenticatedUserId: String?,
    billing: BillingUiState,
    accountDeletionState: AccountDeletionUiState,
    billingActionsEnabled: Boolean,
    onBack: () -> Unit,
    onEditHealth: () -> Unit,
    onNotifications: () -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onPurchaseMonthly: () -> Unit,
    onPurchaseAnnual: () -> Unit,
    onRestorePurchases: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onClearAccountDeletionState: () -> Unit,
    onRunTutorial: () -> Unit,
    privacyPolicyUrl: String = "",
    termsOfServiceUrl: String = "",
    onOpenPrivacyPolicy: () -> Unit,
    onOpenTermsOfService: () -> Unit,
    onDevToggleSubscription: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var deleteEmailConfirmation by remember { mutableStateOf("") }
    val isDemo = isDemoAccount(authenticatedUserId)
    val deleteEnabled = deleteEmailConfirmation.trim().equals(profile.email, ignoreCase = true) &&
        !accountDeletionState.busy

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = {
                confirmDelete = false
                deleteEmailConfirmation = ""
                onClearAccountDeletionState()
            },
            title = { Text("Delete account and data?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (isDemo) {
                            "Demo account data is removed from this device only. Cloud records are not involved."
                        } else {
                            "This permanently removes local logs. The configured backend is also responsible for deleting cloud records, routes, and MCP tokens."
                        }
                    )
                    Text("Type your email to confirm:")
                    OutlinedTextField(
                        value = deleteEmailConfirmation,
                        onValueChange = { deleteEmailConfirmation = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile-delete-email-confirm"),
                        label = { Text("Email") },
                        singleLine = true,
                        enabled = !accountDeletionState.busy
                    )
                    accountDeletionState.error?.let { error ->
                        NutRunInlineMessage(error, NutRunMessageKind.ERROR, testTag = "profile-delete-error")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onDeleteAccount,
                    enabled = deleteEnabled,
                    modifier = Modifier.testTag("profile-delete-confirm"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    if (accountDeletionState.busy) {
                        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    } else {
                        Text("Delete")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        deleteEmailConfirmation = ""
                        onClearAccountDeletionState()
                    },
                    enabled = !accountDeletionState.busy
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    NutRunScreen(
        title = "Profile",
        onBack = onBack,
        modifier = modifier.testTag("profile-screen")
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(horizontal = NutRunSpacing.lg)
                .testTag("profile-list"),
            contentPadding = PaddingValues(vertical = NutRunSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NutRunSpacing.md)
        ) {
            item {
                ProfileSectionHeading("Account", testTag = "profile-section-account")
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(NutRunSpacing.md)) {
                        Text(profile.email, fontWeight = FontWeight.SemiBold)
                        Text(
                            entitlementLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                ProfileSectionHeading("Health", testTag = "profile-section-health")
            }
            item {
                NutRunSettingsRow(
                    title = "Edit health details",
                    subtitle = "Birth date, measurements, and calorie target",
                    icon = Icons.Default.Edit,
                    onClick = onEditHealth,
                    testTag = "profile-edit-health"
                )
            }
            item {
                ProfileSectionHeading("Notifications", testTag = "profile-section-notifications")
            }
            item {
                NutRunSettingsRow(
                    title = "Notification settings",
                    subtitle = "Hydration, training, and supplement reminders",
                    icon = Icons.Default.WaterDrop,
                    onClick = onNotifications,
                    testTag = "profile-notification-settings"
                )
            }
            item {
                ProfileSectionHeading("Appearance", testTag = "profile-section-appearance")
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile-dark-theme"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(NutRunSpacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Dark theme", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Use dark colors across NutRun",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = darkMode,
                            onCheckedChange = onDarkModeChange
                        )
                    }
                }
            }
            if (!isDemo) {
                item {
                    ProfileSectionHeading("Subscription", testTag = "profile-section-subscription")
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            Modifier.padding(NutRunSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(NutRunSpacing.sm)
                        ) {
                            Text("Ad-free subscription", fontWeight = FontWeight.SemiBold)
                            billing.message?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = onPurchaseMonthly,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile-subscribe-monthly"),
                                enabled = billingActionsEnabled
                            ) {
                                Text("Monthly ad-free")
                            }
                            OutlinedButton(
                                onClick = onPurchaseAnnual,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile-subscribe-annual"),
                                enabled = billingActionsEnabled
                            ) {
                                Text("Annual ad-free")
                            }
                            TextButton(
                                onClick = onRestorePurchases,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile-restore-purchases"),
                                enabled = billingActionsEnabled
                            ) {
                                Text("Restore purchases")
                            }
                        }
                    }
                }
            }
            if (onDevToggleSubscription != null) {
                item {
                    OutlinedButton(
                        onClick = onDevToggleSubscription,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile-dev-toggle-subscription")
                    ) {
                        Text("Toggle test subscription")
                    }
                }
            }
            item {
                ProfileSectionHeading("Help", testTag = "profile-section-help")
            }
            if (privacyPolicyUrl.startsWith("https://")) {
                item {
                    NutRunSettingsRow(
                        title = "Privacy policy",
                        subtitle = "Read how NutRun handles your data",
                        icon = Icons.Default.Description,
                        onClick = onOpenPrivacyPolicy,
                        testTag = "profile-privacy-policy"
                    )
                }
            }
            if (termsOfServiceUrl.startsWith("https://")) {
                item {
                    NutRunSettingsRow(
                        title = "Terms of service",
                        subtitle = "Review NutRun usage terms",
                        icon = Icons.Default.Gavel,
                        onClick = onOpenTermsOfService,
                        testTag = "profile-terms-of-service"
                    )
                }
            }
            item {
                NutRunSettingsRow(
                    title = "Run tutorial again",
                    subtitle = "Replay the five-step NutRun overview",
                    icon = Icons.Default.Help,
                    onClick = onRunTutorial,
                    testTag = "profile-run-tutorial"
                )
            }
            item {
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile-sign-out")
                ) {
                    Text("Sign out")
                }
            }
            item {
                ProfileSectionHeading("Data", testTag = "profile-section-data")
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    )
                ) {
                    Column(Modifier.padding(NutRunSpacing.md)) {
                        Text(
                            "Delete account",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            "Permanently remove your account and stored data.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { confirmDelete = true },
                            modifier = Modifier.testTag("profile-delete-account")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Text("Delete account")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSectionHeading(
    title: String,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Text(
        title,
        modifier = modifier.testTag(testTag),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}
