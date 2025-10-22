package vergisst.minecraftmod.weaponthrow.enchantments

import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnchantmentTarget
import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.AxeItem
import net.minecraft.item.ItemStack
import vergisst.minecraftmod.weaponthrow.handler.ConfigRegistry

class ReturnEnchantment(rarity: Enchantment.Rarity, vararg slot: EquipmentSlot):
    Enchantment(rarity, EnchantmentTarget.WEAPON, slot) {

    override fun getMinPower(level: Int) = 5 + level * 7
    override fun getMaxPower(level: Int) = 50
    override fun getMaxLevel() = 1

    override fun isAcceptableItem(stack: ItemStack): Boolean {
        val enchantAll = ConfigRegistry.COMMON.config.enchantments.enchantAllWeapons
        val isAxe = stack.item is AxeItem
        val canApply = super.isAcceptableItem(stack)
        val isEnabled = ConfigRegistry.COMMON.config.enchantments.enableReturn

        return (enchantAll || isAxe || canApply) && isEnabled
    }
}