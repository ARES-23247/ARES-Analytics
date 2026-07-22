package com.ares.analytics.ui.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.service.AuthState
import com.ares.analytics.ui.components.forms.AresTextField
import com.ares.analytics.ui.theme.*

@Composable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun AuthStep(
    authState: AuthState,
    googleClientId: String,
    googleClientSecret: String,
    onClientIdChange: (String) -> Unit,
    onClientSecretChange: (String) -> Unit,
    onSignInClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
        border = androidx.compose.foundation.BorderStroke(1.dp, AresBorder),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (authState is AuthState.Authenticated) {
                val user = authState
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = AresGreen
                    )
                    Column {
                        Text(
                            "Connected to Cloud Roster",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AresTextPrimary
                        )
                        Text(
                            "Signed in as ${user.displayName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = AresTextSecondary
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = AresTextTertiary
                        )
                        Column {
                            Text(
                                "Offline Setup Mode",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = AresTextPrimary
                            )
                            Text(
                                "Sign in to load official team robots.",
                                style = MaterialTheme.typography.labelSmall,
                                color = AresTextSecondary
                            )
                            Spacer(Modifier.height(6.dp))
                            AresTextField(
                                value = googleClientId,
                                onValueChange = onClientIdChange,
                                placeholder = "GCP Client ID (Optional)",
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary, fontSize = 10.sp),
                                modifier = Modifier.width(180.dp).height(38.dp),
                                placeholderFontSize = 10.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            AresTextField(
                                value = googleClientSecret,
                                onValueChange = onClientSecretChange,
                                placeholder = "Client Secret (Optional)",
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary, fontSize = 10.sp),
                                modifier = Modifier.width(180.dp).height(38.dp),
                                placeholderFontSize = 10.sp
                            )
                        }
                    }

                    if (authState is AuthState.Error) {
                        Text(
                            text = authState.message,
                            color = AresError,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 32.dp)
                        )
                    }
                }

                Button(
                    onClick = onSignInClick,
                    colors = ButtonDefaults.buttonColors(containerColor = AresCyan),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Sign In", color = AresBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}
