package com.example.phishingdetector.tokenizer

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 아주 단순화한 Byte-Pair-Encoding 토크나이저.
 *  - vocab.json, merges.txt를 assets 에 두고 로드
 *  - maxLen 기본 128, pad=0, eos=1, unk=<unk> 토큰 가정
 */
class SimpleBPETokenizer(
    private val vocab: Map<String, Int>,
    private val merges: List<Pair<String, String>>,
) {

    companion object {
        fun fromAssets(context: Context,
                       vocabFile: String = "vocab.json",
                       mergesFile: String = "merges.txt"
        ): SimpleBPETokenizer {

            val vocabJson = context.assets.open(vocabFile).bufferedReader().use { it.readText() }
            val mergesLines = context.assets.open(mergesFile)
                .bufferedReader()
                .readLines()

            val vocabMap = JSONObject(vocabJson).let { json ->
                json.keys().asSequence().associateWith { key -> json.getInt(key) }
            }

            // merges 파일 첫 줄은 "#version: x.y"
            val mergesPairs = mergesLines.drop(1).map { line ->
                val parts = line.split(" ")
                parts[0] to parts[1]
            }

            return SimpleBPETokenizer(vocabMap, mergesPairs)
        }
    }

    /** 텍스트를 토큰 ID와 attention mask(LongArray)로 변환  */
    fun encode(text: String, maxLen: Int = 128): Pair<LongArray, LongArray> {
        val tokenIds = mutableListOf<Int>()

        text.split(" ").forEach { word ->
            tokenIds += bpe(word)
        }
        tokenIds += 1                           // </eos>

        // pad / trunc
        val inputIds  = LongArray(maxLen) { 0 }
        val attnMask  = LongArray(maxLen) { 0 }
        tokenIds.take(maxLen).forEachIndexed { i, id ->
            inputIds[i] = id.toLong()
            attnMask[i] = 1L
        }
        return inputIds to attnMask
    }

    /** 매우 단순화한 BPE 병합 루틴 */
    private fun bpe(word: String): List<Int> {
        // 글자 단위로 쪼개고 반복적으로 merges 규칙 적용
        var symbols = word.map { it.toString() }.toMutableList()
        while (true) {
            val pairs = symbols.zipWithNext()
            val mergeIdx = merges.indexOfFirst { it in pairs }
            if (mergeIdx == -1) break
            val mergePair = merges[mergeIdx]
            val i = pairs.indexOf(mergePair)
            symbols[i] = mergePair.first + mergePair.second
            symbols.removeAt(i + 1)
        }
        return symbols.map { ch ->
            vocab[ch] ?: vocab.getValue("<unk>")
        }
    }
}
