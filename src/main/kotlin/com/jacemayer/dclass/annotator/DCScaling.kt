package com.jacemayer.dclass.annotator

import kotlin.math.floor


object DCScaling {

    enum class Kind { DIVISOR, MODULUS }

    data class Op(val kind: Kind, val literal: String)

    enum class Severity { ERROR, WARNING }

    data class Finding(val index: Int, val severity: Severity, val message: String)

    private val NON_NUMERIC = setOf("string", "blob", "blob32", "char")

    private val MODULUS_BITS: Map<String, Int?> = mapOf(
        "int8" to 7, "int8array" to 7,
        "int16" to 15, "int16array" to 15,
        "int32" to 31, "int32array" to 31,
        "int64" to 63,
        "uint8" to 8, "uint8array" to 8,
        "uint16" to 16, "uint16array" to 16,
        "uint32" to 32, "uint32array" to 32,
        "uint64" to null,
        "float64" to null,
    )


    fun check(typeName: String?, ops: List<Op>): List<Finding> {
        if (ops.isEmpty()) return emptyList()
        if (typeName == null) {
            return ops.indices.map {
                Finding(it, Severity.ERROR, "A divisor or modulus is only allowed on a primitive type")
            }
        }

        val numeric = typeName !in NON_NUMERIC
        val modulusAllowed = numeric && MODULUS_BITS.containsKey(typeName)
        val bits = MODULUS_BITS[typeName]

        val findings = ArrayList<Finding>()

        var divisor = 1.0
        var modulus: Double? = null
        var modulusIndex = -1
        var divisorWhenModulusSet = 1.0

        for ((index, op) in ops.withIndex()) {
            val value = op.literal.toDoubleOrNull()
            when (op.kind) {
                Kind.DIVISOR -> {
                    if (!numeric) {
                        findings += Finding(
                            index, Severity.ERROR,
                            "A divisor is not allowed on $typeName, which is not a numeric type",
                        )
                        continue
                    }
                    if (value == null) continue
                    if (value < 1.0) {
                        findings += Finding(index, Severity.ERROR, "A divisor must be greater than zero")
                        continue
                    }
                    divisor = value
                }

                Kind.MODULUS -> {
                    if (!modulusAllowed) {
                        val why =
                            if (!numeric) "which is not a numeric type"
                            else "whose elements are not all the same width"
                        findings += Finding(index, Severity.ERROR, "A modulus is not allowed on $typeName, $why")
                        continue
                    }
                    if (value == null) continue
                    if (value <= 0.0) {
                        findings += Finding(index, Severity.ERROR, "A modulus must be greater than zero")
                        continue
                    }
                    modulus = value
                    modulusIndex = index
                    divisorWhenModulusSet = divisor
                    if (bits != null && !fits(value, divisor, bits)) {
                        findings += Finding(index, Severity.ERROR, tooBig(typeName, value, divisor, bits))
                    }
                }
            }
        }

        if (modulus != null && bits != null &&
            divisor != divisorWhenModulusSet &&
            fits(modulus, divisorWhenModulusSet, bits) &&
            !fits(modulus, divisor, bits)
        ) {
            findings += Finding(
                modulusIndex, Severity.WARNING,
                "Modulus ${show(modulus)} does not fit in $typeName once the divisor " +
                    "${show(divisor)} is applied: ${scaledDescription(modulus, divisor, bits)}. " +
                    "OtpGo checks a modulus only against the divisor written before it, so it " +
                    "accepts this and then packs values incorrectly; write the divisor first " +
                    "to have it checked.",
            )
        }

        return findings
    }

    private fun tooBig(typeName: String, modulus: Double, divisor: Double, bits: Int): String {
        val head = "Modulus ${show(modulus)} does not fit in $typeName"
        return if (divisor == 1.0) {
            "$head, which allows at most ${show(limit(bits))}"
        } else {
            "$head: ${scaledDescription(modulus, divisor, bits)}"
        }
    }

    private fun scaledDescription(modulus: Double, divisor: Double, bits: Int): String =
        "the divisor scales it to ${show(scale(modulus, divisor))}, " +
            "above the limit of ${show(limit(bits))}"

    private fun limit(bits: Int): Double = Math.scalb(1.0, bits)

    private fun scale(modulus: Double, divisor: Double): Double = floor(modulus * divisor + 0.5)


    private fun fits(modulus: Double, divisor: Double, bits: Int): Boolean {
        val scaled = scale(modulus, divisor)
        if (!scaled.isFinite() || scaled < 1.0 || scaled >= TWO_TO_THE_64) return false
        return ((scaled.toULong() - 1uL) shr bits) == 0uL
    }

    private const val TWO_TO_THE_64 = 1.8446744073709552E19

    private fun show(value: Double): String =
        if (value == floor(value) && value.isFinite() && kotlin.math.abs(value) < 1e15) {
            value.toLong().toString()
        } else {
            value.toString()
        }
}
