package top.rootu.lampa.tmdb

import android.net.Uri
import com.google.gson.Gson
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.dnsoverhttps.DnsOverHttps
import top.rootu.lampa.tmdb.models.entity.Entities
import top.rootu.lampa.tmdb.models.entity.Entity
import top.rootu.lampa.tmdb.models.entity.Genre
import java.io.IOException
import java.net.Inet6Address
import java.net.InetAddress
import java.util.Locale
import java.util.concurrent.TimeUnit

object TMDB {
    const val apiKey = ""
    const val apiHost = "api.themoviedb.org"
    const val imgHost = "image.tmdb.org" // image.tmdb.org 403 forbidden from RU
    const val proxyImageHost = "imagetmdb.com"

    private var movieGenres: List<Genre?> = emptyList()
    private var tvGenres: List<Genre?> = emptyList()

    fun getLang(): String {
        // todo: get from prefs
        val lang: String = Locale.getDefault().language
        return when {
            lang.equals("IW", ignoreCase = true) -> {
                "he"
            }

            lang.equals("IN", ignoreCase = true) -> {
                "id"
            }

            lang.equals("JI", ignoreCase = true) -> {
                "yi"
            }

            lang.equals("LV", ignoreCase = true) -> {
                "en" // FIXME: Empty Genre Names on LV, so force EN for TMDB requests
            }

            else -> {
                lang
            }
        }
    }

    fun initGenres() {
        try {
            // https://developers.themoviedb.org/3/genres/get-movie-list
            var ent = video("genre/movie/list")
            ent?.genres?.let {
                movieGenres = it
            }
            // https://developers.themoviedb.org/3/genres/get-tv-list
            ent = video("genre/tv/list")
            ent?.genres?.let {
                tvGenres = it
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Quad9 over HTTPS resolver
    fun startWithQuad9DNS(): OkHttpClient {

        val bootstrapClient = OkHttpClient.Builder().build()
        val okUrl = HttpUrl.parse("https://dns.quad9.net/dns-query")

        val dns = okUrl?.let {
            DnsOverHttps.Builder().client(bootstrapClient)
                .url(it)
                .bootstrapDnsHosts(
                    InetAddress.getByName("9.9.9.9"),
                    InetAddress.getByName("149.112.112.112"),
                    Inet6Address.getByName("2620:fe::fe")
                )
                .build()
        }

        return bootstrapClient.newBuilder()
            .connectTimeout(15000L, TimeUnit.MILLISECONDS)
            .dns(dns)
            .build()
    }

    fun videos(endpoint: String, params: MutableMap<String, String>): Entities? {
        params["api_key"] = apiKey
        params["language"] = getLang()

        val urlBuilder = Uri.Builder()
            .scheme("https")
            .authority(apiHost)
            .path("/3/$endpoint")

        for (param in params) {
            urlBuilder.appendQueryParameter(param.key, param.value)
        }

        var body: String? = null
        val link = urlBuilder.build().toString()
        try {
                val request = Request.Builder()
                    .url(link)
                    .build()
                val client = startWithQuad9DNS()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")
                    body = response.body()?.string()
                    response.body()?.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

        if (body.isNullOrEmpty())
            return null

        val gson = Gson()
        val entities = gson.fromJson(body, Entities::class.java)
        val ret = mutableListOf<Entity>()

        entities?.results?.forEach {
//            if (it.media_type == null)
//                fixEntity(it)
            if (it.media_type == "movie" || it.media_type == "tv") {
                val ent = video("${it.media_type}/${it.id}")
                ent?.let {
//                    fixEntity(ent)
                    ret.add(ent)
                }
            }
        }
        entities?.results = ret
        return entities
    }

    fun video(endpoint: String): Entity? {
        val osLang = getLang()
        val entity = videoDetail(endpoint)
//        entity?.let {
//            Certifications.get(entity)
//        }
        return entity
    }

    fun videoDetail(endpoint: String, lang: String = ""): Entity? {
        val params = mutableMapOf<String, String>()
        params["api_key"] = apiKey
        if (lang.isBlank())
            params["language"] = getLang()
        else params["language"] = lang

        val urlBuilder = Uri.Builder()
            .scheme("https")
            .authority(apiHost)
            .path("/3/$endpoint")

        params["append_to_response"] = "videos,images,alternative_titles"
        params["include_image_language"] = "${getLang()},ru,en,null"

        for (param in params) {
            urlBuilder.appendQueryParameter(param.key, param.value)
        }

        var body: String? = null
        val link = urlBuilder.build().toString()
        try {
            val request = Request.Builder()
                .url(link)
                .build()
            val client = startWithQuad9DNS()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                body = response.body()?.string()
                response.body()?.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (body.isNullOrEmpty())
            return null

        val gson = Gson()
        val ent = gson.fromJson(body, Entity::class.java)
//        fixEntity(ent)
//        ent.videos?.results?.forEach {
//            Trailers.fixTrailers(it)
//        }
        return ent
    }

}