package dev.xmas.overlay.mixin;

import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DimensionType.Skybox.class)
public interface SkyboxAccessor {
	@Accessor("name") String xmas$getName();
}
