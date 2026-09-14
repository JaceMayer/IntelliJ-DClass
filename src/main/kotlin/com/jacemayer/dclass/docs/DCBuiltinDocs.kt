package com.jacemayer.dclass.docs

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode


data class KeywordDoc(
    val name: String,
    val summary: String,
    val implies: List<String> = emptyList(),
    val note: String? = null,
)

data class TypeDoc(
    val name: String,
    val summary: String,
    val fixedBytes: Int? = null,
    val min: BigInteger? = null,
    val max: BigInteger? = null,
    val encoding: String? = null,
)

object DCBuiltinDocs {

    val KEYWORDS: Map<String, KeywordDoc> = listOf(
        KeywordDoc(
            "required",
            "An object must be given its required values on creation. " +
                "It is included in every object snapshot sent  to the AI, and in snapshots to the client when the client can also see it via ownrecv or clrecv.",
            implies = listOf("ram"),
            note = "By convention these fields are named set* and get*.",
        ),
        KeywordDoc(
            "broadcast",
            "Updates to this field are sent to every client that can see the object. " +
                "Leave it off when only the object's owner should hear about the change, " +
                "such as private chat or receiving currency.",
            implies = listOf("clrecv"),
        ),
        KeywordDoc(
            "ownrecv",
            "An object may be owned by a client. That owner receives this field when it gains ownership, " +
                "and receives any later updates to it.",
        ),
        KeywordDoc(
            "ram",
            "The value persists in the State Server for the object's lifetime, so a client that becomes " +
                "interested later is sent the current value rather than missing it.",
        ),
        KeywordDoc(
            "db",
            "The field is persisted long term by the Database Server which " +
                "records updates to this field and restores them when the object is next activated.",
        ),
        KeywordDoc(
            "clsend",
            "Any client that can see the object may update this field. Without it, an update from a " +
                "non-owning client is a security violation.",
            note = "The Client Agent enforces this: a client updating a field marked neither clsend nor " +
                "ownsend is disconnected and the violation logged.",
        ),
        KeywordDoc(
            "clrecv",
            "The client is sent this field when the object first becomes visible to it.",
        ),
        KeywordDoc(
            "ownsend",
            "A client may update this field on an object it owns.",
            note = "The Client Agent enforces this: a client updating a field marked neither clsend nor " +
                "ownsend is disconnected and the violation logged.",
        ),
        KeywordDoc(
            "airecv",
            "Updates to this field are sent to the object's managing AI server.",
        ),
    ).associateBy { it.name }

    val CONVENTIONAL_KEYWORDS: Map<String, KeywordDoc> = listOf(
        KeywordDoc(
            "p2p",
            "Panda3D convention: only the owner of the object receives updates for this method.",
            implies = listOf("clsend"),
            note = "Not one of the nine built-in keywords, so it must be declared with `keyword p2p;` " +
                "and its behaviour comes from your own code rather than from the server.",
        ),
    ).associateBy { it.name }

    val TYPES: Map<String, TypeDoc> = listOf(
        intType("int8", 1, "-128", "127"),
        intType("int16", 2, "-32768", "32767"),
        intType("int32", 4, "-2147483648", "2147483647"),
        intType("int64", 8, "-9223372036854775808", "9223372036854775807"),
        intType("uint8", 1, "0", "255"),
        intType("uint16", 2, "0", "65535"),
        intType("uint32", 4, "0", "4294967295"),
        intType("uint64", 8, "0", "18446744073709551615"),
        TypeDoc(
            "float64", "A C double. The only floating-point type DC has.", fixedBytes = 8,
            encoding = "8 bytes. For small values a scaled integer such as int16/100 is usually cheaper.",
        ),
        TypeDoc("char", "A single byte, treated as a character rather than a number.", fixedBytes = 1),
        TypeDoc(
            "string", "Text, up to 64k. Bandwidth-hungry, so avoid it on frequently sent fields.",
            encoding = "A uint16 length, then that many bytes. Fixing the length to a single value " +
                "(`string(6)`) drops the length prefix and makes the field fixed-size.",
        ),
        TypeDoc(
            "blob", "An arbitrary byte sequence: data too complex or too binary for the normal DC types.",
            encoding = "A uint16 length, then that many bytes. A single-valued range drops the length prefix.",
        ),
        TypeDoc(
            "blob32", "Opaque bytes, for payloads that can exceed 65,535 bytes.",
            encoding = "A uint32 length, then that many bytes.",
        ),
        arrayType("int8array", 1), arrayType("int16array", 2), arrayType("int32array", 4),
        arrayType("uint8array", 1), arrayType("uint16array", 2), arrayType("uint32array", 4),
        TypeDoc(
            "uint32uint8array", "Array of (uint32, uint8) pairs.",
            encoding = "A uint16 byte-length, then 5 bytes per element.",
        ),
    ).associateBy { it.name }

    private fun intType(name: String, bytes: Int, min: String, max: String) = TypeDoc(
        name,
        "${if (name.startsWith("u")) "Unsigned" else "Signed"} ${bytes * 8}-bit integer.",
        fixedBytes = bytes,
        min = BigInteger(min),
        max = BigInteger(max),
    )

    private fun arrayType(name: String, bytesPerElement: Int) = TypeDoc(
        name,
        "Array of ${name.removeSuffix("array")}.",
        encoding = "A uint16 byte-length, then $bytesPerElement byte" +
            "${if (bytesPerElement == 1) "" else "s"} per element.",
    )


    fun divisorNote(type: TypeDoc?, divisorText: String): String {
        val divisor = divisorText.toBigIntegerOrNull()
        val base = "The value is multiplied by $divisorText and sent as ${type?.name ?: "an integer"}, " +
            "so it carries fractional precision without the cost of a float64."
        if (type?.min == null || type.max == null || divisor == null || divisor.signum() <= 0) return base

        val d = BigDecimal(divisor)
        val lo = BigDecimal(type.min).divide(d, 6, RoundingMode.DOWN).stripTrailingZeros().toPlainString()
        val hi = BigDecimal(type.max).divide(d, 6, RoundingMode.DOWN).stripTrailingZeros().toPlainString()
        val step = BigDecimal.ONE.divide(d, 6, RoundingMode.DOWN).stripTrailingZeros().toPlainString()
        return "$base Representable values run from $lo to $hi in steps of $step."
    }

    fun modulusNote(modulus: String): String =
        "Values wrap into a span of $modulus before being sent, so a quantity that cycles " +
            "(a heading, for instance) stays inside the underlying integer's range."

    fun rangeNote(range: String): String =
        "Only values in $range may be packed; anything outside is a packing error. " +
            "On a string or blob the range constrains the length rather than the value, " +
            "and a single-valued range makes the field fixed-size."

    fun arraySpecNote(spec: String): String =
        if (spec.trim() == "[]") "A dynamic array: the length is sent with the data."
        else "A fixed-size array of $spec elements."
}
