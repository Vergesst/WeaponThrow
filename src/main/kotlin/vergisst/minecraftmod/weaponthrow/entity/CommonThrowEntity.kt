package vergisst.minecraftmod.weaponthrow.entity

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.api.EnvironmentInterface
import net.minecraft.block.AnvilBlock
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.ColoredFallingBlock
import net.minecraft.block.TorchBlock
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.Entity
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
import net.minecraft.particle.ItemStackParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import vergisst.minecraftmod.weaponthrow.handler.ConfigRegistry
import vergisst.minecraftmod.weaponthrow.handler.EnchantmentHandler
import vergisst.minecraftmod.weaponthrow.handler.EnchantmentHandler.GRAVITY
import vergisst.minecraftmod.weaponthrow.handler.EnchantmentHandler.RETURN
import vergisst.minecraftmod.weaponthrow.handler.EntityRegistry.COMMON_THROW_ENTITY
import java.util.*
import kotlin.math.abs

@EnvironmentInterface(value = EnvType.CLIENT, itf = FlyingItemEntity::class)
open class CommonThrowEntity : PersistentProjectileEntity, FlyingItemEntity {
    private var clientSideRotation = 0f
    private var counterClockwiseBounce = true
    private var dealtDamage = false
    private var attackDamage = 0f
    private var lastState: BlockState? = null
    var returningTicks: Int = 0

    constructor(type: EntityType<out CommonThrowEntity>, worldIn: World?) : super(type, worldIn, DEFAULT_STACK)

    constructor(
        worldIn: World?,
        thrower: LivingEntity,
        canDestroy: Boolean,
        attackDamage: Float,
        thrownStackIn: ItemStack
    ) : super(
        COMMON_THROW_ENTITY, thrower, worldIn, thrownStackIn
    ) {
        this.attackDamage = attackDamage
        dataTracker.set<NbtCompound?>(COMPOUND_STACK, thrownStackIn.copy().writeNbt(NbtCompound()))
        dataTracker.set<Byte>(LOYALTY_LEVEL, getReturnOrLoyaltyEnchantment(thrownStackIn).toByte())
        dataTracker.set<BlockPos>(DESTROYED_BLOCK, BlockPos.ORIGIN)
        dataTracker.set<Boolean>(SHOULD_DESTROY, canDestroy)
    }

    constructor(worldIn: World?, x: Double, y: Double, z: Double, item: ItemStack) : super(COMMON_THROW_ENTITY, x, y, z, worldIn, item)

    companion object {
        private val DEFAULT_STACK = ItemStack(Items.STONE_AXE)

        private val LOYALTY_LEVEL: TrackedData<Byte> =
            DataTracker.registerData<Byte>(CommonThrowEntity::class.java, TrackedDataHandlerRegistry.BYTE)

        private val COMPOUND_STACK: TrackedData<NbtCompound> = DataTracker.registerData<NbtCompound>(
            CommonThrowEntity::class.java,
            TrackedDataHandlerRegistry.NBT_COMPOUND
        )

        private val DESTROYED_BLOCK: TrackedData<BlockPos> =
            DataTracker.registerData<BlockPos>(CommonThrowEntity::class.java, TrackedDataHandlerRegistry.BLOCK_POS)

        private val SHOULD_DESTROY: TrackedData<Boolean> =
            DataTracker.registerData<Boolean>(CommonThrowEntity::class.java, TrackedDataHandlerRegistry.BOOLEAN)

        private val enchantmentsConfig = ConfigRegistry.COMMON.get().enchantments

        fun getReturnOrLoyaltyEnchantment(stack: ItemStack): Int {
            val loyaltyLevel = EnchantmentHelper.getLevel(Enchantments.LOYALTY, stack)
            val returnLevel = EnchantmentHelper.getLevel(RETURN, stack)
            return if (loyaltyLevel > 0) loyaltyLevel else returnLevel
        }
    }

    /**
     * getters and setters with properties
     */
    var localStack: ItemStack
        get() = ItemStack.fromNbt(dataTracker[COMPOUND_STACK])
        set(stack) =
            dataTracker.set(COMPOUND_STACK, stack.writeNbt(NbtCompound()))

