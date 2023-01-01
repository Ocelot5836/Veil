package foundry.veil.ext;

import com.mojang.blaze3d.shaders.Program;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.NativeResource;

import java.io.IOException;

public interface ShaderInstanceExt extends NativeResource {

    /**
     * Recompiles and re-attaches all shaders.
     *
     * @param resourceProvider The provider for new source files
     * @throws IOException If an error occurs loading or compiling the shaders
     */
    void recompile(ResourceProvider resourceProvider) throws IOException;

    @Nullable
    Program getGeometryProgram();
}
