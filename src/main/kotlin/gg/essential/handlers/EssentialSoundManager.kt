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
package gg.essential.handlers

import dev.folomeev.kotgl.matrix.vectors.Vec3
import dev.folomeev.kotgl.matrix.vectors.mutables.minus
import dev.folomeev.kotgl.matrix.vectors.sqrDistance
import dev.folomeev.kotgl.matrix.vectors.vec3
import dev.folomeev.kotgl.matrix.vectors.vecZero
import gg.essential.config.EssentialConfig
import gg.essential.config.LoadsResources
import gg.essential.gui.elementa.state.v2.Observer
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.memo
import gg.essential.gui.elementa.state.v2.stateOf
import gg.essential.mixins.impl.client.audio.ISoundExt
import gg.essential.mixins.impl.client.audio.SoundSystemExt
import gg.essential.model.ModelAnimationState
import gg.essential.model.ParticleSystem.Locator
import gg.essential.model.PlayerMolangQuery
import gg.essential.model.SoundEffect
import gg.essential.model.util.Quaternion
import gg.essential.model.util.rotateSelfBy
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.util.identifier
import gg.essential.util.USession
import net.minecraft.client.Minecraft
import net.minecraft.client.audio.ISound
import net.minecraft.client.audio.ITickableSound
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.client.audio.Sound
import net.minecraft.client.audio.SoundEventAccessor
import net.minecraft.client.audio.SoundHandler
import net.minecraft.util.ResourceLocation
import net.minecraft.util.SoundCategory
import net.minecraft.util.SoundEvent
import java.util.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toJavaUuid

//#if MC>=11600
//$$ import kotlin.math.roundToInt
//#endif

/**
 * Utility for playing custom sounds added to Minecraft by Essential.
 *
 * Sounds must be registered in `assets/essential/sounds.json` before they can be played.
 *
 * @see gg.essential.util.EssentialSounds
 */
object EssentialSoundManager {
    // Note: This method isn't actually what loads the sounds.json but it's the main place we rely on it.
    //       Callers also need to mark their specific ogg as used.
    //       There's no parsing (nor redacting) of the sounds.json file at the moment.
    @LoadsResources("/assets/essential/sounds.json")
    fun playSound(resourceLocation: ResourceLocation) {
        Minecraft.getMinecraft().soundHandler.playSound(PositionedSoundRecord.getMasterRecord(
            //#if MC>=11903
            //$$ SoundEvent.of(resourceLocation),
            //#elseif MC>=11202
            SoundEvent(resourceLocation),
            //#else
            //$$ resourceLocation,
            //#endif
            1f
        ))

    }

    @OptIn(ExperimentalUuidApi::class) // TODO: can be removed when we migrate to Kotlin 2.4
    @JvmOverloads
    fun playSound(
        event: ModelAnimationState.SoundEvent,
        forceGlobal: Boolean = false,
        volume: State<Float> = stateOf(1f),
        enforceEmoteSoundSettings: Boolean = true,
    ) {
        val mc = Minecraft.getMinecraft()

        //#if MC>=11600
        //#if MC >= 26.2
        //$$ val camera = mc.gameRenderer.mainCamera()
        //#else
        //$$ val camera = mc.gameRenderer.activeRenderInfo
        //#endif
        //$$ val listener = with(camera.projectedView) { vec3(x.toFloat(), y.toFloat(), z.toFloat()) }
        //#else
        @Suppress("UNNECESSARY_SAFE_CALL") // player field inappropriately marked as non-null by Forge
        val listener = mc.player?.let { vec3(it.posX.toFloat(), it.posY.toFloat(), it.posZ.toFloat()) }
        //#endif

        val pos = event.locator.position
        val maxDist = event.effect.maxDistance
        if (!forceGlobal && listener != null && listener.sqrDistance(pos) > maxDist * maxDist) {
            return
        }

        val sourceUuid = event.sourceEntity.uuid
        val finalVolume = if (enforceEmoteSoundSettings && sourceUuid != null) {
            memo {
                if (shouldAllowEmoteSound(sourceUuid.toJavaUuid())) volume() else 0f
            }
        } else {
            volume
        }

        mc.soundHandler.playSound(EssentialSoundInstance(event.effect, event.locator, finalVolume, forceGlobal))
    }

