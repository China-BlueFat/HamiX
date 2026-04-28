package com.zayne.hamix

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.app.NotificationManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.zayne.hamix.ui.component.FloatingBottomBar
import com.zayne.hamix.ui.component.FloatingBottomBarItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Weeks
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.io.File

private val homeTabs = listOf("全部", "饮品", "餐食", "快递")
private val navItems = listOf("主页", "设置", "日志")
private val navIcons = listOf(
    MiuixIcons.Weeks,
    MiuixIcons.Settings,
    MiuixIcons.File
)

private fun getLogoDirectory(context: android.content.Context): File? {
    val directory = context.getExternalFilesDir("logos") ?: return null
    if (!directory.exists()) {
        directory.mkdirs()
    }
    return directory
}

private fun getLogoFileByLogoName(context: android.content.Context, logoName: String?): File? {
    if (logoName.isNullOrBlank()) return null
    val directory = getLogoDirectory(context) ?: return null
    return directory.listFiles()
        ?.firstOrNull { file ->
            file.isFile && file.nameWithoutExtension.equals(logoName, ignoreCase = false)
        }
}

class MainActivity : ComponentActivity() {
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            PaddleOcrHelper.getInstance(applicationContext).initAsync()

            val hasRoot = withContext(Dispatchers.IO) {
                RootUtils.hasRootAccess()
            }

