package vergisst.minecraftmod.weaponthrow.entity

import net.fabricmc.api.EnvType
import net.fabricmc.api.EnvironmentInterface
import net.minecraft.enchantment.Enchantment
import net.minecraft.entity.EntityType
import net.minecraft.entity.FlyingItemEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.entity.projectile.PersistentProjectileEntity
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import vergisst.minecraftmod.weaponthrow.handler.EntityRegistry

/**
 * You are son of bitch java, multi-constructor is just a rubbish in your api design
 * your brain just the same as MS's C# --- which is another rubbish
 * 你妈死了java
 */
@EnvironmentInterface(value = EnvType.CLIENT, itf = FlyingItemEntity::class)
class CommonThrowEntity : PersistentProjectileEntity, FlyingItemEntity {

    var attackDamage: Float = 0F
    var dealtDamage = false
    var returningTicks = 0
    var clientSideRotation = 0F
    var counterClockwiseBounce = true

    /**
     * @param
     */
    constructor(type: EntityType<out CommonThrowEntity>, worldIn: World) : super(type, worldIn)

    /**
     *
     */
    constructor(worldIn: World, thrower: LivingEntity, canDestroy: Boolean, attackDamage: Float, thrownStackIn: ItemStack)
        : super(EntityRegistry.COMMON_THROW_ENTITY, thrower, worldIn) {

        this.attackDamage = attackDamage
        dataTracker.set(COMPOUND_STACK, thrownStackIn.copy().writeNbt(NbtCompound()))
        dataTracker.set(LOYALTY_LEVEL, CommonThrowEntity.getReturnOrLoyaltyEnchantment(thrownStackIn).toByte())
    }

    constructor(worldIn: World, x: Double, y: Double, z: Double)
            : super(EntityRegistry.COMMON_THROW_ENTITY, x, y, z, worldIn)

    companion object {
        val LOYALTY_LEVEL: TrackedData<Byte?>? =
            DataTracker.registerData<Byte?>(WeaponThrowEntity::class.java, TrackedDataHandlerRegistry.BYTE)
        val COMPOUND_STACK: TrackedData<NbtCompound?>? = DataTracker.registerData<NbtCompound?>(
            WeaponThrowEntity::class.java,
            TrackedDataHandlerRegistry.NBT_COMPOUND
        )
        val DESTROYED_BLOCK: TrackedData<BlockPos?>? =
            DataTracker.registerData<BlockPos?>(WeaponThrowEntity::class.java, TrackedDataHandlerRegistry.BLOCK_POS)
        val SHOULD_DESTROY: TrackedData<Boolean?>? =
            DataTracker.registerData<Boolean?>(WeaponThrowEntity::class.java, TrackedDataHandlerRegistry.BOOLEAN)

        fun getReturnOrLoyaltyEnchantment(targetStack: ItemStack): Int {
            TODO("Not yet implemented")
        }
    }

    fun setItemStack(stack: ItemStack) = dataTracker.set(COMPOUND_STACK, stack.writeNbt(NbtCompound()))
    fun getItemStack() = ItemStack.fromNbt(dataTracker[COMPOUND_STACK])

    fun shouldDestroy(stack: Boolean) = dataTracker.set(SHOULD_DESTROY, stack)
    fun shouldDestroy() = dataTracker[SHOULD_DESTROY]

    fun setDestroyedBlock(pos: BlockPos) = dataTracker.set(DESTROYED_BLOCK, pos)
    fun getDestroyedBlock() = dataTracker[DESTROYED_BLOCK]

//    fun tick() {
//        if (inGroundTime > 4 && this.)
//    }

    override fun asItemStack(): ItemStack? {
        TODO("Not yet implemented")
    }

    override fun getStack(): ItemStack? {
        TODO("Not yet implemented")
    }

    override fun initDataTracker() {
        super.initDataTracker()
        dataTracker.startTracking(COMPOUND_STACK, NbtCompound())
        dataTracker.startTracking(LOYALTY_LEVEL, 0)
        dataTracker.startTracking(DESTROYED_BLOCK, BlockPos.ORIGIN)
        dataTracker.startTracking(SHOULD_DESTROY, false)
    }
}

