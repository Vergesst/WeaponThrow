package vergisst.minecraftmod.weaponthrow.handler

import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityTypeBuilder
import net.minecraft.entity.EntityDimensions
import net.minecraft.entity.SpawnGroup
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier
import vergisst.minecraftmod.weaponthrow.WeaponThrow
import vergisst.minecraftmod.weaponthrow.entity.WeaponThrowEntity

object EntityRegistry {
    var WEAPONTHROW = Registry.register(
        Registries.ENTITY_TYPE,
        Identifier.of(WeaponThrow.MOD_ID, "weapon_throw"),
        FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::WeaponThrowEntity)
            .trackRangeBlocks(4)
            .trackedUpdateRate(20)
            .dimensions(EntityDimensions.fixed(.5F, .5F))
            .build()
    )

    fun registerEntities() {}
}