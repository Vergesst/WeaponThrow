package vergisst.minecraftmod.weaponthrow.handler

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.AxeItem
import net.minecraft.item.HoeItem
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.PickaxeItem
import net.minecraft.item.ShovelItem
import net.minecraft.item.SwordItem
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundEvents
import vergisst.minecraftmod.weaponthrow.Interface.IPlayerEntityMixin
import vergisst.minecraftmod.weaponthrow.capabilities.PlayerThrowData
import vergisst.minecraftmod.weaponthrow.entity.WeaponThrowEntity
import vergisst.minecraftmod.weaponthrow.events.OnStartPlayerTick
import vergisst.minecraftmod.weaponthrow.packets.S2CThrowPacket
import vergisst.minecraftmod.weaponthrow.packets.State
import kotlin.math.sign

object EventsHandler {
    private val TOOL_MULTIPLIER_MAP: Map<Class<out Item>, Double> = run {
        val tools = ConfigRegistry.COMMON.get().multipliers.tools
        mapOf(
            SwordItem::class.java to tools.swordMultiplier,
            AxeItem::class.java to tools.axeMultiplier,
            PickaxeItem::class.java to tools.pickaxeMultiplier,
            ShovelItem::class.java to tools.shovelMultiplier,
            HoeItem::class.java to tools.hoeMultiplier
        )
    }

    fun registerEvents() {
        val capRecharge = {player: PlayerEntity, cap: PlayerThrowData ->
            val attacked = player.getAttackCooldownProgress(0.0F) < 1.0F
            val cdConfig = ConfigRegistry.COMMON.get().general.notUseWhenCooldown
            val changedItem = !ItemStack.areItemsEqual(cap.chargingStack, player.mainHandStack)

            if(attacked && cdConfig || changedItem) {
                cap.resetCharging()
            }

            if(cap.chargeTime > 0) {
                cap.chargeTime--
            }
        }

        OnStartPlayerTick.EVENT.register{ player ->
            val cap = (player as IPlayerEntityMixin).`weaponThrow$getThrowPower`()
            if(!player.world.isClient) {
                capRecharge(player, cap)

                when(cap.action) {
                    State.START, State.FINISH -> {
                        PacketHandler.sendToAll(player, S2CThrowPacket(
                            player.uuid,
                            PlayerThrowData.getMaximumCharge(player),
                            cap.action == State.START
                        ))

                        cap.action = if (cap.action == State.FINISH) State.NONE else State.START
                    }
                    else -> {}
                }
            } else {
                if(cap.chargeTime > 0) {
                    cap.chargeTime--
                }
            }
        }

        ServerTickEvents.START_SERVER_TICK.register { server ->
            for(player in server.playerManager.playerList) {
                val cap = (player as IPlayerEntityMixin).`weaponThrow$getThrowPower`()
                capRecharge(player, cap)
            }
        }
    }

