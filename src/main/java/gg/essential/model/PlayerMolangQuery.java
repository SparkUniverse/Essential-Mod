/*
 * Copyright (c) 2024 ModCore Inc. All rights reserved.
 *
 * This code is part of ModCore Inc.'s Essential Mod repository and is protected
 * under copyright registration # TX0009138511. For the full license, see:
 * https://github.com/EssentialGG/Essential/blob/main/LICENSE
 *
 * You may not use, copy, reproduce, modify, sell, license, distribute,
 * commercialize, or otherwise exploit, or create derivative works based
 * upon, this file or any other in this repository, all of which is reserved by Essential.
 */
package gg.essential.model;

import dev.folomeev.kotgl.matrix.matrices.Mat4;
import dev.folomeev.kotgl.matrix.vectors.Vec3;
import dev.folomeev.kotgl.matrix.vectors.mutables.MutableVec3;
import gg.essential.Essential;
import gg.essential.gui.common.EmulatedUI3DPlayer.EmulatedPlayer;
import gg.essential.mixins.impl.client.renderer.entity.PlayerEntityRendererExt;
import gg.essential.model.util.Quaternion;
import kotlin.Pair;
import kotlin.uuid.Uuid;
import kotlin.uuid.UuidKt;
import me.kbrewster.eventbus.Subscribe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.entity.player.EntityPlayer;
import gg.essential.event.render.RenderTickEvent;
import gg.essential.model.molang.MolangQueryEntity;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static dev.folomeev.kotgl.matrix.vectors.Vectors.vec3;
import static dev.folomeev.kotgl.matrix.vectors.Vectors.vecUnitY;
import static dev.folomeev.kotgl.matrix.vectors.mutables.MutableVectors.mutableVec3;
import static dev.folomeev.kotgl.matrix.vectors.mutables.MutableVectors.plus;
import static gg.essential.model.util.KotglKt.toMat3;
import static gg.essential.model.util.KotglKt.transformPosition;

public class PlayerMolangQuery implements MolangQueryEntity, ParticleSystem.Locator {

    private final EntityPlayer player;
    private long removedWorldTime = -1;

    public PlayerMolangQuery(EntityPlayer player) {
        this.player = player;
    }

    public EntityPlayer getPlayer() {
        return player;
    }

    private float getPartialTicks() {
        return player instanceof EmulatedPlayer ? partialTicksMenu : partialTicksInGame;
    }

    @Override
    public float getLifeTime() {
        //Hack for frozen animations in the customizer
        //When pausing animations in the cosmetic studio, we set entity lifetime to 0
        //Don't add partial ticks since that would add a little jitter
        if (player.ticksExisted <= 1) return player.ticksExisted / 20.0f;
        return (player.ticksExisted + getPartialTicks()) / 20.0f;
    }

    @Override
    public float getTime() {
        if (isValid()) {
            return getLifeTime();
        } else {
            //#if MC>=12106
            //$$ World world = player.getWorld();
            //#else
            World world = player.getEntityWorld();
            //#endif
            if (world == null) { // shouldn't happen but better be safe than sorry, it is technically possible
                return getLifeTime();
            }
            long worldTime = world.getTotalWorldTime();
            if (removedWorldTime == -1) {
                removedWorldTime = worldTime;
            }
            int timeSinceRemoved = (int) (worldTime - removedWorldTime);
            return (player.ticksExisted + timeSinceRemoved + getPartialTicks()) / 20.0f;
        }
    }

