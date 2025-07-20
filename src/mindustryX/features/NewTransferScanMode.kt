package mindustryX.features

import arc.Core
import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Fill
import arc.graphics.g2d.Lines
import arc.math.Mathf
import arc.util.Strings
import arc.util.Tmp
import mindustry.Vars.*
import mindustry.gen.Building
import mindustry.graphics.Drawf
import mindustry.graphics.Layer
import mindustry.graphics.Pal
import mindustry.world.blocks.distribution.*
import mindustry.world.blocks.liquid.Conduit
import mindustry.world.blocks.liquid.LiquidBridge
import mindustry.world.blocks.liquid.LiquidJunction
import mindustry.world.blocks.liquid.LiquidRouter
import mindustry.world.blocks.production.GenericCrafter
import mindustryX.features.func.drawText

/**
 * 新的物流扫描模式 - 支持物品和液体传输的可视化
 * 分离收集、逻辑判断和渲染三个部分
 * New transport scanning mode - supports both item and liquid transport visualization
 * Separated into collection, logic, and rendering components
 */
object NewTransferScanMode {
    private val itemInputColor = Color.valueOf("ff8000")
    private val itemOutputColor = Color.valueOf("80ff00")
    private val liquidInputColor = Color.valueOf("4080ff")
    private val liquidOutputColor = Color.valueOf("00ffff")

    enum class TransportType {
        ITEM, LIQUID
    }

    /**
     * 主入口 - 渲染函数
     * Main entry point - rendering function
     */
    fun draw() {
        Draw.z(Layer.overlayUI + 0.01f)

        val pos = Core.input.mouseWorld()
        val text = Strings.format(
            "@,@\n距离: @",
            (pos.x / tilesize).toInt(), (pos.y / tilesize).toInt(),
            (player.dst(pos) / tilesize).toInt()
        )
        drawText(pos, text)

        Draw.z(Layer.overlayUI)
        // 获取鼠标悬停的建筑
        val build = world.tileWorld(Core.input.mouseWorld().x, Core.input.mouseWorld().y)?.build ?: return
        if (!build.isDiscovered(player.team())) {
            return
        }

        Drawf.selected(build, Pal.accent)
        currentBuild = build
        drawOutput(build, TransportType.ITEM, itemOutputColor)
        visited.clear()
        drawOutput(build, TransportType.LIQUID, liquidOutputColor)
        visited.clear()
        drawInput(build, TransportType.ITEM, itemInputColor)
        visited.clear()
        drawInput(build, TransportType.LIQUID, liquidInputColor)
        visited.clear()
    }

    private val visited = mutableSetOf<Building>()
    private var currentBuild: Building? = null

    private fun drawOutput(build: Building, type: TransportType, color: Color) {
        if (!visited.add(build)) return
        val wrapper = getWrapper(build)
        if (wrapper.isEndPoint) return
        for (output in wrapper.getPossibleOutputs(type)) {
            if (getWrapper(output).canInput(build, type)) {
                drawConnection(build, output, itemOutputColor)
                drawOutput(output, type, color)
            }
        }
    }

    private fun drawInput(build: Building, type: TransportType, color: Color) {
        if (!visited.add(build)) return
        val wrapper = getWrapper(build)
        if (wrapper.isEndPoint) return
        val inputs = build.proximity.toMutableList()
        inputs.addAll(wrapper.externalPossibleInputs())
        inputs.retainAll { wrapper.canInput(it, type) && build in getWrapper(it).getPossibleOutputs(type) }
        for (input in inputs) {
            drawConnection(input, build, color)
            drawInput(input, type, color)
        }
    }

    /**
     * 绘制单个连接
     * Draw single connection
     */
    private fun drawConnection(from: Building, to: Building, color: Color, alpha: Float = 1f) {
        val x1 = from.tile.drawx()
        val y1 = from.tile.drawy()
        val x2 = to.tile.drawx()
        val y2 = to.tile.drawy()

        Draw.color(color, alpha * (Mathf.absin(4f, 1f) * 0.4f + 0.6f))
        Lines.stroke(1.5f)
        Lines.line(x1, y1, x2, y2)
        Draw.reset()

        val dst = Mathf.dst(x1, y1, x2, y2)

        if (dst > tilesize) {
            Draw.color(color, alpha)

            // 起点圆点
            Fill.circle(x1, y1, 1.8f)

            // 方向箭头
            val fromPos = Tmp.v1.set(x1, y1)
            val toPos = Tmp.v2.set(x2, y2)

            val midPoint = Tmp.v3.set(fromPos).lerp(toPos, 0.5f)
            val angle = fromPos.angleTo(toPos)

            Fill.poly(midPoint.x, midPoint.y, 3, 3f, angle)
            Draw.reset()
        }
    }

