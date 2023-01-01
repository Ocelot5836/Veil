package foundry.veil.shader;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.Program;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import foundry.veil.mixin.client.shader.ProgramAccessor;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import net.minecraft.FileUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.opengl.GL43C.*;

/**
 * Loads shader types other than just vertex and fragment.
 *
 * @author Ocelot
 */
public final class VeilShaderLoader {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<Integer, Map<String, Program>> PROGRAMS = new Int2ObjectArrayMap<>(2);
    private static final Map<Integer, String> TYPES = Map.of(
            GL_VERTEX_SHADER, "vertex",
            GL_TESS_CONTROL_SHADER, "tesselation_control",
            GL_TESS_EVALUATION_SHADER, "tesselation_evaluation",
            GL_GEOMETRY_SHADER, "geometry",
            GL_FRAGMENT_SHADER, "fragment",
            GL_COMPUTE_SHADER, "compute"
    );
    private static final Map<Integer, String> EXTENSIONS = Map.of(
            GL_VERTEX_SHADER, Program.Type.VERTEX.getExtension(),
            GL_TESS_CONTROL_SHADER, ".tcsh",
            GL_TESS_EVALUATION_SHADER, ".tesh",
            GL_GEOMETRY_SHADER, ".gsh",
            GL_FRAGMENT_SHADER, Program.Type.FRAGMENT.getExtension(),
            GL_COMPUTE_SHADER, ".csh"
    );

    private VeilShaderLoader() {
    }

    // This exists to mirror vanilla in case someone needs it I guess

    /**
     * Retrieves the specified shader program from the global mapping.
     *
     * @param type The type to get a program for
     * @param name The name of the program
     * @return The shader loaded or <code>null</code> if there is no shader
     */
    @Nullable
    public static Program getProgram(int type, String name) {
        if (type == GL_VERTEX_SHADER) {
            return Program.Type.VERTEX.getPrograms().get(name);
        }
        if (type == GL_FRAGMENT_SHADER) {
            return Program.Type.FRAGMENT.getPrograms().get(name);
        }
        return PROGRAMS.containsKey(type) ? PROGRAMS.get(type).get(name) : null;
    }

    /**
     * Removes the specified shader program from the global mapping.
     *
     * @param type The type to get a program for
     * @param name The name of the program
     */
    public static void removeProgram(int type, String name) {
        if (!PROGRAMS.containsKey(type)) {
            return;
        }

        // No need to remove the map because it will probably be needed later anyway
        PROGRAMS.get(type).remove(name);
    }

    /**
     * Retrieves a readable name for a shader type. Supports all shader types instead of just vertex and fragment.
     *
     * @param type The GL enum for the type
     * @return The readable name or a hex value if the type is unknown
     */
    public static String getTypeName(int type) {
        String value = TYPES.get(type);
        return value != null ? value : "0x" + Integer.toHexString(type);
    }

    /**
     * Retrieves the file extension of a shader type.
     *
     * @param type The GL enum for the type
     * @return The extension of the file to use or <code>glsl</code> if the type is unknown
     */
    public static String getTypeExtension(int type) {
        String value = EXTENSIONS.get(type);
        return value != null ? value : "glsl";
    }

    /**
     * Creates a new preprocessor for standard shader programs.
     *
     * @param resourceProvider The provider for resources
     * @param location         The location of the shader
     * @return A new Glsl preprocessor
     */
    public static GlslPreprocessor createPreprocessor(ResourceProvider resourceProvider, ResourceLocation location) {
        String absolutePath = FileUtil.getFullResourcePath(location.getPath()); // TODO make sure this absolute path is correct
        return new GlslPreprocessor() {
            private final Set<String> importedPaths = new HashSet<>();

            @Override
            public String applyImport(boolean absolute, String path) {
                path = FileUtil.normalizeResourcePath((absolute ? absolutePath : "shaders/include/") + path);
                if (!this.importedPaths.add(path)) {
                    return null;
                }

                try (Reader reader = resourceProvider.openAsReader(new ResourceLocation(location.getNamespace(), path))) {
                    return IOUtils.toString(reader);
                } catch (IOException var9) {
                    LOGGER.error("Could not open GLSL import {}: {}", path, var9.getMessage());
                    return "#error " + var9.getMessage();
                }
            }
        };
    }