    //#if MC>=12102
    //$$ // MC no longer tracks the prevDistanceTraveled, so we now do that ourselves.
    //$$ // The value will only be updated while someone is observing it, which should be good enough though assuming the
    //$$ // same PlayerMolangQuery is being reused across time (which is the case). It'll sill be slightly wrong on the
    //$$ // first tick that an emote/cosmetic which cares about this value is equipped, but I doubt that's even noticeable.
    //$$ private float prevDistanceTraveled;
    //$$ private float latestDistanceTraveled;
    //$$ private int latestDistanceTraveledAge;
    //$$ private void updateDistanceTraveled() {
    //$$     int age = player.age;
    //$$
    //$$     // If we haven't updated in a while, reset everything to the current values
    //$$     if (age > latestDistanceTraveledAge + 10) {
    //$$         latestDistanceTraveledAge = age;
    //$$         prevDistanceTraveled = latestDistanceTraveled = player.distanceTraveled;
    //$$         return;
    //$$     }
    //$$
    //$$     // If this is the first update call this tick
    //$$     int ticksSinceLastUpdate = age - latestDistanceTraveledAge;
    //$$     if (ticksSinceLastUpdate > 0) {
    //$$         // compute the value of the previous tick
    //$$         if (ticksSinceLastUpdate == 1) {
    //$$             // trivial case, stored value is the value of the previous tick
    //$$             prevDistanceTraveled = latestDistanceTraveled;
    //$$         } else {
    //$$             // multiple ticks have happened, assume movement to be uniform across them
    //$$             float movedSinceLastUpdate = player.distanceTraveled - latestDistanceTraveled;
    //$$             prevDistanceTraveled = player.distanceTraveled - movedSinceLastUpdate / ticksSinceLastUpdate;
    //$$         }
    //$$
    //$$         // and store the latest value again
    //$$         latestDistanceTraveled = player.distanceTraveled;
    //$$         latestDistanceTraveledAge = age;
    //$$     }
    //$$ }
    //#endif

    @Override
    public float getModifiedDistanceMoved() {
        //#if MC>=12102
        //$$ updateDistanceTraveled();
        //$$ float next = player.distanceTraveled * 0.6f;
        //$$ float prev = prevDistanceTraveled * 0.6f;
        //#else
        float next = player.distanceWalkedModified;
        float prev = player.prevDistanceWalkedModified;
        //#endif
        float now = prev + (next - prev) * getPartialTicks();
        return now * 16; // unclear what the unit is supposed to be
    }

    @Override
    public float getModifiedMoveSpeed() {
        //#if MC>=12102
        //$$ updateDistanceTraveled();
        //$$ float next = player.distanceTraveled * 0.6f;
        //$$ float prev = prevDistanceTraveled * 0.6f;
        //#else
        float next = player.distanceWalkedModified;
        float prev = player.prevDistanceWalkedModified;
        //#endif
        return (next - prev) * 16; // unclear what the unit is supposed to be
    }

    @NotNull
    @Override
    public ParticleSystem.Locator getLocator() {
        return this;
    }

    @NotNull
    @Override
    public Uuid getUuid() {
        return UuidKt.toKotlinUuid(player.getUniqueID());
    }

    @Nullable
    @Override
    public ParticleSystem.Locator getParent() {
        return null;
    }

    @Override
    public boolean isValid() {
        //#if MC>=11700
        //$$ return !player.isRemoved();
        //#else
        return !player.isDead;
        //#endif
    }

    @NotNull
    @Override
    public Vec3 getPosition() {
        return getPositionAndRotation().getFirst();
    }

    @NotNull
    @Override
    public Quaternion getRotation() {
        return getPositionAndRotation().getSecond();
    }

    public Vec3 getEntityPosition() {
        return getEntityPositionMut();
    }

    public Quaternion getEntityRotation() {
        return getBaseRotation();
    }

    private MutableVec3 getEntityPositionMut() {
        float partialTicks = getPartialTicks();
        //#if MC>=11600
        //$$ net.minecraft.util.math.vector.Vector3d nextPos = player.getPositionVec();
        //$$ float nextX = (float) nextPos.x;
        //$$ float nextY = (float) nextPos.y;
        //$$ float nextZ = (float) nextPos.z;
        //#else
        float nextX = (float) player.posX;
        float nextY = (float) player.posY;
        float nextZ = (float) player.posZ;
        //#endif
        float prevX = (float) player.lastTickPosX;
        float prevY = (float) player.lastTickPosY;
        float prevZ = (float) player.lastTickPosZ;
        return mutableVec3(
            prevX + (nextX - prevX) * partialTicks,
            prevY + (nextY - prevY) * partialTicks,
            prevZ + (nextZ - prevZ) * partialTicks
        );
    }

