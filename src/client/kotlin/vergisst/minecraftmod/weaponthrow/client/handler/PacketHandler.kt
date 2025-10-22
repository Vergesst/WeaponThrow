package vergisst.minecraftmod.weaponthrow.client.handler

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.FabricPacket
import vergisst.minecraftmod.weaponthrow.packets.PacketIdentifiers

object PacketHandler {
    fun sendToServer(packet: FabricPacket) {
        ClientPlayNetworking.send(packet)
    }

    @Environment(EnvType.CLIENT)
    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(PacketIdentifiers.SERVER_THROW_PACKET)
        { client, handler, buf, receiver ->
            client.execute {
                EventsHandler.onServerUpdate(buf.readUuid(), buf.readVarInt(), buf.readBoolean())
            }
        }
    }
}