package com.phonepilot.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.Shield
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepilot.app.data.SettingsRepository
import com.phonepilot.app.recording.PhonePilotAccessibilityService
import com.phonepilot.app.repository.RecordingRepository
import com.phonepilot.app.ui.components.ScreenHeader
import com.phonepilot.app.ui.theme.Space

@Composable
fun PrivacyPermissionsScreen(onNavigateTo: (String) -> Unit) {
    val context = LocalContext.current
    var accessibilityEnabled by remember {
        mutableStateOf(PhonePilotAccessibilityService.isServiceEnabled(context))
    }
    val serviceConnected by RecordingRepository.serviceConnected.collectAsStateWithLifecycle()
    val privacyFilterEnabled by SettingsRepository.privacyFilterEnabled.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        accessibilityEnabled = PhonePilotAccessibilityService.isServiceEnabled(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Space.medium),
        verticalArrangement = Arrangement.spacedBy(Space.medium),
    ) {
        ScreenHeader(
            title = "الخصوصية والصلاحيات",
            subtitle = "إدارة الصلاحيات وتصفية البيانات الحساسة",
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = Space.medium, vertical = Space.medium),
                verticalArrangement = Arrangement.spacedBy(Space.small),
            ) {
                Text(
                    text = "خدمة الوصولية",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "PhonePilot AI يحتاج خدمة الوصولية لتسجيل لمسات الشاشة والأحداث.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.small),
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Shield,
                        contentDescription = null,
                        tint = if (accessibilityEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    Text(
                        text = if (accessibilityEnabled) "مفعّلة" else "غير مفعّلة",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (serviceConnected) "• متصلة" else "• غير متصلة",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        } catch (error: Exception) {
                            // بعض الأجهزة تمنع فتح الإعدادات مباشرة
                        }
                    },
                ) {
                    Text(text = if (accessibilityEnabled) "إدارة الصلاحية" else "تفعيل الخدمة")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = Space.medium, vertical = Space.medium),
                verticalArrangement = Arrangement.spacedBy(Space.small),
            ) {
                Text(
                    text = "إعدادات الخصوصية",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(
                        checked = privacyFilterEnabled,
                        onCheckedChange = { SettingsRepository.setPrivacyFilterEnabled(it) },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "تصفية البيانات الحساسة",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "إخفاء كلمات المرور والبيانات الشخصية من التسجيلات",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!privacyFilterEnabled) {
                    Text(
                        text = "تحذير: عند التعطيل ستُخزَّن النصوص الحساسة كما هي في قاعدة البيانات المحلية.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(
                        checked = false,
                        onCheckedChange = null,
                        enabled = false,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "تشفير البيانات المخزنة",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "غير مفعّل بعد — التشفير الكامل في مرحلة لاحقة",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Text(
            text = "PhonePilot AI لا يرسل أي بيانات إلى خوادم خارجية. جميع التسجيلات تُخزن محلياً على جهازك فقط.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