    private Vec3 getBasePosition() {
        MutableVec3 pos = getEntityPositionMut();
        //#if MC>=11400
        //$$ if (player.isCrouching()) {
        //#else
        if (player.isSneaking()) {
        //#endif
            //#if MC>=11200
            // 0.125f from RenderPlayer.doRender (1.12.2) / PlayerRenderer.getRenderOffset (1.16+)
            pos.setY(pos.getY() - 0.125f);
            //#endif
            //#if MC<11400
            // 0.2f from ModelPlayer.render, 0.9375f from RenderPlayer.preRenderCallback
            pos.setY(pos.getY() - 0.2f * 0.9375f);
            //#endif
        }
        return pos;
    }

    private float getRenderYaw() {
        RealYawAccess access = (RealYawAccess) player;
        float next = access.essential$getRealRenderYaw();
        float prev = access.essential$getRealPrevRenderYaw();
        return prev + (next - prev) * getPartialTicks();
    }

    private Quaternion getBaseRotation() {
        return Quaternion.Companion.fromAxisAngle(vecUnitY(), (float) (Math.PI - Math.toRadians(getRenderYaw())));
    }

    private @NotNull Pair<Vec3, Quaternion> calculatePositionAndRotation() {
        if (!(player instanceof AbstractClientPlayer)) {
            return new Pair<>(getBasePosition(), getBaseRotation());
        }

        Object renderer = Minecraft.getMinecraft().getRenderManager().getEntityRenderObject(player);
        if (!(renderer instanceof PlayerEntityRendererExt)) {
            return new Pair<>(getBasePosition(), getBaseRotation());
        }
        PlayerEntityRendererExt rendererExt = (PlayerEntityRendererExt) renderer;

        // Minecraft renders living entities ever so slightly offset (upwards in local space)
        // 0.001f from the 1.501 in RenderLivingBase.prepareScale, 0.9375f from RenderPlayer.preRenderCallback
        Vec3 renderPos = vec3(0f, 0.001f * 0.9375f, 0f);

        Mat4 mat = rendererExt.essential$getTransform((AbstractClientPlayer) player, getRenderYaw(), getPartialTicks());
        Vec3 position = plus(transformPosition(mat, renderPos), getBasePosition());
        Quaternion rotation = Quaternion.Companion.fromRotationMatrix(toMat3(mat));

        return new Pair<>(position, rotation);
    }

    private int lastAccessFrame = -1;
    private Pair<Vec3, Quaternion> cachedPositionAndRotation = null;

    @NotNull
    @Override
    public Pair<Vec3, Quaternion> getPositionAndRotation() {
        if (!isWithinRenderFrame) return calculatePositionAndRotation();

        // calculatePositionAndRotation() is expensive, so we will cache the result during rendering frames
        if (cachedPositionAndRotation != null && frameCount == lastAccessFrame) {
            return cachedPositionAndRotation;
        }

        lastAccessFrame = frameCount;
        cachedPositionAndRotation = calculatePositionAndRotation();
        return cachedPositionAndRotation;
    }

    @NotNull
    @Override
    public Vec3 getVelocity() {
        //#if MC>=11600
        //$$ net.minecraft.util.math.vector.Vector3d motion = player.getMotion();
        //$$ return vec3(
        //$$     (float) motion.x * 20f,
        //$$     (float) motion.y * 20f,
        //$$     (float) motion.z * 20f
        //$$ );
        //#else
        return vec3(
            (float) player.motionX * 20f,
            (float) player.motionY * 20f,
            (float) player.motionZ * 20f
        );
        //#endif
    }

    private static float partialTicksMenu;
    private static float partialTicksInGame;
    private static boolean isWithinRenderFrame = false;
    private static int frameCount;
    static {
        Essential.EVENT_BUS.register(new Object() {
            @Subscribe(priority = 1)
            private void renderWorld(RenderTickEvent event) {
                partialTicksMenu = event.getPartialTicksMenu();
                partialTicksInGame = event.getPartialTicksInGame();

                isWithinRenderFrame = event.isPre();
                if (isWithinRenderFrame) frameCount++;
            }
        });
    }

    @Override
    public boolean isVisible() {
        return !player.isInvisible();
    }

    public interface RealYawAccess {
        float essential$getRealRenderYaw();
        float essential$getRealPrevRenderYaw();
    }
}
