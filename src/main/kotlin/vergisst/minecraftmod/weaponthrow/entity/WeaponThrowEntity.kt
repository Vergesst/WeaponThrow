package vergisst.minecraftmod.weaponthrow.entity

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.api.EnvironmentInterface
import net.minecraft.block.AnvilBlock
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.SandBlock
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
import vergisst.minecraftmod.weaponthrow.handler.EnchantmentHandler.CONCCUSION
import vergisst.minecraftmod.weaponthrow.handler.EnchantmentHandler.GRAVITY
import vergisst.minecraftmod.weaponthrow.handler.EnchantmentHandler.GROUNDEDEDGE
import vergisst.minecraftmod.weaponthrow.handler.EnchantmentHandler.RETURN
import vergisst.minecraftmod.weaponthrow.handler.EnchantmentHandler.THROW
import vergisst.minecraftmod.weaponthrow.handler.EntityRegistry.WEAPON_THROW
import java.util.*
import kotlin.math.abs

@EnvironmentInterface(value = EnvType.CLIENT, itf = FlyingItemEntity::class)
class WeaponThrowEntity : PersistentProjectileEntity, FlyingItemEntity {
    private var clientSideRotation = 0f
    private var counterClockwiseBounce = true

    private var dealtDamage = false
    private var attackDamage = 0f
    var returningTicks: Int = 0

    private var lastState: BlockState? = null

    constructor(type: EntityType<out WeaponThrowEntity>, worldIn: World?) : super(type, worldIn)

    constructor(
        worldIn: World?,
        thrower: LivingEntity,
        canDestroy: Boolean,
        attackDamage: Float,
        thrownStackIn: ItemStack
    ) : super(
        WEAPON_THROW, thrower, worldIn
    ) {
        this.attackDamage = attackDamage
        this.dataTracker.set<NbtCompound?>(COMPOUND_STACK, thrownStackIn.copy().writeNbt(NbtCompound()))
        this.dataTracker.set<Byte?>(LOYALTY_LEVEL, getReturnOrLoyaltyEnchantment(thrownStackIn).toByte())
        this.dataTracker.set<BlockPos?>(DESTROYED_BLOCK, BlockPos.ORIGIN)
        this.dataTracker.set<BlockPos?>(DESTROYED_BLOCK, BlockPos.ORIGIN)
    }

    constructor(worldIn: World?, x: Double, y: Double, z: Double) : super(WEAPON_THROW, x, y, z, worldIn)

    override fun initDataTracker() {
        super.initDataTracker()
        this.dataTracker.startTracking<NbtCompound?>(COMPOUND_STACK, NbtCompound())
        this.dataTracker.startTracking<Byte?>(LOYALTY_LEVEL, 0.toByte())
        this.dataTracker.startTracking<BlockPos?>(DESTROYED_BLOCK, BlockPos.ORIGIN)
        this.dataTracker.startTracking<Boolean?>(SHOULD_DESTROY, false)
    }

    var itemStack: ItemStack
        get() = ItemStack.fromNbt(this.getDataTracker().get<NbtCompound?>(COMPOUND_STACK))
        set(stack) {
            this.getDataTracker().set<NbtCompound?>(COMPOUND_STACK, stack!!.writeNbt(NbtCompound()))
        }

    fun shouldDestroy(stack: Boolean) {
        this.getDataTracker().set<Boolean?>(SHOULD_DESTROY, stack)
    }

    fun shouldDestroy(): Boolean {
        return this.getDataTracker().get<Boolean>(SHOULD_DESTROY)
    }

    var destroyedBlock: BlockPos?
        get() = this.getDataTracker().get<BlockPos?>(DESTROYED_BLOCK)
        set(pos) {
            this.getDataTracker().set<BlockPos?>(DESTROYED_BLOCK, pos)
        }


