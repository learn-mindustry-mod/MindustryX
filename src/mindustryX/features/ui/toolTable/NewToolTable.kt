package mindustryX.features.ui.toolTable

import arc.Core
import arc.scene.ui.layout.Table
import mindustry.Vars
import mindustry.gen.Call
import mindustry.gen.Icon
import mindustry.ui.Styles
import mindustryX.features.SettingsV2

object NewToolTable {
    data class CustomButton(val name: String, val content: String) {
        constructor() : this("?", "未输入指令")

        fun run() {
            if (content.startsWith("@js ")) {
                Vars.mods.scripts.runConsole(Vars.ui.consolefrag.injectConsoleVariables() + content.substring(4))
            } else {
                Call.sendChatMessage(content)
            }
        }
    }

    @JvmField
    val columns = SettingsV2.SliderPref("quickButtons.columns", 6, 3, 10).apply {
        addFallbackName("arcQuickMsgKey")
    }

    @JvmField
    val customButtons = object : SettingsV2.Data<List<CustomButton>>("quickButtons.customButtons", emptyList()) {
        init {
            persistentProvider = SettingsV2.PersistentProvider.AsUBJson(SettingsV2.PersistentProvider.Arc(name), List::class.java, CustomButton::class.java)
            addFallback(object : SettingsV2.PersistentProvider<List<CustomButton>> {
                val num = SettingsV2.PersistentProvider.Arc<Int>("arcQuickMsg")
                override fun get(): List<CustomButton>? {
                    val num = num.get() ?: return null
                    return List(num) { i ->
                        val name = Core.settings.getString("arcQuickMsgShort$i", "")
                        val isJs = Core.settings.getBool("arcQuickMsgJs$i", false)
                        val content = Core.settings.getString("arcQuickMsg$i", "")
                        CustomButton(name, if (isJs) "@js $content" else content)
                    }
                }

                override fun reset() {
                    val num = num.get() ?: return
                    for (i in 0 until num) {
                        Core.settings.remove("arcQuickMsgShort$i")
                        Core.settings.remove("arcQuickMsgJs$i")
                        Core.settings.remove("arcQuickMsg$i")
                    }
                    this.num.reset()
                }
            })
        }

        override fun buildUI(table: Table) {
            var shown = false
            table.button(title) { shown = !shown }.fillX().height(55f).padBottom(2f).get().apply {
                imageDraw { if (shown) Icon.downOpen else Icon.upOpen }.size(Vars.iconMed)
                cells.reverse()
                update { isChecked = shown }
            }
            table.row()
            table.collapser(Table().apply {
                defaults().pad(2f)
                update {
                    if (changed()) clearChildren()
                    if (hasChildren()) return@update
                    add("序号");add("显示名");add("消息(@js 开头为脚本)");row()
                    value.forEachIndexed { i, d ->
                        var tmp = d
                        add(i.toString()).padRight(4f)
                        field(d.name) { v -> tmp = tmp.copy(name = v) }.maxTextLength(10)
                        field(d.content) { v -> tmp = tmp.copy(content = v) }.maxTextLength(300).growX()
                        button(Icon.trashSmall, Styles.clearNonei, Vars.iconMed) {
                            set(value.filterNot { it === d })
                        }
                        button(Icon.saveSmall, Styles.clearNonei, Vars.iconMed) {
                            set(value.map { if (it === d) tmp else it })
                        }.disabled { tmp === d }
                        row()
                    }
                    button("@add", Icon.addSmall) {
                        set(value + CustomButton())
                    }.colspan(columns).fillX().row()
                    add("[yellow]添加新指令前，请先保存编辑的指令").colspan(columns).center().padTop(-4f).row()
                }
            }) { shown }.fillX()
            table.row()
        }
    }
}