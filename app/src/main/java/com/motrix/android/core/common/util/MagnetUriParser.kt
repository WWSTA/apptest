package com.motrix.android.core.common.util

import com.motrix.android.domain.model.MagnetUri

object MagnetUriParser {

    private const val SCHEME = "magnet"
    private const val BTIH_PREFIX = "urn:btih:"
    private val SHA1_HEX_REGEX = Regex("^[0-9a-fA-F]{40}$")
    private val BASE32_REGEX = Regex("^[A-Z2-7]{32}$")

    fun parse(uri: String): Result<MagnetUri> {
        return runCatching {
            if (!isValidMagnetUri(uri)) {
                throw IllegalArgumentException("Invalid magnet URI format: $uri")
            }

            val queryString = uri.substringAfter("?", "")
            if (queryString.isEmpty()) {
                throw IllegalArgumentException("Magnet URI missing query string")
            }

            val params = parseQueryString(queryString)

            val xtParams = params["xt"] ?: emptyList()
            val infoHash = extractInfoHash(xtParams)
                ?: throw IllegalArgumentException("Magnet URI missing valid xt parameter")

            val displayName = params["dn"]?.firstOrNull()?.decodeMagnetUriComponent()
            val trackers = params["tr"]?.map { it.decodeMagnetUriComponent() } ?: emptyList()
            val exactLength = params["xl"]?.firstOrNull()?.toLongOrNull()
            val webSeeds = params["ws"]?.map { it.decodeMagnetUriComponent() } ?: emptyList()

            MagnetUri(
                infoHash = infoHash,
                displayName = displayName,
                trackers = trackers,
                exactLength = exactLength,
                webSeeds = webSeeds
            )
        }
    }

    fun isValidMagnetUri(uri: String): Boolean {
        if (!uri.startsWith("$SCHEME:?", ignoreCase = true)) {
            return false
        }

        val queryString = uri.substringAfter("?", "")
        if (queryString.isEmpty()) {
            return false
        }

        val params = parseQueryString(queryString)
        val xtParams = params["xt"] ?: return false

        return xtParams.any { param ->
            param.startsWith(BTIH_PREFIX, ignoreCase = true)
        }
    }

    private fun extractInfoHash(xtParams: List<String>): String? {
        for (param in xtParams) {
            if (param.startsWith(BTIH_PREFIX, ignoreCase = true)) {
                val hash = param.substring(BTIH_PREFIX.length)
                if (SHA1_HEX_REGEX.matches(hash)) {
                    return hash.lowercase()
                }
                if (BASE32_REGEX.matches(hash.uppercase())) {
                    return decodeBase32ToHex(hash.uppercase())
                }
            }
        }
        return null
    }

    private fun parseQueryString(query: String): Map<String, List<String>> {
        val params = mutableMapOf<String, MutableList<String>>()

        for (pair in query.split("&")) {
            val keyValue = pair.split("=", limit = 2)
            if (keyValue.size == 2) {
                val key = keyValue[0]
                val value = keyValue[1]
                params.getOrPut(key) { mutableListOf() }.add(value)
            } else if (keyValue.size == 1 && keyValue[0].isNotEmpty()) {
                params.getOrPut(keyValue[0]) { mutableListOf() }.add("")
            }
        }

        return params
    }

    private fun String.decodeMagnetUriComponent(): String {
        return try {
            java.net.URLDecoder.decode(this, "UTF-8")
        } catch (e: Exception) {
            this
        }
    }

    private fun decodeBase32ToHex(base32: String): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var bits = 0L
        var bitCount = 0
        val hexBuilder = StringBuilder()

        for (char in base32) {
            val index = alphabet.indexOf(char.uppercaseChar())
            if (index < 0) continue
            bits = (bits shl 5) or index.toLong()
            bitCount += 5

            while (bitCount >= 4) {
                bitCount -= 4
                val hexDigit = ((bits shr bitCount) and 0xF).toInt()
                hexBuilder.append(hexDigit.toString(16))
            }
        }

        return hexBuilder.toString().lowercase().padEnd(40, '0').take(40)
    }
}
