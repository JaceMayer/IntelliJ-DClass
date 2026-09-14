package com.jacemayer.dclass.highlight

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.jacemayer.dclass.DCIcons
import javax.swing.Icon

class DCColorSettingsPage : ColorSettingsPage {

    override fun getIcon(): Icon = DCIcons.FILE
    override fun getHighlighter(): SyntaxHighlighter = DCSyntaxHighlighter()
    override fun getDisplayName(): String = "DClass"
    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
    override fun getAdditionalHighlightingTagToDescriptorMap(): MutableMap<String, TextAttributesKey> =
        mutableMapOf(
            "class" to DCColors.CLASS_NAME,
            "field" to DCColors.FIELD_NAME,
        )

    override fun getDemoText(): String = """
        // A DC Comment
        from direct.distributed import DistributedObject/AI/UD

        typedef uint8 bool;

        keyword awesome;

        struct <class>RGBAColor</class> {
          uint8/100 <field>r</field>;
          uint8/100 <field>g</field>;
          uint8/100 <field>b</field>;
        };

        dclass <class>DistributedPlayer</class> : <class>DistributedAvatar</class> {
          string(0-15) <field>setName</field>(string) required broadcast db;
          int32/10 <field>setPos</field>[] broadcast ram;
          <field>setXYH</field> : <field>setX</field>, <field>setY</field>, <field>setH</field>;
          uint16 <field>setHp</field> = 15;
        };
    """.trimIndent()

    private companion object {
        val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Keyword", DCColors.KEYWORD),
            AttributesDescriptor("Built-in type", DCColors.TYPE),
            AttributesDescriptor("Field keyword//required, broadcast, ram, ...", DCColors.FIELD_KEYWORD),
            AttributesDescriptor("Identifier", DCColors.IDENTIFIER),
            AttributesDescriptor("Declarations//Class or struct name", DCColors.CLASS_NAME),
            AttributesDescriptor("Declarations//Field name", DCColors.FIELD_NAME),
            AttributesDescriptor("Number", DCColors.NUMBER),
            AttributesDescriptor("String", DCColors.STRING),
            AttributesDescriptor("Comments//Line comment", DCColors.LINE_COMMENT),
            AttributesDescriptor("Comments//Block comment", DCColors.BLOCK_COMMENT),
            AttributesDescriptor("Braces and operators//Braces", DCColors.BRACES),
            AttributesDescriptor("Braces and operators//Parentheses", DCColors.PARENTHESES),
            AttributesDescriptor("Braces and operators//Brackets", DCColors.BRACKETS),
            AttributesDescriptor("Braces and operators//Semicolon", DCColors.SEMICOLON),
            AttributesDescriptor("Braces and operators//Comma", DCColors.COMMA),
            AttributesDescriptor("Braces and operators//Operator", DCColors.OPERATOR),
            AttributesDescriptor("Bad character", DCColors.BAD_CHARACTER),
        )
    }
}
