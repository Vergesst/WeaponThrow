package vergisst.minecraftmod.weaponthrow.interfaces

import vergisst.minecraftmod.weaponthrow.capabilities.PlayerThrowData

interface IPlayerEntityMixin {
    fun `weaponThrow$setThrowPower`(value: PlayerThrowData)
    fun `weaponThrow$getThrowPower`(): PlayerThrowData
}