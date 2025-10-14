package vergisst.minecraftmod.weaponthrow.handler

import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.ConfigHolder
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer
import vergisst.minecraftmod.weaponthrow.config.WeaponThrowConfig

object ConfigRegistry {
    lateinit var COMMON: ConfigHolder<WeaponThrowConfig>

    fun registerConfig() {
        COMMON = AutoConfig.register(WeaponThrowConfig::class.java, ::GsonConfigSerializer)
    }
}