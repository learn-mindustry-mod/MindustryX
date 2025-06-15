package mindustryX.features

import arc.Events
import arc.util.Strings
import mindustry.core.PerfCounter
import mindustry.game.EventType
import mindustry.ui.Styles
import mindustry.ui.dialogs.BaseDialog

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
            lastSwitchTexture = 0
            lastFlushCount = lastSwitchTexture
            lastVertices = lastFlushCount
            lastDrawRequests = lastVertices
            rendererTime = PerfCounter.render.rawValueNs()
            uiTime = PerfCounter.ui.rawValueNs()
        }
    }

    @JvmStatic
    fun openDialog() = BaseDialog("DEBUG").apply {
        cont.apply {
            check("Render Debug") { renderDebug = it }.checked { renderDebug }.row()
            label {
                Strings.format("D/V/T/F @/@/@/@", lastDrawRequests, lastVertices, lastSwitchTexture, lastFlushCount)
            }.style(Styles.outlineLabel).row()
        }
        addCloseButton()
        closeOnBack()
        show()
    }
}
