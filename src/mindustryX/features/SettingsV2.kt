package mindustryX.features

import arc.Core
import arc.graphics.Color
import arc.input.KeyCode
import arc.scene.event.ClickListener
import arc.scene.event.InputEvent
import arc.scene.event.Touchable
import arc.scene.ui.*
import arc.scene.ui.layout.Table
import arc.util.Align
import arc.util.Log
import arc.util.Reflect
import arc.util.Time
import mindustry.Vars
import mindustry.gen.Icon
import mindustry.graphics.Pal
import mindustry.ui.Styles
import mindustry.ui.dialogs.BaseDialog

/**
 * 新的设置类
 * 接口与Core.settings解耦，所有设置项将在实例化时读取
 * 所有读取修改应该通过value字段进行
 */

object SettingsV2 {
    //headless Data
    open class DataCore<T>(val name: String, val def: T) {
        var value: T = def
            private set
        var persistentProvider: PersistentProvider = PersistentProvider.Arc
        private val changedSet = mutableSetOf<String>()

        fun get(): T = value //for java usage
        open fun set(value: T) {
            if (value == this.value) return
            this.value = value
            persistentProvider.set(name, value)
            changedSet.clear()
        }

        init {
            if (name in ALL)
                Log.warn("Settings initialized!: $name")
            ALL[name] = this
            persistentProvider.run {
                value = get(name, def)
            }
        }

        @JvmOverloads
        fun changed(name: String = "DEFAULT"): Boolean {
            return changedSet.add(name)
        }

        fun resetDefault() {
            persistentProvider.reset(name)
            value = def
            changedSet.clear()
        }

        fun addFallbackName(name: String) {
            persistentProvider = PersistentProvider.WithFallback(name, persistentProvider)
        }
    }

    open class Data<T>(name: String, def: T) : DataCore<T>(name, def) {
        val category: String get() = name.substringBefore('.', "")
        val title: String get() = Core.bundle.get("settingV2.${name}.name", name)

        open fun buildUI(table: Table) {
            table.table().fillX().padTop(3f).get().apply {
                add(title).padRight(8f)
                label { value.toString() }.ellipsis(true).color(Color.gray).labelAlign(Align.left).growX()
                addTools()
            }
            table.row()
        }

        protected fun Table.addTools() {
            val help = Core.bundle.getOrNull("settingV2.${name}.description")
            button(Icon.info, Styles.clearNonei) { Vars.ui.showInfo(help) }.tooltip(help ?: "@none")
                .fillY().padLeft(8f).disabled { help == null }
            button(Icon.undo, Styles.clearNonei) { resetDefault() }.tooltip("@settingV2.reset")
                .fillY().disabled { value == def }
        }
    }

    sealed interface PersistentProvider {
        fun <T> get(name: String, def: T): T
        fun <T> set(name: String, value: T)
        fun reset(name: String)

        data object Noop : PersistentProvider {
            override fun <T> get(name: String, def: T): T = def
            override fun <T> set(name: String, value: T) {}
            override fun reset(name: String) {}
        }

        data object Arc : PersistentProvider {
            override fun <T> get(name: String, def: T): T {
                @Suppress("UNCHECKED_CAST")
                return Core.settings.get(name, def) as T
            }

            override fun <T> set(name: String, value: T) {
                Core.settings.put(name, value)
            }

            override fun reset(name: String) {
                Core.settings.remove(name)
            }
        }

        class WithFallback(private val fallback: String, private val impl: PersistentProvider) : PersistentProvider {
            override fun <T> get(name: String, def: T): T {
                return impl.get(name, impl.get(fallback, def))
            }

            override fun <T> set(name: String, value: T) {
                impl.reset(fallback)
                impl.set(name, value)
            }

            override fun reset(name: String) {
                impl.reset(name)
                impl.reset(fallback)
            }
        }
    }

    class CheckPref @JvmOverloads constructor(name: String, def: Boolean = false) : Data<Boolean>(name, def) {
        override fun buildUI(table: Table) {
            val box = CheckBox(title)
            box.changed { set(box.isChecked) }
            box.update { box.isChecked = value }

            table.table().fillX().get().apply {
                add(box).left().expandX().padTop(3f)
                addTools()
            }
            table.row()
        }
    }

    open class SliderPref @JvmOverloads constructor(name: String, def: Int, val min: Int, val max: Int, val step: Int = 1, val labelMap: (Int) -> String = { it.toString() }) : Data<Int>(name, def) {
        override fun set(value: Int) {
            super.set(value.coerceIn(min, max))
        }

        override fun buildUI(table: Table) {
            val elem = Slider(min.toFloat(), max.toFloat(), step.toFloat(), false)
            elem.changed { set(elem.value.toInt()) }
            elem.update { elem.value = value.toFloat() }

            val content = Table().apply {
                touchable = Touchable.disabled
                add(title, Styles.outlineLabel).left().growX().wrap()
                label { labelMap(value) }.style(Styles.outlineLabel).padLeft(10f).right().get()
            }
            table.table().fillX().padTop(4f).get().apply {
                stack(elem, content).minWidth(220f).growX()
                addTools()
            }
            table.row()
        }
    }