    fun shouldDestroy(stack: Boolean) = dataTracker.set(SHOULD_DESTROY, stack)


    fun shouldDestroy(): Boolean = getDataTracker().get(SHOULD_DESTROY)

    var destroyedBlock: BlockPos
        get() = dataTracker[DESTROYED_BLOCK]
        set(pos) =
            getDataTracker().set(DESTROYED_BLOCK, pos)

    /**
     * Private helper functions here
     */
    private fun handleItemType(weapon: CommonThrowEntity, target: LivingEntity) {
        val weaponItem = weapon.localStack.item
        if (weaponItem is BlockItem) {
            val blockItem = Block.getBlockFromItem(weaponItem)
            when (blockItem) {
                is ColoredFallingBlock ->
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
        val contusionWorld = if (enchantmentsConfig.enableConccusion) EnchantmentHelper.getLevel(EnchantmentHandler.CONCCUSION, localStack) else 0
        val groundedWorld = if (enchantmentsConfig.enableGroundedEdge) EnchantmentHelper.getLevel(EnchantmentHandler.GROUNDEDEDGE, localStack) else 0
        val fireTime = EnchantmentHelper.getLevel(Enchantments.FIRE_ASPECT, localStack)

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

    fun doInteractions(action: Runnable) {
        val originalStack =
            (Objects.requireNonNull<Entity?>(owner) as PlayerEntity).getStackInHand(Hand.MAIN_HAND)

        (owner as PlayerEntity).setStackInHand(Hand.MAIN_HAND, localStack)

        action.run()

        val newStack = (owner as PlayerEntity).getStackInHand(Hand.MAIN_HAND)

        if (!newStack.isEmpty) {
            (owner as PlayerEntity).setStackInHand(Hand.MAIN_HAND, originalStack)
        } else {
            world
                .playSound(null, blockPos, SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.AMBIENT, 0.8f, 10f)

            spawnItemParticles(localStack)

            remove(RemovalReason.DISCARDED)
        }
    }

    private fun shouldReturnToThrower(): Boolean {
        val entity = owner

        return if (entity != null && entity.isAlive) {
            entity !is ServerPlayerEntity || !entity.isSpectator
        } else {
            false
        }
    }

    override fun initDataTracker() {
        super.initDataTracker()
        dataTracker.startTracking(COMPOUND_STACK, NbtCompound())
        dataTracker.startTracking(LOYALTY_LEVEL, 0.toByte())
        dataTracker.startTracking(DESTROYED_BLOCK, BlockPos.ORIGIN)
        dataTracker.startTracking(SHOULD_DESTROY, false)
    }

    override fun tick() {
        dealtDamage = (inGroundTime > 4 && !dealtDamage)

        if (destroyedBlock != BlockPos.ZERO && !world.isClient) {
            doInteractions{
                val event = world.getBlockState(destroyedBlock).soundGroup.breakSound
                val destroyed = (Objects.requireNonNull<Entity>(owner) as ServerPlayerEntity).interactionManager.tryBreakBlock(destroyedBlock)
                if (destroyed)
                    world.playSound(null, destroyedBlock, event, SoundCategory.AMBIENT, 10f, 1.0f)
            }

            destroyedBlock = BlockPos.ORIGIN
        }

        val gravityWorld = if (enchantmentsConfig.enableGravity) EnchantmentHelper.getLevel(GRAVITY, localStack) else 0
        if (gravityWorld > 0) {
            setNoGravity(true)
            if (world.isOutOfHeightLimit(blockPos))
                velocity = velocity.multiply(1.0, 0.0, 1.0)

            if ((abs(velocity.x) < 0.1 && abs(velocity.z) < 0.1))
                setNoGravity(false)
        }

        val entity = owner
        val i = if (enchantmentsConfig.enableReturn) dataTracker.get(LOYALTY_LEVEL).toInt() else 0

        if (i > 0 && (dealtDamage || isNoClip()) && entity != null) {
            if (!shouldReturnToThrower()) {
                // reminder -- check for twice
                if (!world.isClient && pickupType == PickupPermission.ALLOWED) {
                    dropStack(localStack, 0.1f)
                }

                remove(RemovalReason.DISCARDED)
            } else {
                setNoClip(true)
                val vec3d = entity.eyePos.subtract(pos)
                setPos(x, y + vec3d.y * 0.015 * i.toDouble(), z)
                if (world.isClient) {
                    lastRenderY = y
                }

                val d0 = 0.05 * i.toDouble()
                velocity = velocity.multiply(0.95).add(vec3d.normalize().multiply(d0))
                if (returningTicks == 0) {
                    playSound(SoundEvents.ITEM_TRIDENT_RETURN, 10.0f, 1.0f)
                }

                ++returningTicks
            }
        }
        super.tick()
    }

    override fun asItemStack(): ItemStack? {
        return localStack.copy()
    }


    override fun getEntityCollision(vec3d: Vec3d, anotherVec3d: Vec3d): EntityHitResult? {
        return if (dealtDamage) null else super.getEntityCollision(vec3d, anotherVec3d)
    }

    override fun onEntityHit(entityHitResult: EntityHitResult) {
        val entity = entityHitResult.entity
        var damage = attackDamage
        val damageSource = damageSources.thrown(this, owner ?: this)
        var sound = SoundEvents.ITEM_TRIDENT_HIT
        if (localStack.item is BlockItem) {
            val block = Block.getBlockFromItem(localStack.item)
            sound = block.defaultState.soundGroup.hitSound
        }

        dealtDamage = true

        if (entity is LivingEntity) {
            damage += if (enchantmentsConfig.enableThrow) EnchantmentHelper.getLevel(EnchantmentHandler.THROW, localStack) else 0
            damage += EnchantmentHelper.getAttackDamage(localStack, entity.group)

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
        lastState = world.getBlockState(blockHitResult.blockPos)
        val vec3d = blockHitResult.getPos().subtract(x, y, z)
        velocity = vec3d
        val vec3d1 = vec3d.normalize().multiply(0.05)
        setPos(x - vec3d1.x, y - vec3d1.y, z - vec3d1.z)

        var event = SoundEvents.ITEM_TRIDENT_HIT_GROUND
        if (localStack.item is BlockItem) {
            val block = Block.getBlockFromItem(localStack.item)
            event = block.defaultState.soundGroup.hitSound
        }

        playSound(event, 1.0f, 1.2f / (random.nextFloat() * 0.2f + 0.9f))
        inGround = true
        shake = 7

        isCritical = false
        pierceLevel = 0.toByte()
        sound = event
        isShotFromCrossbow = false

        applyBounce()
    }

    fun applyBounce() {
        if (inGround && localStack.item is BlockItem) {
            if (!(abs(velocity.x) < 0.05 && abs(velocity.z) < 0.05)) {
                val vec3 = velocity.multiply(0.9)
                val landingPos = steppingPos

                if (!world.getBlockState(landingPos.down()).isAir || !world
                        .getBlockState(landingPos.up()).isAir
                ) {
                    setVelocity(vec3.x, -vec3.y, vec3.z)
                } else if (!world.getBlockState(landingPos.west()).isAir || !world
                        .getBlockState(landingPos.east()).isAir
                ) {
                    setVelocity(-vec3.x, vec3.y, vec3.z)
                } else if (!world.getBlockState(landingPos.north()).isAir || !world
                        .getBlockState(landingPos.south()).isAir
                ) {
                    setVelocity(vec3.x, vec3.y, -vec3.z)
                }
                counterClockwiseBounce = !counterClockwiseBounce
                inGround = false
            }
        }
    }


    override fun getHitSound(): SoundEvent? {
        return SoundEvents.BLOCK_METAL_HIT
    }

    override fun onPlayerCollision(entityIn: PlayerEntity) {
        val entity = getOwner()

        if (entity == null || entity.getUuid() === entityIn.getUuid() || inGroundTime > (ConfigRegistry.COMMON.get().times.ticksUntilWeaponLoseOwner)) {
            super.onPlayerCollision(entityIn)
        }
    }


    override fun readCustomDataFromNbt(compound: NbtCompound) {
        super.readCustomDataFromNbt(compound)

        dealtDamage = compound.getBoolean("DealtDamage")

        if (compound.contains("Stack", 10)) {
            localStack = ItemStack.fromNbt(compound.getCompound("Stack"))
        }

        dataTracker.set(
            LOYALTY_LEVEL, getReturnOrLoyaltyEnchantment(
                localStack
            ).toByte()
        )
    }

    override fun writeCustomDataToNbt(compound: NbtCompound) {
        super.writeCustomDataToNbt(compound)
        compound.put("Stack", getDataTracker().get<NbtCompound?>(COMPOUND_STACK))
        compound.putBoolean("DealtDamage", dealtDamage)
        if (lastState != null) {
            compound.put("inBlockState", NbtHelper.fromBlockState(lastState))
        }
    }


    override fun checkDespawn() {
        val i = dataTracker.get<Byte>(LOYALTY_LEVEL).toInt()
        if (pickupType != PickupPermission.ALLOWED || i <= 0) {
            if (inGroundTime >= ConfigRegistry.COMMON.get().times.despawnTime) {
                remove(RemovalReason.DISCARDED)
            }
        }
    }

    override fun onCollision(result: HitResult) {
        val `raytraceresult$type` = result.type
        if (`raytraceresult$type` == HitResult.Type.ENTITY) {
            val hittedEntity = (result as EntityHitResult).entity
            if (hittedEntity is LivingEntity && getOwner() is PlayerEntity) {
                doInteractions(Runnable {
                    (getOwner() as PlayerEntity).attack(hittedEntity)
                })
            }
            onEntityHit(result)
        } else if (`raytraceresult$type` == HitResult.Type.BLOCK) {
            val stickBlockPos = (result as BlockHitResult).getBlockPos()
            val state = world.getBlockState(stickBlockPos)
            if (state.block != Blocks.BEDROCK && shouldDestroy()) {
                val canBreak = ConfigRegistry.COMMON.get().interactions.canBreakBlocks

                val canHarvest = localStack.isSuitableFor(state) && canBreak
                if (canHarvest) {
                    if (!world.isClient && lastState == null) {
                        destroyedBlock = stickBlockPos
                    }
                }
            }
            onBlockHit(result)
        }
    }

    override fun getStack(): ItemStack? {
        return localStack
    }

    override fun createSpawnPacket(): Packet<ClientPlayPacketListener?>? {
        return super.createSpawnPacket()
    }

    private fun spawnItemParticles(stack: ItemStack?) {
        for (i in 0..4) {
            var vec3d = Vec3d((random.nextFloat().toDouble() - 0.5) * 0.1, Math.random() * 0.1 + 0.1, 0.0)
            vec3d = vec3d.rotateX(-pitch * 0.017453292f)
            vec3d = vec3d.rotateY(-yaw * 0.017453292f)
            val d = (-random.nextFloat()).toDouble() * 0.6 - 0.3
            var vec3d2 = Vec3d((random.nextFloat().toDouble() - 0.5) * 0.3, d, 0.6)
            vec3d2 = vec3d2.rotateX(-pitch * 0.017453292f)
            vec3d2 = vec3d2.rotateY(-yaw * 0.017453292f)
            vec3d2 = vec3d2.add(x, getEyeY(), z)
            if (world is ServerWorld)  //Forge: Fix MC-2518 spawnParticle is nooped on server, need to use server specific variant
                (world as ServerWorld).spawnParticles (
                    ItemStackParticleEffect(
                        ParticleTypes.ITEM,
                        stack
                    ), vec3d2.x, vec3d2.y, vec3d2.z, 1, vec3d.x, vec3d.y + 0.05, vec3d.z, 0.0
                )
            else world.addParticle(
                ItemStackParticleEffect(ParticleTypes.ITEM, stack),
                vec3d2.x,
                vec3d2.y,
                vec3d2.z,
                vec3d.x,
                vec3d.y + 0.05,
                vec3d.z
            )
        }
    }

    @Environment(EnvType.CLIENT)
    fun getRotationAnimation(partialTicks: Float): Float {
        if (!inGround) {
            clientSideRotation = (if (counterClockwiseBounce) 1 else -1) * (age + partialTicks) * 50f
        }
        return clientSideRotation
    }
}
