package mindustryX.features

import arc.Events
import arc.scene.ui.layout.Table
import arc.util.Align
import mindustry.core.PerfCounter
import mindustry.game.EventType
import mindustry.gen.Tex
import mindustryX.features.ui.OverlayUI

object DebugUtil {
    @JvmField
    var renderDebug: Boolean = false

    @JvmField
    var lastDrawRequests: Int = 0

    @JvmField
    var lastVertices: Int = 0

    @JvmField
    var lastFlushCount: Int = 0

    @JvmField
    var lastSwitchTexture: Int = 0

    @JvmField
    var logicTime: Long = 0

    @JvmField
    var rendererTime: Long = 0

    @JvmField
    var uiTime: Long = 0 //nanos

    @JvmStatic
    fun init() {
        Events.run(EventType.Trigger.preDraw) {
            rendererTime = PerfCounter.render.rawValueNs()
            uiTime = PerfCounter.ui.rawValueNs()
        }
    }

    @JvmStatic
    fun initUI() {
        OverlayUI.registerWindow("debug", Table(Tex.pane).apply {
            left()
            check("Render Debug") { renderDebug = it }.checked { renderDebug }.row()
            label { "Draw: $lastDrawRequests" }.fillX().labelAlign(Align.left).row()
            label { "Vertices: $lastVertices" }.fillX().labelAlign(Align.left).row()
            label { "Texture: $lastSwitchTexture" }.fillX().labelAlign(Align.left).row()
            label { "Flush: $lastFlushCount" }.fillX().labelAlign(Align.left).row()
            image().update { DebugUtil.reset() }.row()
        })
    }

    @JvmStatic
    fun reset() {
        lastSwitchTexture = 0
        lastFlushCount = 0
        lastVertices = 0
        lastDrawRequests = 0
    }
}