    override fun tick() {
        if (this.inGroundTime > 4 && !this.dealtDamage) {
            this.dealtDamage = true
        }

        if (this.destroyedBlock != BlockPos.ZERO && !this.world.isClient) {
            this.doInteractions(Runnable {
                val event = this.world.getBlockState(this.destroyedBlock).soundGroup.breakSound
                val destroyed =
                    (Objects.requireNonNull<Entity?>(this.getOwner()) as ServerPlayerEntity).interactionManager.tryBreakBlock(
                        this.destroyedBlock
                    )
                if (destroyed) {
                    this.world.playSound(null, this.destroyedBlock, event, SoundCategory.AMBIENT, 10f, 1.0f)
                }
            })
            this.destroyedBlock = BlockPos.ORIGIN
        }

        val gravityWorld = if (ConfigRegistry.COMMON.get().enchantments.enableGravity) EnchantmentHelper.getLevel(
            GRAVITY,
            this.itemStack
        ) else 0
        if (gravityWorld > 0) {
            this.setNoGravity(true)
            if (this.world.isOutOfHeightLimit(this.blockPos)) {
                this.velocity = this.velocity.multiply(1.0, 0.0, 1.0)
            }
            if ((abs(this.velocity.x) < 0.1 && abs(this.velocity.z) < 0.1)) {
                this.setNoGravity(false)
            }
        }

        val entity = this.getOwner()

        val i = if (ConfigRegistry.COMMON.get().enchantments.enableReturn) this.dataTracker.get<Byte>(LOYALTY_LEVEL)
            .toInt() else 0

        if (i > 0 && (this.dealtDamage || this.isNoClip()) && entity != null) {
            if (!this.shouldReturnToThrower()) {
                // reminder -- check for twice
                if (!this.world.isClient && this.pickupType == PickupPermission.ALLOWED) {
                    this.dropStack(this.itemStack, 0.1f)
                }

                this.remove(RemovalReason.DISCARDED)
            } else {
                this.setNoClip(true)
                val vec3d = entity.eyePos.subtract(this.pos)
                this.setPos(this.x, this.y + vec3d.y * 0.015 * i.toDouble(), this.z)
                if (this.world.isClient) {
                    this.lastRenderY = this.y
                }

                val d0 = 0.05 * i.toDouble()
                velocity = velocity.multiply(0.95).add(vec3d.normalize().multiply(d0))
                if (this.returningTicks == 0) {
                    this.playSound(SoundEvents.ITEM_TRIDENT_RETURN, 10.0f, 1.0f)
                }

                ++this.returningTicks
            }
        }
        super.tick()
    }

    private fun shouldReturnToThrower(): Boolean {
        val entity = this.getOwner()

        return if (entity != null && entity.isAlive) {
            entity !is ServerPlayerEntity || !entity.isSpectator
        } else {
            false
        }
    }

    override fun asItemStack(): ItemStack? {
        return this.itemStack.copy()
    }


    override fun getEntityCollision(vec3d: Vec3d, anotherVec3d: Vec3d): EntityHitResult? {
        return if (this.dealtDamage) null else super.getEntityCollision(vec3d, anotherVec3d)
    }

