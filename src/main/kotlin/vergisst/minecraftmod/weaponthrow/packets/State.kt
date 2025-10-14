package vergisst.minecraftmod.weaponthrow.packets

enum class State(val index: Byte) {
    NONE(0),
    START(1),
    DURING(2),
    FINISH(3);

    fun toByte() = index

    companion object {
        fun fromByte(index: Int): State {
            return when (index) {
                1 -> START
                2 -> DURING
                3 -> FINISH
                else -> NONE
            }
        }
    }
}