            if (hasRoot) {
                startService(Intent(this@MainActivity, RootCaptureService::class.java))
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "无 Root 权限",
                    Toast.LENGTH_LONG
                ).show()
            }

            AppSettings.getHamiItems(applicationContext).forEach { item ->
                IslandNotificationHelper.notifyPickup(applicationContext, item)
            }
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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

    var text by rememberSaveable { mutableStateOf("") }
    var summary by rememberSaveable { mutableStateOf("") }
    var logoName by rememberSaveable { mutableStateOf<String?>(null) }
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

    val buttonColors = ButtonDefaults.buttonColors(
        color = Color(0xFFF2F2F2),
        contentColor = Color(0xFF222222),
        disabledColor = Color(0XFF3482FF),
        disabledContentColor = Color.White
    )

    suspend fun decodeBitmapFromUri(uri: Uri) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val maxSide = maxOf(info.size.width, info.size.height)
                if (maxSide > 2048) {
                    val scale = 2048f / maxSide
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1)
                    )
                }
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            try {
                val bitmap = decodeBitmapFromUri(uri)
                val result = withContext(Dispatchers.IO) {
                    val helper = TextRecognitionHelper(context)
                    helper.recognizeAll(bitmap)
                }

                text = result.code ?: ""
                selectedCategory = result.type
                summary = result.brand ?: ""
                logoName = result.logoName
                ocrLogText = result.fullText

                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(hamiItems) {
        AppSettings.saveHamiItems(context, hamiItems)
    }

    BackHandler(enabled = selectedIndex != 0) {
        selectedIndex = 0
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enableFloatingBottomBar = AppSettings.isFloatingBottomBarEnabled(context)
                enableFloatingBottomBarBlur = AppSettings.isGlassEffectEnabled(context)
                hamiItems = AppSettings.getHamiItems(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context1: android.content.Context?, intent: Intent?) {
                if (intent?.action == NotificationActionReceiver.ACTION_ITEMS_CHANGED) {
                    hamiItems = AppSettings.getHamiItems(context)
                } else if (intent?.action == AutoCaptureWorkflow.ACTION_CAPTURE_RESULT) {
                    ocrLogText = intent.getStringExtra(AutoCaptureWorkflow.EXTRA_FULL_TEXT).orEmpty()
                    if (showCreateSheet) {
                        text = intent.getStringExtra(AutoCaptureWorkflow.EXTRA_CODE).orEmpty()
                        selectedCategory = intent.getStringExtra(AutoCaptureWorkflow.EXTRA_CATEGORY)
                        summary = intent.getStringExtra(AutoCaptureWorkflow.EXTRA_SUMMARY).orEmpty()
                        logoName = intent.getStringExtra(AutoCaptureWorkflow.EXTRA_LOGO_NAME)
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(NotificationActionReceiver.ACTION_ITEMS_CHANGED)
            addAction(AutoCaptureWorkflow.ACTION_CAPTURE_RESULT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    fun openAppearanceSettingsPage() {
        context.startActivity(Intent(context, AppearanceSettingsActivity::class.java))
    }

    fun openFeatureSettingsPage() {
        context.startActivity(Intent(context, FeatureSettingsActivity::class.java))
    }

    fun openAboutPage() {
        context.startActivity(Intent(context, AboutActivity::class.java))
    }

    fun resetCreateSheet() {
        showCreateSheet = false
        text = ""
        summary = ""
        logoName = null
        selectedCategory = null
    }

    fun clearCreateSheetFields() {
        text = ""
        summary = ""
        logoName = null
        selectedCategory = null
    }

    fun saveItemAndNotify(item: HamiItem) {
        hamiItems = hamiItems + item
        IslandNotificationHelper.notifyPickup(context, item)
    }

    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = when (selectedIndex) {
                    0 -> "哈米记"
                    1 -> "设置"
                    else -> "日志"
                }
            )
        },
        floatingActionButton = {
            if (selectedIndex == 0) {
                FloatingActionButton(
                    modifier = Modifier.padding(end = 32.dp, bottom = 46.dp),
                    onClick = {
                        clearCreateSheetFields()
                        showCreateSheet = true
                    },
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
                        onDone = { item ->
                            hamiItems = hamiItems - item
                            val notificationManager =
                                context.getSystemService(NotificationManager::class.java)
                            notificationManager?.cancel(item.id.toInt())
                        },
                        onCardClick = { item ->
                            IslandNotificationHelper.notifyPickup(context, item)
                        }
                    )

                    1 -> MainSettingsPage(
                        onOpenAppearanceSettings = { openAppearanceSettingsPage() },
                        onOpenFeatureSettings = { openFeatureSettingsPage() },
                        onOpenAboutPage = { openAboutPage() }
                    )

                    else -> OcrLogPage(ocrLogText)
                }
            }
        }
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
                            imageVector = MiuixIcons.Heavy.AddCircle,
                            contentDescription = "操作"
                        )
                    }
                }
            )

            Column {
                SmallTitle(
                    insideMargin = PaddingValues(3.dp, 8.dp),
                    text = "类别"
                )

                Row(
                    modifier = Modifier
                        .padding(horizontal = 0.dp)
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    homeTabs.drop(1).forEach { category ->
                        val isSelected = selectedCategory == category

                        Button(
                            onClick = { selectedCategory = category },
                            enabled = !isSelected,
                            colors = buttonColors,
                            modifier = Modifier
                                .height(36.dp)
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
                                category = selectedCategory ?: "其他",
                                date = currentDate,
                                summary = summary,
                                logoName = logoName
                            )
                            saveItemAndNotify(newItem)
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
private fun MainPage(
    items: List<HamiItem>,
    onDone: (HamiItem) -> Unit,
    onCardClick: (HamiItem) -> Unit
) {
    var selectedTabIndex by remember { mutableStateOf(0) }

    val filteredItems = if (selectedTabIndex == 0) {
        items
    } else {
        items.filter { it.category == homeTabs[selectedTabIndex] }
    }
    val context = LocalContext.current

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .padding(bottom = 22.dp)
        ) {
            TabRowWithContour(
                modifier = Modifier.fillMaxWidth(),
                tabs = homeTabs,
                maxWidth = 2.dp,
                selectedTabIndex = selectedTabIndex,
                onTabSelected = { selectedTabIndex = it },
                minWidth = 20.dp,
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
                        insideMargin = PaddingValues(
                            start = 16.dp,
                            top = 8.dp,
                            end = 16.dp,
                            bottom = 8.dp
                        ),
                        onClick = { onCardClick(item) },
                        onLongPress = { onDone(item) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
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
                                Text(
                                    modifier = Modifier.padding(top = 8.dp),
                                    fontWeight = FontWeight.Light,
                                    text = "${item.date}·${item.category}",
                                    fontSize = 12.sp,
                                    style = MiuixTheme.textStyles.subtitle
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,

                            ) {
                                val logoFile = remember(item.logoName) {
                                    getLogoFileByLogoName(context, item.logoName)
                                }
                                val logoBitmap = remember(logoFile?.absolutePath) {
                                    logoFile?.let { BitmapFactory.decodeFile(it.absolutePath) }
                                }
                                if (logoBitmap != null) {
                                    Image(
                                        bitmap = logoBitmap.asImageBitmap(),
                                        contentDescription = item.summary,
                                        modifier = Modifier
                                            .size(68.dp)
                                            .padding(bottom = 2.dp)
                                            .wrapContentSize(align = Alignment.Center),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(70.dp))
                                }
                                Button(
                                    modifier = Modifier
                                        .width(52.dp)
                                        .height(28.dp),
                                    insideMargin = PaddingValues(
                                        horizontal = 2.dp,
                                        vertical = 2.dp
                                    ),
                                    colors = ButtonDefaults.buttonColorsPrimary(),
                                    onClick = { onDone(item) }
                                ) {
                                    Text(
                                        text = "完成",
                                        fontSize = 13.sp
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
