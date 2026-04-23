package com.zayne.hamix

data class HamiItem(
    val id: Long = System.currentTimeMillis(),
    val code: String,
    val category: String,
    val date: String,
    val summary: String = "摘要文本"
)
