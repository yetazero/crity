package com.yetazero.crity

import com.hypixel.hytale.protocol.packets.interface_.CustomUICommandType
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder
import com.yetazero.crity.display.DamageContext
import com.yetazero.crity.ui.FieldKind
import com.yetazero.crity.ui.SettingsDraft
import com.yetazero.crity.ui.SettingsField
import com.yetazero.crity.ui.SettingsFields
import com.yetazero.crity.ui.SettingsPanelRenderer
import com.yetazero.crity.ui.SettingsSnapshot
import com.yetazero.crity.ui.SettingsTab
import org.bson.BsonDocument
import org.bson.BsonValue
import java.math.BigDecimal
import java.math.BigInteger

internal fun checkClientCommands(builder: UICommandBuilder) {
    val decimalMax = BigDecimal(BigInteger.ONE.shiftLeft(96).subtract(BigInteger.ONE))
    fun checkValue(value: BsonValue, selector: String) {
        when {
            value.isNumber -> {
                val number = value.asNumber().doubleValue()
                check(number.isFinite() && BigDecimal.valueOf(number).abs() <= decimalMax) {
                    "$selector cannot be represented by the client's Decimal: $number"
                }
            }
            value.isDocument -> value.asDocument().values.forEach { checkValue(it, selector) }
            value.isArray -> value.asArray().values.forEach { checkValue(it, selector) }
        }
    }
    for (command in builder.commands) {
        val selector = command.selector.orEmpty()
        check(".Anchor." !in selector) { "Unsupported nested Anchor selector: $selector" }
        if (command.type == CustomUICommandType.Set) {
            val value = BsonDocument.parse(checkNotNull(command.data)).getValue("0")
            checkValue(value, selector)
            when {
                selector.endsWith(".Anchor") -> check(value.isDocument)
                selector.endsWith(".Entries") -> check(value.isArray)
                selector.endsWith(".Visible") || selector.endsWith(".Disabled") -> check(value.isBoolean)
                selector.endsWith(".Text") || selector.endsWith(".TooltipText") || selector.endsWith(".AssetPath") -> check(value.isString)
                selector.endsWith(".Color") || selector.endsWith(".TextColor") || selector.endsWith(".Background") ->
                    check(value.isString && value.asString().value.matches(Regex("#[0-9a-fA-F]{6}")))
                ".Format." in selector || "#Slider." in selector -> check(value.isNumber)
            }
        }
    }
}

