package vergisst.minecraftmod.weaponthrow.packets

import net.fabricmc.fabric.api.networking.v1.FabricPacket
import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.PacketByteBuf
import java.util.UUID

class S2CThrowPacket(
    val uuid: UUID,
    val maxChargeTime: Int,
    val isCharging: Boolean
): FabricPacket {

    companion object {
        val ID = PacketIdentifiers.SERVER_THROW_PACKET

        val TYPE: PacketType<S2CThrowPacket> = PacketType.create(ID) { buf ->
            S2CThrowPacket(buf)
        }
    }

    constructor(buf: PacketByteBuf): this(
        buf.readUuid(),
        buf.readVarInt(),
        buf.readBoolean()
    )

    override fun write(buf: PacketByteBuf) {
        buf.writeUuid(uuid)
        buf.writeVarInt(maxChargeTime)
        buf.writeBoolean(isCharging)
    }

    override fun getType(): PacketType<*> = TYPE
}
