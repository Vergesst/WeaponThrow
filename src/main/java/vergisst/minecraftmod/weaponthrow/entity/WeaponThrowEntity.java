package vergisst.minecraftmod.weaponthrow.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvironmentInterface;

import net.minecraft.block.*;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import vergisst.minecraftmod.weaponthrow.handler.ConfigRegistry;
import vergisst.minecraftmod.weaponthrow.handler.EnchantmentHandler;
import vergisst.minecraftmod.weaponthrow.handler.EntityRegistry;
import java.util.List;
import java.util.Objects;

@EnvironmentInterface(
        value = EnvType.CLIENT,
        itf = FlyingItemEntity.class
)
public class WeaponThrowEntity extends PersistentProjectileEntity implements FlyingItemEntity{


    private float clientSideRotation = 0;
    private boolean counterClockwiseBounce = true;

    private static final TrackedData<Byte> LOYALTY_LEVEL = DataTracker.registerData(WeaponThrowEntity.class, TrackedDataHandlerRegistry.BYTE);
    private static final TrackedData<NbtCompound> COMPOUND_STACK = DataTracker.registerData(WeaponThrowEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
    private static final TrackedData<BlockPos> DESTROYED_BLOCK = DataTracker.registerData(WeaponThrowEntity.class, TrackedDataHandlerRegistry.BLOCK_POS);
    private static final TrackedData<Boolean> SHOULD_DESTROY = DataTracker.registerData(WeaponThrowEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private boolean dealtDamage = false;
    private float attackDamage;
    public int returningTicks;

    @Nullable
    private BlockState lastState;

    public WeaponThrowEntity(EntityType<? extends WeaponThrowEntity> type, World worldIn) {
        super(type, worldIn);
    }

    public WeaponThrowEntity(World worldIn, LivingEntity thrower, boolean canDestroy , float attackDamage, ItemStack thrownStackIn) {
        super(EntityRegistry.INSTANCE.getWEAPONTHROW(), thrower, worldIn);
        this.attackDamage = attackDamage;
        this.dataTracker.set(COMPOUND_STACK, thrownStackIn.copy().writeNbt(new NbtCompound()));
        this.dataTracker.set(LOYALTY_LEVEL, (byte)WeaponThrowEntity.getReturnOrLoyaltyEnchantment(thrownStackIn));
        this.dataTracker.set(DESTROYED_BLOCK, BlockPos.ORIGIN);
        this.dataTracker.set(SHOULD_DESTROY, canDestroy);
    }

    public WeaponThrowEntity(World worldIn, double x, double y, double z) {
        super(EntityRegistry.INSTANCE.getWEAPONTHROW(), x, y, z, worldIn);
    }

    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(COMPOUND_STACK, new NbtCompound());
        this.dataTracker.startTracking(LOYALTY_LEVEL, (byte)0);
        this.dataTracker.startTracking(DESTROYED_BLOCK, BlockPos.ORIGIN);
        this.dataTracker.startTracking(SHOULD_DESTROY, false);
    }

    public void setItemStack(ItemStack stack) {
        this.getDataTracker().set(COMPOUND_STACK, stack.writeNbt(new NbtCompound()));
    }
    public ItemStack getItemStack() {
        return ItemStack.fromNbt(this.getDataTracker().get(COMPOUND_STACK));
    }

    public void shouldDestroy(boolean stack) {
        this.getDataTracker().set(SHOULD_DESTROY, stack);
    }
    public boolean shouldDestroy() {
        return this.getDataTracker().get(SHOULD_DESTROY);
    }

    public void setDestroyedBlock(BlockPos pos) {
        this.getDataTracker().set(DESTROYED_BLOCK, pos);
    }
    public BlockPos getDestroyedBlock() {
        return this.getDataTracker().get(DESTROYED_BLOCK);
    }


    public void tick() {

        if (this.inGroundTime > 4 && !this.dealtDamage) {
            this.dealtDamage = true;
        }

        if(!this.getDestroyedBlock().equals(BlockPos.ZERO) && !this.getWorld().isClient) {

            this.doInteractions(() -> {
                SoundEvent event = this.getWorld().getBlockState(this.getDestroyedBlock()).getSoundGroup().getBreakSound();
                boolean destroyed = ((ServerPlayerEntity) Objects.requireNonNull(this.getOwner())).interactionManager.tryBreakBlock(this.getDestroyedBlock());
                if(destroyed) {
                    this.getWorld().playSound(null, this.getDestroyedBlock(), event , SoundCategory.AMBIENT, 10, 1.0F);
                }
            });
            this.setDestroyedBlock(BlockPos.ORIGIN);
        }

        int gravityWorld = ConfigRegistry.COMMON.get().enchantments.enableGravity ? EnchantmentHelper.getLevel(EnchantmentHandler.INSTANCE.getGRAVITY(), this.getItemStack()) : 0;
        if(gravityWorld > 0) {this.setNoGravity(true);
            if(this.getWorld().isOutOfHeightLimit(this.getBlockPos())) {
                this.setVelocity(this.getVelocity().multiply(1, 0, 1));
            }
            if((Math.abs(this.getVelocity().getX()) < 0.1 && Math.abs(this.getVelocity().getZ()) < 0.1)) {

                this.setNoGravity(false);

            }
        }

        Entity entity = this.getOwner();

        int i = ConfigRegistry.COMMON.get().enchantments.enableReturn ? this.dataTracker.get(LOYALTY_LEVEL) : 0;

        if (i > 0 && (this.dealtDamage || this.isNoClip()) && entity != null) {

            if (!this.shouldReturnToThrower()) {
                // reminder -- check for twice
                if (!this.getWorld().isClient && this.pickupType == PickupPermission.ALLOWED) {
                    this.dropStack(this.getItemStack(), 0.1F);
                }

                this.remove(RemovalReason.DISCARDED);
            } else {
                this.setNoClip(true);
                Vec3d Vec3d = entity.getEyePos().subtract(this.getPos());
                this.setPos(this.getX(), this.getY() + Vec3d.y * 0.015D * (double)i, this.getZ());
                if (this.getWorld().isClient) {
                    this.lastRenderY = this.getY();
                }

                double d0 = 0.05D * (double)i;
                this.setVelocity(this.getVelocity().multiply(0.95D).add(Vec3d.normalize().multiply(d0)));
                if (this.returningTicks == 0) {
                    this.playSound(SoundEvents.ITEM_TRIDENT_RETURN, 10.0F, 1.0F);
                }

                ++this.returningTicks;
            }
        }
        super.tick();
    }

    private boolean shouldReturnToThrower() {
        Entity entity = this.getOwner();

        if (entity != null && entity.isAlive()) {
            return !(entity instanceof ServerPlayerEntity) || !entity.isSpectator();
        } else {
            return false;
        }
    }

    protected ItemStack asItemStack() {
        return this.getItemStack().copy();
    }


    @Nullable
    protected EntityHitResult getEntityCollision(Vec3d p_37575_, Vec3d p_37576_) {
        return this.dealtDamage ? null : super.getEntityCollision(p_37575_, p_37576_);
    }

    @Override
    protected void onEntityHit(EntityHitResult p_213868_1_) {


        Entity entity = p_213868_1_.getEntity();
        float f = this.attackDamage;
        if (entity instanceof LivingEntity livingentity) {

            f += ConfigRegistry.COMMON.get().enchantments.enableThrow ? EnchantmentHelper.getLevel(EnchantmentHandler.INSTANCE.getTHROW(), this.getItemStack())*1F : 0;
            f += EnchantmentHelper.getAttackDamage(this.getItemStack(), livingentity.getGroup());
        }

        Entity entity1 = this.getOwner();
//	      DamageSource damagesource = DamageSource.thrownProjectile(this, entity1 == null ? this : entity1);
        var damageSource = this.getDamageSources().thrown(this, entity1 == null ? this : entity1);



        this.dealtDamage = true;
        SoundEvent soundevent = SoundEvents.ITEM_TRIDENT_HIT;

        if (entity.damage(damageSource, f)) {

            if (entity.getType() == EntityType.ENDERMAN) {
                return;
            }

            if (entity instanceof LivingEntity livingEntity1) {


                int contusionWorld = ConfigRegistry.COMMON.get().enchantments.enableConccusion ? EnchantmentHelper.getLevel(EnchantmentHandler.INSTANCE.getCONCCUSION(), this.getItemStack()) : 0;

                if (contusionWorld > 0) {
                    livingEntity1.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20*2*contusionWorld, 5));
                    livingEntity1.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 20*5*contusionWorld, 3));
                }

