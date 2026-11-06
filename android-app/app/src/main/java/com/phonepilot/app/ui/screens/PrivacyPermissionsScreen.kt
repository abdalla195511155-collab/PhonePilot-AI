package com.phonepilot.app.ui.screens

import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phonepilot.app.components.AppScaffold
import com.phonepilot.app.components.ScreenHeader
import com.phonepilot.app.recording.PhonePilotAccessibilityService
import com.phonepilot.app.ui.theme.Space
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import android.content.Intent
import androidx.compose.ui.platform.LocalContext

@Composable
fun PrivacyPermissionsScreen(onNavigateTo: (String) -> Unit) {
    val context = LocalContext.current
    val isAccessibilityEnabled = PhonePilotAccessibilityService.isServiceEnabled(context)
    val privacyFilterEnabled = remember { mutableStateOf(true) }
    val dataEncryptionEnabled = remember { mutableStateOf(true) }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Space.medium),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.medium),
    ) {
        ScreenHeader(
            title = "الخصوصية والأذونات",
            subtitle = "إدارة إعدادات الخصوصية والأذونات المطلوبة للتطبيق",
        )

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = Space.small),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = 1.dp,
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.padding(horizontal = Space.medium, vertical = Space.medium),
            ) {
                Text(
                    text = "إذن خدمة الوصولية",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(Space.small))
                Text(
                    text = "يتطلب PhonePilot AI إذن خدمة الوصولية لتسجيل لمسات الشاشة والأحداث. يرجى تفعيل الخدمة من الإعدادات.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(Space.medium))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Shield,
                        contentDescription = null,
                        tint = if (isAccessibilityEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    androidx.compose.foundation.Spacer(modifier = Modifier.width(Space.small))
                    Text(
                        text = if (isAccessibilityEnabled) "خدمة الوصولية مفعلة" else "خدمة الوصولية غير مفعلة",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    androidx.compose.foundation.Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            } catch (e: Exception) {
                                // تجاهل الخطأ إذا تعذر فتح الإعدادات
                            }
                        },
                    ) {
                        Text(text = if (isAccessibilityEnabled) "إدارة" else "تفعيل")
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = Space.small),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = 1.dp,
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.padding(horizontal = Space.medium, vertical = Space.medium),
            ) {
                Text(
                    text = "إعدادات الخصوصية",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(Space.small))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(
                        checked = privacyFilterEnabled.value,
                        onCheckedChange = { privacyFilterEnabled.value = it },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = "تصفية البيانات الحساسة",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "إخفاء كلمات المرور والبيانات الشخصية من التسجيلات",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(Space.small))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(
                        checked = dataEncryptionEnabled.value,
                        onCheckedChange = { dataEncryptionEnabled.value = it },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = "تشفير البيانات المخزنة",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "تشفير قاعدة البيانات محلياً على الجهاز",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        androidx.compose.material3.Text(
            text = "PhonePilot AI لا يرسل أي بيانات إلى خوادم خارجية. جميع التسجيلات تُخزن محلياً على جهازك فقط.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
        )
    }
}
