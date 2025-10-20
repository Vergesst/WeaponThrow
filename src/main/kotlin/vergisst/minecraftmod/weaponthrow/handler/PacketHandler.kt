package vergisst.minecraftmod.weaponthrow.handler

import net.fabricmc.fabric.api.networking.v1.FabricPacket
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.entity.Entity

import net.minecraft.server.world.ServerWorld
import vergisst.minecraftmod.weaponthrow.packets.PacketIdentifiers
import vergisst.minecraftmod.weaponthrow.packets.State

object PacketHandler {
    fun registerServerListener() {
        ServerPlayNetworking.registerGlobalReceiver(PacketIdentifiers.CLIENT_THROW_PACKET)
        { server,
          player,
          handler,
          buf,
          responseSender ->

            val action = State.fromByte(buf.readByte().toInt())
            server.execute {
                EventsHandler.onThrowItem(player, action)
            }
        }
    }

    fun sendToAll(entity: Entity, packet: FabricPacket) {
        for(player in PlayerLookup.tracking(entity.world as ServerWorld, entity.blockPos)) {
            ServerPlayNetworking.send(player, packet)
        }
    }
}