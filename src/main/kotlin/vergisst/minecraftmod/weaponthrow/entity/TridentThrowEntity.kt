package vergisst.minecraftmod.weaponthrow.entity

import net.minecraft.entity.EntityType
import net.minecraft.entity.FlyingItemEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.projectile.PersistentProjectileEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.world.World

/**
 * discarded item
 */
class TridentThrowEntity : CommonThrowEntity {
    companion object {
        val DEFAULT_STACK = ItemStack(Items.TRIDENT)
    }

    constructor(type: EntityType<out TridentThrowEntity>, worldIn: World) : super(type, worldIn)

    constructor(worldIn: World, thrower: LivingEntity, damage: Float): super(worldIn, thrower, false, damage, DEFAULT_STACK)

    override fun tick() {}
}