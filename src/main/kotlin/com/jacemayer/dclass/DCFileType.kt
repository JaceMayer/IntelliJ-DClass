package com.jacemayer.dclass

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object DCFileType : LanguageFileType(DCLanguage) {
    override fun getName(): String = "DClass"
    override fun getDescription(): String = "Panda3D/Astron distributed class definition"
    override fun getDefaultExtension(): String = "dc"
    override fun getIcon(): Icon = DCIcons.FILE
}
