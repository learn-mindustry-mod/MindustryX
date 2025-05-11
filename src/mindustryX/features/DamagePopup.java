package mindustryX.features;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.pooling.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.world.blocks.*;
import mindustryX.events.*;
import mindustryX.features.SettingsV2.*;

import static arc.util.Tmp.*;

/**
 * 玩家子弹伤害跳字
 * Create by 2024/6/5
 */
public class DamagePopup{
    private static final ObjectMap<Sized, ObjectMap<DamageType, Popup>> mappedPopup = new ObjectMap<>();

    // 跳字初始缩放限制
    public static final float minScale = 1f / 4 / Scl.scl(1);
    public static final float maxScale = 1f / 2 / Scl.scl(1);

    // 无持续攻击的消退时间
    public static float popupLifetime = 60f;

    // 设置
    private static final SettingsV2.CheckPref
    enable = new CheckPref("damagePopup.enable"),
    playerOnly = new CheckPref("damagePopup.playerOnly", true),
    healPopup = new CheckPref("damagePopup.showHeal");
    private static final SliderPref minHealth = new SliderPref("damagePopup.minHealth", 600, 0, 4000, 50, v -> v + "[red]HP");

    static{
        enable.addFallbackName("damagePopup");
        playerOnly.addFallbackName("playerPopupOnly");
        healPopup.addFallbackName("healPopup");
        minHealth.addFallbackName("popupMinHealth");
    }

    public static void init(){
        Events.on(HealthChangedEvent.class, DamagePopup::handleEvent);

        Events.on(ResetEvent.class, e -> mappedPopup.clear());
    }

    private static void handleEvent(HealthChangedEvent event){
        if(!enable.get()) return;
        if(event.entity.maxHealth() < minHealth.get()) return;
        if(event.amount < 0 && !healPopup.get()) return;
        if(event.source != null){// 视角外的跳字
            Rect cameraBounds = Core.camera.bounds(r1).grow(4 * Vars.tilesize);
            if(!cameraBounds.contains(event.entity.getX(), event.entity.getY())) return;
            if(event.source != null && playerOnly.get() && !inControl(getOwner(event.source))) return;
        }

        if(event.entity instanceof Sized entitySized)
            popup(event.source instanceof Sized sized ? sized : null, entitySized, event.amount, event.isSplash);
    }

    private static @Nullable Entityc getOwner(Entityc source){
        Entityc current = source;
        while(current instanceof Ownerc o){
            current = o.owner();
            //Lightning create Bullet(owner=null,data=hitter)
            if(current == null && o instanceof Bulletc o2 && o2.data() instanceof Bulletc bullet){
                current = bullet;
            }
        }
        return current;
    }

    private static boolean inControl(Entityc entity){
        if(entity instanceof Unit u && (u.isLocal() || Vars.control.input.selectedUnits.contains(u))) return true;
        if(entity instanceof ControlBlock b && b.unit().isLocal()) return true;
        return false;
    }

    private static void popup(Sized source, Sized damaged, float amount, boolean isSplash){
        if(Mathf.equal(amount, 0f)) return;

        float hitSize = damaged.hitSize();

        float offsetX, offsetY;
        float rotation = source != null
        ? damaged.angleTo(source) + Mathf.random(35f)
        : 90 + Mathf.range(35f);

        float scale = Mathf.clamp(hitSize / 64 / Scl.scl(1), minScale, maxScale);
        float offsetLength = hitSize * Mathf.random(0.4f, 0.7f);

        if(!isSplash){
            offsetX = Angles.trnsx(rotation, hitSize * Mathf.random(0.2f, 0.4f));
            offsetY = Angles.trnsy(rotation, hitSize * Mathf.random(0.2f, 0.4f));
        }else{
            offsetX = Angles.trnsx(rotation, hitSize * Mathf.random(0.3f, 0.4f));
            offsetY = Angles.trnsy(rotation, hitSize * Mathf.random(0.3f, 0.4f));

            scale *= 0.65f; // 堆叠的跳字有更大的效果
        }

        DamageType type = amount < 0 ? DamageType.heal : isSplash ? DamageType.splash : DamageType.normal;

        if(!isSplash){
            ObjectMap<DamageType, Popup> map = mappedPopup.get(damaged, ObjectMap::new);
            Popup popup = map.get(type);

            if(popup == null){
                popup = Popup.create().set(damaged, type, offsetX, offsetY, popupLifetime, Math.abs(amount), 1f, scale, rotation, offsetLength);
                map.put(type, popup);
                popup.add();
            }else{
                popup.superposeAmount(Math.abs(amount));
            }
        }else{
            Popup popup = Popup.create().set(damaged, type, offsetX, offsetY, popupLifetime, Math.abs(amount), 1f, scale, rotation, offsetLength);
            popup.add();
        }
    }

