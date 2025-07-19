package mindustryX.features

import arc.Core
import arc.func.Cons
import arc.scene.ui.layout.Table
import arc.util.Align
import mindustry.gen.Tex
import mindustry.ui.Styles

object UIExtKt {
    @JvmStatic
    fun showFloatSettingsPanel(builder: Cons<Table>) = showFloatSettingsPanel { builder.get(this) }
    @JvmStatic
    fun showFloatSettingsPanel(builder: Table.() -> Unit) {
        val mouse = Core.input.mouse()
        val table = Table(Tex.pane).apply {
            builder.invoke(this)
            button("@close") { this.remove() }.fillX()
        }
        Core.scene.add(table)
        table.pack()
        table.setPosition(mouse.x, mouse.y, Align.center)
        table.keepInStage()
    }
}