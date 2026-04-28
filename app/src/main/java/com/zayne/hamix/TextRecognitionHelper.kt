package com.zayne.hamix

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import java.io.File

data class OcrRule(
    val brand: String,
    val category: String,
    val brandRegexList: List<String>,
    val logoName: String? = null,
    val codeRegex: String,
    val matchText: String? = null
)

class TextRecognitionHelper(private val context: Context) {

    private val barcodeScanner = BarcodeScanning.getClient()

    val paddleOcr = PaddleOcrHelper.getInstance(context)
    private val ocrRules by lazy { loadOcrRules() }

    private val drinkBrands = listOf("星巴克", "瑞幸", "喜茶", "奈雪", "霸王茶姬", "茶百道", "蜜雪冰城", "一点点", "古茗", "Manner", "山楂奶绿", "取茶", "奶茶", "茶颜悦色")
    private val foodBrands = listOf("麦当劳", "肯德基", "KFC", "汉堡王", "塔斯汀", "老乡鸡", "华莱士")
    private val expressBrandKeywords = listOf(
        "邮政", "中国邮政", "申通", "中通", "圆通", "韵达", "顺丰", "极兔", "德邦",
        "菜鸟", "驿站", "丰巢", "包裹"
    )
    private val homePageKeywords = listOf("我的", "首页", "会员码", "到店取餐", "点单", "会员", "我的订单")

    private fun getExternalRulesFile(): File? {
        val directory = context.getExternalFilesDir("config") ?: return null
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return File(directory, "ocr_rules.json")
    }

