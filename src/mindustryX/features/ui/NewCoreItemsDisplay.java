package mindustryX.features.ui;

import arc.*;
import arc.graphics.*;
import arc.math.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import kotlin.collections.*;
import mindustry.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.ConstructBlock.*;
import mindustry.world.blocks.power.*;
import mindustry.world.blocks.storage.*;
import mindustryX.features.*;
import mindustryX.features.SettingsV2.*;

import java.util.*;
import java.util.List;

import static mindustry.Vars.*;

//moved from mindustry.arcModule.ui.RCoreItemsDisplay
public class NewCoreItemsDisplay extends Table{
    public static final float MIN_WIDTH = 64f;

    private Table itemsTable, unitsTable, plansTable, powerTable;

    private static final Interval timer = new Interval(2);

    private final int[] itemDelta;
    private final int[] lastItemAmount;
    private final ObjectSet<Item> usedItems = new ObjectSet<>();
    private final ObjectSet<UnitType> usedUnits = new ObjectSet<>();

    private final ItemSeq planItems = new ItemSeq();
    private final ObjectIntMap<Block> planCounter = new ObjectIntMap<>();

    private final SettingsV2.Data<Boolean> enable = new CheckPref("coreItems.enable", true);//Origin Setting
    private final SettingsV2.Data<Integer> columns = new SettingsV2.SliderPref("coreItems.columns", 5, 4, 15);
    private final SettingsV2.Data<Boolean> showItem = new CheckPref("coreItems.showItem", true);
    private final SettingsV2.Data<Boolean> showUnit = new CheckPref("coreItems.showUnit", true);
    private final SettingsV2.Data<Boolean> showPlan = new CheckPref("coreItems.showPlan", true);
    private final SettingsV2.Data<Boolean> showPower = new CheckPref("coreItems.showPower", true);
    final List<Data<?>> settings = CollectionsKt.listOf(enable, columns, showItem, showUnit, showPlan, showPower);

    {
        enable.addFallbackName("coreitems");
    }

    public NewCoreItemsDisplay(){
        itemDelta = new int[content.items().size];
        lastItemAmount = new int[content.items().size];
        Events.on(ResetEvent.class, e -> {
            usedItems.clear();
            usedUnits.clear();
            Arrays.fill(itemDelta, 0);
            Arrays.fill(lastItemAmount, 0);
            itemsTable.clearChildren();
            unitsTable.clearChildren();
            plansTable.clearChildren();
        });

        setup();
    }

    private void setup(){
        collapser(powerTable = new Table(Styles.black3), showPower::getValue).growX().row();
        collapser(itemsTable = new Table(Styles.black3), showItem::getValue).growX().row();
        collapser(unitsTable = new Table(Styles.black3), showUnit::getValue).growX().row();

        var emptyLine = row().add();
        row().collapser(plansTable = new Table(Styles.black3), showPlan::getValue).growX().row();

        update(() -> {
            var newHeight = plansTable.hasChildren() ? 12f : 0f;
            if(emptyLine.maxHeight() != newHeight){
                emptyLine.height(newHeight);
                emptyLine.getTable().invalidate();
            }

            if(this.columns.changed()){
                rebuildItems();
                rebuildUnits();
                rebuildPlans();
            }
        });

        itemsTable.update(() -> {
            updateItemMeans();
            if(content.items().contains(item -> player.team().items().get(item) > 0 && usedItems.add(item))){
                rebuildItems();
            }
        });
        unitsTable.update(() -> {
            if(content.units().contains(unit -> player.team().data().countType(unit) > 0 && usedUnits.add(unit))){
                rebuildUnits();
            }
        });
        plansTable.update(() -> {
            if(timer.get(1, 10f)){
                rebuildPlans();
            }
        });
        buildPower();
    }

    private float balance, stored, capacity, produced, need, satisfaction;

    private void buildPower(){
        powerTable.update(() -> {
            balance = 0;
            stored = 0;
            capacity = 0;
            produced = 0;
            need = 0;
            Groups.powerGraph.each(item -> {
                var graph = item.graph();
                if(graph.all.isEmpty() || graph.all.first().team != Vars.player.team()) return;
                balance += graph.getPowerBalance();
                stored += graph.getLastPowerStored();
                capacity += graph.getLastCapacity();
                produced += graph.getLastPowerProduced();
                need += graph.getLastPowerNeeded();
            });
            balance *= Time.toSeconds;
            satisfaction = produced == 0 ? 1 : need == 0 ? 1 : Mathf.clamp(produced / need, 0, 1);
        });
        powerTable.margin(2f).stack(
        new Bar("", Pal.powerBar, () -> capacity == 0 ? (balance > 0 ? 1 : 0) : stored / capacity),
        new Table(t -> {
            t.add().growX();
            t.label(() -> Core.bundle.format("bar.powerbalance", (balance >= 0 ? "+" : "") + UI.formatAmount((long)balance)) +
            (satisfaction >= 1 ? "" : " [gray]" + (int)(satisfaction * 100) + "%"));
            t.add().width(16);
            t.label(() -> Core.bundle.format("bar.powerstored", UI.formatAmount((long)stored), UI.formatAmount((long)capacity)));
            t.add().growX();
        })
        ).growX();
    }

