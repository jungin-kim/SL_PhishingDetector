package com.example.phishingdetector.utils

import android.content.Context

object Tokenizer {

    private const val MAX_LEN = 512

    fun init(context: Context) {
        // 초기화 필요 없음
    }

    fun tokenize(text: String): Pair<LongArray, LongArray> {
        val tokens = text.split("\\s+".toRegex())

        val idsList = tokens.map { it.hashCode().toLong() }

        val truncated = if (idsList.size > MAX_LEN) idsList.subList(0, MAX_LEN) else idsList

        val inputIds = LongArray(MAX_LEN) { idx ->
            if (idx < truncated.size) truncated[idx] else 0L
        }
        val attentionMask = LongArray(MAX_LEN) { idx ->
            if (idx < truncated.size) 1L else 0L
        }
        return Pair(inputIds, attentionMask)
    }
}
