package com.jacemayer.dclass.annotator

import kotlin.math.floor


object DCTypeDomain {

    enum class Form { NUMBER, CHARACTER, TEXT, BYTES }

    data class Domain(
        val form: Form,
        val min: Double?,
        val max: Double?,
        val lengthBits: Int? = null,
    )

    enum class Severity { ERROR, WARNING }

    data class Finding(val index: Int, val severity: Severity, val message: String)

    data class Bound(val min: Double?, val max: Double?, val text: String, val isTextual: Boolean)

    private const val I8 = 128.0
    private const val I16 = 32768.0
    private const val I32 = 2147483648.0

    private val DOMAINS: Map<String, Domain> = mapOf(
        "int8" to Domain(Form.NUMBER, -I8, I8 - 1),
        "int8array" to Domain(Form.NUMBER, -I8, I8 - 1),
        "int16" to Domain(Form.NUMBER, -I16, I16 - 1),
        "int16array" to Domain(Form.NUMBER, -I16, I16 - 1),
        "int32" to Domain(Form.NUMBER, -I32, I32 - 1),
        "int32array" to Domain(Form.NUMBER, -I32, I32 - 1),
        "int64" to Domain(Form.NUMBER, null, null),
        "uint8" to Domain(Form.NUMBER, 0.0, 255.0),
        "uint8array" to Domain(Form.NUMBER, 0.0, 255.0),
        "uint16" to Domain(Form.NUMBER, 0.0, 65535.0),
        "uint16array" to Domain(Form.NUMBER, 0.0, 65535.0),
        "uint32" to Domain(Form.NUMBER, 0.0, 4294967295.0),
        "uint32array" to Domain(Form.NUMBER, 0.0, 4294967295.0),
        "uint64" to Domain(Form.NUMBER, null, null),
        "float64" to Domain(Form.NUMBER, null, null),
        "char" to Domain(Form.CHARACTER, 0.0, 255.0),
        "string" to Domain(Form.TEXT, null, null, lengthBits = 16),
        "blob" to Domain(Form.BYTES, null, null, lengthBits = 16),
        "blob32" to Domain(Form.BYTES, null, null, lengthBits = 32),
    )

    fun of(typeName: String?): Domain? = typeName?.let { DOMAINS[it] }

    fun forbidsRange(typeName: String?): Boolean = typeName == "uint32uint8array"

    fun checkRange(
        typeName: String?,
        bounds: List<Bound>,
        divisorAtRange: Double = 1.0,
        finalDivisor: Double = 1.0,
    ): List<Finding> {
        if (bounds.isEmpty()) return emptyList()

        if (forbidsRange(typeName)) {
            return bounds.indices.map {
                Finding(it, Severity.ERROR, "A range is not allowed on $typeName")
            }
        }
        val domain = of(typeName) ?: return emptyList()

        val findings = ArrayList<Finding>()
        val accepted = ArrayList<Pair<Double, Double>>()

        for ((index, bound) in bounds.withIndex()) {
            val min = bound.min
            val max = bound.max

            if (bound.isTextual || min == null || max == null) continue

            if (max < min) {
                findings += Finding(
                    index, Severity.ERROR,
                    "Range ${show(min)}-${show(max)} is empty: the upper bound is below the lower bound",
                )
                continue
            }

            val overlapped = accepted.firstOrNull { (lo, hi) -> min <= hi && max >= lo }
            if (overlapped != null) {
                findings += Finding(
                    index, Severity.ERROR,
                    "Range ${show(min)}-${show(max)} overlaps " +
                        "${show(overlapped.first)}-${show(overlapped.second)}",
                )
                continue
            }
            accepted += min to max

            val strict = outOfDomain(domain, typeName!!, min, max, divisorAtRange)
            if (strict != null) {
                findings += Finding(index, Severity.ERROR, strict)
                continue
            }
            if (finalDivisor != divisorAtRange) {
                outOfDomain(domain, typeName, min, max, finalDivisor)?.let {
                    findings += Finding(
                        index, Severity.WARNING,
                        "$it. OtpGo checks a range only against the divisor written before it, " +
                            "so it accepts this and then packs values incorrectly; write the " +
                            "divisor first to have it checked.",
                    )
                }
            }
        }
        return findings
    }

