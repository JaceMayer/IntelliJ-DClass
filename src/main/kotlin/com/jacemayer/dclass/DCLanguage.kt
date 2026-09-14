package com.jacemayer.dclass

import com.intellij.lang.Language

object DCLanguage : Language("DClass") {
    private fun readResolve(): Any = DCLanguage
    override fun getDisplayName(): String = "DClass"
    override fun isCaseSensitive(): Boolean = true
}