    open class BuildingAdapator(val isEndPoint: Boolean = false) {
        protected open fun getItemPossibleOutputs(): List<Building> = emptyList()
        protected open fun getLiquidPossibleOutputs(): List<Building> = emptyList()
        open fun getPossibleOutputs(type: TransportType): List<Building> =
            if (type == TransportType.ITEM) getItemPossibleOutputs() else getLiquidPossibleOutputs()

        /** external possible Input excluding proximity*/
        open fun externalPossibleInputs(): List<Building> = emptyList()
        protected open fun canInputItem(from: Building) = false
        protected open fun canInputLiquid(from: Building) = false
        open fun canInput(from: Building, type: TransportType): Boolean =
            if (type == TransportType.ITEM) canInputItem(from) else canInputLiquid(from)
    }

    /**
     * 获取建筑的包装器实例
     * Get wrapper instance for building
     */
    fun getWrapper(build: Building): BuildingAdapator = when (build) {
        // 导管
        is Conduit.ConduitBuild -> ConduitAdapator(build)
        // 液体路由器
        is LiquidRouter.LiquidRouterBuild -> LiquidRouterAdapator(build)
        // 液体桥
        is LiquidBridge.LiquidBridgeBuild -> BridgeAdapator(build, TransportType.LIQUID)
        is LiquidJunction.LiquidJunctionBuild -> JunctionAdapator(build, TransportType.LIQUID)
        is DirectionLiquidBridge.DuctBridgeBuild -> DirectionBridgeAdapator(build, TransportType.LIQUID)

        // 传送带类
        is Conveyor.ConveyorBuild, is Duct.DuctBuild -> ConveyorAdapator(build)
        // 路由器和交叉器
        is Router.RouterBuild, is Sorter.SorterBuild, is OverflowGate.OverflowGateBuild -> RouterAdapator(build)
        // 物品桥
        is ItemBridge.ItemBridgeBuild -> BridgeAdapator(build, TransportType.ITEM)
        // 塑钢传送带
        is StackConveyor.StackConveyorBuild -> StackConveyorAdapator(build)
        is Junction.JunctionBuild, is DuctJunction.DuctJunctionBuild -> JunctionAdapator(build, TransportType.ITEM)
        // 导管桥
        is DuctBridge.DuctBridgeBuild -> DirectionBridgeAdapator(build, TransportType.ITEM)
        // 质量驱动器
        is MassDriver.MassDriverBuild -> MassDriverAdapator(build)
        // 定向卸载器
        is DirectionalUnloader.DirectionalUnloaderBuild -> DirectionalUnloaderAdapator(build)

        //TODO 下面的没有检查
        is OverflowDuct.OverflowDuctBuild, is DuctRouter.DuctRouterBuild -> RouterAdapator(build)

        is GenericCrafter.GenericCrafterBuild -> GenericCrafterAdapator(build, build != currentBuild)
        else -> BuildingAdapator()//Noop
    }

    // ===== 具体包装器实现 =====
    private class ConveyorAdapator(private val building: Building) : BuildingAdapator() {
        override fun getItemPossibleOutputs(): List<Building> = listOfNotNull(building.front())
        override fun canInputItem(from: Building): Boolean = from != building.front()
    }

    private class StackConveyorAdapator(private val building: StackConveyor.StackConveyorBuild) : BuildingAdapator() {
        override fun getItemPossibleOutputs(): List<Building> {
            val front = building.front()
            val back = building.back()

            return when (building.state) {
                2 -> { // 输出模式
                    if ((building.block as StackConveyor).outputRouter) {
                        building.proximity.filter { it != back }
                    } else {
                        listOfNotNull(front)
                    }
                }

                1 -> { // 输入模式
                    if (front != null) listOf(front) else emptyList()
                }

                else -> { // 待机模式
                    building.proximity.filterIsInstance<StackConveyor.StackConveyorBuild>()
                }
            }
        }

