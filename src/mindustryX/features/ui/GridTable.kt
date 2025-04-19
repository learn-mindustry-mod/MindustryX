package mindustryX.features.ui

import arc.math.Mathf
import arc.scene.ui.layout.Table
import kotlin.math.min

/**自动换行的Table布局，所有元素使用相同的[cell]，最好设置[cell.minWidth()]*/
class GridTable : Table() {
    val cell = defaults()!!
    override fun act(delta: Float) {
        super.act(delta)
        if (!hasChildren()) return

        val cellWidth = cell.setElement(children.firstOrNull { it.visible }).minWidth()
        val columns = Mathf.floor(width / cellWidth)
        if (this.columns == columns || this.columns == min(columns, this.children.count { it.visible })) return

        val children = this.children.toList()
        clearChildren()
        var i = 0
        children.forEach {
            if (!it.visible) addChild(it)
            else {
                add(it).set(cell)
                i++
                if (i % columns == 0) row()
            }
        }
    }
}