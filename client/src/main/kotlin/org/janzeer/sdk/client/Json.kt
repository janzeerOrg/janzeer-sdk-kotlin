package org.janzeer.sdk.client

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Lossless JSON on top of kotlinx-serialization's [JsonElement]: a [JsonPrimitive] keeps a number's exact source
 * text, so the node's 8-decimal amounts and 128-bit token quantities reach [BigDecimal] / [BigInteger] intact.
 */
public object JsonUtil {
    /** Lenient parser (the node's JSON is strict; leniency only relaxes what we accept). */
    public val json: Json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    /** Parse text to a tree. */
    @JvmStatic public fun parse(text: String): JsonElement = json.parseToJsonElement(text)

    /** Any Kotlin/Java value (maps, lists, numbers, strings, booleans, null, [JsonElement]) → [JsonElement]. */
    @JvmStatic public fun toElement(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is JsonElement -> v
        is String -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        is BigDecimal -> JsonUnquotedLiteral(v.toPlainString())
        is BigInteger -> JsonUnquotedLiteral(v.toString())
        is Number -> JsonPrimitive(v)
        is Enum<*> -> JsonPrimitive(v.name)
        is Map<*, *> -> buildJsonObject { for ((k, x) in v) put(k.toString(), toElement(x)) }
        is Iterable<*> -> buildJsonArray { for (x in v) add(toElement(x)) }
        is Array<*> -> buildJsonArray { for (x in v) add(toElement(x)) }
        else -> JsonPrimitive(v.toString())
    }

    /** Serialize any value. */
    @JvmStatic public fun stringify(v: Any?): String = json.encodeToString(JsonElement.serializer(), toElement(v))

    @Suppress("FunctionName")
    private fun JsonUnquotedLiteral(s: String): JsonElement = json.parseToJsonElement(s)

    // ---- field accessors ----
    /** object field, or null */
    @JvmStatic public fun obj(e: JsonElement?): JsonObject? = e as? JsonObject
    /** required object */
    @JvmStatic public fun reqObj(e: JsonElement?, field: String = "value"): JsonObject = e as? JsonObject ?: throw JanzeerException("expected object for $field, got ${describe(e)}")
    /** required array */
    @JvmStatic public fun reqArr(e: JsonElement?, field: String = "value"): JsonArray = e as? JsonArray ?: throw JanzeerException("expected array for $field, got ${describe(e)}")
    /** required string (a number is accepted and rendered as its decimal text) */
    @JvmStatic public fun str(e: JsonElement?, field: String = "value"): String = optStr(e) ?: throw JanzeerException("expected string for $field, got ${describe(e)}")
    /** optional string */
    @JvmStatic public fun optStr(e: JsonElement?): String? = (e as? JsonPrimitive)?.takeIf { it !is JsonNull }?.let { if (it.isString) it.content else BigDecimal(it.content).toPlainString() }
    /** decimal amount field → [BigDecimal] (numbers and strings both accepted) */
    @JvmStatic public fun dec(e: JsonElement?, field: String = "value"): BigDecimal = optDec(e) ?: throw JanzeerException("expected decimal for $field, got ${describe(e)}")
    /** optional decimal */
    @JvmStatic public fun optDec(e: JsonElement?): BigDecimal? = (e as? JsonPrimitive)?.takeIf { it !is JsonNull }?.let { runCatching { BigDecimal(it.content) }.getOrNull() }
    /** integer field that fits a long */
    @JvmStatic public fun long(e: JsonElement?, field: String = "value"): Long = optLong(e) ?: throw JanzeerException("expected integer for $field, got ${describe(e)}")
    /** optional long */
    @JvmStatic public fun optLong(e: JsonElement?): Long? = (e as? JsonPrimitive)?.takeIf { it !is JsonNull }?.let { runCatching { BigDecimal(it.content).longValueExact() }.getOrNull() }
    /** int field */
    @JvmStatic public fun int(e: JsonElement?, field: String = "value"): Int = long(e, field).toInt()
    /** optional int */
    @JvmStatic public fun optInt(e: JsonElement?): Int? = optLong(e)?.toInt()
    /** arbitrary-size integer field (token amounts) */
    @JvmStatic public fun big(e: JsonElement?, field: String = "value"): BigInteger = optBig(e) ?: throw JanzeerException("expected integer for $field, got ${describe(e)}")
    /** optional BigInteger */
    @JvmStatic public fun optBig(e: JsonElement?): BigInteger? = (e as? JsonPrimitive)?.takeIf { it !is JsonNull }?.let { runCatching { BigDecimal(it.content).toBigIntegerExact() }.getOrNull() }
    /** boolean field */
    @JvmStatic public fun bool(e: JsonElement?, field: String = "value"): Boolean = optBool(e) ?: throw JanzeerException("expected boolean for $field, got ${describe(e)}")
    /** optional boolean */
    @JvmStatic public fun optBool(e: JsonElement?): Boolean? = (e as? JsonPrimitive)?.takeIf { it !is JsonNull && !it.isString }?.content?.toBooleanStrictOrNull()

    /** Convert a tree to plain Kotlin values (numbers → Long when integral and in range, else BigDecimal). */
    @JvmStatic public fun toPlain(e: JsonElement?): Any? = when (e) {
        null, JsonNull -> null
        is JsonPrimitive -> if (e.isString) e.content else e.content.toBooleanStrictOrNull() ?: BigDecimal(e.content).let { d -> if (d.scale() <= 0 && d.abs() < BigDecimal(Long.MAX_VALUE)) d.longValueExact() else d }
        is JsonArray -> e.map(::toPlain)
        is JsonObject -> e.mapValues { toPlain(it.value) }
    }

    private fun describe(e: JsonElement?): String = when (e) { null -> "missing"; JsonNull -> "null"; is JsonPrimitive -> "primitive ${e.content}"; is JsonArray -> "array"; is JsonObject -> "object" }
}