    override fun onEntityHit(entityHitResult: EntityHitResult) {
        val entity = entityHitResult.entity
        var f = this.attackDamage
        if (entity is LivingEntity) {
            f += if (ConfigRegistry.COMMON.get().enchantments.enableThrow) EnchantmentHelper.getLevel(
                THROW,
                this.itemStack
            ) * 1f else 0f
            f += EnchantmentHelper.getAttackDamage(this.itemStack, entity.group)
        }

        val entity1 = this.getOwner()
        //	      DamageSource damagesource = DamageSource.thrownProjectile(this, entity1 == null ? this : entity1);
        val damageSource = this.damageSources.thrown(this, entity1 ?: this)



        this.dealtDamage = true
        val soundEvent = SoundEvents.ITEM_TRIDENT_HIT

        if (entity.damage(damageSource, f)) {
            if (entity.type === EntityType.ENDERMAN) {
                return
            }

            if (entity is LivingEntity) {
                val contusionWorld =
                    if (ConfigRegistry.COMMON.get().enchantments.enableConccusion) EnchantmentHelper.getLevel(
                        CONCCUSION,
                        this.itemStack
                    ) else 0

                if (contusionWorld > 0) {
                    entity.addStatusEffect(StatusEffectInstance(StatusEffects.SLOWNESS, 20 * 2 * contusionWorld, 5))
                    entity.addStatusEffect(StatusEffectInstance(StatusEffects.NAUSEA, 20 * 5 * contusionWorld, 3))
                }

                val fireTime = EnchantmentHelper.getLevel(
                    Enchantments.FIRE_ASPECT,
                    this.itemStack
                )
                val groundedWorld =
                    if (ConfigRegistry.COMMON.get().enchantments.enableGroundedEdge) EnchantmentHelper.getLevel(
                        GROUNDEDEDGE,
                        this.itemStack
                    ) else 0

                if (fireTime > 0 || groundedWorld > 0) {
                    val nearEntities = this.world.getNonSpectatingEntities<LivingEntity?>(
                        LivingEntity::class.java,
                        this.getBoundingBox().expand(1.0)
                    )

                    if (!nearEntities.isEmpty()) {
                        for (nearEntity in nearEntities) {
                            if (nearEntity != null) {
                                if (nearEntity.getRandom().nextInt(3) == 0) {
                                    nearEntity.setOnFireFor(fireTime)
                                }
                            }
                            nearEntity?.addStatusEffect(
                                StatusEffectInstance(
                                    StatusEffects.WEAKNESS,
                                    80,
                                    groundedWorld - 1
                                )
                            )
                        }
                    }
                }

                if (entity1 is LivingEntity) {
                    EnchantmentHelper.onUserDamaged(entity, entity1)
                    EnchantmentHelper.onTargetDamaged(entity1, entity)
                }

                this.onHit(entity)

                if (this.itemStack.item is BlockItem) {
                    val blockItem = Block.getBlockFromItem(this.itemStack.item)
                    when (blockItem) {
                        is SandBlock -> {
                            if (entity.getRandom().nextInt(10) == 0) entity.addStatusEffect(
                                StatusEffectInstance(
                                    StatusEffects.BLINDNESS,
                                    60,
                                    3
                                )
                            )
                        }

                        is TorchBlock -> {
                            if (entity.getRandom().nextInt(5) == 0) entity.setOnFireFor(1)
                        }

                        is AnvilBlock -> {
                            entity.addStatusEffect(StatusEffectInstance(StatusEffects.NAUSEA, 60, 3))
                            entity.addStatusEffect(StatusEffectInstance(StatusEffects.SLOWNESS, 60, 5))
                        }

                        else -> {}
                    }
                } else {
                    val itemThrown = this.itemStack.item
                    if (itemThrown == Items.BLAZE_ROD || itemThrown == Items.BLAZE_POWDER) {
                        entity.setOnFireFor(1)
                    }
                }
            }
        }

        this.setVelocity(this.velocity.multiply(-0.01, -0.1, -0.01))

        this.playSound(soundEvent, 1.0f, 1.0f)
    }


    override fun getHitSound(): SoundEvent? {
        return SoundEvents.BLOCK_METAL_HIT
    }


    override fun onPlayerCollision(entityIn: PlayerEntity) {
        val entity = this.getOwner()

        if (entity == null || entity.getUuid() === entityIn.getUuid() || this.inGroundTime > (ConfigRegistry.COMMON.get().times.ticksUntilWeaponLoseOwner)) {
            super.onPlayerCollision(entityIn)
        }
    }

    override fun readCustomDataFromNbt(compound: NbtCompound) {
        super.readCustomDataFromNbt(compound)

        this.dealtDamage = compound.getBoolean("DealtDamage")

        if (compound.contains("Stack", 10)) {
            this.itemStack = ItemStack.fromNbt(compound.getCompound("Stack"))
        }

        this.dataTracker.set<Byte?>(
            LOYALTY_LEVEL, Companion.getReturnOrLoyaltyEnchantment(
                this.itemStack
            ).toByte()
        )
    }