        override fun canInputItem(from: Building): Boolean {
            val front = building.front()
            val back = building.back()

            return when (building.state) {
                2 -> from is StackConveyor.StackConveyorBuild && from == back
                1 -> from != front
                else -> from is StackConveyor.StackConveyorBuild
            }
        }
    }

    private class RouterAdapator(private val building: Building) : BuildingAdapator() {
        override fun getItemPossibleOutputs(): List<Building> = building.proximity.toList()
        override fun canInputItem(from: Building): Boolean = true
    }

    private class DirectionalUnloaderAdapator(private val building: Building) : BuildingAdapator() {
        override fun getItemPossibleOutputs(): List<Building> = listOfNotNull(building.front())
        override fun canInputItem(from: Building): Boolean = from == building.back()
    }

    private class BridgeAdapator(private val build: ItemBridge.ItemBridgeBuild, val type: TransportType) : BuildingAdapator() {
        val block = build.block as ItemBridge
        private val linkValid get() = block.linkValid(build.tile, world.tile(build.link))
        override fun getPossibleOutputs(type: TransportType): List<Building> {
            if (type != this.type) return emptyList()
            if (linkValid) return listOf(world.build(build.link))
            val back = build.back()
            return build.proximity.filter { it != back }
        }

        override fun externalPossibleInputs(): List<Building> = buildList {
            // 从链接源接收
            build.incoming.each { pos ->
                val source = world.tile(pos).build
                if (source != null) add(source)
            }
        }

        override fun canInput(from: Building, type: TransportType): Boolean {
            if (type != this.type) return false
            return build.arcCheckAccept(from)
        }
    }

    private class JunctionAdapator(private val build: Building, val type: TransportType) : BuildingAdapator() {
        override fun getPossibleOutputs(type: TransportType): List<Building> {
            if (type != this.type) return emptyList()
            return build.proximity.toList()
        }

        override fun canInput(from: Building, type: TransportType): Boolean {
            if (type != this.type) return false
            val dir = from.relativeTo(build).toInt()
            val output = build.nearby(dir) ?: return false
            return getWrapper(output).canInput(build, type)
        }
    }

    private class MassDriverAdapator(private val building: MassDriver.MassDriverBuild) : BuildingAdapator() {
        override fun getItemPossibleOutputs(): List<Building> {
            return if (building.arcLinkValid()) {
                val target = world.build(building.link)
                if (target != null) listOf(target) else emptyList()
            } else {
                building.proximity.toList()
            }
        }

        override fun canInputItem(from: Building): Boolean = true
    }

    private class DirectionBridgeAdapator(private val building: DirectionBridge.DirectionBridgeBuild, val type: TransportType) : BuildingAdapator() {
        override fun getPossibleOutputs(type: TransportType): List<Building> {
            if (type != this.type) return emptyList()
            val link = building.findLink()
            return if (link != null) listOf(link) else listOfNotNull(building.front())
        }

        override fun externalPossibleInputs(): List<Building> {
            return building.occupied.filterNotNull()
        }

        override fun canInput(from: Building, type: TransportType): Boolean {
            if (type != this.type) return false
            return from in building.occupied || from != building.front()
        }
    }

    private class ConduitAdapator(private val building: Building) : BuildingAdapator() {
        override fun getLiquidPossibleOutputs(): List<Building> = listOfNotNull(building.front())
        override fun canInputLiquid(from: Building): Boolean = from != building.front()
    }

    private class LiquidRouterAdapator(private val building: Building) : BuildingAdapator() {
        override fun getLiquidPossibleOutputs(): List<Building> = building.proximity.toList()
        override fun canInputLiquid(from: Building): Boolean = true
    }

    private class GenericCrafterAdapator(private val build: GenericCrafter.GenericCrafterBuild, isEndPoint: Boolean) : BuildingAdapator(isEndPoint) {
        val block = build.block as GenericCrafter
        override fun getPossibleOutputs(type: TransportType): List<Building> {
            if (type == TransportType.ITEM && block.outputItems == null) return emptyList()
            if (type == TransportType.LIQUID && block.outputLiquids == null) return emptyList()
            return build.proximity.toList()
        }

        override fun canInputItem(from: Building): Boolean = build.block.hasItems && build.block.itemFilter.any { it }
        override fun canInputLiquid(from: Building): Boolean = build.block.hasLiquids && build.block.liquidFilter.any { it }
    }
}