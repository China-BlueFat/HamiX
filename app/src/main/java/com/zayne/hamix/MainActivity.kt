package com.zayne.hamix

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.zayne.hamix.ui.component.FloatingBottomBar
import com.zayne.hamix.ui.component.FloatingBottomBarItem
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Weeks
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiuixTheme {
                HomeScreen()
            }
        }
    }
}

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var selectedIndex by rememberSaveable { mutableStateOf(0) }
    var enableFloatingBottomBar by rememberSaveable {
        mutableStateOf(AppSettings.isFloatingBottomBarEnabled(context))
    }
    var enableFloatingBottomBarBlur by rememberSaveable {
        mutableStateOf(AppSettings.isGlassEffectEnabled(context))
    }
    var showAddPanel by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = showAddPanel || selectedIndex != 0) {
        if (showAddPanel) {
            showAddPanel = false
        } else {
            selectedIndex = 0
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enableFloatingBottomBar = AppSettings.isFloatingBottomBarEnabled(context)
                enableFloatingBottomBarBlur = AppSettings.isGlassEffectEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val items = listOf("主页", "设置")
    val icons = listOf(
        MiuixIcons.Weeks,
        MiuixIcons.Settings
    )

    fun openSettingsPage() {
        context.startActivity(Intent(context, SettingsActivity::class.java))
    }

    fun openAboutPage() {
        context.startActivity(Intent(context, AboutActivity::class.java))
    }

    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(if (showAddPanel) 2.dp else 0.dp)
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = if (selectedIndex == 0) {
                            "Hamix"
                        } else {
                            "设置"
                        }
                    )
                },
                floatingActionButton = {
                    if (selectedIndex == 0) {
                        FloatingActionButton(
                            modifier = Modifier.padding(end = 32.dp, bottom = 46.dp),
                            onClick = { showAddPanel = true },
                            containerColor = Color(0xFF3482FF),
                            minWidth = 56.dp,
                            minHeight = 56.dp
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Heavy.Add,
                                contentDescription = "新增",
                                tint = Color.White
                            )
                        }
                    }
                },
                bottomBar = {
                    if (enableFloatingBottomBar) {
                        Box(Modifier.fillMaxWidth()) {
                            FloatingBottomBar(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .padding(bottom = 12.dp),
                                selectedIndex = { selectedIndex },
                                onSelected = { index -> selectedIndex = index },
                                backdrop = backdrop,
                                tabsCount = items.size,
                                isBlurEnabled = enableFloatingBottomBarBlur && Build.VERSION.SDK_INT >= 33
                            ) {
                                items.forEachIndexed { index, label ->
                                    FloatingBottomBarItem(
                                        onClick = { selectedIndex = index },
                                        modifier = Modifier.defaultMinSize(minWidth = 76.dp)
                                    ) {
                                        Icon(
                                            imageVector = icons[index],
                                            contentDescription = label,
                                            tint = MiuixTheme.colorScheme.onSurface
                                        )
                                        Text(text = label, fontSize = 11.sp, lineHeight = 14.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        NavigationBar {
                            items.forEachIndexed { index, label ->
                                NavigationBarItem(
                                    modifier = Modifier.weight(1f),
                                    icon = icons[index],
                                    label = label,
                                    selected = selectedIndex == index,
                                    onClick = { selectedIndex = index }
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .layerBackdrop(backdrop)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (selectedIndex) {
                            0 -> MainPage()
                            1 -> MainSettingsPage(
                                onOpenAppearanceSettings = { openSettingsPage() },
                                onOpenAboutPage = { openAboutPage() }
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showAddPanel,
            enter = fadeIn(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(140)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.08f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showAddPanel = false
                    }
            )
        }

        AnimatedVisibility(
            visible = showAddPanel,
            enter = fadeIn(animationSpec = tween(220)) + scaleIn(
                initialScale = 0.92f,
                animationSpec = tween(220)
            ),
            exit = fadeOut(animationSpec = tween(140)) + scaleOut(
                targetScale = 0.95f,
                animationSpec = tween(140)
            ),
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.86f)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MiuixTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                            text = "新增",
                        fontSize = 22.sp
                    )
                    Text(
                            text = "这里可以放新增表单或快捷操作。",
                        color = Color.Gray
                    )

                    Surface(
                        onClick = { showAddPanel = false },
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF3482FF)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "关闭",
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainPage() {
    val tabs = listOf(
        "全部",
        "饮品",
        "餐食",
        "快递"
    )
    var selectedTabIndex by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(0.72f),
            contentAlignment = Alignment.CenterStart
        ) {
            TabRowWithContour(
                modifier = Modifier.fillMaxWidth(),
                tabs = tabs,
                maxWidth = 2.dp,
                selectedTabIndex = selectedTabIndex,
                onTabSelected = { selectedTabIndex = it },
                colors = TabRowDefaults.tabRowColors(
                    backgroundColor = Color(0xFFE8E8E8),
                    contentColor = Color.Gray,
                    selectedBackgroundColor = Color.White,
                    selectedContentColor = Color.Black
                )
            )
        }
    }
}

@Composable
private fun MainSettingsPage(
    onOpenAppearanceSettings: () -> Unit,
    onOpenAboutPage: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.height(72.dp),
            color = MiuixTheme.colorScheme.background,
            shape = RoundedCornerShape(16.dp)
        ) {
            ArrowPreference(
                title = "外观设置",
                summary = "界面外观高级设置",
                startAction = {
                    Icon(
                        imageVector = MiuixIcons.Theme,
                        contentDescription = "外观设置",
                        tint = MiuixTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .height(80.dp)
                    )
                },
                onClick = onOpenAppearanceSettings
            )
        }

        Surface(
            modifier = Modifier.height(56.dp),
            color = MiuixTheme.colorScheme.background,
            shape = RoundedCornerShape(16.dp)
        ) {
            ArrowPreference(
                title = "关于",
                startAction = {
                    Icon(
                        imageVector = MiuixIcons.File,
                        contentDescription = "关于",
                        tint = MiuixTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .height(80.dp)
                    )
                },
                onClick = onOpenAboutPage
            )
        }
    }
}
