package com.yetazero.crity.compat

internal object ApiCapabilities {
    fun nativeUiAssets() {
        val root = com.hypixel.hytale.server.core.asset.AssetModule.get().baseAssetPack.root
        val common = java.nio.file.Files.readString(root.resolve("Common/UI/Custom/Common.ui"))
        val required = listOf("PageOverlay", "DecoratedContainer", "Container", "Title", "DefaultSliderStyle", "TextField", "NumberField",
            "CheckBox", "DropdownBox", "DefaultScrollbarStyle", "SecondaryTextButton", "SmallSecondaryTextButton", "TextButton", "DefaultTextTooltipStyle")
        check(required.all { Regex("@" + it + "\\s*=").containsMatchIn(common) }) { "Hytale's shared UI styles have changed; the settings panel is unavailable." }
    }

    fun requireMethod(owner: String, name: String, vararg parameterTypes: String) {
        val loader = javaClass.classLoader
        val type = Class.forName(owner, false, loader)
        val parameters = parameterTypes.map { parameter ->
            when (parameter) {
                "boolean" -> Boolean::class.javaPrimitiveType!!
                "int" -> Int::class.javaPrimitiveType!!
                "float" -> Float::class.javaPrimitiveType!!
                else -> Class.forName(parameter, false, loader)
            }
        }.toTypedArray()
        type.getMethod(name, *parameters)
    }

    fun settingsPanel() {
        val root = "com.hypixel.hytale.server.core."
        requireMethod(root + "entity.entities.Player", "getPageManager")
        requireMethod(root + "entity.entities.player.pages.PageManager", "openCustomPage",
            "com.hypixel.hytale.component.Ref", "com.hypixel.hytale.component.Store", root + "entity.entities.player.pages.CustomUIPage")
        requireMethod(root + "ui.builder.UICommandBuilder", "append", "java.lang.String")
        requireMethod(root + "ui.builder.UIEventBuilder", "addEventBinding",
            "com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType", "java.lang.String",
            root + "ui.builder.EventData", "boolean")
    }
}
