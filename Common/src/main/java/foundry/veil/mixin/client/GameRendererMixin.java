package foundry.veil.mixin.client;

import foundry.veil.postprocessing.PostProcessingHandler;
import foundry.veil.shader.RenderTypeRegistry;
import foundry.veil.shader.VeilShaderLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    public void initRenderer(Minecraft $$0, ItemInHandRenderer $$1, ResourceManager $$2, RenderBuffers $$3, CallbackInfo ci) {
        RenderTypeRegistry.init();
    }

    @Inject(method = "resize", at = @At(value = "HEAD"))
    public void resizeListener(int pWidth, int pHeight, CallbackInfo ci) {
        PostProcessingHandler.resize(pWidth, pHeight);
    }

    @Inject(method = "reloadShaders", at=@At("HEAD"))
    public void closeShaders(ResourceManager resourceManager, CallbackInfo ci){
        VeilShaderLoader.free();
    }
}