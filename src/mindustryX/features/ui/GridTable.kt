package mindustryX.features.ui

import arc.math.Mathf
import arc.scene.ui.layout.Table
import kotlin.math.min

/**自动换行的Table布局
 * - 所有元素使用相同的[cell]，最好设置[cell.minWidth()]
 * - 支持检测子元素[visible]，进行动态增减
 * */
class GridTable : Table() {
    val cell = defaults()!!
    override fun act(delta: Float) {
        super.act(delta)
        if (!hasChildren()) return

        val cellWidth = cell.setElement(children.firstOrNull { it.visible }).minWidth()
        val newColumns = Mathf.floor(width / cellWidth)
        val columnsChanged = columns != min(newColumns, children.count { it.visible })
        val visibleChanged = children.count { it.visible } != cells.size || cells.any { it.get()?.visible != true }
        if (!columnsChanged && !visibleChanged) return

        val children = this.children.begin()
        clearChildren()
        var i = 0
        children.forEach {
            if (!it.visible) addChild(it)
            else {
                add(it).set(cell)
                i++
                if (i % newColumns == 0) row()
            }
        }
        this.children.end()
    }
}