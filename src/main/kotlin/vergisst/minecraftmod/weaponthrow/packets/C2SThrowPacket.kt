package vergisst.minecraftmod.weaponthrow.packets

import net.fabricmc.fabric.api.networking.v1.FabricPacket
import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.PacketByteBuf

class C2SThrowPacket(val state: State): FabricPacket {

    companion object{
        val ID = PacketIdentifiers.CLIENT_THROW_PACKET

        val TYPE: PacketType<C2SThrowPacket> = PacketType.create(ID) { buf ->
            C2SThrowPacket(buf)
        }
    }

    constructor(buf: PacketByteBuf): this(
        State.fromByte(buf.readByte().toInt())
    )

    override fun write(buf: PacketByteBuf) {
        buf.writeByte(state.ordinal)
    }

    override fun getType(): PacketType<*> = TYPE
}