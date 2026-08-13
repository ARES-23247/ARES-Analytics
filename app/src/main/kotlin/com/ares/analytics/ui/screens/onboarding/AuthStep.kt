package com.ares.analytics.ui.screens.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ares.analytics.service.AuthState
import com.ares.analytics.ui.components.forms.AresTextField
import com.ares.analytics.ui.theme.AresBackground
import com.ares.analytics.ui.theme.AresBorder
import com.ares.analytics.ui.theme.AresCyan
import com.ares.analytics.ui.theme.AresError
import com.ares.analytics.ui.theme.AresGreen
import com.ares.analytics.ui.theme.AresGold
import com.ares.analytics.ui.theme.AresSurfaceElevated
import com.ares.analytics.ui.theme.AresTextPrimary
import com.ares.analytics.ui.theme.AresTextSecondary
import com.ares.analytics.ui.theme.AresTextTertiary

/** Optional Google Drive setup. Local onboarding never depends on this card. */
@Composable
fun AuthStep(
    authState: AuthState,
    googleClientId: String,
    googleClientSecret: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onClientIdChange: (String) -> Unit,
    onClientSecretChange: (String) -> Unit,
    onSignInClick: () -> Unit,
) {
    var developerFieldsExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
        border = androidx.compose.foundation.BorderStroke(1.dp, AresBorder),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onExpandedChange(!expanded) },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (authState is AuthState.Authenticated) Icons.Default.CloudDone else Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = if (authState is AuthState.Authenticated) AresGreen else AresTextTertiary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("Cloud sync (optional)", color = AresTextPrimary, fontWeight = FontWeight.Bold)
                    Text(
                        if (authState is AuthState.Authenticated) "Signed in as ${authState.displayName}"
                        else "Skip this to keep logs and settings on this computer.",
                        color = AresTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Hide cloud settings" else "Show cloud settings",
                    tint = AresTextSecondary,
                )
            }

            if (expanded) {
                Text(
                    "Cloud sync copies laptop-managed data to Google Drive. Robots still work offline and never upload directly.",
                    color = AresTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )

                if (authState !is AuthState.Authenticated) {
                    if (googleClientId.isBlank()) {
                        Text(
                            "To enable cloud sync, create a Google Desktop OAuth client with the Drive API enabled and paste its client ID below. You may skip this step.",
                            color = AresGold,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(
                        onClick = onSignInClick,
                        enabled = googleClientId.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = AresCyan),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text("Sign in with Google", color = AresBackground, fontWeight = FontWeight.Bold)
                    }
                }

                if (authState is AuthState.Error) {
                    Text(authState.message, color = AresError, style = MaterialTheme.typography.bodySmall)
                }

                OutlinedButton(onClick = { developerFieldsExpanded = !developerFieldsExpanded }) {
                    Text(if (developerFieldsExpanded) "Hide OAuth setup" else "Configure Google OAuth")
                }
                if (developerFieldsExpanded) {
                    Text(
                        "ARES does not ship a shared Google credential. Use a Desktop app OAuth client owned by your team; a client secret is normally unnecessary.",
                        color = AresTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    AresTextField(
                        value = googleClientId,
                        onValueChange = onClientIdChange,
                        label = "Google OAuth client ID",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(2.dp))
                    AresTextField(
                        value = googleClientSecret,
                        onValueChange = onClientSecretChange,
                        label = "Google OAuth client secret (optional)",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
