package com.jacemayer.dclass.parser

import com.intellij.psi.tree.IElementType
import com.jacemayer.dclass.DCLanguage

class DCElementType(debugName: String) : IElementType(debugName, DCLanguage)

object DCElementTypes {
    @JvmField val IMPORT_DECL = DCElementType("IMPORT_DECL")
    @JvmField val KEYWORD_DECL = DCElementType("KEYWORD_DECL")
    @JvmField val TYPEDEF_DECL = DCElementType("TYPEDEF_DECL")
    @JvmField val CLASS_DECL = DCElementType("CLASS_DECL")
    @JvmField val STRUCT_DECL = DCElementType("STRUCT_DECL")
    @JvmField val SWITCH_DECL = DCElementType("SWITCH_DECL")

    @JvmField val IMPORT_MODULE = DCElementType("IMPORT_MODULE")
    @JvmField val IMPORT_SYMBOL = DCElementType("IMPORT_SYMBOL")

    @JvmField val PARENT_LIST = DCElementType("PARENT_LIST")
    @JvmField val PARENT_REF = DCElementType("PARENT_REF")
    @JvmField val CLASS_BODY = DCElementType("CLASS_BODY")

    @JvmField val PARAMETER_FIELD = DCElementType("PARAMETER_FIELD")
    @JvmField val ATOMIC_FIELD = DCElementType("ATOMIC_FIELD")
    @JvmField val MOLECULAR_FIELD = DCElementType("MOLECULAR_FIELD")
    @JvmField val MOLECULAR_REF = DCElementType("MOLECULAR_REF")

    @JvmField val PARAMETER = DCElementType("PARAMETER")
    @JvmField val TYPE_REF = DCElementType("TYPE_REF")
    @JvmField val BUILTIN_TYPE = DCElementType("BUILTIN_TYPE")
    @JvmField val INLINE_STRUCT_TYPE = DCElementType("INLINE_STRUCT_TYPE")
    @JvmField val RANGE = DCElementType("RANGE")
    @JvmField val RANGE_BOUND = DCElementType("RANGE_BOUND")
    @JvmField val ARRAY_SPEC = DCElementType("ARRAY_SPEC")
    @JvmField val DIVISOR = DCElementType("DIVISOR")
    @JvmField val MODULUS = DCElementType("MODULUS")
    @JvmField val DEFAULT_VALUE = DCElementType("DEFAULT_VALUE")
    @JvmField val VALUE_LIST = DCElementType("VALUE_LIST")

    @JvmField val KEYWORD_LIST = DCElementType("KEYWORD_LIST")
    @JvmField val KEYWORD_REF = DCElementType("KEYWORD_REF")

    @JvmField val SWITCH_CASE = DCElementType("SWITCH_CASE")
}
