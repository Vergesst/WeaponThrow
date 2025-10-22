package vergisst.minecraftmod.weaponthrow.enchantments

import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnchantmentTarget
import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.AxeItem
import net.minecraft.item.ItemStack
import vergisst.minecraftmod.weaponthrow.handler.ConfigRegistry

class GroundedEdgeEnchantment(rarity: Enchantment.Rarity, vararg slot: EquipmentSlot):
    Enchantment(rarity, EnchantmentTarget.WEAPON, slot) {

    override fun getMaxLevel() = 3

    override fun isAcceptableItem(stack: ItemStack): Boolean {
        val enchantAll = ConfigRegistry.COMMON.config.enchantments.enchantAllWeapons
        val isAxe = stack.item is AxeItem
        val canApply = super.isAcceptableItem(stack)
        val isEnabled = ConfigRegistry.COMMON.config.enchantments.enableGroundedEdge

        return (isAxe || enchantAll || canApply) && isEnabled
    }
}