package vergisst.minecraftmod.weaponthrow.handler

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.entity.Entity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import vergisst.minecraftmod.weaponthrow.packets.BasePacket
import vergisst.minecraftmod.weaponthrow.packets.CPacketThrow

object PacketHandler {
    fun registerServerListener() = CPacketThrow.register()

    fun sendToAllTracking(entity: Entity, packet: BasePacket) {
        if(entity.world is ServerWorld) {
            if(entity is ServerPlayerEntity) {
                ServerPlayNetworking.send(entity, packet.identifier, packet.buf)
            }

            sendToAll(entity, packet)
        }
    }

    fun sendToAll(entity: Entity, packet: BasePacket) {
        for(player in PlayerLookup.tracking(entity.world as ServerWorld, entity.blockPos)) {
            ServerPlayNetworking.send(player, packet.identifier, packet.buf)
        }
    }
}