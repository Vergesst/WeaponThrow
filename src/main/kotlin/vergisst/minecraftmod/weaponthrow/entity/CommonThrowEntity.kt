package vergisst.minecraftmod.weaponthrow.entity

import net.fabricmc.api.EnvType
import net.fabricmc.api.EnvironmentInterface
import net.minecraft.block.AnvilBlock
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.SandBlock
import net.minecraft.block.TorchBlock
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.EntityType
import net.minecraft.entity.FlyingItemEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.projectile.PersistentProjectileEntity
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtHelper
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import vergisst.minecraftmod.weaponthrow.config.WeaponThrowConfig
import vergisst.minecraftmod.weaponthrow.handler.ConfigRegistry
import vergisst.minecraftmod.weaponthrow.handler.EnchantmentHandler
import vergisst.minecraftmod.weaponthrow.handler.EntityRegistry
import kotlin.math.abs

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
    var lastState: BlockState? = null

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
        dataTracker.set(LOYALTY_LEVEL, getReturnOrLoyaltyEnchantment(thrownStackIn).toByte())
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
        val SHOULD_DESTROY: TrackedData<Boolean> =
            DataTracker.registerData<Boolean>(WeaponThrowEntity::class.java, TrackedDataHandlerRegistry.BOOLEAN)

        val enchantmentsConfig: WeaponThrowConfig.Enchantments = ConfigRegistry.COMMON.get().enchantments

        fun getReturnOrLoyaltyEnchantment(targetStack: ItemStack): Int {
            TODO("Not yet implemented")
        }
    }

    fun setItemStack(stack: ItemStack) = dataTracker.set(COMPOUND_STACK, stack.writeNbt(NbtCompound()))
    fun getItemStack(): ItemStack = ItemStack.fromNbt(dataTracker[COMPOUND_STACK])

    fun shouldDestroy(stack: Boolean) = dataTracker.set(SHOULD_DESTROY, stack)
    fun shouldDestroy(): Boolean = dataTracker[SHOULD_DESTROY]

    fun setDestroyedBlock(pos: BlockPos) = dataTracker.set(DESTROYED_BLOCK, pos)
    fun getDestroyedBlock() = dataTracker[DESTROYED_BLOCK]

    private val handleItemType = { weapon: CommonThrowEntity, target: LivingEntity ->
        val weaponItem = weapon.getItemStack().item
        if (weaponItem is BlockItem) {
            val blockItem = Block.getBlockFromItem(weaponItem)
            when (blockItem) {
                is SandBlock ->
                    if (target.random.nextInt(10) == 0)
                        target.addStatusEffect(StatusEffectInstance(StatusEffects.BLINDNESS, 60, 3))
                is TorchBlock ->
                    if (target.random.nextInt(5) == 0)
                        target.setOnFireFor(1)
                is AnvilBlock -> {
                    target.addStatusEffect(StatusEffectInstance(StatusEffects.NAUSEA, 60, 3));
                    target.addStatusEffect(StatusEffectInstance(StatusEffects.SLOWNESS, 60, 5));
                }
            }
        } else if(weaponItem == Items.BLAZE_ROD || weaponItem == Items.BLAZE_POWDER)
            target.setOnFireFor(1)
    }

    private val handleDamage = { entity: LivingEntity ->
        val contusionWorld = if (enchantmentsConfig.enableConccusion) EnchantmentHelper.getLevel(EnchantmentHandler.CONCCUSION, getItemStack()) else 0
        val groundedWorld = if (enchantmentsConfig.enableGroundedEdge) EnchantmentHelper.getLevel(EnchantmentHandler.GROUNDEDEDGE, getItemStack()) else 0
        val fireTime = EnchantmentHelper.getLevel(Enchantments.FIRE_ASPECT, getItemStack())

        if(contusionWorld > 0) {
            entity.addStatusEffect(StatusEffectInstance(StatusEffects.SLOWNESS, 20*2*contusionWorld, 5))
            entity.addStatusEffect(StatusEffectInstance(StatusEffects.NAUSEA, 20*2*contusionWorld, 5))
        }


        if (fireTime > 0 || groundedWorld > 0) {
            val nearEntities = world.getNonSpectatingEntities(LivingEntity::class.java, boundingBox.expand(1.0))
            if (nearEntities.isNotEmpty()) {
                for (nearEntity in nearEntities) {
                    if (nearEntity.random.nextInt(3) == 0) {
                        nearEntity.setOnFireFor(fireTime)
                    }
                    nearEntity.addStatusEffect(StatusEffectInstance(StatusEffects.WEAKNESS, 80, groundedWorld - 1))
                }
            }
        }
    }

    private val doInteractions = {action: Runnable ->
        val originStack = (owner as PlayerEntity).getStackInHand(Hand.MAIN_HAND)
        (owner as PlayerEntity).setStackInHand(Hand.MAIN_HAND, getItemStack())

        action.run()

        val newStack = (owner as PlayerEntity).getStackInHand(Hand.MAIN_HAND)
        if (!newStack.isEmpty) {
            (owner as PlayerEntity).setStackInHand(Hand.MAIN_HAND, originStack)
        } else {
            world.playSound(null, blockPos, SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.AMBIENT, 0.8F, 10F)
            spawnItemParticles(getItemStack())
            remove(RemovalReason.DISCARDED)
        }
    }

    private val spawnItemParticles = {stack: ItemStack ->
        val loyaltyLevel = EnchantmentHelper.getLevel(Enchantments.LOYALTY, stack)
        val returnLevel = EnchantmentHelper.getLevel(EnchantmentHandler.RETURN, stack)
        if(loyaltyLevel > 0) loyaltyLevel else returnLevel
    }

    override fun getHitSound(): SoundEvent = SoundEvents.BLOCK_METAL_HIT

    override fun onPlayerCollision(entityIn: PlayerEntity) {
        if (owner == null || owner?.uuid == entityIn.uuid || inGroundTime > ConfigRegistry.COMMON.get().times.ticksUntilWeaponLoseOwner)
            super.onPlayerCollision(entityIn)
    }

    override fun tick() {
        dealtDamage = (inGroundTime > 4 && !dealtDamage)

        if (getDestroyedBlock() != BlockPos.ZERO && !world.isClient) {
            this.setDestroyedBlock(BlockPos.ORIGIN)
        }
        // TODO do interactions is needed

        val gravityWorld = if (enchantmentsConfig.enableGravity) EnchantmentHelper.getLevel(EnchantmentHandler.GRAVITY, getItemStack()) else 0

        if (gravityWorld > 0) {
            setNoGravity(true)

            if(world.isOutOfHeightLimit(blockPos)) {
                velocity = velocity.multiply(1.0, 0.0, 1.0)
            }

            if(abs(velocity.x) < 0.1 && abs(velocity.z) < 0.1) {
                setNoGravity(false)
            }
        }
    }

    override fun onEntityHit(entityHitResult: EntityHitResult) {
        val entity = entityHitResult.entity
        var damage = attackDamage
        val damageSource = damageSources.thrown(this, owner ?: this)
        val sound = SoundEvents.ITEM_TRIDENT_HIT



        dealtDamage = true

        if (entity is LivingEntity) {
            damage += if (enchantmentsConfig.enableThrow) EnchantmentHelper.getLevel(EnchantmentHandler.THROW, getItemStack()) else 0
            damage += EnchantmentHelper.getAttackDamage(getItemStack(), entity.group)

            if (entity.damage(damageSource, damage)) {
                when(entity.type) {
                    EntityType.ENDERMAN -> return
                }

                handleDamage(entity)

                if (owner is LivingEntity) {
                    EnchantmentHelper.onUserDamaged(entity, owner)
                    EnchantmentHelper.onTargetDamaged(owner as LivingEntity, entity)
                }

                this.onHit(entity)
                handleItemType(this, entity)
            }
        }

        velocity = velocity.multiply(-0.01, -0.1, -0.01)
        playSound(sound, 1.0F, 1.0F)
    }

    override fun readCustomDataFromNbt(compound: NbtCompound) {
        super.readCustomDataFromNbt(compound)

        dealtDamage = compound.getBoolean("DealtDamage")

        if (compound.contains("Stack", 10))
            compound.put("inBlockState", NbtHelper.fromBlockState(lastState))

        dataTracker[LOYALTY_LEVEL] = getReturnOrLoyaltyEnchantment(getItemStack()).toByte()
    }

    override fun writeCustomDataToNbt(compound: NbtCompound) {
        super.writeCustomDataToNbt(compound)

        compound.put("Stack", dataTracker[COMPOUND_STACK])
        compound.putBoolean("DealtDamage", dealtDamage)
        if (lastState != null)
            compound.put("inBlockState", NbtHelper.fromBlockState(lastState))
    }

    override fun checkDespawn() {
        if (pickupType != PickupPermission.ALLOWED || dataTracker[LOYALTY_LEVEL]!! <= 0
            || inGroundTime >= ConfigRegistry.COMMON.get().times.despawnTime) {
            remove(RemovalReason.DISCARDED)
        }
    }

    override fun onCollision(hitResult: HitResult) {
        when (hitResult.type) {
            HitResult.Type.BLOCK -> {
                val stickPos = (hitResult as BlockHitResult).blockPos
                val state = world.getBlockState(stickPos)
                if (state.block != Blocks.BEDROCK && shouldDestroy()) {
                    val canBreak = ConfigRegistry.COMMON.get().interactions.canBreakBlocks
                    val canHarvest = getItemStack().isSuitableFor(state) && canBreak

                    if (canHarvest && !world.isClient && lastState == null) {
                        setDestroyedBlock(stickPos)
                    }
                }
            }

            HitResult.Type.ENTITY -> {
                val hitEntity = (hitResult as EntityHitResult).entity
                if (hitEntity is LivingEntity && owner is PlayerEntity) {
                    doInteractions {
                        (owner as PlayerEntity).attack(hitEntity)
                    }
                }
                onEntityHit(hitResult)
            }

            HitResult.Type.MISS -> {}
        }
    }

    override fun asItemStack(): ItemStack = getItemStack().copy()

    override fun getStack(): ItemStack = getItemStack()

    override fun initDataTracker() {
        super.initDataTracker()
        dataTracker.startTracking(COMPOUND_STACK, NbtCompound())
        dataTracker.startTracking(LOYALTY_LEVEL, 0)
        dataTracker.startTracking(DESTROYED_BLOCK, BlockPos.ORIGIN)
        dataTracker.startTracking(SHOULD_DESTROY, false)
    }

}

