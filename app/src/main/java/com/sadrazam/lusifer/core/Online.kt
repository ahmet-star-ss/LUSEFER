package com.sadrazam.lusifer.core

import android.content.Context
import com.sadrazam.lusifer.Prefs
import com.sadrazam.lusifer.Secure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Online mod: OpenAI uyumlu API'ler (Groq, Gemini, OpenRouter, Cerebras...). */
object Online {
    data class Provider(val name: String, val base: String, val model: String)

    const val CUSTOM = "Özel (OpenAI uyumlu)"

    val providers: List<Provider> = listOf(
        Provider("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
        Provider("Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.0-flash"),
        Provider("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
        Provider("Cerebras", "https://api.cerebras.ai/v1", "llama-3.3-70b"),
        Provider("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
        Provider("Mistral", "https://api.mistral.ai/v1", "mistral-small-latest"),
        Provider("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
        Provider(CUSTOM, "", "")
    )

    fun current(p: Prefs): Provider {
        val pr = providers.firstOrNull { it.name == p.provider } ?: providers[0]
        return if (pr.name == CUSTOM) pr.copy(base = p.customBase.trimEnd('/')) else pr
    }

    private fun http(method: String, url: String, key: String, body: String?): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15000
        c.readTimeout = 90000
        c.setRequestProperty("Authorization", "Bearer $key")
        c.setRequestProperty("Content-Type", "application/json")
        if (body != null) {
            c.doOutput = true
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val txt = stream?.bufferedReader()?.readText() ?: ""
        return code to txt
    }

    /** API anahtarını sınar ve kullanılabilir modelleri otomatik bulur. */
    suspend fun listModels(base: String, key: String): kotlin.Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            if (base.isBlank()) return@withContext kotlin.Result.failure(Exception("Adres boş"))
            if (key.isBlank()) return@withContext kotlin.Result.failure(Exception("API anahtarı boş"))
            val (code, txt) = http("GET", "$base/models", key, null)
            if (code !in 200..299) return@withContext kotlin.Result.failure(Exception("HTTP $code: ${txt.take(160)}"))
            val root = JSONObject(txt)
            val arr: JSONArray = root.optJSONArray("data") ?: root.optJSONArray("models") ?: JSONArray()
            val out = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val id = arr.getJSONObject(i).optString("id").removePrefix("models/")
                if (id.isNotBlank()) out.add(id)
            }
            kotlin.Result.success(out.sorted())
        } catch (e: Throwable) {
            kotlin.Result.failure(e)
        }
    }

    suspend fun chat(ctx: Context, system: String, user: String): kotlin.Result<String> = withContext(Dispatchers.IO) {
        try {
            val p = Prefs.get(ctx)
            val pr = current(p)
            val key = Secure.getKey(ctx, pr.name)
            if (key.isBlank()) return@withContext kotlin.Result.failure(Exception("API anahtarı girilmemiş"))
            val model = p.model.ifBlank { pr.model }
            if (pr.base.isBlank() || model.isBlank()) return@withContext kotlin.Result.failure(Exception("Sağlayıcı/model seçilmemiş"))
            val msgs = JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", user))
            val body = JSONObject().put("model", model).put("messages", msgs)
                .put("temperature", 0.3).put("max_tokens", 1200).toString()
            val (code, txt) = http("POST", "${pr.base}/chat/completions", key, body)
            if (code !in 200..299) return@withContext kotlin.Result.failure(Exception("HTTP $code: ${txt.take(200)}"))
            val content = JSONObject(txt).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").optString("content")
            kotlin.Result.success(content.trim())
        } catch (e: Throwable) {
            kotlin.Result.failure(e)
        }
    }
}
