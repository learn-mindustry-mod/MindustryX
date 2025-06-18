package mindustryX.features

import arc.Events
import arc.util.Strings
import mindustry.core.PerfCounter
import mindustry.game.EventType
import mindustry.ui.Styles
import mindustry.ui.dialogs.BaseDialog
import mindustryX.features.ui.comp.DragSortListener
import mindustryX.features.ui.comp.GridTable

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
    fun reset() {
        lastSwitchTexture = 0
        lastFlushCount = 0
        lastVertices = 0
        lastDrawRequests = 0
    }

    @JvmStatic
    fun openDialog() = BaseDialog("DEBUG").apply {
        cont.apply {
            check("Render Debug") { renderDebug = it }.checked { renderDebug }.row()
            label {
                Strings.format("D/V/T/F @/@/@/@", lastDrawRequests, lastVertices, lastSwitchTexture, lastFlushCount).also {
                    DebugUtil.reset()
                }
            }.style(Styles.outlineLabel).row()

            add(GridTable().apply {
                defaults().width(80f).height(40f).pad(4f)

                repeat(48) {
                    val cell = button("#$it") {
                        // Do nothing, just for debug
                    }
                    cell.get().addListener(DragSortListener())
                }
            }).width(480f).row()
        }
        addCloseButton()
        closeOnBack()
        show()
    }
}
