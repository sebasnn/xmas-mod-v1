package dev.xmas.overlay.mixin;

import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SkyRenderState.class)
public interface SkyRenderStateAccessor {
	@Accessor("skyColor") int xmas$getSkyColor();
	@Mutable @Accessor("skyColor") void xmas$setSkyColor(int color);
	@Accessor("starBrightness") float xmas$getStarBrightness();
	@Mutable @Accessor("starBrightness") void xmas$setStarBrightness(float value);
	@Mutable @Accessor("skybox") void xmas$setSkybox(DimensionType.Skybox skybox);
}
