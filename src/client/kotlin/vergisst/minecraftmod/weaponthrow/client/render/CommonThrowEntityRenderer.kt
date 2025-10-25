package vergisst.minecraftmod.weaponthrow.client.render

import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.FlyingItemEntityRenderer
import net.minecraft.client.render.item.ItemRenderer
import net.minecraft.client.render.model.json.ModelTransformationMode
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.MathHelper
import org.joml.Quaternionf
import vergisst.minecraftmod.weaponthrow.entity.CommonThrowEntity
import vergisst.minecraftmod.weaponthrow.impl.MathConstant.POSITIVE_Y
import vergisst.minecraftmod.weaponthrow.impl.MathConstant.POSITIVE_Z

class CommonThrowEntityRenderer(renderManagerIn: EntityRendererFactory.Context):
    FlyingItemEntityRenderer<CommonThrowEntity>(renderManagerIn) {
    var itemRenderer: ItemRenderer = renderManagerIn.itemRenderer

    override fun render(
        entityIn: CommonThrowEntity, entityYaw: Float, partialTicks: Float, matrixStackIn: MatrixStack,
        bufferIn: VertexConsumerProvider, packedLightIn: Int
    ) {
        val degrees = entityIn.getRotationAnimation(partialTicks)
        val scale = 0.75F
        val interpolatedYaw = MathHelper.lerp(partialTicks, entityIn.prevYaw, entityIn.yaw)
        val count = entityIn.localStack.count

        matrixStackIn.push()
        matrixStackIn.translate(0F, 0.15F, 0F)
        matrixStackIn.multiply(Quaternionf().fromAxisAngleDeg(POSITIVE_Y, interpolatedYaw - 90))
        matrixStackIn.multiply(Quaternionf().fromAxisAngleDeg(POSITIVE_Z, -degrees))
        matrixStackIn.scale(scale, scale, scale)

        this.itemRenderer.renderItem(entityIn.stack, ModelTransformationMode.FIXED, packedLightIn,
            OverlayTexture.DEFAULT_UV, matrixStackIn, bufferIn, null, entityIn.id)

        if (count > 32) {
            matrixStackIn.translate(-0.05f, -0.05f, -0.05f)
            this.itemRenderer.renderItem(
                entityIn.stack, ModelTransformationMode.FIXED,
                packedLightIn, OverlayTexture.DEFAULT_UV, matrixStackIn, bufferIn, null, entityIn.getId()
            )
        }
        matrixStackIn.pop()
    }
}