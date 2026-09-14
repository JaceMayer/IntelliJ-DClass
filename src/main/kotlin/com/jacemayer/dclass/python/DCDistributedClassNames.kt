package com.jacemayer.dclass.python


object DCDistributedClassNames {
    enum class Side(val suffix: String, val label: String) {
        CLIENT("", "Client"),
        AI("AI", "AI"),
        UD("UD", "UD"),
    }

    fun baseClass(global: Boolean, side: Side): String =
        if (global) "DistributedObjectGlobal${side.suffix}" else "DistributedObject${side.suffix}"


    fun className(typed: String, global: Boolean, side: Side): String {
        val stem = if (global) typed else "Distributed$typed"
        return stem + side.suffix
    }

    fun fileName(typed: String, global: Boolean, side: Side): String =
        className(typed, global, side) + ".py"

    fun validate(typed: String, global: Boolean): String? {
        val name = typed.trim()
        if (name.isEmpty()) return "Enter a name"
        if (!name[0].isLetter() && name[0] != '_') return "A name must start with a letter"
        if (!name.all { it.isLetterOrDigit() || it == '_' }) {
            return "A name may only contain letters, digits and underscores"
        }
        if (!global && name.startsWith("Distributed")) {
            return "Drop the 'Distributed' prefix; it is added for you"
        }
        for (side in Side.entries) {
            if (name.endsWith(side.suffix) && side.suffix.isNotEmpty()) {
                return "Drop the '${side.suffix}' suffix; it is added for you"
            }
        }
        return null
    }
}
