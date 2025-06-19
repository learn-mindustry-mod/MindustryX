package mindustryX.features;

import arc.*;
import arc.func.*;
import arc.scene.style.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import mindustry.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.ui.dialogs.SettingsMenuDialog.*;

import static mindustry.Vars.*;

public class Settings{
    public static class LazySettingsCategory extends SettingsCategory{
        private final Prov<Drawable> iconProv;

        public LazySettingsCategory(String name, Prov<Drawable> icon, Cons<SettingsTable> builder){
            super(name, null, builder);
            iconProv = icon;
        }

        public void init(){
            icon = iconProv.get();
        }
    }

    public static final Seq<LazySettingsCategory> categories = new Seq<>();

    @SuppressWarnings({"deprecation"})
    public static void addSettings(){
        ArcOld.init(categories);
        Events.on(ClientLoadEvent.class, e -> {
            ui.settings.getCategories().add(new SettingsCategory("@settings.category.settingV2", Icon.box, (c) -> {
            }){

                {
                    table = new SettingsTable(){
                        @Override
                        public Table build(){
                            SettingsV2.buildSettingsTable(this);
                            add().width(500).row();
                            return this;
                        }
                    };
                }
            });
            categories.each(LazySettingsCategory::init);
            Vars.ui.settings.getCategories().addAll(categories);
        });
    }

    public static void toggle(String name){
        Core.settings.put(name, !Core.settings.getBool(name));
    }

    public static void cycle(String name, int max){
        Core.settings.put(name, (Core.settings.getInt(name) + 1) % max);
    }
}
