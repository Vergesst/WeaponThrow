package vergisst.minecraftmod.weaponthrow.client.handler;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import vergisst.minecraftmod.weaponthrow.client.render.WeaponThrowRenderer;
import vergisst.minecraftmod.weaponthrow.handler.EntityRegistry;

public class RenderRegistry {

    public static void registerRenderers() {
        EntityRendererRegistry.register(EntityRegistry.INSTANCE.getWEAPONTHROW(), WeaponThrowRenderer::new);

    }
}