    class ChoosePref @JvmOverloads constructor(name: String, val values: List<String>, def: Int = 0) : SliderPref(name, def, 0, values.size - 1, labelMap = { values[it] })

    open class TextPref @JvmOverloads constructor(name: String, def: String = "") : Data<String>(name, def) {
        override fun set(value: String) {
            super.set(value.trim())
        }

        override fun buildUI(table: Table) {
            val elem = TextField(value)
            elem.changed { set(elem.text) }
            elem.update { elem.text = value }

            table.table().fillX().padTop(3f).get().apply {
                add(title).padRight(8f)
                add(elem).growX()
                addTools()
            }
            table.row()
        }
    }

    class TextAreaPref @JvmOverloads constructor(name: String, def: String = "") : TextPref(name, def) {
        override fun buildUI(table: Table) {
            val elem = TextArea("")
            elem.setPrefRows(5f)
            elem.changed { set(elem.text) }
            elem.update { elem.text = value }

            table.table().fillX().padTop(3f).get().apply {
                add(title).left().expandX()
                addTools()
                row().add(elem).colspan(columns).growX()
            }
            table.row()
        }
    }

    val ALL = LinkedHashMap<String, DataCore<*>>()

    class SettingDialog(val settings: Iterable<Data<*>>) : BaseDialog("@settings") {
        init {
            cont.add(Table().also { t ->
                settings.forEach { it.buildUI(t) }
            }).fill().row()
            cont.button("@setting.reset") {
                settings.forEach { it.resetDefault() }
            }
            addCloseButton()
            closeOnBack()
        }

        fun showFloatPanel(x: Float, y: Float) {
            val table = Table().apply {
                background(Styles.black8).margin(8f)
                settings.forEach { it.buildUI(this) }
                button("@close") { this.remove() }.fillX()
            }
            Core.scene.add(table)
            table.pack()
            table.setPosition(x, y, Align.center)
            table.keepInStage()
        }
    }

    private var settingSearch: String = ""

    @JvmStatic
    fun buildSettingsTable(table: Table) {
        table.clearChildren()
        val searchTable = table.table().growX().get()
        table.row()
        val contentTable = table.table().growX().get()
        table.row()

        fun rebuildContent() {
            contentTable.clearChildren()
            ALL.values.filterIsInstance<Data<*>>().groupBy { it.category }.toSortedMap().forEach { (c, settings0) ->
                val category = Core.bundle.get("settingV2.$c.category")
                val categoryMatch = c.contains(settingSearch, ignoreCase = true) || category.contains(settingSearch, ignoreCase = true)
                val settings = if (categoryMatch) settings0 else settings0.filter {
                    if ("@modified" in settingSearch) return@filter it.changed()
                    it.name.contains(settingSearch, true) || it.title.contains(settingSearch, true)
                }
                if (c.isNotEmpty() && settings.isNotEmpty()) {
                    contentTable.add(category).color(Pal.accent).padTop(10f).padBottom(5f).center().row()
                    contentTable.image().color(Pal.accent).growX().height(3f).padBottom(10f).row()
                }
                settings.forEach { it.buildUI(contentTable) }
            }
        }
        searchTable.apply {
            image(Icon.zoom)
            field(settingSearch) {
                settingSearch = it
                rebuildContent()
            }.growX()
        }
        rebuildContent()
    }

    @JvmStatic
    fun bindQuickSettings(button: Button, settings: Iterable<Data<*>>) {
        button.removeListener(button.clickListener)
        Reflect.set(Button::class.java, button, "clickListener", object : ClickListener() {
            private var startTime: Long = Long.MAX_VALUE
            override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: KeyCode?): Boolean {
                if (super.touchDown(event, x, y, pointer, button)) {
                    startTime = Time.millis()
                    return true
                }
                return false
            }

            override fun clicked(event: InputEvent, x: Float, y: Float) {
                if (Core.input.keyDown(KeyCode.shiftLeft) || Time.timeSinceMillis(startTime) > 500) {
                    SettingDialog(settings).showFloatPanel(event.stageX, event.stageY)
                } else {
                    if (button.isDisabled) return
                    button.setProgrammaticChangeEvents(true)
                    button.toggle()
                }
            }
        })
        button.addListener(button.clickListener)
    }

    //零散的设置，放在下方

    @JvmField
    val blockInventoryWidth = SliderPref("blockInventoryWidth", 3, 3, 16)

    @JvmField
    val minimapSize = SliderPref("minimapSize", 140, 40, 400, 10)

    @JvmField
    val arcTurretShowPlaceRange = CheckPref("arcTurretPlaceCheck")

    @JvmField
    val arcTurretShowAmmoRange = CheckPref("arcTurretPlacementItem")

    @JvmField
    val staticShieldsBorder = CheckPref("staticShieldsBorder")

    @JvmField
    val allUnlocked = CheckPref("allUnlocked")

    @JvmField
    val editorBrush = SliderPref("editorBrush", 6, 3, 13)

    @JvmField
    val noPlayerHitBox = CheckPref("noPlayerHitBox")
}