    /**
     * Loads and compiles shader source for the specified shader.
     *
     * @param resourceProvider The provider for resources
     * @param type             The type of shader to compile
     * @param name             The name of the shader to compile
     * @throws IOException If the shader had to be compiled and an error occurred
     */
    public static void compile(ResourceProvider resourceProvider, int shaderId, int type, String name) throws IOException {
        RenderSystem.assertOnRenderThread();
        ResourceLocation id = new ResourceLocation(name);
        ResourceLocation location = new ResourceLocation(id.getNamespace(), "shaders/core/" + id.getPath() + getTypeExtension(type));
        Resource resource = resourceProvider.getResourceOrThrow(location);

        try (InputStream stream = resource.open()) {
            String source = IOUtils.toString(stream, StandardCharsets.UTF_8);
            if (source == null) {
                throw new IOException("Could not load program " + getTypeName(type));
            }

            GlStateManager.glShaderSource(shaderId, createPreprocessor(resourceProvider, location).process(source));
            GlStateManager.glCompileShader(shaderId);
            if (GlStateManager.glGetShaderi(shaderId, 35713) == 0) {
                String error = StringUtils.trim(GlStateManager.glGetShaderInfoLog(shaderId, 32768));
                throw new IOException("Couldn't compile " + getTypeName(type) + " program (" + resource.sourcePackId() + ", " + name + ") : " + error);
            }
        }
    }

    /**
     * Attempts to re-use an existing shader before compiling a new one.
     *
     * @param resourceProvider The provider for resources
     * @param type             The type of shader to compile
     * @param name             The name of the shader to compile
     * @return A previously loaded shader of the specified type and name or a newly loaded one
     * @throws IOException If the shader had to be compiled and an error occurred
     */
    public static Program getOrCreate(ResourceProvider resourceProvider, int type, String name) throws IOException {
        Program cachedProgram = getProgram(type, name);
        if (cachedProgram != null) {
            return cachedProgram;
        }

        // Make sure vanilla types go through vanilla system
        if (type == GL_VERTEX_SHADER || type == GL_FRAGMENT_SHADER) {
            ResourceLocation id = new ResourceLocation(name);
            ResourceLocation location = new ResourceLocation(id.getNamespace(), "shaders/core/" + id.getPath() + getTypeExtension(type));
            Resource resource = resourceProvider.getResourceOrThrow(location);

            try (InputStream stream = resource.open()) {
                return Program.compileShader(type == GL_VERTEX_SHADER ? Program.Type.VERTEX : Program.Type.FRAGMENT, name, stream, resource.sourcePackId(), createPreprocessor(resourceProvider, location));
            }
        }

        int shaderId = GlStateManager.glCreateShader(type);
        try {
            compile(resourceProvider, shaderId, type, name);
        } catch (IOException e) {
            GlStateManager.glDeleteShader(shaderId);
            throw e;
        }

        Program program = new VeilProgram(type, shaderId, name);
        PROGRAMS.computeIfAbsent(type, __ -> new HashMap<>()).put(name, program);
        return program;
    }

    /**
     * Closes all shader programs.
     */
    public static void free() {
        RenderSystem.assertOnRenderThread();
        Set<Program> programs = new HashSet<>();
        PROGRAMS.values().forEach(map -> programs.addAll(map.values()));
        programs.forEach(Program::close); // This is because programs remove themselves from the map
    }

    public static class VeilProgram extends Program {

        private final int type;

        public VeilProgram(int type, int id, String name) {
            super(Type.VERTEX, id, name);
            this.type = type;
        }

        @Override
        public void close() {
            int id = this.getId();
            if (id != -1) {
                RenderSystem.assertOnRenderThread();
                GlStateManager.glDeleteShader(id);
                ((ProgramAccessor) this).setId(-1);
                VeilShaderLoader.removeProgram(this.type, this.getName());
            }
        }
    }
}
