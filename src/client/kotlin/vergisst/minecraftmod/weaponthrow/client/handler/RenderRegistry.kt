package vergisst.minecraftmod.weaponthrow.client.handler

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import vergisst.minecraftmod.weaponthrow.client.render.WeaponThrowRenderer
import vergisst.minecraftmod.weaponthrow.handler.EntityRegistry

object RenderRegistry {
    fun registerRenderers() {
        EntityRendererRegistry.register(EntityRegistry.WEAPONTHROW, ::WeaponThrowRenderer)
    }
}