    private fun ensureExternalRulesFile(): File? {
        val rulesFile = getExternalRulesFile() ?: return null
        if (!rulesFile.exists()) {
            runCatching {
                context.assets.open("ocr_rules.json").use { input ->
                    rulesFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }.onFailure { e ->
                Log.e("RecognitionMonitor", "解压 OCR 规则文件失败: ${e.message}", e)
            }
        }
        return rulesFile
    }

    private fun loadOcrRules(): List<OcrRule> {
        return try {
            val jsonText = ensureExternalRulesFile()
                ?.takeIf { it.exists() }
                ?.readText(Charsets.UTF_8)
                ?: context.assets.open("ocr_rules.json")
                    .bufferedReader()
                    .use { it.readText() }
            val jsonArray = JSONArray(jsonText)
            buildList {
                for (index in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(index)
                    val brandRegexList = when (val raw = item.opt("brandRegex")) {
                        is JSONArray -> buildList {
                            for (brandIndex in 0 until raw.length()) {
                                val brand = raw.optString(brandIndex).trim()
                                if (brand.isNotEmpty()) add(brand)
                            }
                        }
                        else -> listOfNotNull(item.optString("brandRegex").takeIf { it.isNotBlank() })
                    }
                    add(
                        OcrRule(
                            brand = item.optString("brand"),
                            category = item.optString("category"),
                            brandRegexList = brandRegexList,
                            logoName = item.optString("logoName").takeIf { it.isNotBlank() },
                            codeRegex = item.optString("codeRegex"),
                            matchText = item.optString("matchText").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("RecognitionMonitor", "加载 OCR 规则失败: ${e.message}", e)
            emptyList()
        }
    }

    private fun extractCodeByJsonRules(mergedText: String, rawFullText: String): RecognitionResult? {
        for (rule in ocrRules) {
            val matched = rule.brandRegexList.any { keyword ->
                keyword.isNotBlank() && mergedText.contains(keyword, ignoreCase = true)
            }
            if (!matched) continue

            val code = when (rule.codeRegex) {
                "previous_line" -> extractPreviousLine(rawFullText, rule.matchText)
                else -> extractCodeByRegex(mergedText, rule.codeRegex)
            }
            if (code.isBlank()) continue

            return RecognitionResult(
                code = code,
                qr = null,
                type = rule.category,
                brand = rule.brand.ifBlank { null },
                fullText = rawFullText,
                pickupLocation = null,
                logoName = rule.logoName
            )
        }
        return null
    }

    private fun extractCodeByRegex(text: String, extractRegex: String?): String {
        if (extractRegex.isNullOrBlank()) return ""

        val match = Regex(extractRegex).find(text) ?: return ""
        return (if (match.groups.size > 1) match.groups[1]?.value else match.value)
            ?.trim()
            .orEmpty()
    }

    private fun extractPreviousLine(rawText: String, matchText: String?): String {
        if (matchText.isNullOrBlank()) return ""

        val lines = rawText
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        for (index in lines.indices) {
            if (lines[index].contains(matchText)) {
                if (index > 0) {
                    return lines[index - 1].trim()
                }
            }
        }
        return ""
    }

    suspend fun recognizeAll(bitmap: Bitmap, sourcePkg: String? = null): RecognitionResult {
        var waitCount = 0
        while (!paddleOcr.isInitialized && waitCount < 30) {
            kotlinx.coroutines.delay(100)
            waitCount++
        }
        if (!paddleOcr.isInitialized) {
            Log.e("RecognitionMonitor", "OCR 未初始化，尝试同步初始化")
            paddleOcr.init()
        }

        val ocrResult = paddleOcr.recognize(bitmap)
        val rawFullText = ocrResult?.fullText ?: ""
        val textBlocks = ocrResult?.textBlocks ?: emptyList()

        val image = InputImage.fromBitmap(bitmap, 0)
        val barcodeResult = try {
            withContext(Dispatchers.Main) {
                barcodeScanner.process(image).await()
            }
        } catch (e: Exception) { null }

        val mergedText = cleanChineseText(rawFullText)
        extractCodeByJsonRules(mergedText, rawFullText)?.let { jsonRuleResult ->
            return jsonRuleResult
        }

        val hasTakeoutKeywords = mergedText.contains("取餐") || mergedText.contains("取茶") ||
                mergedText.contains("取件") || mergedText.contains("取性") ||
                mergedText.contains("验证码") || mergedText.contains("券码") ||
                mergedText.contains("订单") || mergedText.contains("准备完毕") ||
                mergedText.contains("领取") || mergedText.contains("取件码") ||
                mergedText.contains("取養")

        val homePageElementCount = homePageKeywords.count { mergedText.contains(it) }

        val isLikelyHomePage = homePageElementCount >= 3

        var qrCode = barcodeResult?.firstOrNull()?.rawValue
        if (qrCode != null && (qrCode.contains("http://", ignoreCase = true) || qrCode.contains("https://", ignoreCase = true))) {
            qrCode = null
        }

        var takeoutCode: String? = null
        var pickupLocation: String? = null

        var detectedBrand: String? = when (sourcePkg) {
            "com.mcdonalds.gma.cn" -> "麦当劳"
            "com.yek.android.kfc.activitys" -> "肯德基"
            "com.lucky.luckyclient" -> "瑞幸"
            "com.mxbc.mxsa" -> "蜜雪冰城"
            "com.starbucks.cn" -> "星巴克"
            "com.heyteago" -> "喜茶"
            else -> null
        }

        if (mergedText.contains("啡快口令")) {
            detectedBrand = "星巴克"
        } else if (detectedBrand == null) {
            val brandHits = mutableMapOf<String, Int>()
            if (mergedText.contains("熊猫币") || mergedText.contains("葫芦")) brandHits["古茗"] = 15
            if (mergedText.contains("喜茶GO")) brandHits["喜茶"] = 15
            for (brand in drinkBrands + foodBrands) {
                if (mergedText.contains(brand, ignoreCase = true)) {
                    val score = if (Regex("$brand[:：]\\d").containsMatchIn(mergedText)) 1 else 4
                    brandHits[brand] = (brandHits[brand] ?: 0) + score
                }
            }
            detectedBrand = brandHits.maxByOrNull { it.value }?.key
        }

        if (detectedBrand == "KFC") detectedBrand = "肯德基"

        var category = "餐食"
        if (drinkBrands.contains(detectedBrand) || mergedText.contains("奶茶") || mergedText.contains("咖啡")) {
            category = "饮品"
        } else if (
            mergedText.contains("取件") || mergedText.contains("取性") || mergedText.contains("快递") ||
            mergedText.contains("包裹") || mergedText.contains("待取件") || mergedText.contains("丰巢") ||
            expressBrandKeywords.any { mergedText.contains(it) }
        ) {
            category = "快递"
        }

        if (!isLikelyHomePage || category == "快递") {
            takeoutCode = if (category == "快递") {
                extractExpressCode(textBlocks, mergedText)
            } else {
                extractFoodCode(textBlocks, mergedText, detectedBrand, qrCode)
            }
        }

        if (detectedBrand == "瑞幸" && qrCode == null) {
            takeoutCode = null
        }

        pickupLocation = findPickupLocation(mergedText, textBlocks)

        return RecognitionResult(takeoutCode, qrCode, category, detectedBrand, rawFullText, pickupLocation)
    }

    private fun extractExpressCode(
        blocks: List<PaddleOcrHelper.TextBlock>,
        mergedText: String
    ): String? {

        val expressKeywords = listOf("取件码", "取性码", "请凭", "靖凭", "凭")

        fun pickCandidate(source: String, contextText: String): String? {

            val dashMatch3 = Regex("([A-Z0-9]+-[A-Z0-9]+-[A-Z0-9]+)").find(source)

            val dashMatch2 = Regex("([A-Z]{1,3}[0-9]{0,3}-[0-9]{3,8}|[A-Z0-9]{1,4}-[A-Z0-9]{3,8})").find(source)

            val numMatch = Regex("(?<![0-9])([0-9]{4,8})(?![0-9])").find(source)

            val alphaNumMatch = Regex("([A-Z]{1,3}[0-9]{4,8}|[A-Z][0-9]{6,10})").find(source)

            val alphaMatch = Regex("[A-Z0-9-]{3,12}").find(source)

            val match = dashMatch3 ?: dashMatch2 ?: numMatch ?: alphaNumMatch ?: alphaMatch
            val value = match?.value ?: return null
            if (isInvalidExpressCode(value)) return null
            if (isLikelyPhoneTail(value, contextText)) return null
            return value
        }

        for (i in blocks.indices) {
            val block = blocks[i]
            val text = block.text.replace("\n", "")
                .replace(Regex("\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}"), "")
                .replace(" ", "")
            val matchedKeyword = expressKeywords.firstOrNull { text.contains(it) } ?: continue
            val afterKeyword = text.substringAfter(matchedKeyword).trimStart(':', '：', ' ')

            val fromSameBlock = pickCandidate(afterKeyword, text)
            if (fromSameBlock != null) return fromSameBlock

            if (afterKeyword.isBlank()) {

                for (lookAhead in 1..3) {
                    val next = blocks.getOrNull(i + lookAhead) ?: break
                    val nextText = next.text.replace("\n", "").replace(" ", "")
                    val fromNext = pickCandidate(nextText, nextText)
                    if (fromNext != null) return fromNext
                }
            }
        }

        val hasExpressKeyword = mergedText.contains("取件码") || mergedText.contains("取性码") ||
                mergedText.contains("请凭") || mergedText.contains("靖凭")
        val hasExpressBrand = expressBrandKeywords.any { mergedText.contains(it) }
        if (!hasExpressKeyword && !hasExpressBrand) return null
        val candidates = blocks.flatMap { block ->
            val text = block.text.replace(" ", "").replace("\n", "")

            val pattern = Regex("(?<![a-zA-Z0-9-])(" +
                "[0-9]{4,8}|" +
                "[A-Z0-9]+-[A-Z0-9]+-[A-Z0-9]+|" +
                "[A-Z]{1,3}[0-9]{0,3}-[0-9]{3,8}|" +
                "[A-Z0-9]{1,4}-[A-Z0-9]{3,8}|" +
                "[A-Z]{1,3}[0-9]{4,8}|" +
                "[A-Z][0-9]{6,10}|" +
                "[A-Z0-9][A-Z0-9-]{2,11}" +
                ")(?![a-zA-Z0-9-])")
            pattern.findAll(text).mapNotNull { match ->
                val value = match.value
                if (isInvalidExpressCode(value)) return@mapNotNull null
                if (isLikelyPhoneTail(value, text)) return@mapNotNull null
                var weight = (block.boundingBox?.width() ?: 0) * value.length

                if (value.contains("-")) weight *= 20

                if (value.any { it.isLetter() }) weight *= 5
                value to weight
            }.toList()
        }.sortedByDescending { it.second }
        return candidates.firstOrNull()?.first
    }

    private fun isInvalidExpressCode(value: String): Boolean {

        if (value.all { it.isLetter() }) return true

        if (Regex("^1\\d{10}$").matches(value)) return true

        if (value.startsWith("202") && value.length == 4) return true

        if (Regex("^\\d{2}-\\d{2,6}$").matches(value)) return true

        if (value.length > 12) return true
        return false
    }

    private fun isLikelyPhoneTail(value: String, contextText: String): Boolean {

        if (value.length != 4 || !value.all { it.isDigit() }) return false

        val escaped = Regex.escape(value)
        if (Regex("\\*{2,}$escaped").containsMatchIn(contextText)) return true

        val phoneContextPattern = Regex(
            "(本人|手机|手机号|电话|联系|收件人|尾号)[^\\n]{0,12}(\\*{0,6})$escaped"
        )
        if (phoneContextPattern.containsMatchIn(contextText)) return true

        return false
    }

    private fun extractFoodCode(
        blocks: List<PaddleOcrHelper.TextBlock>,
        mergedText: String,
        detectedBrand: String?,
        qrCode: String?
    ): String? {

        val queueKeywords = listOf("叫号", "取号", "过号", "排队", "迎宾台", "到店就餐", "还需等待", "桌安排")
        val queueHitCount = queueKeywords.count { mergedText.contains(it) }
        if (queueHitCount >= 2) {
            val queuePatterns = listOf(

                Regex("(小桌|中桌|大桌)\\s*([A-Z]{1,2}\\d{1,3}|\\d{1,3}[A-Z]{1,2})"),

                Regex("([A-Z]{1,2}\\d{1,3}|\\d{1,3}[A-Z]{1,2})(?=[A-Z]{0,2}号|\\s*(?:号|桌|台|单))")
            )

            fun pickQueueCode(text: String): String? {
                val normalized = text.replace(" ", "").replace("\n", "")

                val deskWithCode = queuePatterns[0].find(normalized)
                if (deskWithCode != null) {
                    val desk = deskWithCode.groupValues[1]
                    val code = deskWithCode.groupValues[2]
                    if (code.length in 2..5 && code.any { it.isLetter() } && code.any { it.isDigit() }) {
                        return "$desk $code"
                    }
                }

                val plain = queuePatterns[1].find(normalized)
                if (plain != null) {
                    val code = plain.groupValues[1]
                    if (code.length in 2..5 && code.any { it.isLetter() } && code.any { it.isDigit() }) {
                        return code
                    }
                }
                return null
            }

            for (block in blocks) {
                val c = pickQueueCode(block.text)
                if (c != null) return c
            }
            pickQueueCode(mergedText)?.let { return it }
        }

        val foodHintKeywords = listOf("取餐码", "取餐号", "取单码", "取单号", "取茶号", "待取餐", "当前订单")
        if (foodHintKeywords.any { mergedText.contains(it) }) {
            val sloganPattern = Regex("([A-Z][A-Z0-9]{2,9}[.．][\\u4e00-\\u9fa5A-Za-z0-9]{2,24})")
            val fromBlocks = blocks.asSequence()
                .map { it.text.replace(" ", "").replace("\n", "") }
                .mapNotNull { txt -> sloganPattern.find(txt)?.groupValues?.get(1) }
                .firstOrNull()
            if (fromBlocks != null && !isInvalidFoodCode(fromBlocks, mergedText, detectedBrand)) {
                return fromBlocks
            }
            val fromMerged = sloganPattern.find(mergedText)?.groupValues?.get(1)
            if (fromMerged != null && !isInvalidFoodCode(fromMerged, mergedText, detectedBrand)) {
                return fromMerged
            }
        }

        if (detectedBrand == "星巴克" || mergedText.contains("啡快口令")) {
            for (block in blocks) {
                val text = block.text.replace(" ", "").replace("\n", "")

                val starbucksMatch = Regex("(\\d{1,3}[.．][\\u4e00-\\u9fa5]{2,10})").find(text)
                if (starbucksMatch != null) {
                    return starbucksMatch.value
                }
            }
        }

        val foodKeywords = listOf("取单码", "取单号", "取餐号", "取餐码", "取茶号", "取货码", "券码", "订单号", "取性码", "取養号")
        val hasFoodKeywords = mergedText.contains("取餐") || mergedText.contains("取茶") ||
                mergedText.contains("验证码") || mergedText.contains("券码") ||
                mergedText.contains("订单") || mergedText.contains("准备完毕") ||
                mergedText.contains("领取") || mergedText.contains("取養") ||
                mergedText.contains("取单") || mergedText.contains("取货")

        var targetKeywordRect: android.graphics.Rect? = null

        for (block in blocks) {
            val text = block.text.replace(" ", "").replace("\n", "")
            val keywordPattern = "(取单码|取单号|取餐号|取餐码|取茶号|取货码|券码|订单号|取性码|取養号)"
            val forwardMatch = Regex("$keywordPattern[:：]?([A-Z0-9]{3,10})").find(text)
            if (forwardMatch != null) {
                val code = forwardMatch.groupValues[2]
                if (!isInvalidFoodCode(code, text, detectedBrand)) {
                    return code
                }
            }
            val reverseMatch = Regex("([A-Z0-9]{3,10})[:：]?$keywordPattern").find(text)
            if (reverseMatch != null) {
                val code = reverseMatch.groupValues[1]
                if (!isInvalidFoodCode(code, text, detectedBrand)) {
                    return code
                }
            }
            val matchedKeyword = foodKeywords.firstOrNull { text.contains(it) } ?: continue
            targetKeywordRect = block.boundingBox
            val afterKeyword = text.substringAfter(matchedKeyword).trimStart(':', '：', ' ')

            val sloganCodeMatch = Regex("([A-Z][A-Z0-9]{2,9}[.．][\\u4e00-\\u9fa5A-Za-z0-9]{2,24})").find(afterKeyword)
            if (sloganCodeMatch != null) {
                val sloganCode = sloganCodeMatch.groupValues[1]
                if (!isInvalidFoodCode(sloganCode, afterKeyword, detectedBrand)) {
                    return sloganCode
                }
            }
            val match = Regex("[A-Z0-9]{3,10}").find(afterKeyword)
            if (match != null && !foodKeywords.any { it.contains(match.value) || match.value.contains(it) }) {
                if (!isInvalidFoodCode(match.value, afterKeyword, detectedBrand)) {
                    return match.value
                }
            }
        }

        if (targetKeywordRect != null) {
            val candidates = blocks.mapNotNull { block ->
                val box = block.boundingBox ?: return@mapNotNull null
                val text = block.text.replace(" ", "").replace("\n", "")
                if (Regex("^[A-Z0-9]{3,10}$").matches(text) && !isInvalidFoodCode(text, text, detectedBrand)) {
                    val dist = Math.abs((box.top + box.bottom) / 2 - (targetKeywordRect!!.top + targetKeywordRect!!.bottom) / 2)
                    if (dist < 400) text to dist else null
                } else null
            }.sortedBy { it.second }
            candidates.firstOrNull()?.first?.let { return it }
        }

        if (!hasFoodKeywords) return null
        val pattern = Regex("(?<![a-zA-Z0-9])([A-Z0-9]{3,10})(?![a-zA-Z0-9])")
        val candidates = blocks.flatMap { block ->
            val text = block.text.replace(" ", "").replace("\n", "")
            pattern.findAll(text).mapNotNull { match ->
                val value = match.value
                if (value.length == 3 && value.all { it.isDigit() }) {
                    val aroundStart = (match.range.first - 6).coerceAtLeast(0)
                    val aroundEnd = (match.range.last + 6).coerceAtMost(text.lastIndex)
                    val around = text.substring(aroundStart, aroundEnd + 1)
                    val nearKeyword = foodKeywords.any { around.contains(it) }
                    if (!nearKeyword) return@mapNotNull null
                }
                if (isInvalidFoodCode(value, text, detectedBrand)) return@mapNotNull null
                var weight = (block.boundingBox?.width() ?: 0) * value.length
                if (detectedBrand == "肯德基" || detectedBrand == "麦当劳") {
                    if (value.length >= 4 && value.any { it.isLetter() }) weight *= 20
                    if (value.length == 5 && value.all { it.isDigit() }) weight *= 5
                }
                if (detectedBrand == "喜茶" && value.length == 4 && value.all { it.isDigit() }) weight *= 5
                value to weight
            }.toList()
        }.sortedByDescending { it.second }
        return candidates.firstOrNull()?.first
    }

    private fun isInvalidFoodCode(value: String, context: String, detectedBrand: String?): Boolean {
        if (value.startsWith("202") && value.length == 4) return true
        if (context.contains(":") || context.contains("/")) {
            if (value.all { it.isDigit() || it == ':' || it == '/' }) return true
        }
        if (context.contains("时间") || context.contains("日期") || context.contains("预计获得") || context.contains("积分")) return true
        val lowerContext = context.lowercase()
        val distractions = listOf("ml", "g", "元", "¥", "购", "券", "赢", "送", "补贴", "减", "满", "起", "合计", "实付")
        if (distractions.any { lowerContext.contains(it) && lowerContext.indexOf(it) in (lowerContext.indexOf(value) - 2)..(lowerContext.indexOf(value) + value.length + 2) }) return true
        return false
    }

    private fun findPickupLocation(mergedText: String, blocks: List<PaddleOcrHelper.TextBlock>): String? {

        val startKeywords = listOf("已到", "已至", "到达", "到了", "在", "于", "己到", "前往", "送到", "前住")
        val targetKeywords = listOf("服务站", "驿站", "自提点", "快递站", "菜鸟站", "代收点", "代点", "丰巢柜", "快递柜", "智能柜", "门面", "邮政大厅", "大厅")
        val stopKeywords = listOf("领取", "取件", "查看", "请凭", "靖凭", "如有", "如有疑问", "取您的", "复制")

        fun isGarbageMatch(location: String): Boolean {

            if (location.contains("代收点(") || location.contains("代收点（")) return true

            if (Regex("\\d{10,}").containsMatchIn(location)) return true
            return false
        }

        fun locationScore(location: String): Int {
            var score = location.length

            for (keyword in targetKeywords) {
                if (location.contains(keyword)) score += 20
            }
            return score
        }

        val candidates = mutableListOf<Pair<String, Int>>()

        val addressPattern = Regex("地址[:：\\s]*(.{4,80}?(?:${targetKeywords.joinToString("|")}))")
        val addressMatch = addressPattern.find(mergedText)
        if (addressMatch != null) {
            val loc = truncateLocation(addressMatch.groupValues[1])
            if (!isGarbageMatch(loc)) candidates.add(loc to locationScore(loc) + 1000)
        }

        val addressFallback = Regex("地址[:：\\s]*([^,，。！!?；;.\\n]{4,60})")
        val fallbackMatch = addressFallback.find(mergedText)
        if (fallbackMatch != null) {
            val candidate = truncateLocation(fallbackMatch.groupValues[1])
            if (candidate.length > 8 && !isGarbageMatch(candidate)) {
                candidates.add(candidate to locationScore(candidate) + 500)
            }
        }

        val locWithTargetPattern = Regex("(?:${startKeywords.joinToString("|")})([^,，。！!?;？\\s]{2,60}?(?:${targetKeywords.joinToString("|")}))")
        for (match in locWithTargetPattern.findAll(mergedText)) {
            val loc = truncateLocation(match.groupValues[1])
            if (!isGarbageMatch(loc)) candidates.add(loc to locationScore(loc) + 100)
        }

        val locToVerbPattern = Regex("(?:${startKeywords.joinToString("|")})([^,，。！!?;？\\s]{4,60}?)(?=${stopKeywords.joinToString("|")})")
        val locMatch2 = locToVerbPattern.find(mergedText)
        if (locMatch2 != null) {
            val loc = truncateLocation(locMatch2.groupValues[1])
            if (!isGarbageMatch(loc)) candidates.add(loc to locationScore(loc))
        }

        for (block in blocks) {
            val text = block.text.replace("\n", "").replace(" ", "")
            if (targetKeywords.any { text.contains(it) }) {
                val loc = truncateLocation(text)
                if (!isGarbageMatch(loc)) candidates.add(loc to locationScore(loc))
            }
        }

        return candidates.maxByOrNull { it.second }?.first
    }

    private fun cleanChineseText(text: String): String {
        return text
            .replace(Regex("\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}"), "")
            .replace(Regex("(?<=[\\u4e00-\\u9fa5A-Z0-9-])\\s+(?=[\\u4e00-\\u9fa5])"), "")
            .replace("\n", "")
            .replace("|", "")
            .replace("包 裹", "包裹")
            .replace("己到", "已到")
            .replace("己至", "已至")
            .replace("取性码", "取件码")
            .replace("前住", "前往")
            .replace("取養号", "取餐号")
            .replace("靖凭", "请凭")
            .replace("冰域", "冰城")
    }

    private fun truncateLocation(location: String): String {
        val stopKeywords = listOf("请凭", "靖凭", "取件", "领取", "复制", "查看", "日已接收", "已接收", "日已签收", "日已到", "点击", "联系", "如有", "如有疑问", "取您的")
        var result = location

        val semicolonIdx = result.indexOf(';')
        if (semicolonIdx != -1) result = result.substring(0, semicolonIdx)
        for (stop in stopKeywords) {
            val index = result.indexOf(stop)
            if (index != -1) result = result.substring(0, index)
        }
        return result.replace("[,，。！!?;？;|\\s]+$".toRegex(), "")
    }

    fun recognizeFromText(text: String): RecognitionResult {
        val mergedText = cleanChineseText(text)

        var detectedBrand: String? = null
        val brandHits = mutableMapOf<String, Int>()
        if (mergedText.contains("熊猫币") || mergedText.contains("葫芦")) brandHits["古茗"] = 15
        if (mergedText.contains("喜茶GO")) brandHits["喜茶"] = 15
        for (brand in drinkBrands + foodBrands) {
            if (mergedText.contains(brand, ignoreCase = true)) {
                val score = if (Regex("$brand[:：]\\d").containsMatchIn(mergedText)) 1 else 4
                brandHits[brand] = (brandHits[brand] ?: 0) + score
            }
        }
        detectedBrand = brandHits.maxByOrNull { it.value }?.key
        if (detectedBrand == "KFC") detectedBrand = "肯德基"

        if (mergedText.contains("啡快口令")) {
            detectedBrand = "星巴克"
        }

        var category = "餐食"
        if (drinkBrands.contains(detectedBrand) || mergedText.contains("奶茶") || mergedText.contains("咖啡")) {
            category = "饮品"
        } else if (
            mergedText.contains("取件") || mergedText.contains("取性") || mergedText.contains("快递") ||
            mergedText.contains("包裹") || mergedText.contains("待取件") || mergedText.contains("丰巢") ||
            expressBrandKeywords.any { mergedText.contains(it) }
        ) {
            category = "快递"
        }

        val takeoutCode = if (category == "快递") {
            extractExpressCodeFromText(mergedText)
        } else {
            extractFoodCodeFromText(mergedText, detectedBrand)
        }

        val pickupLocation = findPickupLocation(mergedText, emptyList())

        return RecognitionResult(takeoutCode, null, category, detectedBrand, text, pickupLocation)
    }

    private fun extractExpressCodeFromText(mergedText: String): String? {
        val expressKeywords = listOf("取件码", "取性码", "请凭", "靖凭", "凭")
        val hasExpressKeyword = mergedText.contains("取件码") || mergedText.contains("取性码") ||
                mergedText.contains("请凭") || mergedText.contains("靖凭")
        val hasExpressBrand = expressBrandKeywords.any { mergedText.contains(it) }

        val matchedKeyword = expressKeywords.firstOrNull { mergedText.contains(it) }
        if (matchedKeyword != null) {
            val afterKeyword = mergedText.substringAfter(matchedKeyword).trimStart(':', '：', ' ')

            val dashMatch3 = Regex("([A-Z0-9]+-[A-Z0-9]+-[A-Z0-9]+)").find(afterKeyword)

            val dashMatch2 = Regex("([A-Z]{1,3}[0-9]{0,3}-[0-9]{3,8}|[A-Z0-9]{1,4}-[A-Z0-9]{3,8})").find(afterKeyword)

            val numMatch = Regex("(?<![0-9])([0-9]{4,8})(?![0-9])").find(afterKeyword)

            val alphaNumMatch = Regex("([A-Z]{1,3}[0-9]{4,8}|[A-Z][0-9]{6,10})").find(afterKeyword)

            val alphaMatch = Regex("[A-Z0-9-]{3,12}").find(afterKeyword)

            val match = dashMatch3 ?: dashMatch2 ?: numMatch ?: alphaNumMatch ?: alphaMatch
            if (match != null &&
                !isInvalidExpressCode(match.value) &&
                !isLikelyPhoneTail(match.value, mergedText)
            ) {
                return match.value
            }
        }

        if (!hasExpressKeyword && !hasExpressBrand) return null

        val pattern = Regex("(?<![a-zA-Z0-9-])(" +
            "[0-9]{4,8}|" +
            "[A-Z0-9]+-[A-Z0-9]+-[A-Z0-9]+|" +
            "[A-Z]{1,3}[0-9]{0,3}-[0-9]{3,8}|" +
            "[A-Z0-9]{1,4}-[A-Z0-9]{3,8}|" +
            "[A-Z]{1,3}[0-9]{4,8}|" +
            "[A-Z][0-9]{6,10}|" +
            "[A-Z0-9][A-Z0-9-]{2,11}" +
            ")(?![a-zA-Z0-9-])")
        val candidates = pattern.findAll(mergedText).mapNotNull { match ->
            val value = match.value
            if (isInvalidExpressCode(value)) return@mapNotNull null
            if (isLikelyPhoneTail(value, mergedText)) return@mapNotNull null
            var weight = value.length

            if (value.contains("-")) weight *= 20

            if (value.any { it.isLetter() }) weight *= 5
            value to weight
        }.sortedByDescending { it.second }
        return candidates.firstOrNull()?.first
    }

    private fun extractFoodCodeFromText(mergedText: String, detectedBrand: String?): String? {
        val foodKeywords = listOf("取单码", "取单号", "取餐号", "取餐码", "取茶号", "取货码", "券码", "订单号", "取性码", "取養号")
        val hasFoodKeywords = mergedText.contains("取餐") || mergedText.contains("取茶") ||
            mergedText.contains("验证码") || mergedText.contains("券码") ||
            mergedText.contains("订单") || mergedText.contains("准备完毕") ||
            mergedText.contains("领取") || mergedText.contains("取養") ||
            mergedText.contains("取单") || mergedText.contains("取货")

        if (hasFoodKeywords) {
            val sloganPattern = Regex("([A-Z][A-Z0-9]{2,9}[.．][\\u4e00-\\u9fa5A-Za-z0-9]{2,24})")
            val sloganCode = sloganPattern.find(mergedText)?.groupValues?.get(1)
            if (sloganCode != null && !isInvalidFoodCode(sloganCode, mergedText, detectedBrand)) {
                return sloganCode
            }
        }

        val keywordPattern = "(取单码|取单号|取餐号|取餐码|取茶号|取货码|券码|订单号|取性码|取養号)"
        val forwardMatch = Regex("$keywordPattern[:：]?([A-Z0-9]{3,10})").find(mergedText)
        if (forwardMatch != null) {
            val code = forwardMatch.groupValues[2]
            if (!isInvalidFoodCode(code, mergedText, detectedBrand)) return code
        }
        val reverseMatch = Regex("([A-Z0-9]{3,10})[:：]?$keywordPattern").find(mergedText)
        if (reverseMatch != null) {
            val code = reverseMatch.groupValues[1]
            if (!isInvalidFoodCode(code, mergedText, detectedBrand)) return code
        }

        val matchedKeyword = foodKeywords.firstOrNull { mergedText.contains(it) }
        if (matchedKeyword != null) {
            val afterKeyword = mergedText.substringAfter(matchedKeyword).trimStart(':', '：', ' ')

            val sloganCodeMatch = Regex("([A-Z][A-Z0-9]{2,9}[.．][\\u4e00-\\u9fa5A-Za-z0-9]{2,24})").find(afterKeyword)
            if (sloganCodeMatch != null) {
                val sloganCode = sloganCodeMatch.groupValues[1]
                if (!isInvalidFoodCode(sloganCode, afterKeyword, detectedBrand)) {
                    return sloganCode
                }
            }
            val match = Regex("[A-Z0-9]{3,10}").find(afterKeyword)
            if (match != null && !foodKeywords.any { it.contains(match.value) || match.value.contains(it) }) {
                if (!isInvalidFoodCode(match.value, afterKeyword, detectedBrand)) {
                    return match.value
                }
            }
        }

        if (!hasFoodKeywords) return null
        val pattern = Regex("(?<![a-zA-Z0-9])([A-Z0-9]{3,10})(?![a-zA-Z0-9])")
        val candidates = pattern.findAll(mergedText).mapNotNull { match ->
            val value = match.value
            if (value.length == 3 && value.all { it.isDigit() }) {
                val aroundStart = (match.range.first - 6).coerceAtLeast(0)
                val aroundEnd = (match.range.last + 6).coerceAtMost(mergedText.lastIndex)
                val around = mergedText.substring(aroundStart, aroundEnd + 1)
                val nearKeyword = foodKeywords.any { around.contains(it) }
                if (!nearKeyword) return@mapNotNull null
            }
            if (isInvalidFoodCode(value, mergedText, detectedBrand)) return@mapNotNull null
            var weight = value.length
            if (detectedBrand == "肯德基" || detectedBrand == "麦当劳") {
                if (value.length >= 4 && value.any { it.isLetter() }) weight *= 20
                if (value.length == 5 && value.all { it.isDigit() }) weight *= 5
            }
            if (detectedBrand == "喜茶" && value.length == 4 && value.all { it.isDigit() }) weight *= 5
            value to weight
        }.sortedByDescending { it.second }
        return candidates.firstOrNull()?.first
    }

}

data class RecognitionResult(
    val code: String?,
    val qr: String?,
    val type: String,
    val brand: String?,
    val fullText: String,
    val pickupLocation: String? = null,
    val logoName: String? = null
)
