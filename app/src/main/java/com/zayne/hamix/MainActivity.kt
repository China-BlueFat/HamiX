package com.zayne.hamix
import android.R.attr.fontWeight
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.unit.sp

import android.content.Intent
import android.graphics.ImageDecoder
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.AddCircle
import top.yukonga.miuix.kmp.icon.extended.All
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Weeks
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.TextButton
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.runtime.CompositionLocalProvider

private val homeTabs = listOf(
    "全部",
    "饮品",
    "餐食",
    "快递"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            PaddleOcrHelper.getInstance(applicationContext).initAsync()
        }

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
    val scope = rememberCoroutineScope()
    val buttonColors = ButtonDefaults.buttonColors(
        color = Color(0xFFF2F2F2),
        contentColor = Color(0xFF222222),
        disabledColor = Color(0XFF3482FF),
        disabledContentColor = Color.White
    )
    var text by rememberSaveable { mutableStateOf("") }
    var summary by rememberSaveable { mutableStateOf("") }
    var ocrLogText by rememberSaveable { mutableStateOf("") }
    var showCreateSheet by rememberSaveable { mutableStateOf(false) }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedIndex by rememberSaveable { mutableStateOf(0) }
    var enableFloatingBottomBar by rememberSaveable {
        mutableStateOf(AppSettings.isFloatingBottomBarEnabled(context))
    }
    var enableFloatingBottomBarBlur by rememberSaveable {
        mutableStateOf(AppSettings.isGlassEffectEnabled(context))
    }
    var hamiItems by remember { mutableStateOf(AppSettings.getHamiItems(context)) }

    // 当 hamiItems 改变时自动保存
    LaunchedEffect(hamiItems) {
        AppSettings.saveHamiItems(context, hamiItems)
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(
                            ImageDecoder.createSource(context.contentResolver, uri)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    }

                    val helper = TextRecognitionHelper(context)
                    val result = helper.recognizeAll(bitmap)
                    text = result.code ?: ""
                    selectedCategory = result.type
                    summary = result.brand ?: ""
                    ocrLogText = result.fullText

                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
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

    val navItems = listOf("主页", "设置", "日志")
    val navIcons = listOf(
        MiuixIcons.Weeks,
        MiuixIcons.Settings,
        MiuixIcons.File
    )

    fun openAppearanceSettingsPage() {
        context.startActivity(Intent(context, AppearanceSettingsActivity::class.java))
    }

    fun openFeatureSettingsPage() {
        context.startActivity(Intent(context, FeatureSettingsActivity::class.java))
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
                        tabsCount = navItems.size,
                        isBlurEnabled = enableFloatingBottomBarBlur && Build.VERSION.SDK_INT >= 33
                    ) {
                        navItems.forEachIndexed { index, label ->
                            FloatingBottomBarItem(
                                onClick = { selectedIndex = index },
                                modifier = Modifier.defaultMinSize(minWidth = 76.dp)
                            ) {
                                Icon(
                                    imageVector = navIcons[index],
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
                    navItems.forEachIndexed { index, label ->
                        NavigationBarItem(
                            modifier = Modifier.weight(1f),
                            icon = navIcons[index],
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
                    0 -> MainPage(
                        items = hamiItems,
                        onDone = { item -> hamiItems = hamiItems - item }
                    )
                    1 -> MainSettingsPage(
                        onOpenAppearanceSettings = { openAppearanceSettingsPage() },
                        onOpenFeatureSettings = { openFeatureSettingsPage() },
                        onOpenAboutPage = { openAboutPage() }
                    )
                    2 -> OcrLogPage(ocrLogText)
                }
            }
        }
    }
    fun resetCreateSheet() {
        showCreateSheet = false
        text = ""
        summary = ""
        selectedCategory = null
    }

    WindowBottomSheet(
        show = showCreateSheet,
        title = "添加待取",
        onDismissRequest = { resetCreateSheet() }
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
            Column {
                SmallTitle(insideMargin = PaddingValues(3.dp, 8.dp),text = "类别")

                Row(
                    modifier = Modifier.padding(horizontal = 0.dp).padding(bottom = 16.dp),
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
                                .width(58.dp),
                            cornerRadius = 999.dp,
                            insideMargin = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                        ) {
                            Text(category)
                        }
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(
                        top = 0.dp,
                        bottom = 12.dp,
                        start = 2.dp,
                        end = 2.dp
                    )
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { resetCreateSheet() },
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Text("取消")
                }


                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (text.isNotBlank()) {
                            val sdf = SimpleDateFormat("M月d日", Locale.getDefault())
                            val currentDate = sdf.format(Calendar.getInstance().time)
                            val newItem = HamiItem(
                                code = text,
                                category = selectedCategory ?: "其它",
                                date = currentDate,
                                summary = summary
                            )
                            hamiItems = hamiItems + newItem
                            resetCreateSheet()
                        }
                    },
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    Text("保存")
                }
            }

        }
    }

}

@Composable
private fun OcrLogPage(logText: String) {
    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MiuixTheme.colorScheme.background,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "OCR完整文本",
                fontSize = 20.sp
            )
            Text(
                text = if (logText.isBlank()) "暂无识别日志" else logText
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainPage(items: List<HamiItem>, onDone: (HamiItem) -> Unit) {
    var selectedTabIndex by remember { mutableStateOf(0) }

    val filteredItems = if (selectedTabIndex == 0) {
        items
    } else {
        items.filter { it.category == homeTabs[selectedTabIndex] }
    }

    Column (
    ) { Box(
        modifier = Modifier.fillMaxWidth(0.72f)
                            .padding(bottom = 22.dp)
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
        LazyColumn {
            items(
                items = filteredItems,
                key = { it.id }
            ) { item ->
                val viewConfiguration = LocalViewConfiguration.current
                val customViewConfiguration = remember(viewConfiguration) {
                    object : ViewConfiguration by viewConfiguration {
                        override val longPressTimeoutMillis: Long
                            get() = 1000L
                    }
                }
                CompositionLocalProvider(LocalViewConfiguration provides customViewConfiguration) {
                    Card(
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .animateItem(),
                        showIndication = true,
                        insideMargin = PaddingValues(16.dp),
                        onClick = { /* 可以点击进入详情，暂时留空 */ },
                        onLongPress = { onDone(item) }
                    ) {
                        Column() {
                            Text(
                                text = item.code,
                                color = MiuixTheme.colorScheme.primary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                style = MiuixTheme.textStyles.main
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = item.summary,
                                style = MiuixTheme.textStyles.body2
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    modifier = Modifier
                                        .padding(top = 8.dp),
                                    fontWeight = FontWeight.Light,
                                    text = "${item.date}·${item.category}",
                                    fontSize = 12.sp,
                                    style = MiuixTheme.textStyles.subtitle
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    modifier = Modifier
                                        .width(56.dp)
                                        .height(32.dp),
                                    insideMargin = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.buttonColorsPrimary(),
                                    onClick = { onDone(item) }
                                ) {
                                    Text(
                                        text = "完成",
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun MainSettingsPage(
    onOpenAppearanceSettings: () -> Unit,
    onOpenFeatureSettings: () -> Unit,
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
            modifier = Modifier.height(72.dp),
            color = MiuixTheme.colorScheme.background,
            shape = RoundedCornerShape(16.dp)
        ) {
            ArrowPreference(
                title = "功能设置",
                summary = "自定义识别方式",
                startAction = {
                    Icon(
                        imageVector = MiuixIcons.GridView,
                        contentDescription = "功能设置",
                        tint = MiuixTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .height(80.dp)
                    )
                },
                onClick = onOpenFeatureSettings
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
