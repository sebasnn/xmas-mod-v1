package dev.xmas.overlay.mixin;

import dev.xmas.overlay.Safe;
import dev.xmas.overlay.XmasConfig;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Galaxy night sky: at night the overworld uses the End-style skybox, whose texture is replaced by the
 * galaxy picture from the built-in "xmas_galaxy_sky" pack. The sky colour is darkened so it stays night.
 */
@Mixin(SkyRendering.class)
public class SkyRenderingMixin {
	private static DimensionType.Skybox xmas$end;

	@Inject(method = "updateRenderState", at = @At("TAIL"))
	private void xmas$galaxy(ClientWorld world, float tickProgress, Camera camera, SkyRenderState state, CallbackInfo ci) {
		XmasConfig cfg = XmasConfig.get();
		if (!cfg.galaxySky || !Safe.ok("sky") || world.getRegistryKey() != World.OVERWORLD) return;
		try {
			SkyRenderStateAccessor acc = (SkyRenderStateAccessor) state;
			float stars = acc.xmas$getStarBrightness();       // 0 by day, ~0.5 at full night
			if (stars < 0.2f) return;
			if (xmas$end == null) {
				for (DimensionType.Skybox s : DimensionType.Skybox.values())
					if ("end".equalsIgnoreCase(((SkyboxAccessor) (Object) s).xmas$getName())) xmas$end = s;
			}
			if (xmas$end != null) acc.xmas$setSkybox(xmas$end);
			int v = acc.xmas$getSkyColor();
			int r = (int) (((v >> 16) & 0xFF) * 0.35f), g = (int) (((v >> 8) & 0xFF) * 0.35f), b = (int) ((v & 0xFF) * 0.45f);
			acc.xmas$setSkyColor((v & 0xFF000000) | (r << 16) | (g << 8) | b);
			acc.xmas$setStarBrightness(Math.min(1f, stars * (cfg.starBoost / 100f)));
		} catch (Throwable t) {
			Safe.fail("sky", t);
		}
	}
}