    private fun outOfDomain(
        domain: Domain,
        typeName: String,
        min: Double,
        max: Double,
        divisor: Double,
    ): String? {
        domain.lengthBits?.let { bits ->
            val limit = Math.scalb(1.0, bits) - 1
            val bad = listOf(min, max).firstOrNull { it < 0 || it > limit } ?: return null
            return "Length ${show(bad)} does not fit in $typeName, whose length is counted " +
                "in $bits bits (0 to ${show(limit)})"
        }

        val low = domain.min ?: return null
        val high = domain.max ?: return null
        val bad = listOf(min, max).firstOrNull {
            val scaled = scale(it, divisor)
            scaled < low || scaled > high
        } ?: return null
        val scaled = scale(bad, divisor)

        return if (divisor == 1.0) {
            "Value ${show(bad)} is outside the range of $typeName (${show(low)} to ${show(high)})"
        } else {
            "The divisor scales ${show(bad)} to ${show(scaled)}, outside the range of " +
                "$typeName (${show(low)} to ${show(high)})"
        }
    }


    fun checkCaseValue(typeName: String?, keyRange: List<Bound>, literal: String): String? {
        val domain = of(typeName) ?: return null

        val quoted = literal.length >= 2 && (literal.first() == '"' || literal.first() == '\'')
        val hex = literal.startsWith("<")

        when (domain.form) {
            Form.NUMBER -> {
                if (quoted || hex) {
                    return "A $typeName case must be a number, not ${describe(literal)}"
                }
                val value = numberOf(literal) ?: return null
                val low = domain.min
                val high = domain.max
                if (low != null && high != null && (value < low || value > high)) {
                    return "Case ${show(value)} is outside the range of the key type " +
                        "$typeName (${show(low)} to ${show(high)})"
                }
                if (keyRange.isNotEmpty() && keyRange.none { b ->
                        b.min != null && b.max != null && value >= b.min && value <= b.max
                    }
                ) {
                    return "Case ${show(value)} is outside the range declared on the key"
                }
            }

            Form.CHARACTER -> {
                if (!quoted) {
                    return "A char case must be a quoted character, such as 'A', not ${describe(literal)}"
                }
            }

            Form.TEXT -> {
                if (!quoted) return "A $typeName case must be a quoted string, not ${describe(literal)}"
                val length = literal.length - 2
                if (keyRange.isNotEmpty() && keyRange.none { b ->
                        b.min != null && b.max != null && length >= b.min && length <= b.max
                    }
                ) {
                    return "Case of length $length is outside the length range declared on the key"
                }
            }
            Form.BYTES -> Unit
        }
        return null
    }

    fun caseKey(typeName: String?, literal: String): String {
        val domain = of(typeName)
        if (domain != null && domain.form == Form.NUMBER) {
            numberOf(literal)?.let { return show(it) }
        }
        return literal
    }

    private fun describe(literal: String): String =
        if (literal.length > 16) "${literal.take(15)}…" else literal

    private fun scale(value: Double, divisor: Double): Double =
        if (divisor == 1.0) value else floor(value * divisor + 0.5)

    fun numberOf(text: String): Double? {
        val t = text.trim()
        if (t.startsWith("0x") || t.startsWith("0X")) return t.drop(2).toLongOrNull(16)?.toDouble()
        if (t.startsWith("-0x") || t.startsWith("-0X")) {
            return t.drop(3).toLongOrNull(16)?.toDouble()?.unaryMinus()
        }
        return t.toDoubleOrNull()
    }

    private fun show(value: Double): String =
        if (value == floor(value) && value.isFinite() && kotlin.math.abs(value) < 1e15) {
            value.toLong().toString()
        } else {
            value.toString()
        }
}
