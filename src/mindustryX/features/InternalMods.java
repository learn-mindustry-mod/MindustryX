package mindustryX.features;

import arc.files.*;
import arc.struct.*;
import arc.util.*;
import mindustry.core.*;
import mindustry.mod.*;
import mindustry.mod.Mods.*;
import mindustryX.*;

import static arc.Core.files;
import static mindustry.Vars.modDirectory;

public class InternalMods{
    public static Seq<LoadedMod> load(){
        Seq<LoadedMod> mods = new Seq<>();
        mods.add(internalMod(meta("Kotlin", "Kotlin语言标准库", "1.9.20", "Jetbrains")));
        if(!VarsX.isLoader)
            mods.add(internalMod(meta("MindustryX", "MindustryX", VarsX.version, "")));
        if(OS.isIos){
            try {
            mods.add(internalMod(meta("extra-utilities", "ExtraUtilities", "1.2.2.0", "guiY"""),(Mod) Class.forName("ExtraUtilities.ExtraUtilitiesMod").newInstance()));
            mods.add(internalMod(meta("he", "Helium", "beta-1.1", "EBwilson"),(Mod) Class.forName("helium.Helium").newInstance()));
            mods.add(internalMod(meta("new-horizon", "New Horizon", "2.0-alpha-50", "Yuria"), (Mod) Class.forName("newhorizon.NewHorizon").newInstance()));
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return mods;
    }

    public static Seq<Fi> spritesOverride(){
        if(VarsX.isLoader) return Seq.with();
        Fi root = files.internal("sprites-override");
        //internal don't support findAll
        return Seq.with(
        root.child("status-invincible.png"),
        root.child("ui/status-invincible-ui.png"),
        root.child("ui/logo.png")
        );
    }

    private static ModMeta meta(String id, String displayName, String version, String author){
        ModMeta meta = new ModMeta();
        meta.name = id;
        meta.displayName = "[内置]" + displayName;
        meta.version = version;
        meta.author = author;
        meta.minGameVersion = Version.buildString();
        meta.hidden = true;
        meta.cleanup();
        return meta;
    }

    private static LoadedMod internalMod(ModMeta meta, @Nullable Mod main){
        Fi file = modDirectory.child("internal-" + meta.name + ".jar");
        Fi root = files.internal("/mindustryX/mods/" + meta.name);
        return new LoadedMod(file, root, main, null, meta);
    }

    private static LoadedMod internalMod(ModMeta meta){
        return internalMod(meta, null);
    }
}
