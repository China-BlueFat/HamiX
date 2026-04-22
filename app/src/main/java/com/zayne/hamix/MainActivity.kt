package com.zayne.hamix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.AddCircle
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Weeks
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

private val homeTabs = listOf(
    "全部",
    "饮品",
    "餐食",
    "快递"
)

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

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
        }
    }
    val buttonColors = ButtonDefaults.buttonColors(
        color = Color(0xFFF2F2F2),
        contentColor = Color(0xFF222222),
        disabledColor = Color(0XFF3482FF),
        disabledContentColor = Color.White
    )
    var text by remember { mutableStateOf("") }
    var showCreateSheet by rememberSaveable { mutableStateOf(false) }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }


    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var selectedIndex by rememberSaveable { mutableStateOf(0) }
    var enableFloatingBottomBar by rememberSaveable {
        mutableStateOf(AppSettings.isFloatingBottomBarEnabled(context))
    }
    var enableFloatingBottomBarBlur by rememberSaveable {
        mutableStateOf(AppSettings.isGlassEffectEnabled(context))
    }

    BackHandler(enabled = selectedIndex != 0) {
        selectedIndex = 0
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
                    onClick = { showCreateSheet = true },
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
    WindowBottomSheet(
        show = showCreateSheet,
        title = "添加待取",
        onDismissRequest = { showCreateSheet = false }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                label = "输入取餐码/取件码",
                trailingIcon = {
                    IconButton(
                        modifier = Modifier.padding(end = 12.dp),
                        onClick = {
                            pickImageLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        backgroundColor = Color.Transparent,
                        minWidth = 32.dp,
                        minHeight = 32.dp
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Heavy.AddCircle, // 这里换成你真正想要的图标
                            contentDescription = "操作"
                        )
                    }
                }
            )
            Text("类别")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                homeTabs.drop(1).forEach { category ->
                    val isSelected = selectedCategory == category

                    Button(
                        onClick = {
                            selectedCategory = category
                        },
                        enabled = !isSelected,
                        colors = buttonColors,
                        modifier = Modifier.height(36.dp)
                            .width(68.dp),
                        cornerRadius = 999.dp,
                        insideMargin = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                    ) {
                        Text(category)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { /* 处理点击事件 */ },
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Text("取消")
                }

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { /* 处理点击事件 */ },
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    Text("保存")
                }
            }

        }
    }

}

@Composable
private fun MainPage() {
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
                tabs = homeTabs,
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
