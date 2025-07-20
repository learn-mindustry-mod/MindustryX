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
import mindustry.content.Items
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
import mindustry.world.blocks.production.Pump
import mindustry.world.blocks.sandbox.LiquidSource
import mindustry.world.blocks.storage.Unloader
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
        if (wrapper.isEndPoint && build != currentBuild) {
            Drawf.selected(build, color)
            return
        }
        for (output in wrapper.getPossibleOutputs(type)) {
            val receiver = getWrapper(output).actualInputReceiver(build, type) ?: continue
            drawConnection(build, receiver, itemOutputColor)
            drawOutput(receiver, type, color)
        }
    }

    private fun drawInput(build: Building, type: TransportType, color: Color) {
        if (!visited.add(build)) return
        val wrapper = getWrapper(build)
        if (wrapper.isEndPoint && build != currentBuild) {
            Drawf.selected(build, color)
            return
        }
        val inputs = build.proximity.toMutableList()
        inputs.addAll(wrapper.externalPossibleInputs())
        inputs.retainAll { wrapper.canInput(it, type) }
        for (input in inputs) {
            val source = getWrapper(input).actualOutputSource(build, type) ?: continue
            drawConnection(source, build, color)
            drawInput(source, type, color)
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

    abstract class BuildingAdaptor(val isEndPoint: Boolean = false) {
        abstract val build: Building
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

        // For special causes, it delegates to other building
        open fun actualOutputSource(to: Building, type: TransportType): Building? = if (to in getPossibleOutputs(type)) build else null
        open fun actualInputReceiver(from: Building, type: TransportType): Building? = if (canInput(from, type)) build else null
    }

    /**
     * 获取建筑的包装器实例
     * Get wrapper instance for build
     */
    fun getWrapper(build: Building): BuildingAdaptor = when (build) {
        is Conduit.ConduitBuild -> ConduitAdaptor(build)
        is LiquidRouter.LiquidRouterBuild -> LiquidRouterAdaptor(build)
        is LiquidBridge.LiquidBridgeBuild -> BridgeAdaptor(build, TransportType.LIQUID)
        is LiquidJunction.LiquidJunctionBuild -> JunctionAdaptor(build, TransportType.LIQUID)
        is DirectionLiquidBridge.DuctBridgeBuild -> DirectionBridgeAdaptor(build, TransportType.LIQUID)
        is Pump.PumpBuild, is LiquidSource.LiquidSourceBuild -> SourceAdaptor(build, TransportType.LIQUID)

        is Conveyor.ConveyorBuild, is Duct.DuctBuild -> ConveyorAdaptor(build)
        is Router.RouterBuild, is Sorter.SorterBuild, is OverflowGate.OverflowGateBuild -> RouterAdaptor(build)
        is ItemBridge.ItemBridgeBuild -> BridgeAdaptor(build, TransportType.ITEM)
        is StackConveyor.StackConveyorBuild -> StackConveyorAdaptor(build)
        is Junction.JunctionBuild, is DuctJunction.DuctJunctionBuild -> JunctionAdaptor(build, TransportType.ITEM)
        is DuctBridge.DuctBridgeBuild -> DirectionBridgeAdaptor(build, TransportType.ITEM)
        is Unloader.UnloaderBuild -> UnloaderAdaptor(build)
        is DirectionalUnloader.DirectionalUnloaderBuild -> DirectionalUnloaderAdaptor(build)
        is MassDriver.MassDriverBuild -> MassDriverAdaptor(build)

        //TODO 下面的没有检查
        is OverflowDuct.OverflowDuctBuild, is DuctRouter.DuctRouterBuild -> RouterAdaptor(build)

        is GenericCrafter.GenericCrafterBuild -> GenericCrafterAdaptor(build)
        else -> NoopAdaptor(build)
    }

    // ===== 具体包装器实现 =====
    private class ConveyorAdaptor(override val build: Building) : BuildingAdaptor() {
        override fun getItemPossibleOutputs(): List<Building> = listOfNotNull(build.front())
        override fun canInputItem(from: Building): Boolean = from != build.front()
    }

    private class StackConveyorAdaptor(override val build: StackConveyor.StackConveyorBuild) : BuildingAdaptor() {
        override fun getItemPossibleOutputs(): List<Building> {
            val front = build.front()
            val back = build.back()

            return when (build.state) {
                2 -> { // 输出模式
                    if ((build.block as StackConveyor).outputRouter) {
                        build.proximity.filter { it != back }
                    } else {
                        listOfNotNull(front)
                    }
                }

                1 -> { // 输入模式
                    if (front != null) listOf(front) else emptyList()
                }

                else -> { // 待机模式
                    build.proximity.filterIsInstance<StackConveyor.StackConveyorBuild>()
                }
            }
        }

        override fun canInputItem(from: Building): Boolean {
            val front = build.front()
            val back = build.back()

            return when (build.state) {
                2 -> from is StackConveyor.StackConveyorBuild && from == back
                1 -> from != front
                else -> from is StackConveyor.StackConveyorBuild
            }
        }
    }

    private class RouterAdaptor(override val build: Building) : BuildingAdaptor() {
        override fun getItemPossibleOutputs(): List<Building> = build.proximity.toList()
        override fun canInputItem(from: Building): Boolean = true
    }

    private class UnloaderAdaptor(override val build: Building) : BuildingAdaptor() {
        override fun getItemPossibleOutputs(): List<Building> = build.proximity.toList()
        override fun canInputItem(from: Building): Boolean = from.canUnload()
    }

    private class DirectionalUnloaderAdaptor(override val build: Building) : BuildingAdaptor() {
        override fun getItemPossibleOutputs(): List<Building> = listOfNotNull(build.front())
        override fun canInputItem(from: Building): Boolean = from == build.back() && from.canUnload()
    }

    private class BridgeAdaptor(override val build: ItemBridge.ItemBridgeBuild, val type: TransportType) : BuildingAdaptor() {
        val block = build.block as ItemBridge
        private val linkValid get() = block.linkValid(build.tile, world.tile(build.link))
        override fun getPossibleOutputs(type: TransportType): List<Building> {
            if (type != this.type) return emptyList()
            if (linkValid) return listOf(world.build(build.link))
            return build.proximity.filter { build.canDump(it, Items.copper) }
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

    private class JunctionAdaptor(override val build: Building, val type: TransportType) : BuildingAdaptor(isEndPoint = true) {
        override fun getPossibleOutputs(type: TransportType): List<Building> {
            if (type != this.type) return emptyList()
            return build.proximity.toList()
        }

        override fun actualInputReceiver(from: Building, type: TransportType): Building? {
            if (type != this.type) return null
            val dir = from.relativeTo(build).toInt()
            val output = build.nearby(dir) ?: return null
            return getWrapper(output).actualInputReceiver(build, type)
        }

        override fun actualOutputSource(to: Building, type: TransportType): Building? {
            if (type != this.type) return null
            val dir = to.relativeTo(build).toInt()
            val output = build.nearby(dir) ?: return null
            return getWrapper(output).actualOutputSource(build, type)
        }
    }

    private class MassDriverAdaptor(override val build: MassDriver.MassDriverBuild) : BuildingAdaptor() {
        override fun getItemPossibleOutputs(): List<Building> {
            return if (build.arcLinkValid()) {
                val target = world.build(build.link)
                if (target != null) listOf(target) else emptyList()
            } else {
                build.proximity.toList()
            }
        }

        override fun canInputItem(from: Building): Boolean = true
    }

    private class DirectionBridgeAdaptor(override val build: DirectionBridge.DirectionBridgeBuild, val type: TransportType) : BuildingAdaptor() {
        override fun getPossibleOutputs(type: TransportType): List<Building> {
            if (type != this.type) return emptyList()
            val link = build.findLink()
            return if (link != null) listOf(link) else listOfNotNull(build.front())
        }

        override fun externalPossibleInputs(): List<Building> {
            return build.occupied.filterNotNull()
        }

        override fun canInput(from: Building, type: TransportType): Boolean {
            if (type != this.type) return false
            return from in build.occupied || from != build.front()
        }
    }

    private class ConduitAdaptor(override val build: Building) : BuildingAdaptor() {
        override fun getLiquidPossibleOutputs(): List<Building> = listOfNotNull(build.front())
        override fun canInputLiquid(from: Building): Boolean = from != build.front()
    }

    private class LiquidRouterAdaptor(override val build: Building) : BuildingAdaptor() {
        override fun getLiquidPossibleOutputs(): List<Building> = build.proximity.toList()
        override fun canInputLiquid(from: Building): Boolean = true
    }

    private class GenericCrafterAdaptor(override val build: GenericCrafter.GenericCrafterBuild) : BuildingAdaptor(isEndPoint = true) {
        val block = build.block as GenericCrafter
        override fun getPossibleOutputs(type: TransportType): List<Building> {
            if (type == TransportType.ITEM && block.outputItems == null) return emptyList()
            if (type == TransportType.LIQUID && block.outputLiquids == null) return emptyList()
            return build.proximity.toList()
        }

        override fun canInputItem(from: Building): Boolean = build.block.hasItems && build.block.itemFilter.any { it }
        override fun canInputLiquid(from: Building): Boolean = build.block.hasLiquids && build.block.liquidFilter.any { it }
    }

    private class SourceAdaptor(override val build: Building, val type: TransportType) : BuildingAdaptor(isEndPoint = true) {
        override fun getPossibleOutputs(type: TransportType): List<Building> {
            if (type != this.type) return emptyList()
            return build.proximity.toList()
        }
    }

    private class NoopAdaptor(override val build: Building) : BuildingAdaptor() {
        // No-op adaptor for unsupported buildings
        override fun getPossibleOutputs(type: TransportType): List<Building> = emptyList()
        override fun canInput(from: Building, type: TransportType): Boolean = false
    }
}