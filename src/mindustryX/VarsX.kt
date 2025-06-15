package mindustryX;

import arc.*;
import arc.Files.*;
import arc.files.*;
import arc.util.*;
import arc.util.serialization.*;
import mindustry.core.*;

public class VarsX{
    public static String version;
    public static boolean isLoader = false, devVersion = false;

    static {
        try{
            Fi file = OS.isAndroid || OS.isIos ? Core.files.internal("mod.hjson") : new Fi("mod.hjson", FileType.internal);
            Jval meta = Jval.read(file.readString());
            version = meta.getString("version");
            devVersion = version.endsWith("-dev");
        }catch(Throwable e){
            e.printStackTrace();
            version = "custom build";
        }
        Version.mdtXBuild = version;
    }
}
