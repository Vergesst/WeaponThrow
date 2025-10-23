package vergisst.minecraftmod.weaponthrow.capabilities

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.util.math.MathHelper
import vergisst.minecraftmod.weaponthrow.handler.ConfigRegistry
import vergisst.minecraftmod.weaponthrow.packets.State

class PlayerThrowData(val player: PlayerEntity) {
    // client
    var maxCharge = -1

    // both
    var action = State.NONE

    // Server
    var chargeTime = -1

    var item: ItemStack = ItemStack.EMPTY

    fun startCharging(stack: ItemStack) {
        item = stack.copy()
        chargeTime = getMaximumCharge(player)
    }

    fun resetCharging() {
        action = if(action == State.DURING) State.FINISH else action
        item = ItemStack.EMPTY
        chargeTime = -1
    }

    fun getChargingStack(): ItemStack = item

    companion object {
        fun getMaximumCharge(player: PlayerEntity): Int =
            MathHelper.floor(
                player
                    .attackCooldownProgressPerTick * ConfigRegistry
                    .COMMON
                    .get()
                    .times
                    .castTimeMultiplier
            )

    }
}