package mindustryX.features

import arc.Core
import arc.func.Cons
import arc.func.Prov
import arc.scene.ui.layout.Table
import arc.util.Align
import mindustry.Vars
import mindustry.gen.Icon
import mindustry.gen.Tex
import mindustry.ui.Styles
import mindustryX.features.SettingsV2.CheckPref
import mindustryX.features.ui.LogicSupport
import mindustryX.features.ui.OverlayUI
import mindustryX.features.ui.toolTable.NewToolTable

object UIExtKt {
    private val showOverlayButton: CheckPref = CheckPref("gameUI.overlayButton", true)

    @JvmStatic
    fun init() {
        Vars.ui.hudGroup.fill { t: Table ->
            t.left().name = "quickTool"
            t.button(Icon.settings, Styles.flati, Vars.iconMed) { OverlayUI.toggle() }
            t.visible { showOverlayButton.value }
        }
        LogicSupport.init()
        OverlayUI.init()

        val inGameOnly = Prov { Vars.state.isGame }
        OverlayUI.registerWindow("debug", DebugUtil.metricTable())
        OverlayUI.registerWindow("auxiliaryTools", UIExt.auxiliaryTools).availability = inGameOnly
        OverlayUI.registerWindow("quickTool", NewToolTable).availability = inGameOnly
        OverlayUI.registerWindow("mappingTool", UIExt.advanceToolTable).availability = inGameOnly
        OverlayUI.registerWindow("advanceBuildTool", UIExt.advanceBuildTool).availability = inGameOnly
        OverlayUI.registerWindow("teamsStats", UIExt.teamsStatDisplay.wrapped()).availability = inGameOnly
    }

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