    override fun writeCustomDataToNbt(compound: NbtCompound) {
        super.writeCustomDataToNbt(compound)
        compound.put("Stack", this.getDataTracker().get<NbtCompound?>(COMPOUND_STACK))
        compound.putBoolean("DealtDamage", this.dealtDamage)
        if (this.lastState != null) {
            compound.put("inBlockState", NbtHelper.fromBlockState(this.lastState))
        }
    }

    override fun checkDespawn() {
        val i = this.dataTracker.get<Byte>(LOYALTY_LEVEL).toInt()
        if (this.pickupType != PickupPermission.ALLOWED || i <= 0) {
            if (this.inGroundTime >= ConfigRegistry.COMMON.get().times.despawnTime) {
                this.remove(RemovalReason.DISCARDED)
            }
        }
    }


    override fun onCollision(result: HitResult) {
        val `raytraceresult$type` = result.type
        if (`raytraceresult$type` == HitResult.Type.ENTITY) {
            val hittedEntity = (result as EntityHitResult).entity
            if (hittedEntity is LivingEntity && this.getOwner() is PlayerEntity) {
                this.doInteractions(Runnable {
                    (this.getOwner() as PlayerEntity).attack(hittedEntity)
                })
            }
            this.onEntityHit(result)
        } else if (`raytraceresult$type` == HitResult.Type.BLOCK) {
            val stickBlockPos = (result as BlockHitResult).getBlockPos()
            val state = this.world.getBlockState(stickBlockPos)
            if (state.block != Blocks.BEDROCK && this.shouldDestroy()) {
                val canBreak = ConfigRegistry.COMMON.get().interactions.canBreakBlocks

                val canHarvest = this.itemStack.isSuitableFor(state) && canBreak
                if (canHarvest) {
                    if (!this.world.isClient && this.lastState == null) {
                        this.destroyedBlock = stickBlockPos
                    }
                }
            }
            this.onBlockHit(result)
        }
    }

    override fun onBlockHit(blockHitResult: BlockHitResult) {
        this.lastState = this.world.getBlockState(blockHitResult.blockPos)
        val vec3d = blockHitResult.getPos().subtract(this.x, this.y, this.z)
        this.velocity = vec3d
        val vec3d1 = vec3d.normalize().multiply(0.05)
        this.setPos(this.x - vec3d1.x, this.y - vec3d1.y, this.z - vec3d1.z)

        var event = SoundEvents.ITEM_TRIDENT_HIT_GROUND
        if (this.itemStack.item is BlockItem) {
            val block = Block.getBlockFromItem(this.itemStack.item)
            event = block.defaultState.soundGroup.hitSound
        }

        this.playSound(event, 1.0f, 1.2f / (this.random.nextFloat() * 0.2f + 0.9f))
        this.inGround = true
        this.shake = 7

        this.isCritical = false
        this.pierceLevel = 0.toByte()
        this.sound = event
        this.isShotFromCrossbow = false

        this.applyBounce()
    }

    fun applyBounce() {
        if (this.inGround && this.itemStack.item is BlockItem) {
            if (!(abs(this.velocity.x) < 0.05 && abs(this.velocity.z) < 0.05)) {
                val vec3 = this.velocity.multiply(0.9)
                val landingPos = steppingPos

                if (!this.world.getBlockState(landingPos.down()).isAir || !this.world
                        .getBlockState(landingPos.up()).isAir
                ) {
                    this.setVelocity(vec3.x, -vec3.y, vec3.z)
                } else if (!this.world.getBlockState(landingPos.west()).isAir || !this.world
                        .getBlockState(landingPos.east()).isAir
                ) {
                    this.setVelocity(-vec3.x, vec3.y, vec3.z)
                } else if (!this.world.getBlockState(landingPos.north()).isAir || !this.world
                        .getBlockState(landingPos.south()).isAir
                ) {
                    this.setVelocity(vec3.x, vec3.y, -vec3.z)
                }
                this.counterClockwiseBounce = !this.counterClockwiseBounce
                this.inGround = false
            }
        }
    }