                int fireTime = EnchantmentHelper.getLevel(Enchantments.FIRE_ASPECT, this.getItemStack());
                int groundedWorld = ConfigRegistry.COMMON.get().enchantments.enableGroundedEdge ? EnchantmentHelper.getLevel(EnchantmentHandler.INSTANCE.getGROUNDEDEDGE(), this.getItemStack()) : 0;

                if(fireTime > 0 || groundedWorld > 0) {
                    List<LivingEntity> nearEntities = this.getWorld().getNonSpectatingEntities(LivingEntity.class, this.getBoundingBox().expand(1.0D));

                    if(!nearEntities.isEmpty()) {
                        for(LivingEntity nearEntity: nearEntities) {
                            if(nearEntity.getRandom().nextInt(3) == 0) {
                                nearEntity.setOnFireFor(fireTime);
                            }
                            nearEntity.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 80, groundedWorld - 1));
                        }
                    }
                }

                if (entity1 instanceof LivingEntity) {
                    EnchantmentHelper.onUserDamaged(livingEntity1, entity1);
                    EnchantmentHelper.onTargetDamaged((LivingEntity)entity1, livingEntity1);
                }

                this.onHit(livingEntity1);

                if (this.getItemStack().getItem() instanceof BlockItem) {
                    var blockItem = Block.getBlockFromItem(this.getItemStack().getItem());
                    switch(blockItem) {
                        case SandBlock sandBlock -> {
                            if (livingEntity1.getRandom().nextInt(10) == 0)
                                livingEntity1.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 3));
                        }
                        case TorchBlock torchBlock -> {
                            if (livingEntity1.getRandom().nextInt(5) == 0)
                                livingEntity1.setOnFireFor(1);
                        }
                        case AnvilBlock anvilBlock -> {
                            livingEntity1.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 60, 3));
                            livingEntity1.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 5));
                        }
                        default -> {}
                    }
                } else {
                    Item itemThrown = this.getItemStack().getItem();
                    if(itemThrown.equals(Items.BLAZE_ROD) || itemThrown.equals(Items.BLAZE_POWDER)) {
                        livingEntity1.setOnFireFor(1);
                    }
                }
            }


        }

        this.setVelocity(this.getVelocity().multiply(-0.01D, -0.1D, -0.01D));

        this.playSound(soundevent, 1.0F, 1.0F);

    }


    protected SoundEvent getHitSound() {

        return SoundEvents.BLOCK_METAL_HIT;
    }


    public void onPlayerCollision(PlayerEntity entityIn) {

        Entity entity = this.getOwner();

        if (entity == null || entity.getUuid() == entityIn.getUuid() || this.inGroundTime > (ConfigRegistry.COMMON.get().times.ticksUntilWeaponLoseOwner)) {
            super.onPlayerCollision(entityIn);
        }
    }

    public void readCustomDataFromNbt(NbtCompound compound) {
        super.readCustomDataFromNbt(compound);

        this.dealtDamage = compound.getBoolean("DealtDamage");

        if (compound.contains("Stack", 10)) {
            this.setItemStack(ItemStack.fromNbt(compound.getCompound("Stack")));
        }

        this.dataTracker.set(LOYALTY_LEVEL, (byte)WeaponThrowEntity.getReturnOrLoyaltyEnchantment(this.getItemStack()));

    }


    public void writeCustomDataToNbt(NbtCompound compound) {
        super.writeCustomDataToNbt(compound);
        compound.put("Stack", this.getDataTracker().get(COMPOUND_STACK));
        compound.putBoolean("DealtDamage", this.dealtDamage);
        if (this.lastState != null) {
            compound.put("inBlockState", NbtHelper.fromBlockState(this.lastState));
        }
    }

    @Override
    public void checkDespawn() {
        int i = this.dataTracker.get(LOYALTY_LEVEL);
        if (this.pickupType != PickupPermission.ALLOWED || i <= 0) {

            if (this.inGroundTime >= ConfigRegistry.COMMON.get().times.despawnTime) {
                this.remove(RemovalReason.DISCARDED);
            }
        }
    }


    protected void onCollision(HitResult result) {

        HitResult.Type raytraceresult$type = result.getType();
        if (raytraceresult$type == HitResult.Type.ENTITY) {
            Entity hittedEntity = ((EntityHitResult)result).getEntity();
            if(hittedEntity instanceof LivingEntity && this.getOwner() instanceof PlayerEntity) {
                this.doInteractions(() -> {
                    ((PlayerEntity) this.getOwner()).attack(hittedEntity);
                });

            }
            this.onEntityHit((EntityHitResult)result);

        } else if (raytraceresult$type == HitResult.Type.BLOCK) {
            BlockPos stickBlockPos = ((BlockHitResult)result).getBlockPos();
            BlockState state = this.getWorld().getBlockState(stickBlockPos);
            if(!state.getBlock().equals(Blocks.BEDROCK) && this.shouldDestroy()) {
                boolean canBreak = ConfigRegistry.COMMON.get().interactions.canBreakBlocks;

                boolean canHarvest = this.getItemStack().isSuitableFor(state) && canBreak;
                if(canHarvest) {
                    if(!this.getWorld().isClient && this.lastState == null) {
                        this.setDestroyedBlock(stickBlockPos);
                    }

                }
            }
            this.onBlockHit((BlockHitResult)result);
        }
    }

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        this.lastState = this.getWorld().getBlockState(blockHitResult.getBlockPos());
        Vec3d Vec3d = blockHitResult.getPos().subtract(this.getX(), this.getY(), this.getZ());
        this.setVelocity(Vec3d);
        Vec3d Vec3d1 = Vec3d.normalize().multiply(0.05F);
        this.setPos(this.getX() - Vec3d1.x, this.getY() - Vec3d1.y, this.getZ() - Vec3d1.z);

        SoundEvent event = SoundEvents.ITEM_TRIDENT_HIT_GROUND;
        if(this.getItemStack().getItem() instanceof BlockItem) {
            Block block = Block.getBlockFromItem(this.getItemStack().getItem());
            event = block.getDefaultState().getSoundGroup().getHitSound();
        }

        this.playSound(event, 1.0F, 1.2F / (this.random.nextFloat() * 0.2F + 0.9F));
        this.inGround = true;
        this.shake = 7;

        this.setCritical(false);
        this.setPierceLevel((byte)0);
        this.setSound(event);
        this.setShotFromCrossbow(false);

        this.applyBounce();
    }

    void applyBounce() {

        if(this.inGround && this.getItemStack().getItem() instanceof BlockItem ) {
            if(!(Math.abs(this.getVelocity().x) < 0.05 && Math.abs(this.getVelocity().z) < 0.05)) {

                Vec3d vec3 = this.getVelocity().multiply(0.9F);
                BlockPos landingPos = getSteppingPos();

                if(!this.getWorld().getBlockState(landingPos.down()).isAir() || !this.getWorld().getBlockState(landingPos.up()).isAir()) {
                    this.setVelocity(vec3.x, -vec3.y, vec3.z);
                }
                else if(!this.getWorld().getBlockState(landingPos.west()).isAir() || !this.getWorld().getBlockState(landingPos.east()).isAir()){
                    this.setVelocity(-vec3.x, vec3.y, vec3.z);
                }
                else if(!this.getWorld().getBlockState(landingPos.north()).isAir() || !this.getWorld().getBlockState(landingPos.south()).isAir()){
                    this.setVelocity(vec3.x, vec3.y, -vec3.z);
                }
                this.counterClockwiseBounce = !this.counterClockwiseBounce;
                this.inGround = false;
            }
        }
    }

    @Override
    public ItemStack getStack() {
        return this.getItemStack();
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return super.createSpawnPacket();
    }

    public void doInteractions(Runnable action) {
        ItemStack originalStack = ((PlayerEntity) Objects.requireNonNull(this.getOwner())).getStackInHand(Hand.MAIN_HAND);

        ((PlayerEntity) this.getOwner()).setStackInHand(Hand.MAIN_HAND, this.getItemStack());

        action.run();

        ItemStack newStack = ((PlayerEntity) this.getOwner()).getStackInHand(Hand.MAIN_HAND);

        if (!newStack.isEmpty()) {
            ((PlayerEntity) this.getOwner()).setStackInHand(Hand.MAIN_HAND, originalStack);
        } else {

            this.getWorld().playSound(null, this.getBlockPos(), SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.AMBIENT, 0.8F, 10F);

            this.spawnItemParticles(this.getItemStack());

            this.remove(RemovalReason.DISCARDED);
        }
    }

    private void spawnItemParticles(ItemStack stack) {
        for(int i = 0; i < 5; ++i) {
            Vec3d vec3d = new Vec3d(((double)this.random.nextFloat() - 0.5D) * 0.1D, Math.random() * 0.1D + 0.1D, 0.0D);
            vec3d = vec3d.rotateX(-this.getPitch() * 0.017453292F);
            vec3d = vec3d.rotateY(-this.getYaw() * 0.017453292F);
            double d = (double)(-this.random.nextFloat()) * 0.6D - 0.3D;
            Vec3d vec3d2 = new Vec3d(((double)this.random.nextFloat() - 0.5D) * 0.3D, d, 0.6D);
            vec3d2 = vec3d2.rotateX(-this.getPitch() * 0.017453292F);
            vec3d2 = vec3d2.rotateY(-this.getYaw() * 0.017453292F);
            vec3d2 = vec3d2.add(this.getX(), this.getEyeY(), this.getZ());
            if (this.getWorld() instanceof ServerWorld) //Forge: Fix MC-2518 spawnParticle is nooped on server, need to use server specific variant
                ((ServerWorld) this.getWorld()).spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, stack),  vec3d2.x, vec3d2.y, vec3d2.z, 1, vec3d.x, vec3d.y + 0.05D, vec3d.z, 0.0D);
            else
                this.getWorld().addParticle(new ItemStackParticleEffect(ParticleTypes.ITEM, stack), vec3d2.x, vec3d2.y, vec3d2.z, vec3d.x, vec3d.y + 0.05D, vec3d.z);
        }
    }

    public static int getReturnOrLoyaltyEnchantment(ItemStack stack) {
        int loyaltyLevel = EnchantmentHelper.getLevel(Enchantments.LOYALTY, stack);
        int returnLevel = EnchantmentHelper.getLevel(EnchantmentHandler.INSTANCE.getRETURN(), stack);
        return loyaltyLevel > 0 ? loyaltyLevel : returnLevel;
    }

    @Environment(EnvType.CLIENT)
    public float getRotationAnimation(float partialTicks) {
        if(!this.inGround) {clientSideRotation = (this.counterClockwiseBounce? 1:-1)*(this.age+partialTicks)*50F;}
        return this.clientSideRotation;
    }
}
