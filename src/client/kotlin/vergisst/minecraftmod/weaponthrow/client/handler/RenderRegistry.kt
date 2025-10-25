package vergisst.minecraftmod.weaponthrow.client.handler

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import vergisst.minecraftmod.weaponthrow.client.render.CommonThrowEntityRenderer
import vergisst.minecraftmod.weaponthrow.handler.EntityRegistry

object RenderRegistry {
    fun registerRenderers() {
        EntityRendererRegistry.register(EntityRegistry.COMMON_THROW_ENTITY, ::CommonThrowEntityRenderer)
    }
}