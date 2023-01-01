package foundry.veil.mixin.client.shader;

import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.shaders.Shader;
import org.lwjgl.system.NativeResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ProgramManager.class)
public class ProgramManagerMixin {

    @Inject(method = "releaseProgram", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;glDeleteProgram(I)V", shift = At.Shift.BEFORE))
    private static void free(Shader shader, CallbackInfo ci) {
        if (shader instanceof NativeResource resource) {
            resource.free();
        }
    }
}