internal fun checkSettingsUiContracts(original: SettingsSnapshot) {
    val baseline = original.copy(visual = original.visual.copy(damage = original.visual.damage.copy(minAngle = -180f, maxAngle = 180f)))
    var states = 0
    fun render(draft: SettingsDraft, selected: String? = draft.current.visual.damage.rules.firstOrNull()?.id) {
        for (tab in SettingsTab.entries) {
            val commands = UICommandBuilder()
            val events = UIEventBuilder()
            SettingsPanelRenderer.fields(draft, tab, selected, 1, commands, events)
            checkClientCommands(commands)
            val fields = when (tab) {
                SettingsTab.DAMAGE -> SettingsFields.modes + SettingsFields.damage
                SettingsTab.HUD -> SettingsFields.hud
                SettingsTab.HIGHLIGHT -> SettingsFields.highlight
                SettingsTab.RULES -> if (selected == null) emptyList() else SettingsFields.rule
            }
            fields.forEachIndexed { index, field ->
                val selector = "#Fields[$index] #Input"
                val value = BsonDocument.parse(commands.commands.single { it.selector == "$selector.Value" }.data).getValue("0")
                when (field.kind) {
                    FieldKind.NUMBER -> check(value.isNumber)
                    FieldKind.TOGGLE -> check(value.isBoolean)
                    else -> check(value.isString)
                }
                if (field.kind == FieldKind.COLOR) check(commands.commands.single { it.selector == "#Fields[$index] #Swatch.Background" }.data != null)
                val event = events.events.single { it.selector == selector }
                val key = when (field.kind) {
                    FieldKind.NUMBER -> "@Number"
                    FieldKind.TOGGLE -> "@Flag"
                    else -> "@Text"
                }
                val eventData = BsonDocument.parse(event.data)
                check(eventData.getString(key).value == "$selector.Value")
                check(!event.locksInterface)
                val canonical = if (tab == SettingsTab.RULES) draft.ruleValue(checkNotNull(selected), field.path) else draft.value(field.path)
                for (source in listOf(null, selector, "#Fields[$index] #Slider")) {
                    val update = UICommandBuilder()
                    SettingsPanelRenderer.syncControl(update, "#Fields[$index]", field, canonical, source)
                    checkClientCommands(update)
                    if (source != null) check(update.commands.none { it.selector == "$source.Value" })
                }
            }
        }
        val preview = UICommandBuilder()
        SettingsPanelRenderer.preview(draft, DamageContext(42f, 0.9f), true, preview)
        checkClientCommands(preview)
        states++
    }
    render(SettingsDraft(baseline))
    for (field in SettingsFields.all) {
        val values = when (field.kind) {
            FieldKind.NUMBER -> listOf(field.min.toString(), field.max.toString())
            FieldKind.TOGGLE -> listOf("true", "false")
            FieldKind.CHOICE -> field.choices
            FieldKind.COLOR -> listOf("#000000", "#ffffff", "12aBeF")
            FieldKind.TEXT -> if (field.path.endsWith("format")) listOf("HIT \"text\" \\") else listOf("CrityCombatRise")
        }
        for (value in values) {
            val draft = SettingsDraft(baseline)
            draft.field(field.path, value)
            render(draft)
        }
        if (field.kind == FieldKind.NUMBER) {
            for (value in listOf("NaN", "Infinity", "-Infinity", "1e100", "-1e100")) {
                val draft = SettingsDraft(baseline)
                check(runCatching { draft.field(field.path, value) }.isFailure)
                check(draft.current == baseline)
            }
            check(runCatching { field.numberValue("NaN") }.isFailure)
            val bounded = UICommandBuilder()
            SettingsPanelRenderer.syncControl(bounded, "#Fields[0]", field, Double.MAX_VALUE.toString(), null)
            checkClientCommands(bounded)
        }
    }
    val rules = SettingsDraft(baseline)
    val id = rules.addRule("numeric_boundary")
    for (amount in listOf("0", "0.1", "1000000", "1e28", "1e30", Float.MAX_VALUE.toString())) {
        rules.rule(id, "maxAmount", "")
        rules.rule(id, "minAmount", amount)
        rules.rule(id, "maxAmount", amount)
        render(rules, id)
    }
    rules.rule(id, "minAmount", "")
    rules.rule(id, "maxAmount", "")
    check(rules.current.visual.damage.rules.first().minAmount == 0f)
    check(rules.current.visual.damage.rules.first().maxAmount == Float.MAX_VALUE)
    for (field in SettingsFields.rule) {
        rules.rule(id, field.path, rules.ruleValue(id, field.path))
    }
    for (path in listOf("minAmount", "maxAmount", "minPercent")) {
        for (value in listOf("NaN", "Infinity", "-1", "1e100")) {
            val before = rules.current
            check(runCatching { rules.rule(id, path, value) }.isFailure)
            check(rules.current == before)
        }
    }
    for (value in listOf("0", "1")) {
        rules.rule(id, "minPercent", value)
        render(rules, id)
    }
    while (rules.current.visual.damage.rules.size < 64) rules.addRule()
    render(rules, id)
    for (rule in rules.current.visual.damage.rules.toList()) rules.removeRule(rule.id)
    render(rules, null)
    rules.addRule()
    render(rules)
    rules.reset(original.visual)
    render(rules)
    rules.accept(baseline)
    render(rules)
    for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, Float.MAX_VALUE.toDouble())) {
        check(runCatching { SettingsField("unsafe", "Unsafe", FieldKind.NUMBER, max = invalid) }.isFailure)
    }
    println("PASS: $states settings states across all tabs, typed field/event bindings, Decimal-safe limits and updates, huge/empty damage bounds, invalid input rollback, empty/full rules and preview commands")
}
