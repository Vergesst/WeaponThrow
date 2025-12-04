package vergisst.minecraftmod.weaponthrow.client.handler

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.AbstractClientPlayerEntity
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.model.BipedEntityModel
import net.minecraft.client.render.item.HeldItemRenderer
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.client.world.ClientWorld
import net.minecraft.item.ItemStack
import net.minecraft.util.Arm
import net.minecraft.util.Hand
import net.minecraft.util.math.MathHelper
import org.joml.Quaternionf
import vergisst.minecraftmod.weaponthrow.client.events.OnHeldItemRender
import vergisst.minecraftmod.weaponthrow.client.events.OnStartPlayerRender
import vergisst.minecraftmod.weaponthrow.events.OnApplySlow
import vergisst.minecraftmod.weaponthrow.events.OnFOVUpdate
import vergisst.minecraftmod.weaponthrow.impl.MathConstant.POSITIVE_X
import vergisst.minecraftmod.weaponthrow.impl.MathConstant.POSITIVE_Z
import vergisst.minecraftmod.weaponthrow.interfaces.IPlayerEntityMixin
import vergisst.minecraftmod.weaponthrow.packets.C2SThrowPacket
import vergisst.minecraftmod.weaponthrow.packets.State
import java.util.UUID
import kotlin.math.sign

object EventsHandler {
    var wasPressed = false

    fun onServerUpdate(playerUuid: UUID, maxCharge: Int, isCharging: Boolean) {
        val playerEntity = MinecraftClient.getInstance().world?.getPlayerByUuid(playerUuid)
        if (playerEntity != null) {
            val cap = (playerEntity as IPlayerEntityMixin).`weaponThrow$getThrowPower`()
            cap.maxCharge = maxCharge

            if (isCharging) cap.chargeTime = maxCharge

            cap.action = if (isCharging) State.DURING else State.NONE
        }
    }

    fun registerClientEvents() {
        OnHeldItemRender.EVENT.register(object : OnHeldItemRender {
            override fun interact(
                renderer: HeldItemRenderer?,
                player: AbstractClientPlayerEntity?,
                tickDelta: Float,
                pitch: Float,
                hand: Hand?,
                swingProgress: Float,
                item: ItemStack?,
                equipProgress: Float,
                matrices: MatrixStack?,
                vertexConsumers: VertexConsumerProvider?,
                light: Int
            ) {
                if (player == null) return

                val cap = (player as IPlayerEntityMixin).`weaponThrow$getThrowPower`()
                if (cap.action == State.DURING) {
                    var preProgress = 1.0f
                    if (cap.maxCharge.toFloat() != 0.0f && cap.chargeTime > 0) {
                        val lerp: Float =
                            MathHelper.lerp(tickDelta, (cap.chargeTime + 1).toFloat(), cap.chargeTime.toFloat())
                        preProgress = 1f - lerp / cap.maxCharge
                    }

                    val progress = MathHelper.clamp(preProgress, 0f, 1.0f)

                    // 确保 matrices 不为 null
                    matrices?.apply { // 使用安全调用和作用域函数
                        translate(0.0, 0.0, progress * 0.50)
                        multiply(Quaternionf().fromAxisAngleDeg(POSITIVE_Z, progress * 10.0f))
                        multiply(Quaternionf().fromAxisAngleDeg(POSITIVE_X, progress * 10.0f))
                    }
                }
            }
        })

        OnStartPlayerRender.EVENT.register(OnStartPlayerRender { render, entity ->
            val cap = (entity as IPlayerEntityMixin).`weaponThrow$getThrowPower`()
            if (cap.action == State.DURING) {
                if (entity is AbstractClientPlayerEntity) {
                    val hand = entity.mainArm
                    if (hand == Arm.RIGHT) render.model.rightArmPose = BipedEntityModel.ArmPose.THROW_SPEAR
                    else render.model.leftArmPose = BipedEntityModel.ArmPose.THROW_SPEAR
                }
            }
        })

        OnApplySlow.EVENT.register(OnApplySlow { player ->
            val cap = (player as IPlayerEntityMixin).`weaponThrow$getThrowPower`()

            cap.action == State.DURING
        })

        OnFOVUpdate.EVENT.register(OnFOVUpdate { player, fov ->
            val cap = (player as IPlayerEntityMixin).`weaponThrow$getThrowPower`()
            val maxChargeTime = cap.maxCharge
            val chargeTime = cap.chargeTime
            val isCharging = cap.action == State.DURING
            var f = fov

            if (isCharging) {
                var f1 = 1.0F
                if (sign(maxChargeTime.toDouble()) != 0.0 && chargeTime > 0) {
                    val lerp = MathHelper.lerp(
                        MinecraftClient.getInstance().tickDelta,
                        chargeTime + 1,
                        chargeTime
                    )
                    f1 = MathHelper.clamp((1.0F - lerp / maxChargeTime), 0.0F, 1.0F)
                }
                f1 = if(f1 > 1.0F) 1.0F else f1 * f1

                f *= 1.0F + f1 * 0.15F
            }

            f
        })

        ClientTickEvents.END_WORLD_TICK.register(ClientTickEvents.EndWorldTick { client: ClientWorld? ->
            val pressed: Boolean = KeyBindingHandler.KEYBINDING.isPressed
            if (pressed) {
                PacketHandler.sendToServer(C2SThrowPacket(if (wasPressed) State.DURING else State.START))
                wasPressed = true
            } else if (wasPressed) {
                PacketHandler.sendToServer(C2SThrowPacket(State.FINISH))
                wasPressed = false
            }
        })
    }
}