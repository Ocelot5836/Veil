package foundry.veil.mixin.client.shader;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.shaders.Program;
import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.vertex.VertexFormat;
import foundry.veil.ext.ShaderInstanceExt;
import foundry.veil.shader.VeilShaderLoader;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.io.IOException;
import java.util.List;

import static org.lwjgl.opengl.GL20C.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL32C.GL_GEOMETRY_SHADER;

@Mixin(ShaderInstance.class)
public abstract class ShaderInstanceMixin implements ShaderInstanceExt {

    @Unique
    private Program geometryProgram;

    @Mutable
    @Shadow
    @Final
    private String name;

    @Shadow
    protected abstract void updateLocations();

    @Shadow
    @Final
    private Program vertexProgram;
    @Shadow
    @Final
    private Program fragmentProgram;
    @Shadow
    @Final
    private List<Integer> samplerLocations;
    @Shadow
    @Final
    private List<Integer> uniformLocations;
    @Unique
    private static String captureLocation;
    @Unique
    private static Program.Type captureType;

    @ModifyVariable(method = "<init>", index = 2, at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/ShaderInstance;vertexFormat:Lcom/mojang/blaze3d/vertex/VertexFormat;"), argsOnly = true)
    public String clearLocationString(String value) {
        return "";
    }

    @ModifyVariable(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/resources/ResourceProvider;openAsReader(Lnet/minecraft/resources/ResourceLocation;)Ljava/io/BufferedReader;", shift = At.Shift.BEFORE), index = 4)
    public ResourceLocation modifyLocation(ResourceLocation location) {
        // Make sure other mods haven't already changed the location
        if (!location.getNamespace().equals("minecraft") || !"shaders/core/.json".equals(location.getPath())) {
            return location;
        }
        ResourceLocation id = new ResourceLocation(this.name);
        this.name = id.getNamespace().equals("minecraft") ? id.getPath() : id.toString();
        return new ResourceLocation(id.getNamespace(), "shaders/core/" + id.getPath() + ".json");
    }

    @Inject(method = "getOrCreate", at = @At("HEAD"))
    private static void captureGetOrCreate(ResourceProvider resourceProvider, Program.Type type, String string, CallbackInfoReturnable<Program> cir) {
        captureLocation = string;
        captureType = type;
    }

    @Inject(method = "getOrCreate", at = @At("TAIL"))
    private static void deleteGetOrCreate(ResourceProvider resourceProvider, Program.Type type, String string, CallbackInfoReturnable<Program> cir) {
        captureLocation = null;
        captureType = null;
    }

    @ModifyVariable(method = "getOrCreate", at = @At(value = "NEW", target = "net/minecraft/resources/ResourceLocation", ordinal = 0, shift = At.Shift.BEFORE), ordinal = 1)
    private static String modifyStaticLocation(String value) {
        ResourceLocation id = new ResourceLocation(captureLocation);
        return id.getNamespace() + ":shaders/core/" + id.getPath() + captureType.getExtension();
    }

    // I would ideally want to do this, but I don't know if this is legal
    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/shaders/ProgramManager;createProgram()I"), locals = LocalCapture.CAPTURE_FAILHARD)
    public void createExtraPrograms(ResourceProvider provider, String $$1, VertexFormat $$2, CallbackInfo ci, ResourceLocation location, JsonObject json) throws IOException {
        this.geometryProgram = json.has("geometry") ? VeilShaderLoader.getOrCreate(provider, GL_GEOMETRY_SHADER, GsonHelper.getAsString(json, "geometry")) : null;
    }

    // This is here in case the above mixin is actually invalid. It works but is worse.
//    @Inject(method = "<init>", at = @At("TAIL"))
//    public void createExtraPrograms(ResourceProvider provider, String $$1, VertexFormat $$2, CallbackInfo ci) throws IOException {
//        // Unfortunately, I have to re-open the file. If anyone has a good way to re-use the parsed json please change this
//        ResourceLocation id = new ResourceLocation(this.name);
//        try (Reader reader = provider.openAsReader(new ResourceLocation(id.getNamespace(), "shaders/core/" + id.getPath() + ".json"))) {
//            JsonObject json = GsonHelper.parse(reader);
//            this.geometryProgram = json.has("geometry") ? VeilShaderLoader.getOrCreate(provider, GL_GEOMETRY_SHADER, GsonHelper.getAsString(json, "geometry")) : null;
//        } catch (Exception e) {
//            ChainedJsonException chainedJsonException = ChainedJsonException.forException(e);
//            chainedJsonException.setFilenameAndFlush(id.getPath());
//            throw chainedJsonException;
//        }
//        // Can't inject before the initial link, so it has to be linked again
//        if (this.geometryProgram != null) {
//            ProgramManager.linkShader((ShaderInstance) (Object) this);
//            this.updateLocations();
//        }
//    }

    // Have to clear sampler and uniform locations because this can be called multiple times before creating a new program now
    @Inject(method = "updateLocations", at = @At("HEAD"))
    public void updateLocations(CallbackInfo ci) {
        this.samplerLocations.clear();
        this.uniformLocations.clear();
    }

    @Inject(method = "attachToProgram", at = @At("TAIL"))
    public void attachToProgram(CallbackInfo ci) {
        if (this.geometryProgram != null) {
            this.geometryProgram.attachToShader((ShaderInstance) (Object) this);
        }
    }

    @Override
    public void recompile(ResourceProvider resourceProvider) throws IOException {
        VeilShaderLoader.compile(resourceProvider, ((ProgramAccessor) this.vertexProgram).getId(), GL_VERTEX_SHADER, this.vertexProgram.getName());
        VeilShaderLoader.compile(resourceProvider, ((ProgramAccessor) this.fragmentProgram).getId(), GL_VERTEX_SHADER, this.fragmentProgram.getName());
        if (this.geometryProgram != null) {
            VeilShaderLoader.compile(resourceProvider, ((ProgramAccessor) this.geometryProgram).getId(), GL_VERTEX_SHADER, this.geometryProgram.getName());
        }

        ProgramManager.linkShader((ShaderInstance) (Object) this);
        this.updateLocations();
    }

    @Nullable
    @Override
    public Program getGeometryProgram() {
        return geometryProgram;
    }

    @Override
    public void free() {
        if (this.geometryProgram != null) {
            this.geometryProgram.close();
        }
        this.geometryProgram = null;
    }
}