    private void updateItemMeans(){
        if(!timer.get(0, 60f)) return;
        var items = player.team().items();
        for(Item item : usedItems){
            short id = item.id;
            int coreAmount = items.get(id);
            int lastAmount = lastItemAmount[id];
            itemDelta[id] = coreAmount - lastAmount;
            lastItemAmount[id] = coreAmount;
        }
    }

    private void rebuildItems(){
        itemsTable.clearChildren();
        if(player.team().core() == null) return;

        int i = 0;
        for(Item item : content.items()){
            if(!usedItems.contains(item)){
                continue;
            }

            itemsTable.stack(
            new Table(t ->
            t.image(item.uiIcon).size(iconMed - 4).scaling(Scaling.fit).pad(2f)
            .tooltip(tooltip -> tooltip.background(Styles.black6).margin(4f).add(item.localizedName).style(Styles.outlineLabel))
            ),
            new Table(t -> t.label(() -> {
                int update = itemDelta[item.id];
                if(update == 0) return "";
                return (update < 0 ? "[red]" : "[green]+") + UI.formatAmount(update);
            }).fontScale(0.85f)).top().left()
            );

            itemsTable.table(amountTable -> {
                amountTable.defaults().expand().left();

                Label amountLabel = amountTable.add("").growY().get();
                amountTable.row();
                var planLabel = amountTable.add("").fontScale(0.6f).height(0.01f);

                amountTable.update(() -> {
                    int planAmount = planItems.get(item);
                    int amount = player.team().items().get(item);

                    float newFontScale = 1f;
                    Color amountColor = Color.white;
                    if(planAmount == 0){
                        var core = player.team().core();
                        if(core != null && amount >= core.storageCapacity * 0.99){
                            amountColor = Pal.accent;
                        }
                        planLabel.height(0.01f);//can't use 0 as maxHeight;
                        planLabel.get().setText("");
                    }else{
                        amountColor = (amount > planAmount ? Color.green
                        : amount > planAmount / 2 ? Pal.stat
                        : Color.scarlet);
                        planLabel.height(Float.NEGATIVE_INFINITY);
                        planLabel.color(planAmount > 0 ? Color.scarlet : Color.green);
                        planLabel.get().setText(UI.formatAmount(planAmount));
                        newFontScale = 0.7f;
                    }

                    if(amountLabel.getFontScaleX() != newFontScale)
                        amountLabel.setFontScale(newFontScale);
                    amountLabel.setColor(amountColor);
                    amountLabel.setText(UI.formatAmount(amount));
                });
            }).minWidth(MIN_WIDTH).left();

            if(++i % columns.getValue() == 0){
                itemsTable.row();
            }
        }
    }

    private void rebuildUnits(){
        unitsTable.clearChildren();

        int i = 0;
        for(UnitType unit : content.units()){
            if(usedUnits.contains(unit)){
                unitsTable.image(unit.uiIcon).size(iconSmall).scaling(Scaling.fit).pad(2f)
                .tooltip(t -> t.background(Styles.black6).margin(4f).add(unit.localizedName).style(Styles.outlineLabel));
                unitsTable.label(() -> {
                    int typeCount = player.team().data().countType(unit);
                    return (typeCount == Units.getCap(player.team()) ? "[stat]" : "") + typeCount;
                }).minWidth(MIN_WIDTH).left();

                if(++i % columns.getValue() == 0){
                    unitsTable.row();
                }
            }
        }
    }

    private void rebuildPlans(){
        planItems.clear();
        planCounter.clear();

        control.input.allPlans().each(plan -> {
            Block block = plan.block;

            if(block instanceof CoreBlock) return;

            if(plan.build() instanceof ConstructBuild build){
                block = build.current;
            }

            planCounter.increment(block, plan.breaking ? -1 : 1);

            for(ItemStack stack : block.requirements){
                int planAmount = (int)(plan.breaking ? -state.rules.buildCostMultiplier * state.rules.deconstructRefundMultiplier * stack.amount * plan.progress
                : state.rules.buildCostMultiplier * stack.amount * (1 - plan.progress));
                planItems.add(stack.item, planAmount);
            }
        });

        plansTable.clearChildren();
        if(planCounter.isEmpty()) return;
        int i = 0;
        for(Block block : content.blocks()){
            int count = planCounter.get(block, 0);
            if(count == 0 || block.category == Category.distribution && block.size < 3
            || block.category == Category.liquid && block.size < 3
            || block instanceof PowerNode
            || block instanceof BeamNode) continue;

            plansTable.image(block.uiIcon).size(iconSmall).scaling(Scaling.fit).pad(2f);
            plansTable.label(() -> (count > 0 ? "[green]+" : "[red]") + count).minWidth(MIN_WIDTH).left();

            if(++i % columns.getValue() == 0){
                plansTable.row();
            }
        }
    }

    public boolean hadItem(Item item){
        return usedItems.contains(item);
    }
}
