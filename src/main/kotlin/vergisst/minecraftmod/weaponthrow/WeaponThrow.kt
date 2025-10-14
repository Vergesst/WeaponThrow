package vergisst.minecraftmod.weaponthrow

import net.fabricmc.api.ModInitializer
import org.apache.logging.log4j.LogManager
import vergisst.minecraftmod.weaponthrow.handler.ConfigRegistry
import vergisst.minecraftmod.weaponthrow.handler.EnchantmentHandler
import vergisst.minecraftmod.weaponthrow.handler.EntityRegistry
import vergisst.minecraftmod.weaponthrow.handler.EventsHandler
import vergisst.minecraftmod.weaponthrow.handler.PacketHandler

class WeaponThrow: ModInitializer {
    companion object{
        val LOGGER = LogManager.getLogger()
        const val MOD_ID = "weaponthrow"
    }

    override fun onInitialize() {
        ConfigRegistry.registerConfig()

        EntityRegistry.registerEntities()

        EventsHandler.registerEvents()

        PacketHandler.registerServerListener()

        EnchantmentHandler.registerEnchantment()
    }
}