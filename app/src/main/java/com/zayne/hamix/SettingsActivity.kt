package com.zayne.hamix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.Rename
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiuixTheme {
                SettingsScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var enableFloatingBottomBar by rememberSaveable {
        mutableStateOf(AppSettings.isFloatingBottomBarEnabled(context))
    }
    var enableFloatingBottomBarBlur by rememberSaveable {
        mutableStateOf(AppSettings.isGlassEffectEnabled(context))
    }

    AppearanceSettingsPage(
        enableFloatingBottomBar = enableFloatingBottomBar,
        enableFloatingBottomBarBlur = enableFloatingBottomBarBlur,
        onSetFloatingBottomBarEnabled = { enabled ->
            enableFloatingBottomBar = enabled
            AppSettings.setFloatingBottomBarEnabled(context, enabled)
        },
        onSetGlassEffectEnabled = { enabled ->
            enableFloatingBottomBarBlur = enabled
            AppSettings.setGlassEffectEnabled(context, enabled)
        },
        onBack = onBack
    )
}

@Composable
fun AppearanceSettingsPage(
    enableFloatingBottomBar: Boolean,
    enableFloatingBottomBarBlur: Boolean,
    onSetFloatingBottomBarEnabled: (Boolean) -> Unit,
    onSetGlassEffectEnabled: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = "\u5916\u89c2\u8bbe\u7f6e") }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "\u8fd4\u56de",
                modifier = Modifier.clickable { onBack() }
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SwitchPreference(
                    title = "\u60ac\u6d6e\u5e95\u680f",
                    summary = "\u4f7f\u7528 Apple \u98ce\u683c\u7684\u60ac\u6d6e\u5e95\u680f",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Rename,
                            contentDescription = "\u60ac\u6d6e\u5e95\u680f",
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    },
                    checked = enableFloatingBottomBar,
                    onCheckedChange = onSetFloatingBottomBarEnabled
                )

                SwitchPreference(
                    title = "\u6db2\u6001\u73bb\u7483",
                    summary = "\u542f\u7528\u60ac\u6d6e\u5e95\u680f\u7684\u6db2\u6001\u73bb\u7483\u6548\u679c",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Album,
                            contentDescription = "\u6db2\u6001\u73bb\u7483",
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    },
                    checked = enableFloatingBottomBarBlur,
                    onCheckedChange = onSetGlassEffectEnabled
                )
            }
        }
    }
}