    override fun getStack(): ItemStack? {
        return this.itemStack
    }

    override fun createSpawnPacket(): Packet<ClientPlayPacketListener?>? {
        return super.createSpawnPacket()
    }

    fun doInteractions(action: Runnable) {
        val originalStack =
            (Objects.requireNonNull<Entity?>(this.getOwner()) as PlayerEntity).getStackInHand(Hand.MAIN_HAND)

        (this.getOwner() as PlayerEntity).setStackInHand(Hand.MAIN_HAND, this.itemStack)

        action.run()

        val newStack = (this.getOwner() as PlayerEntity).getStackInHand(Hand.MAIN_HAND)

        if (!newStack.isEmpty) {
            (this.getOwner() as PlayerEntity).setStackInHand(Hand.MAIN_HAND, originalStack)
        } else {
            this.world
                .playSound(null, this.getBlockPos(), SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.AMBIENT, 0.8f, 10f)

            this.spawnItemParticles(this.itemStack)

            this.remove(RemovalReason.DISCARDED)
        }
    }

    private fun spawnItemParticles(stack: ItemStack?) {
        for (i in 0..4) {
            var vec3d = Vec3d((this.random.nextFloat().toDouble() - 0.5) * 0.1, Math.random() * 0.1 + 0.1, 0.0)
            vec3d = vec3d.rotateX(-this.pitch * 0.017453292f)
            vec3d = vec3d.rotateY(-this.yaw * 0.017453292f)
            val d = (-this.random.nextFloat()).toDouble() * 0.6 - 0.3
            var vec3d2 = Vec3d((this.random.nextFloat().toDouble() - 0.5) * 0.3, d, 0.6)
            vec3d2 = vec3d2.rotateX(-this.pitch * 0.017453292f)
            vec3d2 = vec3d2.rotateY(-this.yaw * 0.017453292f)
            vec3d2 = vec3d2.add(this.x, this.getEyeY(), this.z)
            if (this.world is ServerWorld)  //Forge: Fix MC-2518 spawnParticle is nooped on server, need to use server specific variant
                (this.world as ServerWorld).spawnParticles<ItemStackParticleEffect?>(
                    ItemStackParticleEffect(
                        ParticleTypes.ITEM,
                        stack
                    ), vec3d2.x, vec3d2.y, vec3d2.z, 1, vec3d.x, vec3d.y + 0.05, vec3d.z, 0.0
                )
            else this.world.addParticle(
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
        if (!this.inGround) {
            clientSideRotation = (if (this.counterClockwiseBounce) 1 else -1) * (this.age + partialTicks) * 50f
        }
        return this.clientSideRotation
    }

    companion object {
        private val LOYALTY_LEVEL: TrackedData<Byte?>? =
            DataTracker.registerData<Byte?>(WeaponThrowEntity::class.java, TrackedDataHandlerRegistry.BYTE)
        private val COMPOUND_STACK: TrackedData<NbtCompound?>? = DataTracker.registerData<NbtCompound?>(
            WeaponThrowEntity::class.java,
            TrackedDataHandlerRegistry.NBT_COMPOUND
        )
        private val DESTROYED_BLOCK: TrackedData<BlockPos?>? =
            DataTracker.registerData<BlockPos?>(WeaponThrowEntity::class.java, TrackedDataHandlerRegistry.BLOCK_POS)
        private val SHOULD_DESTROY: TrackedData<Boolean?>? =
            DataTracker.registerData<Boolean?>(WeaponThrowEntity::class.java, TrackedDataHandlerRegistry.BOOLEAN)

        fun getReturnOrLoyaltyEnchantment(stack: ItemStack): Int {
            val loyaltyLevel = EnchantmentHelper.getLevel(Enchantments.LOYALTY, stack)
            val returnLevel = EnchantmentHelper.getLevel(RETURN, stack)
            return if (loyaltyLevel > 0) loyaltyLevel else returnLevel
        }
    }
}