    fun onThrowItem(serverPlayer: ServerPlayerEntity, action: State) {
        val world = serverPlayer.world as ServerWorld
        val stack = serverPlayer.mainHandStack
        val isThrowable = ConfigRegistry.COMMON.get().experimental.shouldThrowItemsToo
        val multimap = stack.getAttributeModifiers(EquipmentSlot.MAINHAND)
        val haveAttributes = multimap.containsKey(EntityAttributes.GENERIC_ATTACK_DAMAGE) || multimap.containsKey(EntityAttributes.GENERIC_ATTACK_SPEED)
        val data = (serverPlayer as IPlayerEntityMixin).`weaponThrow$getThrowPower`()

        if((isThrowable || haveAttributes) && !stack.isEmpty) {
            val cdConfig = ConfigRegistry.COMMON.get().general.notUseWhenCooldown

            if(!(serverPlayer.itemCooldownManager.getCooldownProgress(stack.item, 1.0F) > 0 && cdConfig)) {
                data.action = action

                when(action) {
                    State.START -> if(data.chargeTime <= 0) data.startCharging(stack)
                    State.FINISH -> if(data.chargeTime >= 0) {
                        data.resetCharging()
                        val (baseThrow, baseExhaustion, modThrow) = dataFetcher(data, haveAttributes, serverPlayer)
                        val shouldDestroy = modThrow > 0.99
                        val baseDamage = serverPlayer.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE)
                        val toolMultiplier = fetchToolMultiplier(haveAttributes, stack)
                        val size = if(serverPlayer.isSneaking) stack.count else 1

                        val (totalDamage, totalVelocity, totalExhaustion) = fetchMultiplier(
                            toolMultiplier,
                            baseDamage,
                            baseThrow,
                            baseExhaustion,
                            modThrow,
                            size
                        )

                        val thrownEntity = WeaponThrowEntity(
                            world,
                            serverPlayer,
                            shouldDestroy,
                            totalDamage.toFloat(),
                            stack.split(size)
                        )

                        thrownEntity.setVelocity(serverPlayer, serverPlayer.pitch, serverPlayer.yaw, 0.0F, totalVelocity.toFloat(), 1.0F)
                        serverPlayer.addExhaustion(totalExhaustion.toFloat())

                        world.spawnEntity(thrownEntity)

                        thrownEntity.playSound(SoundEvents.ENTITY_EGG_THROW, 1.0F, 0.5F)
                    }
                    else -> {}
                }
                (serverPlayer as IPlayerEntityMixin).`weaponThrow$setThrowPower`(data)
            }
        }
    }

    fun dataFetcher(data: PlayerThrowData ,haveAttributes: Boolean, serverPlayer: ServerPlayerEntity): Triple<Float, Float, Float> {
        var baseThrow = 0.0F
        var baseExhaustion = 0.05F
        var modThrow = 1.0F
        val defaultVelocity = ConfigRegistry.COMMON.get().defaults.velocityDefault
        val isExperiment = ConfigRegistry.COMMON.get().experimental.shouldThrowItemsToo

        if(sign(PlayerThrowData.getMaximumCharge(serverPlayer).toFloat()) != 0.0F)
            modThrow = 1.0F - (data.chargeTime / PlayerThrowData.getMaximumCharge(serverPlayer).toFloat())

        if(isExperiment)
            baseThrow = defaultVelocity.toFloat()

        if(haveAttributes) {
            baseThrow = 20 / serverPlayer.attackCooldownProgressPerTick
            baseExhaustion = 1 / baseThrow
        }

        return Triple(baseThrow, baseExhaustion, modThrow)
    }

    fun fetchToolMultiplier(haveAttributes: Boolean, stack: ItemStack): Double {
        return if(haveAttributes){
            val baseMultiplier = TOOL_MULTIPLIER_MAP.getOrDefault(stack.item::class.java, 1.0)
            if(baseMultiplier == 0.0) 1.0 else baseMultiplier
        } else {
            1.0
        }
    }

    fun fetchMultiplier(toolMultiplier: Double, baseDamage: Double, baseThrow: Float, baseExhaustion: Float, modThrow: Float, size: Int): Triple<Double, Double, Double> {
        val commonMul = ConfigRegistry.COMMON.get().multipliers

        val bDamageMul = commonMul.damages.baseDamageMultiplier
        val sDamageMul = commonMul.damages.stackDamageMultiplier
        val mDamageMul = commonMul.damages.modifiedDamageMultiplier
        val totalDamage = (baseDamage * (bDamageMul + modThrow * mDamageMul) + size * sDamageMul) * toolMultiplier

        val bVelocityMul = commonMul.velocities.baseVelocityMultiplier
        val sVelocityMul = commonMul.velocities.stackVelocityMultiplier
        val mVelocityMul = commonMul.velocities.modifiedVelocityMultiplier
        val totalVelocity = (baseThrow * (bVelocityMul + modThrow * mVelocityMul) - size * sVelocityMul) * toolMultiplier

        val bExhaustionMul = commonMul.exhaustion.baseExhaustionMultiplier
        val sExhaustionMul = commonMul.exhaustion.stackExhaustionMultiplier
        val mExhaustionMul = commonMul.exhaustion.modifiedExhaustionMultiplier
        val totalExhaustion = (baseExhaustion * (bExhaustionMul + modThrow * mExhaustionMul) + size * sExhaustionMul) * toolMultiplier

        return Triple(totalDamage, totalVelocity, totalExhaustion)
    }
}