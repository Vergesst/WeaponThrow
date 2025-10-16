package vergisst.minecraftmod.weaponthrow.packets

import net.minecraft.util.Identifier
import vergisst.minecraftmod.weaponthrow.WeaponThrow

object PacketIdentifiers {
    val SPAWN_PACKET = Identifier(WeaponThrow.MOD_ID, "spawn_packet")
    val CLIENT_THROW_PACKET = Identifier(WeaponThrow.MOD_ID, "client_throw_packet")
    val SERVER_THROW_PACKET = Identifier(WeaponThrow.MOD_ID, "server_throw_packet")
}