    private class EssentialSoundInstance(
        val effect: SoundEffect,
        val loc: Locator,
        private val volume: State<Float>,
        forceGlobal: Boolean,
    ) : ITickableSound, ISoundExt {
        private val identifier: ResourceLocation =
            effect.name.let { if (":" in it) identifier(it) else identifier("essential", it) }

        private val category: SoundCategory =
            when (effect.category) {
                gg.essential.model.SoundCategory.MUSIC -> SoundCategory.MUSIC
                gg.essential.model.SoundCategory.RECORD -> SoundCategory.RECORDS
                gg.essential.model.SoundCategory.WEATHER -> SoundCategory.WEATHER
                gg.essential.model.SoundCategory.BLOCK -> SoundCategory.BLOCKS
                gg.essential.model.SoundCategory.HOSTILE -> SoundCategory.HOSTILE
                gg.essential.model.SoundCategory.NEUTRAL -> SoundCategory.NEUTRAL
                gg.essential.model.SoundCategory.PLAYER -> SoundCategory.PLAYERS
                gg.essential.model.SoundCategory.AMBIENT -> SoundCategory.AMBIENT
            }

        private val sound = effect.randomEntry()
        private val mcSound = sound?.let { sound ->
            //#if MC>=11200
            Sound(
                //#if MC>=12100
                //$$ identifier("essential", sound.asset.checksum),
                //#else
                "essential:${sound.asset.checksum}",
                //#endif
                //#if MC>=11900
                //$$ { sound.volume }, { sound.pitch },
                //#else
                sound.volume, sound.pitch,
                //#endif
                sound.weight,
                Sound.Type.FILE,
                sound.stream,
                //#if MC>=11600
                //$$ false, // preload only affects regularly loaded sounds
                //$$ effect.maxDistance.roundToInt(),
                //#else
                // passed below via essential$maxDistance
                //#endif
            )
            //#else
            //$$ SoundPoolEntry(
            //$$     identifier("essential:sounds/${sound.asset.checksum}.ogg"),
            //$$     sound.pitch.toDouble(),
            //$$     sound.volume.toDouble(),
            //$$     sound.stream,
            //$$ )
            //#endif
        }

        //#if MC>=11200
        private val soundSet = SoundEventAccessor(identifier, null)
        //#else
        //$$ private val soundSet = object : SoundEventAccessorComposite(identifier, 1.0, 1.0, category) {
        //$$     override fun cloneEntry(): SoundPoolEntry = mcSound ?: SoundHandler.missing_sound
        //$$ }
        //#endif

        override fun getSoundLocation(): ResourceLocation = identifier

        //#if MC >= 26.3
        //$$ override fun getSoundEvent(): WeighedSoundEvents = soundSet
        //#endif
        //#if MC>=11200
        override fun createAccessor(handler: SoundHandler): SoundEventAccessor = soundSet
        override fun getSound(): Sound = mcSound ?: SoundHandler.MISSING_SOUND
        override fun getCategory(): SoundCategory = category
        //#else
        //$$ override fun `essential$createAccessor`(): SoundEventAccessorComposite = soundSet
        //#endif

        override fun canRepeat(): Boolean = sound?.looping ?: false

        override fun getRepeatDelay(): Int = 0

        // When the sound volume is zero during the initial playSound call, MC will completely skip submitting the
        // sound. So even if the volume increases later, it won't be playing.
        // To work around this, we never return 0 until the first `update` call.
        private var mayReturnTrueVolume: Boolean = false
        private fun getMinVolume(): Float = if (mayReturnTrueVolume) 0f else 1e-10f
        override fun getVolume(): Float  = if (!loc.isVisible) getMinVolume() else // Mute sound while the locator is hidden
                ((sound?.volume ?: 1f) * volume.getUntracked()).coerceAtLeast(getMinVolume())

        override fun getPitch(): Float = sound?.pitch ?: 1f

        private val isGlobal = forceGlobal || sound?.directional == false
        private val isRelativeToListener = isGlobal || (loc.isRelativeToCamera() && !effect.fixedPosition)
        //#if MC>=11600
        //$$ // Note: 1.16 forge and pre-1.18 yarn names for this method are bad, correct name is `isRelative`.
        //$$ //       The method is equivalent to our pre-1.16 mixin which sets `AL_SOURCE_RELATIVE`.
        //$$ override fun isGlobal(): Boolean = isRelativeToListener
        //#else
        override fun `essential$isRelativeToListener`(): Boolean = isRelativeToListener
        //#endif

        private var pos: Vec3 = if (isGlobal) vecZero() else loc.position.relativeToReference()
        //#if MC>=11600
        //$$ override fun getX(): Double = pos.x.toDouble()
        //$$ override fun getY(): Double = pos.y.toDouble()
        //$$ override fun getZ(): Double = pos.z.toDouble()
        //#else
        override fun getXPosF(): Float = pos.x
        override fun getYPosF(): Float = pos.y
        override fun getZPosF(): Float = pos.z
        //#endif

        //#if MC>=11600
        //$$ // passed via mcSound
        //#else
        override fun `essential$maxDistance`(): Float = effect.maxDistance
        //#endif

        override fun getAttenuationType(): ISound.AttenuationType =
            if (isGlobal) ISound.AttenuationType.NONE else ISound.AttenuationType.LINEAR

        private var alive = loc.isValid
        override fun isDonePlaying(): Boolean {
            return !alive
        }

        override fun update() {
            mayReturnTrueVolume = true

            if (sound == null || sound.interruptible || sound.looping) {
                alive = loc.isValid
            }
            if (alive && !isGlobal && !effect.fixedPosition) {
                pos = loc.position.relativeToReference()
            }
        }

        private fun Vec3.relativeToReference(): Vec3 =
            if (isRelativeToListener) {
                val soundHandler = Minecraft.getMinecraft().soundHandler as SoundSystemExt
                val origin = soundHandler.`essential$getListenerPosition`() ?: vecZero()
                val rotation = soundHandler.`essential$getListenerRotation`() ?: Quaternion.Identity
                this.minus(origin).rotateSelfBy(rotation.invert())
            } else {
                this
            }
    }

    private fun Locator.isRelativeToCamera(): Boolean {
        if (this is PlayerMolangQuery) {
            return player == Minecraft.getMinecraft().player
        }
        return parent?.isRelativeToCamera() ?: false
    }

    // TODO This is currently an explicit property because the IRelationshipStates implementation relies on a
    //  WeakReference in StateCallbackManager to receive updates.
    //  Can be inlined once that's no longer the case.
    private val relationships by lazy { platform.createSocialStates().relationships }

    private fun Observer.shouldAllowEmoteSound(uuid: UUID): Boolean =
        when (EssentialConfig.allowEmoteSounds()) {
            EssentialConfig.AllowEmoteSounds.FROM_EVERYONE -> true
            EssentialConfig.AllowEmoteSounds.FROM_MYSELF_AND_FRIENDS -> uuid == USession.active().uuid || uuid in relationships.friends()
            EssentialConfig.AllowEmoteSounds.FROM_MYSELF_ONLY -> USession.active().uuid == uuid
            EssentialConfig.AllowEmoteSounds.FROM_NOBODY -> false
        }

}
