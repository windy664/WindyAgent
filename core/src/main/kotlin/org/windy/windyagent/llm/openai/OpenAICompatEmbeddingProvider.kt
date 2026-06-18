package org.windy.windyagent.llm.openai

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.windy.windyagent.llm.EmbeddingProvider
import org.windy.windyagent.llm.LLMException
import java.util.concurrent.TimeUnit

/**
 * OpenAI 兼容的文本嵌入（mimo / 智谱 / 讯飞等）。
 * baseUrl 传到 /v1 为止；POST {baseUrl}/embeddings，body = {model, input:[...]}。
 * 与 [OpenAICompatProvider] 同一套 base-url/key 习惯，只是换 embedding 模型名。
 */
class OpenAICompatEmbeddingProvider(
    baseUrl: String,
    private val model: String,
    private val apiKey: String
) : EmbeddingProvider {

    private val url = baseUrl.trimEnd('/') + "/embeddings"
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
    private val mapper = ObjectMapper()

    override val name = "openai-embedding($model)"

    override fun embed(texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()
        val json = mapper.writeValueAsString(mapOf("model" to model, "input" to texts))
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { resp ->
            val bodyBytes = resp.body?.bytes() ?: ByteArray(0)
            if (bodyBytes.size > 8 * 1024 * 1024) throw IllegalStateException("Embedding 响应超过 8MB")
            val bodyStr = String(bodyBytes, Charsets.UTF_8)
            if (!resp.isSuccessful) throw LLMException("Embedding HTTP ${resp.code}: ${bodyStr.take(200)}")
            val node = mapper.readTree(bodyStr)
            val data = node["data"] ?: error("embedding 响应缺少 data：${bodyStr.take(200)}")
            // 按 index 对齐输入顺序
            return data.sortedBy { it["index"]?.asInt() ?: 0 }.map { item ->
                val arr = item["embedding"]
                FloatArray(arr.size()) { i -> arr[i].floatValue() }
            }
        }
    }
}
