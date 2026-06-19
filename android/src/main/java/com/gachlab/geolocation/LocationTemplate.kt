// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

interface LocationTemplate : java.io.Serializable {
    @Throws(JSONException::class)
    fun locationToJson(location: BGLocation): Any  // JSONObject or JSONArray
    fun isEmpty(): Boolean
    /** Serialize the template definition back to JSON (for storage / JS round-trip). */
    fun toDefinitionJson(): Any?  // JSONObject, JSONArray, or null
}

/**
 * Resolves `@`-prefixed placeholders inside an ARBITRARY template shape against a
 * location, recursively. Walks nested objects and arrays to any depth, replaces
 * each `"@key"` string with the location's value for that key, and preserves
 * every other value (literal strings, numbers, booleans, null) untouched.
 *
 * This is plugin-public behaviour — consumers may pass any JSON template (flat,
 * nested, arrays of objects, …), so resolution must be structure-agnostic. The
 * full `"@key"` (with the `@`) is passed to [BGLocation.getValueForKey], which
 * matches on the `@` prefix.
 */
internal object TemplateValueResolver {
    fun resolve(value: Any?, location: BGLocation): Any? = when (value) {
        is JSONObject -> resolveObject(value, location)
        is JSONArray  -> resolveArray(value, location)
        is String     -> if (value.startsWith("@")) (location.getValueForKey(value) ?: value) else value
        else          -> value
    }

    fun resolveObject(obj: JSONObject, location: BGLocation): JSONObject {
        val result = JSONObject()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            result.put(key, resolve(obj.get(key), location))
        }
        return result
    }

    fun resolveArray(arr: JSONArray, location: BGLocation): JSONArray {
        val result = JSONArray()
        for (i in 0 until arr.length()) {
            result.put(resolve(arr.get(i), location))
        }
        return result
    }
}

/**
 * Default template: serializes to a JSONObject using all location fields.
 * Keys may include `@`-prefixed placeholders resolved by UrlTemplateResolver.
 * The template definition is stored as a JSON string for Serializable compat.
 */
class HashMapLocationTemplate(templateJson: JSONObject? = null) : LocationTemplate {

    // Store as String so Serializable works (JSONObject is not Serializable).
    private val templateStr: String? = templateJson?.toString()

    private fun parsedTemplate(): JSONObject? = templateStr?.let { JSONObject(it) }

    override fun locationToJson(location: BGLocation): JSONObject {
        val template = parsedTemplate()
        if (template == null || template.length() == 0) {
            return location.toJSONObject()
        }
        return TemplateValueResolver.resolveObject(template, location)
    }

    override fun isEmpty(): Boolean = templateStr == null || parsedTemplate()?.length() == 0

    override fun toDefinitionJson(): JSONObject? = parsedTemplate()

    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Array template: each element is either a literal or a `@`-prefixed location key.
 * The template definition is stored as a JSON string for Serializable compat.
 */
class ArrayListLocationTemplate(templateJson: JSONArray? = null) : LocationTemplate {

    private val templateStr: String? = templateJson?.toString()

    private fun parsedTemplate(): JSONArray? = templateStr?.let { JSONArray(it) }

    override fun locationToJson(location: BGLocation): JSONArray {
        val template = parsedTemplate()
        if (template == null || template.length() == 0) {
            return JSONArray().put(location.toJSONObject())
        }
        return TemplateValueResolver.resolveArray(template, location)
    }

    override fun isEmpty(): Boolean = templateStr == null || parsedTemplate()?.length() == 0

    override fun toDefinitionJson(): JSONArray? = parsedTemplate()

    companion object {
        private const val serialVersionUID = 2L
    }
}

object LocationTemplateFactory {
    fun fromJSONObject(json: JSONObject?): LocationTemplate = HashMapLocationTemplate(json)
    fun fromJSONArray(json: JSONArray?): LocationTemplate = ArrayListLocationTemplate(json)
    fun empty(): LocationTemplate = HashMapLocationTemplate(null)

    /** Accept either a JSONObject or JSONArray (for use when the type is unknown at call site). */
    fun fromJSON(value: Any?): LocationTemplate = when (value) {
        is JSONObject -> HashMapLocationTemplate(value)
        is JSONArray  -> ArrayListLocationTemplate(value)
        else          -> empty()
    }
}
