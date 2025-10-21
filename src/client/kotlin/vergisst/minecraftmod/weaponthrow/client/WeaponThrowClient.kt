package vergisst.minecraftmod.weaponthrow.client

import net.fabricmc.api.ClientModInitializer
import vergisst.minecraftmod.weaponthrow.client.handler.EventsHandler
import vergisst.minecraftmod.weaponthrow.client.handler.KeyBindingHandler
import vergisst.minecraftmod.weaponthrow.client.handler.PacketHandler
import vergisst.minecraftmod.weaponthrow.client.handler.RenderRegistry

class WeaponThrowClient: ClientModInitializer {

    override fun onInitializeClient() {
        EventsHandler.registerClientEvents()

        KeyBindingHandler.registerKeybindings()

        PacketHandler.register()

        RenderRegistry.registerRenderers()
    }
}