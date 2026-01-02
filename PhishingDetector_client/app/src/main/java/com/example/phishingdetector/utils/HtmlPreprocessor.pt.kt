package com.example.phishingdetector.utils

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * HTML 문자열에서 <script>, <style>, <noscript> 태그 제거하고
 * 텍스트만 추출하는 간단 전처리 유틸리티
 */
object HtmlPreprocessor {
    fun cleanHtml(html: String): String {
        return try {
            val doc: Document = Jsoup.parse(html)
            doc.select("script, style, noscript").remove()
            doc.text()
        } catch (e: Exception) {
            // 파싱 실패 시 원본 HTML 그대로 반환
            html
        }
    }
}
