package com.zayne.hamix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.AddCircle
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

class FeatureSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiuixTheme {
                FeatureSettingsPage(onBack = { finish() })
            }
        }
    }
}

@Composable
fun FeatureSettingsPage(onBack: () -> Unit) {
    var selectedIndex by remember { mutableStateOf(0) }
    val options = listOf("本地OCR识别", "AI识别")
    var text by remember { mutableStateOf("") }
    var isHidden by remember { mutableStateOf(true) }


    Scaffold(
        topBar = { TopAppBar(title = "功能设置") }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = MiuixTheme.colorScheme.background,
                shape = RoundedCornerShape(16.dp)
            ) {
                OverlayDropdownPreference(
                    title = "选择识别方式",
                    items = options,
                    selectedIndex = selectedIndex,
                    onSelectedIndexChange = { selectedIndex = it }
                )
            }
            if (selectedIndex == 1) {
                Column() {
                    SmallTitle(
                        text = "API地址"
                    )
                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        label = "请输入链接",
                        useLabelAsPlaceholder = true
                    )
                    SmallTitle(
                        text = "API密钥"
                    )
                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        label = "请输入密钥",
                        useLabelAsPlaceholder = true,
                        trailingIcon = {
                            IconButton(
                                modifier = Modifier.padding(end = 12.dp),
                                onClick = {
                                    isHidden = !isHidden
                                },
                                backgroundColor = Color.Transparent,
                                minWidth = 32.dp,
                                minHeight = 32.dp
                            ) {
                                Icon(
                                    imageVector = if (isHidden) {
                                        MiuixIcons.Heavy.Hide
                                    } else {
                                        MiuixIcons.Heavy.Show
                                    },
                                    contentDescription = if (isHidden) "隐藏" else "显示"
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
