package vergisst.minecraftmod.weaponthrow.handler

import net.minecraft.enchantment.Enchantment
import net.minecraft.entity.EquipmentSlot
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier
import vergisst.minecraftmod.weaponthrow.WeaponThrow
import vergisst.minecraftmod.weaponthrow.enchantments.ConccusionEnchantment
import vergisst.minecraftmod.weaponthrow.enchantments.GravityEnchantment
import vergisst.minecraftmod.weaponthrow.enchantments.GroundedEdgeEnchantment
import vergisst.minecraftmod.weaponthrow.enchantments.ReturnEnchantment
import vergisst.minecraftmod.weaponthrow.enchantments.ThrowEnchantment

object EnchantmentHandler {
    val THROW = Registry.register(Registries.ENCHANTMENT,
        Identifier(WeaponThrow.MOD_ID, "throw"), ThrowEnchantment(Enchantment.Rarity.RARE, EquipmentSlot.MAINHAND)
    );
    val GROUNDEDEDGE = Registry.register(Registries.ENCHANTMENT, Identifier(WeaponThrow.MOD_ID, "groundededge"),
        GroundedEdgeEnchantment(Enchantment.Rarity.UNCOMMON, EquipmentSlot.MAINHAND)
    );
    val CONCCUSION = Registry.register(Registries.ENCHANTMENT, Identifier(WeaponThrow.MOD_ID, "conccusion"),
        ConccusionEnchantment(Enchantment.Rarity.VERY_RARE, EquipmentSlot.MAINHAND)
    );
    val GRAVITY = Registry.register(Registries.ENCHANTMENT, Identifier(WeaponThrow.MOD_ID, "gravity"),
        GravityEnchantment(Enchantment.Rarity.VERY_RARE, EquipmentSlot.MAINHAND)
    );
    val RETURN = Registry.register(Registries.ENCHANTMENT, Identifier(WeaponThrow.MOD_ID, "return"),
        ReturnEnchantment(Enchantment.Rarity.RARE, EquipmentSlot.MAINHAND)
    );

    fun registerEnchantment() {}
}