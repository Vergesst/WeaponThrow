package vergisst.minecraftmod.weaponthrow.handler

import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityTypeBuilder
import net.minecraft.entity.EntityDimensions
import net.minecraft.entity.EntityType
import net.minecraft.entity.SpawnGroup
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier
import vergisst.minecraftmod.weaponthrow.WeaponThrow
import vergisst.minecraftmod.weaponthrow.entity.CommonThrowEntity
import vergisst.minecraftmod.weaponthrow.entity.WeaponThrowEntity

object EntityRegistry {
    var WEAPON_THROW: EntityType<WeaponThrowEntity> = Registry.register(
        Registries.ENTITY_TYPE,
        Identifier.of(WeaponThrow.MOD_ID, "weapon_throw"),
        FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::WeaponThrowEntity)
            .trackRangeBlocks(4)
            .trackedUpdateRate(20)
            .dimensions(EntityDimensions.fixed(.5F, .5F))
            .build()
    )

//    Candidate
    var COMMON_THROW_ENTITY: EntityType<CommonThrowEntity> = Registry.register(
        Registries.ENTITY_TYPE,
        Identifier.of(WeaponThrow.MOD_ID, "common_throw"),
        FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::CommonThrowEntity)
            .trackRangeBlocks(4)
            .trackedUpdateRate(20)
            .dimensions(EntityDimensions.fixed(.5F, .5F))
            .build()
    )

//    throw entity for trident is also needed --- which will be also applied when 1.21.10

    fun registerEntities() {}
}