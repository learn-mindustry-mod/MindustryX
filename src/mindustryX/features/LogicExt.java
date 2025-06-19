package mindustryX.features;

import arc.*;
import mindustry.core.*;
import mindustry.game.EventType.*;
import mindustry.net.Packets.*;
import mindustryX.features.SettingsV2.*;

public class LogicExt{
    public static boolean worldCreator = false;
    public static boolean terrainSchematic = false;
    public static boolean invertMapClick = false;
    public static boolean placeShiftReplacement = false;
    public static boolean v146Mode = false;
    public static boolean contentsCompatibleMode = false;

    private static final CheckPref invertMapClick0 = new CheckPref("gameUI.invertMapClick");
    public static final CheckPref reliableSync = new CheckPref("debug.reliableSync");
    public static final SliderPref limitUpdate = new SliderPref("debug.limitUpdate", 0, 0, 100, 1, (it) -> {
        if(it == 0) return "关闭";
        return it + "格";
    });
    public static final CheckPref rotateCanvas = new CheckPref("block.rotateCanvas");

    public static int limitUpdateTimer = 10;

    public static void init(){
        invertMapClick0.addFallbackName("invertMapClick");
        reliableSync.addFallbackName("reliableSync");


        Events.run(Trigger.update, () -> {
            limitUpdateTimer = (limitUpdateTimer + 1) % 10;
            worldCreator = Core.settings.getBool("worldCreator");
            terrainSchematic = Core.settings.getBool("terrainSchematic");
            invertMapClick = invertMapClick0.get();
            placeShiftReplacement = Core.settings.getBool("placeReplacement");
            v146Mode = ConnectPacket.clientVersion == 146;
            contentsCompatibleMode = ConnectPacket.clientVersion > 0 && ConnectPacket.clientVersion != Version.build;
        });
    }
}