    private static boolean superpose(DamageType type){
        return type == DamageType.normal || type == DamageType.heal;
    }

    private static class Popup extends Decal{
        public static float maxAmountEffect = 5_000;
        public static int maxCountEffect = 50;
        public static float amountEffect = 3f;
        public static float countEffect = 2f;
        public static float fontScaleEffectScl = 8f;
        public static float splashTime = 15f;

        // data
        public DamageType type;
        public Font font = Fonts.outline;
        public Sized damaged;
        public float originX, originY;
        public float offsetX, offsetY;
        public float lifetime;
        public float alpha;
        public float scale;
        public float offsetLength;
        public float rotation; // deg

        public float amount;
        public int count;

        private float timer;
        private float floatTimer;
        private float splashTimer;

        public Popup set(Sized damaged, DamageType type, float offsetX, float offsetY, float lifetime, float amount, float alpha, float scale, float rotation, float offsetLength){
            this.damaged = damaged;

            this.type = type;

            this.originX = damaged.getX();
            this.originY = damaged.getY();

            this.offsetX = offsetX;
            this.offsetY = offsetY;

            this.lifetime = lifetime;
            this.amount = amount;
            this.alpha = alpha;
            this.scale = scale;
            this.rotation = rotation;
            this.offsetLength = offsetLength;

            return this;
        }

        public void draw(){
            float fin = timer / lifetime;

            float alphaScaleEase = Bezier.quadratic(v1, fin,
            v2.set(1f, 1f),
            v3.set(0f, 1f),
            v4.set(0f, 0.8f),
            v5.set(0f, 0.5f)).y;

            float alpha = this.alpha * alphaScaleEase;
            float scale = this.scale * alphaScaleEase * Math.max(effect() / fontScaleEffectScl, 1);

            float fx = getX() + offsetX, fy = getY() + offsetY;

            if(!superpose(type)){
                float positionEase = Bezier.quadratic(v1, fin,
                v2.set(0f, 0f),
                v3.set(0.19f, 1f),
                v4.set(0.22f, 1f),
                v5.set(1f, 1f)).y;

                float offsetLength = this.offsetLength * positionEase;

                fx += Angles.trnsx(rotation, offsetLength);
                fy += Angles.trnsy(rotation, offsetLength);
            }

            c1.set(type.color).a(alpha).lerp(Color.white, splashTimer / splashTime * 0.75f);

            String text = (type.icon != null ? type.icon : "") + Strings.autoFixed(amount, 1);
            Draw.z(Layer.overlayUI);
            font.draw(text, fx, fy, c1, scale, false, Align.center);
            Draw.reset();
        }

        public void update(){
            x = !superpose(type) ? originX : damaged.getX();
            y = !superpose(type) ? originY : damaged.getY();
            if(floatTimer > 0){
                floatTimer = Math.max(0, floatTimer - Time.delta);
            }else{
                timer += Time.delta;
            }

            if(splashTimer > 0){
                splashTimer = Math.max(0, splashTimer - Time.delta);
            }
        }

        protected float effect(){
            float damageEffect = Popup.amountEffect * Math.min(amount / maxAmountEffect, 1);
            float countEffect = Popup.countEffect * Math.min(count / maxCountEffect, 1);
            return 1f + damageEffect + countEffect;
        }

        public void superposeAmount(float amount){
            this.amount += amount;
            count++;

            floatTimer = lifetime;
            splashTimer = splashTime;
        }

        public static Popup create(){
            return Pools.obtain(Popup.class, Popup::new);
        }

        @Override
        public void remove(){
            if(damaged != null && superpose(type)){
                mappedPopup.get(damaged, ObjectMap::new).remove(type);
            }
            super.remove();
        }

        @Override
        public void reset(){
            damaged = null;

            type = DamageType.normal;

            offsetX = 0;
            offsetY = 0;

            lifetime = 0;
            alpha = 0;
            scale = 0;
            offsetLength = 0;
            rotation = 0;

            amount = 0f;
            count = 0;
            timer = 0f;
        }
    }

    public static class DamageType{
        public static DamageType
        normal = new DamageType(null, Pal.health),
        heal = new DamageType(null, Pal.heal),
        splash = new DamageType(StatusEffects.blasted.emoji(), StatusEffects.blasted.color);

        public final Color color;
        public @Nullable String icon;

        private DamageType(String icon, Color color){
            this.icon = icon;
            this.color = color.cpy();
        }
    }
}
