package vergisst.minecraftmod.weaponthrow.entity

import net.fabricmc.api.EnvType
import net.fabricmc.api.EnvironmentInterface
import net.minecraft.block.*
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
import net.minecraft.network.listener.ClientPlayPacketListener
import net.minecraft.network.packet.Packet
import net.minecraft.server.network.ServerPlayerEntity
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
 * Just a candidate to replace WeaponThrowEntity --- this is a more common entity for weapons except TRIDENT and HARMER
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
        val LOYALTY_LEVEL: TrackedData<Byte> =
            DataTracker.registerData<Byte>(CommonThrowEntity::class.java, TrackedDataHandlerRegistry.BYTE)

        val COMPOUND_STACK: TrackedData<NbtCompound> = DataTracker.registerData<NbtCompound>(
            CommonThrowEntity::class.java,
            TrackedDataHandlerRegistry.NBT_COMPOUND
        )

        val DESTROYED_BLOCK: TrackedData<BlockPos> =
            DataTracker.registerData<BlockPos>(CommonThrowEntity::class.java, TrackedDataHandlerRegistry.BLOCK_POS)

        val SHOULD_DESTROY: TrackedData<Boolean> =
            DataTracker.registerData<Boolean>(CommonThrowEntity::class.java, TrackedDataHandlerRegistry.BOOLEAN)

        val enchantmentsConfig: WeaponThrowConfig.Enchantments = ConfigRegistry.COMMON.get().enchantments

        fun getReturnOrLoyaltyEnchantment(targetStack: ItemStack): Int {
            val loyaltyLevel = EnchantmentHelper.getLevel(Enchantments.LOYALTY, targetStack)
            val returnLevel = EnchantmentHelper.getLevel(EnchantmentHandler.RETURN, targetStack)
            return if (loyaltyLevel > 0) loyaltyLevel else returnLevel
        }
    }

    fun setItemStack(stack: ItemStack) = dataTracker.set(COMPOUND_STACK, stack.writeNbt(NbtCompound()))
    fun getItemStack(): ItemStack = ItemStack.fromNbt(dataTracker[COMPOUND_STACK])

    fun shouldDestroy(stack: Boolean) = dataTracker.set(SHOULD_DESTROY, stack)
    fun shouldDestroy(): Boolean = dataTracker[SHOULD_DESTROY]

    fun setDestroyedBlock(pos: BlockPos) = dataTracker.set(DESTROYED_BLOCK, pos)
    fun getDestroyedBlock(): BlockPos = dataTracker[DESTROYED_BLOCK]

    private fun handleItemType(weapon: CommonThrowEntity, target: LivingEntity) {
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

    private fun handleDamage(entity: LivingEntity) {
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

    private fun doInteractions(action: Runnable) {
        val originStack = (owner as PlayerEntity).getStackInHand(Hand.MAIN_HAND)
        (owner as? PlayerEntity)?.setStackInHand(Hand.MAIN_HAND, getItemStack())

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

    private fun spawnItemParticles(stack: ItemStack) {
        val loyaltyLevel = EnchantmentHelper.getLevel(Enchantments.LOYALTY, stack)
        val returnLevel = EnchantmentHelper.getLevel(EnchantmentHandler.RETURN, stack)
        if(loyaltyLevel > 0) loyaltyLevel else returnLevel
    }

    private fun shouldReturnToOwner(): Boolean {
        val entity = owner
        return if (entity != null && entity.isAlive)
            (entity !is ServerPlayerEntity || !entity.isSpectator)
        else
            false
    }

    override fun getHitSound(): SoundEvent = SoundEvents.BLOCK_METAL_HIT

    override fun createSpawnPacket(): Packet<ClientPlayPacketListener?>? = super.createSpawnPacket()

    override fun onPlayerCollision(entityIn: PlayerEntity) {
        val entity = owner
        if (entity == null || entity.uuid == entityIn.uuid || inGroundTime > ConfigRegistry.COMMON.get().times.ticksUntilWeaponLoseOwner)
            super.onPlayerCollision(entityIn)
    }

    override fun tick() {
        dealtDamage = (inGroundTime > 4 && !dealtDamage)

        if (getDestroyedBlock() != BlockPos.ZERO && !world.isClient) {
            doInteractions {
                val event = world.getBlockState(getDestroyedBlock()).soundGroup.breakSound
                val destroyed = (owner as ServerPlayerEntity).interactionManager.tryBreakBlock(getDestroyedBlock())
                if (destroyed)
                    world.playSound(null, getDestroyedBlock(), event, SoundCategory.AMBIENT, 10F, 1.0F)
            }

            this.setDestroyedBlock(BlockPos.ORIGIN)
        }

        val gravityWorld = if (enchantmentsConfig.enableGravity)
                EnchantmentHelper.getLevel(EnchantmentHandler.GRAVITY, getItemStack())
            else 0

        if (gravityWorld > 0) {
            setNoGravity(true)

            if(world.isOutOfHeightLimit(blockPos)) {
                velocity = velocity.multiply(1.0, 0.0, 1.0)
            }

            if(abs(velocity.x) < 0.1 && abs(velocity.z) < 0.1) {
                setNoGravity(false)
            }
        }
        val i: Int = if (enchantmentsConfig.enableReturn) dataTracker[LOYALTY_LEVEL]!!.toInt() else 0

        val tempOwner=  owner
        if (i > 0 && (dealtDamage || isNoClip) && tempOwner != null) {
            if (shouldReturnToOwner()) {
                setNoClip(true)
                val vec3d = tempOwner.eyePos.subtract(pos)
                setPos(x, y + vec3d.y * 0.015 * i.toDouble(), z)
                if (world.isClient) {
                    lastRenderY = y
                }

                val d0 = 0.05 * i.toDouble()
                velocity = velocity.multiply(0.95).add(vec3d.normalize().multiply(d0))
                if (returningTicks == 0) {
                    playSound(SoundEvents.ITEM_TRIDENT_RETURN, 10.0F, 1.0F)
                }

                returningTicks++
            } else {
                if (!world.isClient && pickupType == PickupPermission.ALLOWED) {
                    dropStack(getItemStack(), 0.1F)
                }
                remove(RemovalReason.DISCARDED)
            }
        }
        super.tick()
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

    override fun onBlockHit(blockHitResult: BlockHitResult) {
        val vec3d = blockHitResult.pos.subtract(x, y, z)
        val anotherVec3d = vec3d.normalize().multiply(0.05)
        val event = if (getItemStack().item is BlockItem) {
            val block = Block.getBlockFromItem(getItemStack().item)
            block.defaultState.soundGroup.hitSound
        } else
            SoundEvents.ITEM_TRIDENT_HIT_GROUND

        lastState = world.getBlockState(blockHitResult.blockPos)
        velocity = vec3d
        inGround = true
        shake = 7
        isCritical = false
        pierceLevel = 0
        sound = event
        isShotFromCrossbow = false

        setPos(x - anotherVec3d.x, y - anotherVec3d.y, z - anotherVec3d.z)
        playSound(event, 1.0F, 1.2F / (random.nextFloat() * 0.2F + 0.9F))
        applyBounce()
    }

    fun applyBounce() {
        if (
            inGround && getItemStack().item is BlockItem &&
            abs(velocity.x) < 0.05 && abs(velocity.z) < 0.05
        ) {
            val vec3d = velocity.multiply(0.9)
            val landingPos = steppingPos

            if (!this.world.getBlockState(landingPos.down()).isAir || !this.world
                    .getBlockState(landingPos.up()).isAir
            ) {
                this.setVelocity(vec3d.x, -vec3d.y, vec3d.z)
            } else if (!this.world.getBlockState(landingPos.west()).isAir || !this.world
                    .getBlockState(landingPos.east()).isAir
            ) {
                this.setVelocity(-vec3d.x, vec3d.y, vec3d.z)
            } else if (!this.world.getBlockState(landingPos.north()).isAir || !this.world
                    .getBlockState(landingPos.south()).isAir
            ) {
                this.setVelocity(vec3d.x, vec3d.y, -vec3d.z)
            }

            counterClockwiseBounce = !counterClockwiseBounce
            inGround = false
        }
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
        if (pickupType != PickupPermission.ALLOWED || dataTracker[LOYALTY_LEVEL] <= 0
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

    fun getRotationAnimation(partialTicks: Float): Float {
        if (!inGround)
            clientSideRotation = (if (counterClockwiseBounce) 1 else -1)*(age + partialTicks)

        return clientSideRotation
    }

}

