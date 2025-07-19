package mindustryX.features.ui

import arc.Core
import arc.Graphics.Cursor.SystemCursor
import arc.func.Boolp
import arc.input.KeyCode
import arc.math.geom.Rect
import arc.math.geom.Vec2
import arc.scene.Element
import arc.scene.event.InputEvent
import arc.scene.event.InputListener
import arc.scene.event.Touchable
import arc.scene.ui.ImageButton
import arc.scene.ui.layout.Scl
import arc.scene.ui.layout.Table
import arc.scene.ui.layout.WidgetGroup
import arc.util.Align
import arc.util.Tmp
import mindustry.Vars
import mindustry.gen.Icon
import mindustry.gen.Tex
import mindustry.ui.Styles

object OverlayUI {
    class Window(val table: Table) : Table() {
        inner class DragListener : InputListener() {
            private val last = Vec2()
            override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode?): Boolean {
                if (Core.app.isMobile && pointer != 0) return false
                last.set(event.stageX, event.stageY)
                dragging = true

                toFront()
                return true
            }

            override fun touchDragged(event: InputEvent, x: Float, y: Float, pointer: Int) {
                if (Core.app.isMobile && pointer != 0) return
                moveBy(event.stageX - last.x, event.stageY - last.y)
                last.set(event.stageX, event.stageY)
            }

            override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: KeyCode?) {
                if (Core.app.isMobile && pointer != 0) return
                dragging = false
                saveTableRect()
            }
        }

        inner class ResizeListener : InputListener() {
            private val last = Vec2()
            private var resizeSide: Int = 0

            override fun mouseMoved(event: InputEvent, x: Float, y: Float): Boolean {
                resizeSide = when {
                    event.targetActor != this@Window -> 0
                    x < table.getX(Align.left) -> Align.left
                    x > table.getX(Align.right) -> Align.right
                    y < table.getY(Align.bottom) -> Align.bottom
                    y > table.getY(Align.top) -> Align.top
                    else -> 0
                }
                if (Align.isLeft(resizeSide) || Align.isRight(resizeSide)) {
                    Core.graphics.cursor(SystemCursor.horizontalResize)
                } else if (Align.isTop(resizeSide) || Align.isBottom(resizeSide)) {
                    Core.graphics.cursor(SystemCursor.verticalResize)
                } else {
                    Core.graphics.restoreCursor()
                    return false
                }
                return true
            }

            override fun exit(event: InputEvent?, x: Float, y: Float, pointer: Int, toActor: Element?) {
                if (Core.app.isMobile && pointer != 0) return
                Core.graphics.restoreCursor()
            }

            override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode): Boolean {
                if (Core.app.isMobile && pointer != 0) return false
                if (event.targetActor != this@Window || resizeSide == 0) return false
                last.set(event.stageX, event.stageY)
                toFront()
                return true
            }

            override fun touchDragged(event: InputEvent, x: Float, y: Float, pointer: Int) {
                if (Core.app.isMobile && pointer != 0) return
                val delta = Tmp.v1.set(event.stageX, event.stageY).sub(last)
                last.set(event.stageX, event.stageY)

                //消除不相关方向偏置
                if (Align.isCenterHorizontal(resizeSide)) delta.x = 0f
                if (Align.isCenterVertical(resizeSide)) delta.y = 0f
                //delta 将delta转换为尺寸增量
                if (Align.isLeft(resizeSide)) delta.x = -delta.x
                if (Align.isBottom(resizeSide)) delta.y = -delta.y
                //clamp delta变化
                if (width + delta.x < minWidth) delta.x = minWidth - width
                if (maxWidth > 0 && width + delta.x > maxWidth) delta.x = maxWidth - width
                if (height + delta.y < minHeight) delta.y = minHeight - height
                if (maxHeight > 0 && height + delta.y > maxHeight) delta.y = maxHeight - height
                //应用delta
                if (Align.isLeft(resizeSide)) this@Window.x -= delta.x
                if (Align.isBottom(resizeSide)) this@Window.y -= delta.y
                setSize(width + delta.x, height + delta.y)

            }

            override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: KeyCode?) {
                if (Core.app.isMobile && pointer != 0) return
                saveTableRect()
            }
        }

        private val paneBg = Tex.pane
        var pinned = false
        var rect = Rect(0f, 0f, 800f, 600f)
        private var dragging = false

        init {
            rebuild()
            update { if (!dragging) keepInStage() }
        }

        fun rebuild() {
            clear()
            if (open) {
                //编辑模式
                background = paneBg
                touchable = Touchable.enabled
                addListener(ResizeListener())

                table { header ->
                    header.add("测试面板")
                    header.label { "$rect" }
                    header.add().growX()

                    header.touchable = Touchable.enabled
                    header.addListener(DragListener())

                    header.defaults().size(Vars.iconMed).pad(2f)
                    header.button(Icon.settingsSmall, Styles.cleari) {
                    }
                    header.add().width(Vars.iconMed / 2)
                    header.button(Icon.lockOpenSmall, ImageButton.ImageButtonStyle(Styles.cleari).apply {
                        up = null
                        imageChecked = Icon.lockSmall
                    }) { pinned = !pinned }.checked { pinned }
                    header.button(Icon.cancelSmall, Styles.cleari) {
                        remove()
                    }
                }.fillX().row()
                image().fillX().row()

                setPosition(rect.x - background.leftWidth, rect.y - background.bottomHeight)
                val tableCell = add(table).size(rect.width, rect.height).grow()
                pack()
                tableCell.size(Float.NEGATIVE_INFINITY)
            } else {
                //预览模式, 作为Group使用
                background = null
                setPosition(rect.x, rect.y)
                touchable = Touchable.childrenOnly
                add(table).grow()
                setBounds(rect.x, rect.y, rect.width, rect.height)
            }
        }

        fun saveTableRect() {
            if (parent == null) return
            keepInStage()
            validate()
            val pos = localToParentCoordinates(Tmp.v1.set(table.x, table.y))
            rect.setPosition(pos)
            rect.setSize(table.width / Scl.scl(), table.height / Scl.scl())

            //自动贴边处理
            if (getX(Align.left) == 0f) rect.x = 0f
            if (getX(Align.right) == parent.width) rect.x = parent.width - rect.width
            if (getY(Align.bottom) == 0f) rect.y = 0f
            if (getY(Align.top) == parent.height) rect.y = parent.height - rect.height
        }
    }

    var open = false

    private val group = WidgetGroup().apply {
        name = "overlayUI"
        setFillParent(true)
        touchable = Touchable.childrenOnly
        zIndex = 99

        fill(Styles.black6) { t ->
            t.touchable = Touchable.enabled
            t.visibility = Boolp { open }
            t.bottom()
            t.defaults().size(Vars.iconLarge).pad(4f)
            t.button(Icon.add) {
                addChild(Window(Table(Tex.whitePane)))
            }.width(Vars.iconLarge * 1.5f)
        }

        addChild(Window(Table(Tex.whitePane)))
    }

    fun toggle() {
        Core.scene.add(group)
        open = !open
        group.children.filterIsInstance<Window>().forEach {
            it.visible = open || it.pinned
            if (it.visible) it.rebuild()
        }
    }
}