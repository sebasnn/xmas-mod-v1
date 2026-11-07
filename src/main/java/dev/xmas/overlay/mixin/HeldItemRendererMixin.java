package dev.xmas.overlay.mixin;

import dev.xmas.overlay.Safe;
import dev.xmas.overlay.XmasConfig;
import dev.xmas.overlay.client.KnifeRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {
	@Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
	private void xmas$held(AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand,
			float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
			OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
		XmasConfig cfg = XmasConfig.get();
		try {
			if (hand == Hand.MAIN_HAND && cfg.customKnife && Safe.ok("knife") && item.isOf(Items.TOTEM_OF_UNDYING)) {
				try {
					KnifeRenderer.render((HeldItemRenderer) (Object) this, player, swingProgress, equipProgress, matrices, queue, light);
					ci.cancel();
					return;
				} catch (Throwable t) {
					Safe.fail("knife", t);
				}
			}
			// every other item: optionally move it away from the screen corner
			if (cfg.itemShiftEnabled && cfg.itemShift > 0) {
				Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
				float side = arm == Arm.RIGHT ? 1f : -1f;
				float d = cfg.itemShift / 100f;
				matrices.translate(-side * 0.45f * d, 0.30f * d, -0.25f * d);
			}
		} catch (Throwable t) {
			Safe.fail("hands", t);
		}
	}
}
