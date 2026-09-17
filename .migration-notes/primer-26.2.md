# Minecraft 26.1.x -> 26.2 Mod Migration Primer

This is a high level, non-exhaustive overview on how to migrate your mod from 26.1.x to 26.2. This does not look at any specific mod loader, just the changes to the vanilla classes.

This primer is licensed under the [Creative Commons Attribution 4.0 International](http://creativecommons.org/licenses/by/4.0/), so feel free to use it as a reference and leave a link so that other readers can consume the primer.

If there's any incorrect or missing information, please file an issue on this repository or ping @ChampionAsh5357 in the Neoforged Discord server.

Thank you to:

- @RogueLogix for reviews on the Blaze3d changes
- @cassiancc for confirmation on P2P friends being Xbox friends and more specific info on `TabNavigationBar` and `MenuTabBar`
- @gigahertz for wording

## Pack Changes

There are a number of user-facing changes that are part of vanilla which are not discussed below that may be relevant to modders. You can find a list of them on [Misode's version changelog](https://misode.github.io/versions/?id=26.2&tab=changelog).

## Too Many Rendering Changes

### Vulkan

Vanilla has added support for the Vulkan graphics API, which can be set in the options menu. As such, for all classes in `com.mojang.blaze3d.opengl`, there is generally a parallel found in `com.mojang.blaze3d.vulkan`. With this change, more of the Blaze3d components has been generalized further.

### Blend Factors

`DestFactor` and `SourceFactor` have been combined into a single `BlendFactor`, which is used within `BlendEquation`s and `BlendFunction`s. How these `BlendFactor`s are applied is through `BlendOp`, which defines the operation to merge the source and destination. `BlendOp#ADD` is used as a default when not specified in a `BlendFunction`.

### GPU Formats

`TextureFormat` and the buffer formats of `VertexFormatElement` have been replaced by the `GpuFormat` enum. The names of each enum entry can be broken down into three components: a channel with their bit size, an underscore, and finally the data type. Here are some examples:

- `R8_UNORM`: An unsigned normalized value with a single 8-bit red channel.
- `RGBA32_SINT`: A signed integer with 32-bit red, green, blue, and alpha channels.
- `D32_FLOAT_S8_UINT`: A float with a 32-bit depth channel, and a unsigned integer with an 8-bit stencil channel.

The previous data types of `RGBA8`, `RED8`, `RED8I`, `DEPTH32` can be represented using one of these `GpuFormat`s, though their mappings do not have one-to-one parity for the GL codes previously used.

### Rewriting Vertex Formats

`VertexFormat` along with `VertexFormatElement` has been partially rewritten into a more dynamic framework. There is no longer a set of existing `VertexFormatElement`s. Instead, the elements are constructed when building the `VertexFormat` by adding attributes via `$Builder#addAttribute`. A `VertexFormat` can define at most sixteen elements or attributes, each providing at least the name and `GpuFormat`.

`VertexFormat$IndexType` and `VertexFormat$Mode` have also been moved into their own `IndexType` and `PrimitiveTopology` enums, respectively. This is because `RenderPipeline`s can now define up to sixteen vertex buffers with different `VertexFormat`s, but must be applied to the same `PrimitiveTopology`.

### Multiple Color Target States

With this expansion, `RenderPipeline`s can also specify up to eight `ColorTargetState`s. This must match the number of color attachments specified when creating a `RenderPass` (typically 1 in vanilla usecases).

### Bind Group Layouts

`BindGroupLayout` is a collection of sampler names and `$UniformDescription`s, replacing the single list of sampler names and `RenderPipeline#UniformDescription`. Due to this separate object, `RenderPipeline$Snippet`s that were made up of only samplers and uniforms are now decoupled, allowing for a modular, non-repeatable implementation when dealing with many pipelines and layouts. All vanilla layouts are stored in `BindGroupLayouts` and are attached to a `RenderPipeline` via `RenderPipeline$Builder#withBindGroupLayout`.

```java
public static final BindGroupLayout EXAMPLE_LAYOUT = BindGroupLayout.builder()
    // Specifies that the shaders have a 'Sampler0' sampler
    .withSampler("Sampler0")
    // Specifies that the shaders have access to the 'Globals' uniform
    .withUniform("Globals", UniformType.UNIFORM_BUFFER)
    .build();

public static final RenderPipeline EXAMPLE_PIPELINE = RenderPipeline.builder()
    .withLocation(Identifier.fromNamespaceAndPath("examplemod", "pipeline/example"))
    .withVertexShader(Identifier.fromNamespaceAndPath("examplemod", "example_shader"))
    .withFragmentShader(Identifier.fromNamespaceAndPath("examplemod", "example_shader"))
    .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
    // Specify the layouts to use
    .withBindGroupLayout(EXAMPLE_LAYOUT)
    // Can use multiple layouts
    .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
    .build();
```

Note that all `BindGroupLayout`s added to a `RenderPipeline` must not contain any duplicate entries (e.g. two shaders named `Sampler0`, two uniforms named `Globals`) and by extension the same `BindGroupLayout` multiple times, or an error will be thrown during the shader compilation process.

### Render Areas

`RenderPass`es can now define a rectangle within a texture to draw its data to via `CommandEncoder#createRenderPass`. If this not specified, it defaults to the size of the color `GpuTextureView`.

### Replacing `ChatFormatting` in Components

`ChatFormatting` has been mostly gutted in favor of using `Style` for `Component`s. The text color can be set via `Style#withColor`, while the formatting can be set using `withBold`, `withItalic`, `withUnderlined`, `withStrikethrough`, and `withObfuscated`. `ChatFormatting#RESET` is the same as using a different component.

### Gui Reorganization

The `Gui` class has been reorganized to handle all of the components of the graphical user interface (as the name implies). As such, fields like the current `Screen` or `ChatListener` have been moved off of their previous class (e.g. `Minecraft`) and into `Gui`. Additionally, the in-game heads-up display has been moved into a separate class called `Hud`, which `Gui` takes in.

As such, some field and method calls will need to go through `Gui` or `Hud`:

```diff
// Getting the current screen
- Minecraft.getInstance().screen
+ Minecraft.getInstance().gui.screen()

// Setting the overlay message
- Minecraft.getInstance().gui.setOverlayMessage(...)
+ Minecraft.getInstance().gui.hud.setOverlayMessage(...)
```

### Font Preparations

`Font` draw methods have been completely removed, and instead need to be prepared and handled manually. This should only be done if your text cannot be submitted as a feature via `OrderedSubmitNodeCollector#submitText`.

The text rendering pipeline works like so: the `Font` prepares the text into a `$PreparedText`, which can visit the glyphs in the string. Then, a `$GlyphVisitor` loops through all the glyph components, which typically call `$GlyphVisitor#acceptRenderable`. `acceptRenderable` is where the text is uploaded to the buffer, ready to be drawn to the screen.

```java
// Font has `prepareText` if don't want to implement the glyph parsing manually
// For some Font font
Font.PreparedText text = font.prepareText(
    // The text to draw
    "Hello world!",
    // The position of the text on the string
    0, 0,
    // The color of the text
    0xFF00FF00,
    // Whether to draw the drop shadow
    false,
    // The background color of the text
    0
);

// We can create a glyph visitor to loop through the text components
public class ExampleVisitor implements Font.GlyphVisitor {

    @Override
    public void acceptRenderable(TextRenderable renderable) {
        // Both glyphs and effects call this by default
        // If you want more specialized rendering, override:
        // - 'acceptGlyph' for text characters
        // - 'acceptEffect' for any effects applied to the characters

        // Render to the buffer
        VertexConsumer buffer = Minecraft.getInstance().gameRenderer.renderBuffers().bufferSource()
            .getBuffer(renderable.renderType(Font.DisplayMode.NORMAL);
        renderable.render(new Matrix4f(), buffer, 0xF000F0, false);
    }
}

// Then, we can visit all the renderables in our prepared text,
// which will upload them to the buffer.
text.visit(new ExampleVisitor());
```

### Submitting Gizmos

Gizmos are now rendered using the `GizmoFeatureRenderer`. Gizmos that do not call `GizmoProperties#setAlwaysOnTop` are render after translucents but before the translucent terrain features, while those that do are rendered after all other features.

### Feature Rendering: The Takeover

The feature rendering system has been overhauled to completely replace `MultiBufferSource` and any direct vertex uploads outside of vanilla chunk rendering.

For users of the system, `FeatureRenderDispatcher` now takes in a `SubmitNodeStorage` as part of its render methods. This allows for a specific subset of features to be rendered depending on user implementation rather than only at vanilla entrypoints:

```java
// Create your own storage to submit elements to
SubmitNodeStorage collector = new SubmitNodeStorage();

collector.submitModel(...);

// Render the features through the dispatcher
Minecraft.getInstance().gameRenderer.featureRenderDispatcher().renderAllFeatures(collector);

// If you need more granular control of when individual passes should be rendered,
// you can use `FeatureRenderDispatcher#prepareFrame` instead.
FeatureRenderDispatcher.PreparedFrame frame = Minecraft.getInstance()
    .gameRenderer.featureRenderDispatcher().prepareFrame(collector);

frame.executeSolid(); // Replaces `renderSolidFeatures`
frame.executeTranslucent(); // Replaces `renderTranslucentFeatures`
frame.executeTranslucentAfterTerrain(); // Replaces `renderTranslucentParticles`
frame.executeAlwaysOnTop(); // Replaces gizmo rendering

// The frame must be closed once finished.
frame.close();
```

For modders that want to create custom features outside of the already existing submissions, three things a required: a `SubmitNode` to represent the data of the submitted feature, the `FeatureRenderPhase`(s) to store the `SubmitNode`s and supply them to the correct renderer, and the `FeatureRenderer` responsible for rendering the submissions.

A `SubmitNode` is, as the name implies, a node for some submitted element. It holds a record of the submitted data that is used by the specified `FeatureRenderer`. `SubmitNode#featureType` is used to link the node to the `FeatureRenderer` that should be used.

`SubmitNode` contains two other useful subtypes: `TranslucentSubmit`, which specifies the squared distance to the camera for ordering (`distanceToCameraSq`); and `BatchableSubmit`, which just groups similar features together using the `batchKey` such that they are rendered one after the other.

```java
// The node should contain whatever data is necessary to properly render
// the object.
public record ExampleSubmit(Matrix4fc pose, RenderType type, ...) implements TranslucentSubmit, BatchableSubmit {
    @Override
    public FeatureRendererType<? extends TranslucentSubmit> featureType() {
        // The identifier for our feature renderer.
    }

    @Override
    public Object batchKey() {
        // The batch key should be an object that allows the renderer or
        // phase to hold its state for as long as possible before switching.
        return this.type;
    }

    @Override
    public float distanceToCameraSq() {
        // Compute the camera distance.
        return TranslucentSubmit.computeDistanceToCameraSq(this.pose);
    }
}
```

The `FeatureRenderPhase` is then responsible for collecting these submissions and supplying them to an `$Output` for rendering. `FeatureRenderPhase` defines three methods: `submit` which is what the `OrderedSubmitNodeCollector#submit*` methods delegate to, `isEmpty` for checking if there are any submissions to render, and `sortInto` for passing the submissions to their appropriate group via `$Output#accept`.

If you are making use of `SubmitNode` or `TranslucentSubmit` with no additional sorting behavior, then you can instead make use of `SimpleFeatureRenderPhase` and `TranslucentFeatureRenderPhase`, respectively. Alternatively, you can instead submit to an existing render phase in `SubmitNodeCollection`. It purely depends on how much the render order of your elements matter.

```java
// Assume we have some method of injecting into `SubmitNodeCollection` collection

// `TranslucentFeatureRenderPhase` is used because of `TranslucentSubmit`
public final TranslucentFeatureRenderPhase examplePhase = new TranslucentFeatureRenderPhase();

// Assuming we have some method of making `SubmitNodeCollection#allPhases` mutable

// The order of the phases in this list does not matter. It is only used by the
// `SubmitNodeStorage` to know what phases can be rendered.
collection.allPhases.add(examplePhase);
```

Finally, there is the `FeatureRenderer` used to render the submitted elements to the desired output. The rendering process is broken into two sections: preparation and execution.

The preparation section is responsible for turning the submitted elements into an intermediate state, usually buffer data stored in the `StagedVertexBuffer`. Preparation can be broken into three parts: `beginPrepare` for setting up any state required to generate the intermediate buffer data, `prepareGroup` for creating the draw calls to generate the intermediate buffer data, and `finishPrepare` to restore the initial state and setup anything for the execution section. The `StagedVertexBuffer` is uploaded after the preparation stage has been completed, so the only thing that should be stored during `prepareGroup` is the draw reference (for `StagedVertexBuffer` this is `StagedVertexBuffer$Draw`).

The execution section is responsible for drawing the intermediate buffer data to the desired output (e.g. `RenderTarget`). Execution can be broken into two parts: `executeGroup` for using a `RenderPass` to draw the target color and depth texture, and `finishExecute` for cleaning up the renderer for the next render frame.

The `FeatureRenderer` is also `AutoCloseable` in case the renderer stores allocations that need to be released.

If your `SubmitNode` contains a `RenderType`, then you can extend `RenderTypeFeatureRenderer` instead. `RenderTypeFeatureRenderer` functions similarly to the now removed `MultiBufferSource`, where within `buildGroup`, the `VertexConsumer` can be obtained from the render type via `getVertexBuffer`, which can then be written to. 

```java
public class ExampleFeatureRenderer extends RenderTypeFeatureRenderer<ExampleSubmit> {
    // The unique identifier that represents this feature renderer.
    public static final FeatureRendererType<ExampleSubmit> TYPE = FeatureRenderer.type("examplemod:example_submit");

    @Override
    protected void buildGroup(FeatureFrameContext context, List<ExampleSubmit> submits) {
        // For each submit
        for (ExampleSubmit submit : submits) {
            // Get the `VertexConsumer` to write to
            VertexConsumer builder = this.getVertexBuilder(submit.renderType());
            
            // Write the vertex data
            builder.addVertex(...);
        }
    }
}
```

From there, everything just needs to be linked together.

First, `SubmitNode#featureType` must return the renderer to use:

```java
public record ExampleSubmit(Matrix4fc pose, RenderType type, ...) implements TranslucentSubmit, BatchableSubmit {
    @Override
    public FeatureRendererType<? extends TranslucentSubmit> featureType() {
        // The identifier for our feature renderer.
        return ExampleFeatureRenderer.TYPE;
    }
}
```

Then, the `FeatureRenderer` must be linked to its type in `FeatureRendererDispatcher`:

```java
// Assume we can inject into the end of the `FeatureRendererDispatcher` constructor
// and that `featureRenderers` is accessible.
public FeatureRenderDispatcher(...) {
    // Injecting into the tail
    this.featureRenderers.put(ExampleFeatureRenderer.TYPE, new ExampleFeatureRenderer());
}
```

And, if you created a new `FeatureRenderPhase`, you will need to add the phase for execution within `FeatureRenderDispatcher$PreparedFrame`:

```java
// Assume we can inject into `FeatureRenderDispatcher$PreparedFrame`
// Since our render phase contains translucent elements, we will somehow inject
// into `executeTranslucent` before gizmos.
public void executeTranslucent() {
    // ...

    for (SubmitNodeCollection collection : submitNodeStorage.getSubmitsPerOrder().values()) {
        // ...
        this.executePhase(collection.shapeOutlines, context);

        // Add our own phase here
        this.executePhase(collection.examplePhase, context);

        this.executePhase(collection.gizmos, context);
    }

    // ...
}
```

### Dispatching Picture-In-Picture

Direct access to the `MultiBufferSource$BufferSource` has been removed from the `PictureInPictureRenderer`. Instead `prepare` now takes in the `FeatureRenderDispatcher` to render the elements to a texture, while `renderToTexture` takes in the `SubmitNodeCollector`.

```java
public class ExamplePIPRenderer extends PictureInPictureRenderer<ExamplePIPRenderState> {

    // The constructor is no longer required as it doesn't take in the `MultiBufferSource$BufferSource`.

    @Override
    protected void renderToTexture(ExamplePIPRenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector) {
        // Submit elements to render to the texture here.
    }

    // ...
}
```

### Shape Outlines Feature

Shape outlines is a new render feature that replaces `ShapeRenderer`, allowing for the outline of a `VoxelShape` to be rendered via `OrderedSubmitNodeCollector#submitShapeOutline`. This takes in the `PoseStack` of where the element should be placed, the `VoxelShape` to render, the `RenderType` to use, the `color` of the outline, the `width` of the line being outlined, and whether it should be rendered before or after the translucent terrain is rendered.

- `assets/minecraft/shaders/core`
    - `rendertype_text` -> `text`
    - `rendertype_text_background` -> `text_background`
    - `rendertype_text_background_see_through` -> `text_background` with `IS_SEE_THROUGH` shader define
    - `rendertype_text_intensity` -> `text` with `IS_GRAYSCALE` shader define
    - `rendertype_text_intensity_see_through` -> `text` with `IS_GRAYSCALE` and `IS_SEE_THROUGH` shader defines
    - `rendertype_text_see_through` -> `text` with `IS_SEE_THROUGH` shader define
- `com.mojang.blaze3d`
    - `GraphicsWorkarounds` -> `GlHeuristics`, `HintsAndWorkarounds`; not one-to-one
        - `alwaysCreateFreshImmediateBuffer` is removed
    - `GpuDeviceLossException` - An exception thrown if the backing GPU device crashes or becomes unresponsive.
    - `GpuFormat` - An object representing the format and color components of a pixel.
        - `$ComponentType` - An enum representing the pixel format and its associated byte size.
- `com.mojang.blaze3d.audio.OpenAlUtil#checkALError`, `checkALCError`, `audioFormatToOpenAl` are now `public` from package-private
- `com.mojang.blaze3d.buffers`
    - `GpuBuffer`
        - `USAGE_INDIRECT_PARAMETERS` - A flag that indicates the buffer can be used in indirect draw and compute dispatch calls.
        - `$MappedView` -> `GpuBufferSlice$MappedView`, not one-to-one
        - `$Usage` can now only target `ElementType#TYPE_USE`
    - `GpuFence#awaitCompletion` now takes in a nanosecond timeout instead of a millisecond timeout
- `com.mojang.blaze3d.opengl`
    - `BufferStorage`
        - `mapBuffer` replaced by `GpuBuffer#map`, `GpuBufferSlice#map`; not one-to-one
        - `createBuffer` no longer takes in the supplied `String` label
    - `DirectStateAccess` methods are now `public` from package-private
        - `bindFrameBufferTextures` now has an overload that takes in arrays for the color and mip levels, along with an `int` for the mip level depth
    - `FrameBufferAttachment` - An object that is bound to a framebuffer, like a texture.
    - `FrameBufferCache` - A cache that creates framebuffers given its attachments.
    - `GlBuffer` is now abstract, taking in a `boolean` for whether a persistent buffer can be mapped rather than the persistent `ByteBuffer`, and no longer taking in the supplied `String` label
        - `MEMORY_POOl` -> `MEMORY_POOL`
        - `closed` -> `$Direct#closed`, now `private` from `protected`
        - `handle` field is now `private` from `protected`
            - Accessible through the `public` `handle` method
        - `persistentBuffer` replaced by `mappedBuffer`, not one-to-one
        - `mappingFlags` - The flags that define how the persistent buffer should be mapped.
        - `mappingRefCount` - The number of buffers currently mapped.
        - `checkCanBeUsed` - Checks whether a non-persistent buffer isn't mapped when used in a command.
        - `$Direct` - An implementation that owns the used buffer.
            - `canPersistentMap` - Whether the persistent buffer can be used while mapped.
                - This is an internal flag. `DeviceFeatures#persistentMapping` should be used to check for the feature.
        - `$GlMappedView` replaced by `GpuBufferSlice$MappedView`, not one-to-one
    - `GlCommandEncoder` is now `AutoCloseable`
        - `MAX_SUBMITS_IN_FLIGHT` - The maximum number of submissions that can be sent to the buffer at any given time.
        - `currentSubmitIndex` - The current index of the next submission.
        - `currentSubmitSlot` - The current slot of the next submission to make.
        - `awaitSubmit` - Attempts to clear the previous submit fence in the current slot, returning `true` if successful.
        - `finishRenderPass` -> `CommandEncoder#submitRenderPass`
        - `executeDraw` now takes takes in an `int` for the first instance used for fetching vertex attributes
        - `executeDrawIndirect` - Executes the draw call using the indirect API.
        - `executeDraws` - Executes a multi-draw call.
        - `presentTexture` now takes in the swapchain width and height `int`s 
    - `GlConst`
        - `GL_DEPTH_TEXTURE_MODE`, `GL_ALPHA_BIAS` are removed
        - `toGl(DestFactor)`, `toGl(SourceFactor)` -> `toGl(BlendFactor)`
        - `toGl` now has an overload taking the in the `BlendOp` to map
        - `toGl(NativeImage$Format)` is removed
        - `glFormatChannelCount` - Returns the number of channels the format has.
        - `isGlFormatInteger` - Returns whether the format is backed by an integer-like data type.
        - `isFormatNormalized` - Returns whether the format uses normalized data.
        - `toGlInternalId`, `toGlExternalId`, `toGlType` now take in a `GpuFormat` instead of the `TextureFormat`
    - `GlDebugLabel#applyLabel` now takes in the supplied `String` label
    - `GlDevice`
        - `USE_GL_ARB_base_instance` - Whether to enable the `ARB_base_instance` extension, allowing for the the offset used for instance rendering to be specified.
        - `USE_GL_ARB_draw_indirect` - Whether to enable the `ARB_draw_indirect` extension, allowing for the consumption of arguments from buffer object memory.
        - `USE_GL_ARB_multi_draw_indirect` - Whether to enable the `ARB_multi_draw_indirect` extension, allowing multiple draws to be invoked from a single procedural call.
        - `USE_GL_ARB_shader_draw_parameters` - Whether to enable the `ARB_shader_draw_parameters` extension, providing access to the values passed into the draw commands.
        - `frameBufferCache` - Returns the framebuffer cache.
    - `GlHeuristics` class is now `public` from package-private
    - `GlProgram`
        - `setupUniforms` -> `setupBindGroupLayouts`, now taking a list of `BindGroupLayout`s instead of uniforms and samplers directly
        - `link` now takes in an array of `VertexFormat`s rather than a single one
    - `GlRenderPass` now takes in the default `ScissorState` and how many color textures are used.
        - `MAX_VERTEX_BUFFERS` -> `RenderPass#MAX_VERTEX_BUFFERS`, not one-to-one
        - `vertexBuffers` is now an array of `GpuBufferSlice`s instead of `GpuBuffer`s
        - `vertexBufferDirty` - Returns whether there is a dirty vertex buffer to upload.
        - `colorAttachmentCount` - The number of color textures used.
    - `GlStateManager`
        - `_blendEquationSeparate`, `glBlendEquationSeparate` - Sets the blend mode of the RGB and alpha channels.
        - `_disableBlend`, `_enableBlend` now take in an `int` index for the buffer the blend is applied to
        - `_colorMask` now has an overload that specifies the index of the buffer to apply the write mask to
        - `_clearBuffer` - Clears the give buffer using the associated value.
        - `$BlendState`
            - `modeRgb` - The blend mode of the RGB color channels.
            - `modeAlpha` - The blend mode of the alpha channel.
    - `GlSurface` - The OpenGL implementation of the surface backend.
    - `GlTexture` now implements `FrameBufferAttachment`
        - The constructor now takes in the `GpuFormat` instead of the `TextureFormat`, and the `FrameBufferCache`
        - `getFbo` replaced by `FrameBufferCache#getFbo`
    - `GlTextureView` now implements `FrameBufferAttachment`
        - The constructor now takes in the `FrameBufferCache`
        - `getFbo` replaced by `FrameBufferCache#getFbo`
    - `GlTimerQuery` replaced by `GlQueryPool`
    - `GlTransientMemory` - The OpenGL implementation of the `TransientMemory`.
    - `GlUtil` - A utility for helping with OpenGL call determinations.
    - `Uniform$Utb` now takes in the `GpuFormat` instead of the `TextureFormat`
    - `VertexArrayCache#bindVertexArray` now takes in arrays for the `VertexFormat` bindings and `GpuBufferSlice` buffers along with the last bound `$VertexArray`, returning the newly bound `$VertexArray`
- `com.mojang.blaze3d.pipeline`
    - `BindGroupLayout` - The samplers and uniforms a shader will use.
    - `BlendEquation` - An equation that blends the source and destination factors using the provided operation.
    - `BlendFunction` now takes in `BlendEquation`s, `BlendFactor`s, or `BlendOp`s instead of `SourceFactor`s and `DestFactor`s
    - `ColorTargetState` now takes in the `GpuFormat`
        - `MAX_COLOR_TARGETS` - The maximum number of color states a buffer can use.
        - `$WriteMask` can now only target `ElementType#TYPE_USE`
    - `RenderPipeline` now takes in a list of `BindGroupLayout`s instead of uniforms and samplers directly, and an array of `ColorTargetState`s and `VertexFormat`s instead of just one
        - `getSamplers`, `getUniforms` -> `getBindGroupLayouts`
            - Can get the samplers and uniforms via `BindGroupLayout#flattenSamplers`, `flattenUniforms`
        - `getColorTargetStates` - Returns the array of color target states.
            - The original now just returns the first one
        - `getVertexFormat` replaced by `getVertexFormatBinding`, taking in the bound index
        - `getVertexFormatBindings` - Returns the array of vertex formats.
        - `getVertexFormatMode` -> `getPrimitiveTopology`
        - `$Builder`
            - `withUniform`, `withSampler` -> `withBindGroupLayout`
                - Can add samplers and uniforms via `BindGroupLayout$Builder#withSampler`, `withUniform`
            - `withColorTargetState` now takes in the `int` index to bind
            - `withUnusedColorTargetState` - Unbinds the specified `int` index.
            - `withVertexFormat` -> `withVertexBinding`, now taking in the `int` index to bind, and no longer taking in the `VertexFormat$Mode`
            - `withPrimitiveTopology` - Sets the topology the pipeline uses.
        - `$Snippet`
            - `samplers`, `uniforms` -> `bindGroupLayouts`, not one-to-one
            - `colorTargetState` -> `colorTargetStates`, now a nullable array of `ColorTargetState`s instead of an optional single state
            - `activeColorTargetStateCount` - The number of active color states used by the pipeline.
            - `vertexFormat` -> `vertexFormatPerBuffer`, now a nullable array of `VertexFormat`s instead of an optional single state
        - `$UniformDescription` -> `BindGroupLayout$UniformDescription`
    - `RenderTarget` now takes in the `GpuFormat`
        - `blitToScreen` is removed
            - Usage replaced by `GpuSurface#blitFromTexture`
        - `blitAndBlendToTexture` now takes in a `GpuTextureView` for the output depth texture
    - `TextureTarget` now takes in the `GpuFormat`
- `com.mojang.blaze3d.platform`
    - `BackendOptions` record is removed
    - `BlendOp` - The operation performed when blending the source and destination outputs.
    - `ClientShutdownWatchdog#startShutdownWatchdog` now takes in a `boolean` of whether to force shutdown with a `-8` exit call
    - `DestFactor`, `SourceFactor` -> `BlendFactor`
    - `GLX`
        - `_initGlfw` no longer takes in the `BackendOptions`
        - `getGlfwPlatform` - Returns the currently selected platform.
    - `InputConstants$Value` can now only target `ElementType#TYPE_USE`
    - `MacosUtil#setWindowColorSpaceForOpenGLBecauseGLFWDoesnt` - Sets the window color space.
    - `Monitor` is now a record, taking in the `String` monitor name
        - Original constructor behavior is handled through `Monitor#tryCreate`
        - `refreshVideoModes` is removed
        - `getVideoModeIndex` -> `indexOfMode`
        - `getCurrentMode` is removed
        - `getX`, `getY` -> `x`, `y`
        - `getMode` -> `mode`
        - `getModeCount` -> `modeCount`
        - `getMonitor` is removed
    - `MonitorCreator` interface is removed
        - Use `Monitor#tryCreate` instead
    - `NativeImage#getPixelBytes` - Gets a buffer of the pixels in the image.
    - `NativeLibrariesBootstrap` - A bootstrap for validating and loading the required native libraries.
    - `ScreenManager` -> `MonitorManager`, not one-to-one
    - `TextureUtil#writeAsPNG` now only allows `RGBA8_UNORM` formatted textures to be written to disk.
    - `Window` now takes in a `boolean` for whether to use exclusive fullscreen and the `MonitorManager`
        - `updateVsync` is removed
            - Usage replaced by `Window#setMode` and `WindowEventHandler#framebufferSizeChanged`
        - `isResized`, `resetIsResized` are removed
    - `WindowEventHandler#framebufferSizeChanged` - Handles when the window buffer size has changed.
- `com.mojang.blaze3d.resource.RenderTargetDescriptor` now takes in a `Vector4fc` for the clear color instead of an `int`, and the `GpuFormat`
- `com.mojang.blaze3d.shaders`
    - `GpuDebugOptions` now takes in a `boolean` of whether to use validation layers; only used by the Vulkan backend
    - `UniformType` no longer specifies the name
- `com.mojang.blaze3d.systems`
    - `BackendCreationException` now takes in a `$Reason` and optional string list of missing capabilities
        - `getReason` - The reason for the creation error.
        - `getMissingCapabilities` - The list of missing capabilities.
        - `$Reason` - The reasons why the backend creation may throw an exception.
    - `CommandEncoder` now takes in the `TracyGpuProfiler`
        - `backend` - Returns the encoder backend.
        - `submit` - Submits the elements to the GPU and ends the frame.
        - `submitRenderPass` - Submits the current render pass, handled as part of the try-with-resources.
        - `presentTexture` -> `GpuSurface#blitFromTexture`, not one-to-one
        - `timerQueryBegin`, `timerQueryEnd` replaced by `writeTimestamp`
        - `createRenderPass`
            - Now takes in an optional `Vector4fc` instead of an `int` for the clear color
            - Now has an overload that takes in a `$RenderArea` for where to draw to
            - Now has an overload that only takes in a `RenderPassDescriptor`
        - `clearColorTexture`, `clearColorAndDepthTextures` now take in an optional `Vector4fc` instead of an `int` for the clear color
        - `mapBuffer` replaced by `GpuBuffer#map`, `GpuBufferSlice#map`
        - `transientMemory` - Gets the transient memory of the encoder.
        - `writeToTexture` overload
            - No longer takes in the width, height, and source XY of the image
            - No longer takes in the `NativeImage$Format`
        - `copyBufferToTexture` - Copies the buffer slice with the given parameters to the destination texture.
    - `CommandEncoderBackend`
        - `isInRenderPass` -> `CommandEncoder#isInRenderPass`, now `protected` from `public`
        - `submitRenderPass` - Submits the current render pass, handled as part of the try-with-resources.
        - `presentTexture` -> `GpuSurface#blitFromTexture`, not one-to-one
        - `timerQueryBegin`, `timerQueryEnd` replaced by `writeTimestamp`
        - `createRenderPass` now only takes in the `RenderPassDescriptor`
        - `clearColorTexture`, `clearColorAndDepthTextures` now take in an optional `Vector4fc` instead of an `int` for the clear color
        - `mapBuffer` replaced by `GpuBuffer#map`, `GpuBufferSlice#map`
        - `transientMemory` - Gets the transient memory of the encoder.
        - `writeToTexture(GpuTexture, ByteBuffer, NativeImage.Format, int, int, int, int, int, int)` is removed
            - Original method no longer takes in the source XY and swaps the `NativeImage` for a `ByteBuffer`
        - `copyBufferToTexture` - Copies the buffer slice with the given parameters to the destination texture.
    - `DeviceFeatures` - A record containing the features that the GPU device supports.
    - `DeviceInfo` - A record containing information about the GPU device.
    - `DeviceLimits` - A record containing information about the limits of GPU features.
    - `DeviceType` - The type of the device performing the rendering (e.g., cpu, discrete graphics, integrated graphics).
    - `GpuBackend#createDevice` now takes in a `Runnable` for the shaders that must be loaded for the client to start up, and can throw a `BackendCreationException`
    - `GpuDevice` now takes in a `Runnable` for the shaders that must be loaded for the client to start up
        - `createSurface` - Creates the surface for a window to write data to.
        - `getImplementationInformation` -> `getDeviceInfo`, not one-to-one
        - `getVendor` -> `DeviceInfo#vendorName`
        - `getBackendName` -> `DeviceInfo#backendName`
        - `getVersion` -> `DeviceInfo#driverInfo`
        - `getRenderer` -> `DeviceInfo#name`
        - `getMaxTextureSize` -> `DeviceLimits#maxTextureSize`
        - `getUniformOffsetAlignment` -> `DeviceLimits#minUniformOffsetAlignment`
        - `getEnabledExtensions` -> `DeviceInfo#underlyingExtensions`
        - `getMaxSupportedAnisotropy` -> `DeviceLimits#maxAnisotropy`
        - `setVsync` -> `GpuSurface#configure`, not one-to-one
        - `presentFrame` -> `GpuSurface#present`
        - `isZZeroToOne` -> `DeviceInfo#isZZeroToOne`
        - `createTimestampQueryPool` - Creates the query pool for getting data from the GPU.
        - `getTimestampNow` - Returns the current epoch timestamp.
        - `loadCriticalShaders` - Loads the shaders required for the client to start up.
    - `GpuDeviceBackend`
        - `createSurface` - Creates the surface for a window to write data to.
        - `getImplementationInformation` -> `getDeviceInfo`, not one-to-one
        - `getVendor` -> `DeviceInfo#vendorName`
        - `getBackendName` -> `DeviceInfo#backendName`
        - `getVersion` -> `DeviceInfo#driverInfo`
        - `getRenderer` -> `DeviceInfo#name`
        - `getMaxTextureSize` -> `DeviceLimits#maxTextureSize`
        - `getUniformOffsetAlignment` -> `DeviceLimits#minUniformOffsetAlignment`
        - `getEnabledExtensions` -> `DeviceInfo#underlyingExtensions`
        - `getMaxSupportedAnisotropy` -> `DeviceLimits#maxAnisotropy`
        - `setVsync` -> `GpuSurface#configure`
        - `presentFrame` -> `GpuSurface#present`
        - `isZZeroToOne` -> `DeviceInfo#isZZeroToOne`
        - `createTimestampQueryPool` - Creates the query pool for getting data from the GPU.
        - `getTimestampNow` - Returns the current epoch timestamp.
    - `GpuQueryPool` - A pool for getting query objects and their associated values from the GPU.
    - `GpuSurface` - The surface wrapper, swapchain, and validator.
    - `GpuSurfaceBackend` - An API for modifying and writing data to linked window using its handle.
    - `HintsAndWorkarounds` - A record containing issues with the supported GPU device and what workarounds are required.
    - `RenderPass` now takes in a `Runnable` for what to do on close, typically from a try-with-resources; a list of `RenderPassDescriptor$Attachment` color textures; and a `$RenderArea` of where to draw to
        - `writeTimestamp` - Writes the timestamp to the pool at the given index.
        - `setVertexBuffer` now takes in a `GpuBufferSlice` instead of a `GpuBuffer`
        - `drawIndexed` parameter order for the five `int`s are as follows: the index count, instance count, first index, vertex offset / base vertex, and the first instance for fetching vertex attributes (new)
        - `drawIndexedIndirect` - Draws the indexed data to the buffer using the indirect API.
        - `draw` parameter order for the four `int`s are as follows: the vertex count, instance count, first vertex, and the first instance for fetching vertex attributes (new)
        - `drawIndirect` - Draws the vertex data to the buffer using the indirect API.
        - `multiDrawIndexed` - Draws the indexed data using an interleaved multi-draw call.
        - `multiDraw` - Draws the vertex data using an interleaved multi-draw call.
        - `$RenderArea` - A rectangle defining where the pass can write data to.
    - `RenderPassBackend` is no longer `AutoCloseable`
        - `isClosed` is removed
        - `writeTimestamp` - Writes the timestamp to the pool at the given index.
        - `setVertexBuffer` now takes in a `GpuBufferSlice` instead of a `GpuBuffer`
        - `drawIndexed` parameter order for the five `int`s are as follows: the index count, instance count, first index, vertex offset / base vertex, and the first instance for fetching vertex attributes (new)
        - `drawIndexedIndirect` - Draws the indexed data to the buffer using the indirect API.
        - `draw` parameter order for the four `int`s are as follows: the vertex count, instance count, first vertex, and the first instance for fetching vertex attributes (new)
        - `drawIndirect` - Draws the vertex data to the buffer using the indirect API.
        - `multiDrawIndexed` - Draws the indexed data using an interleaved multi-draw call.
        - `multiDraw` - Draws the vertex data using an interleaved multi-draw call.
    - `RenderPassDescriptor` - A class containing the configuration of the `RenderPass` to use during drawing.
    - `RenderSystem`
        - `DEFAULT_DEPTH_CLEAR_VALUE` - The default clear value for the depth buffer.
        - `flipFrame` is removed
        - `getApiDescription` is removed
        - `shutdownRenderer` - Shuts down the renderer and GPU device.
        - `getModelViewMatrix` -> `getModelViewMatrixCopy`, not one-to-one
        - `initBackendSystem` no longer takes in the `BackendOptions`
        - `$AutoStorageIndexBuffer` now implements `AutoCloseable`
    - `ScissorState` now has a constructor that takes in the `ScissorState` to copy from
        - `setFrom` - Copies the state from another scissor.
    - `SurfaceException` - An exception thrown when operating on or with a GPU surface.
    - `TimerQuery` now implements `AutoCloseable`
        - `getInstance` replaced by using the constructor
        - `isRecording` -> `getStatus`, not one-to-one
        - `endProfile` no longer returns anything
        - `$FrameProfile` class is removed
        - `$Status` - The current status of the timer query.
    - `TracyGpuProfiler` - An implementation of the Tracy profiler (hybrid frame and sampling profiler) for the GPU.
    - `TransientMemory` - A memory system for handling short lived (e.g., single frame) allocations, typically freeing or re-using the memory as needed.
- `com.mojang.blaze3d.textures`
    - `GpuTexture$Usage` now only targets `ElementType#TYPE_USE`
    - `TextureFormat` replaced with `GpuFormat`
        - `RGBA8` -> `RGBA8_UNORM`, not one-to-one
        - `RED8` -> `R8_UNORM`, not one-to-one
            - This should be taken with a grain of salt as it does not exactly map to the previous version's GL type
        - `RED8I` -> `R8_SINT`, not one-to-one
            - This should be taken with a grain of salt as it does not exactly map to the previous version's GL type
        - `DEPTH32` -> `D32_FLOAT`, not one-to-one
            - This should be taken with a grain of salt as it does not exactly map to the previous version's GL type
- `com.mojang.blaze3d.util.TransientBlockAllocator` - An allocator for data broken into blocks, typically releasing the memory after use.
- `com.mojang.blaze3d.vertex`
    - `ByteBufferBuilder$Result#size` - The size of the resulting buffer.
    - `DefaultVertexFormat`
        - `*_SEMANTIC_NAME` - The name of the attributes used by the default vertex formats.
        - `EMPTY` is removed
            - This is typically replaced with either a `null` value or an empty array
    - `MeshData`
        - `unpackQuadCentroids` -> `decodeQuadCentroids`, now `public` from `private`, not one-to-one
        - `vertexBufferSlice` - The resulting vertex buffer containing the `MeshData`
        - `writeSortedIndexBuffer` - Writes the sorted vertices to an index buffer.
    - `StagingBuffer` - A buffer for staging changes to write at a later point in time.
        - Many of the methods or logic were originally part of `UberGpuBuffer`.
    - `Tesselator` class is removed
    - `TlsfAllocator`
        - `$Block` instance fields are now `public` from package-private
        - `$Heap` constructor is now `public` from package-private
    - `UberGpuBuffer` now takes in the `StagingBuffer` instead of the `GpuDevice`, buffer size `int`, and `GraphicsWorkarounds`
        - `uploadStagedAllocations` now takes in the `StagingBuffer$Uploader` instead of the `CommandEncoder`
        - `$UberGpuBufferHeap` constructor is now `public` from package-private
    - `VertexFormat`
        - `UNKNOWN_ELEMENT` is removed
        - `MAX_VERTEX_ELEMENTS` - The maximum number of elements or attributes that can be defined on the format.
        - `uploadImmediateVertexBuffer`, `uploadImmediateIndexBuffer` are removed
        - `builder` now takes in an `int` for how many instances pass between updates to the attribute
        - `getStepRate` - How many instances pass between updates to the attribute.
            - OpenGL is 0 per vertex.
        - `getElementAttributeNames` is removed
        - `getOffsetsByElement` is removed
        - `getOffset` replaced by `getElement`
        - `contains` now takes in the `String` attribute name instead of the `VertexFormatElement`
        - `getElementsMask`, `getElementName` are removed
        - `$Builder`
            - `add` -> `addAttribute`, not one-to-one
            - `padding` is removed
        - `$IndexType` -> `com.mojang.blaze3d.IndexType`
        - `$Mode` -> `PrimitiveTopology`
    - `VertexFormatElement` now takes in the `GpuFormat` instead of the `$Type`, normalized `boolean`, and count `int`; the `String` attribute name instead of the `int` id, and the `int` offset` instead of the index
        -  Constant `VertexFormatElement` fields are now removed
            - They are constructed when creating the `VertexFormat`
        - `MAX_COUNT` is removed
        - `register` is removed
        - `mask`, `byteSize` are removed
        - `byId`, `elementsFromMask` are removed
        - `type`, `$Type` -> `GpuFormat` following the `_` in the entries
        - `normalized` -> `GpuFormat` if `NORM` is in the entry name
        - `count` -> `GpuFormat` counting `R`, `G`, `B`, `A` in the initial characters before the size
    - `VertexMultiConsumer` class is removed
        - Usage replaced by `SheetedDecalTextureGenerator` for the foil buffer
- `com.mojang.blaze3d.vulkan`
    - `Destroyable` - An interface for destroying the object, typically through `vkDestroy*`.
    - `DestructionQueue` - A queue for managing the destruction of objects at a specific time.
    - `VulkanBackend` - The Vulkan implementation of the `GpuBackend`.
    - `VulkanBindGroupLayout` - A list of layout bindings for the handle.
    - `VulkanCommandEncoder` - The Vulkan implementation of the `CommandEncoderBackend`.
    - `VulkanCommandPool` - A pool for allocating the command buffers.
    - `VulkanConst` - The constant mappings for Blaze3d to Vulkan.
    - `VulkanDebug` - A utility for debugging Vulkan objects.
    - `VulkanDevice` - The Vulkan implementation of the `GpuDeviceBackend`.
    - `VulkanGpuBuffer` - The Vulkan implementation of a `GpuBuffer`.
    - `VulkanGpuSampler` - The Vulkan implementation of a `GpuSampler`.
    - `VulkanGpuSurface` - The Vulkan implementation of the `GpuSurfaceBackend`.
    - `VulkanGpuTexture` - The Vulkan implementation of a `GpuTexture`.
    - `VulkanGpuTextureView` - The Vulkan implementation of a `GpuTextureView`.
    - `VulkanInstance` - The connection between the application and the Vulkan library, maps almost directly to [VkInstance](https://docs.vulkan.org/refpages/latest/refpages/source/VkInstance.html).
    - `VulkanPhysicalDevice` - The installed device with Vulkan support available on the system, maps almost directly to [VkPhysicalDevice](https://docs.vulkan.org/refpages/latest/refpages/source/VkPhysicalDevice.html).
    - `VulkanQueryPool` - The Vulkan implementation of a `GpuQueryPool`.
    - `VulkanQueue` - A wrapped queue on a device to submit elements.
    - `VulkanRenderPass` - The Vulkan implementation of the `RenderPassBackend`.
    - `VulkanRenderPipeline` - The Vulkan implementation of a `CompiledRenderPipeline`.
    - `VulkanTransientMemory` - The Vulkan implementation of the `TransientMemory`.
    - `VulkanUtils` - A utility for common operations within the Vulkan implementation.
- `com.mojang.blaze3d.vulkan.checkpoints`
    - `AbstractCheckpointStorage` - An abstract implementation of the storage that handles the markers.
    - `AmdCheckpointExtension` - A `CheckpointExtension` for AMD GPUs.
    - `CheckpointExtension` - An extension that inserts markers into a command stream to help determine what caused a device lost failure.
    - `NoopCheckpointExtension` - A `CheckpointExtension` that does nothing.
    - `NvidiaCheckpointExtension` - A `CheckpointExtension` for Nvidia GPUs.
- `com.mojang.blaze3d.vulkan.glsl`
    - `GlslCompiler` - Compiles the GLSL shaders into SPIR-V bytecode.
    - `IntermediaryShaderModule` - An intermediary module representing the shader after initial compilation but before binding.
    - `ShaderCompileException` - An exception thrown during the Vulkan shader compilation process.
    - `SpvcUtil` - A utility for converting a dimension to its string representation in an SPV shader.
    - `SpvSampler` - A sampler for an SPV shader.
    - `SpvUniformBuffer` - A uniform buffer for an SPV shader.
    - `SpvVariable` - A variable for an SPV shader.
- `com.mojang.blaze3d.vulkan.init`
    - `VulkanFeature` - A Vulkan feature on the device.
    - `VulkanPNextStruct` - A representation of a Vulkan struct on the `pNext` chain.
- `net.minecraft`
    - `ChatFormatting` is no longer `StringRepresentable`
        - `CODEC`, `COLOR_CODEC` replaced by `TextColor#CODEC`, not one-to-one
        - `getChar` is removed
        - `getId`, `getName` replaced by `TextColor#serialize`, not one-to-one
        - `isFormat` is removed
        - `isColor`, `getColor` replaced by `TextColor#getValue`, not one-to-one
        - `getByName`, `getById` replaced by `TextColor#parseColor`, not one-to-one
        - `getNames` is removed
    - `SharedConstants#DEBUG_SIMULATE_LIBRARY_LOAD_FAILURE` - A debug flag that simulates a native library failing to load.
- `net.minecraft.client`
    - `Minecraft`
        - `UNIFORM_FONT` replaced by `FontOption#UNIFORM`
        - `ALT_FONT` replaced by `EnchantmentNames#ALT_FONT`, now `private` from `public`
        - `screen` -> `Gui#screen`
        - `levelExtractor` - Extracts the level into its render state.
        - `openChatScreen` -> `Gui#openChatScreen`
        - `setScreen` -> `Gui#setScreen`
        - `setOverlay` -> `Gui#setOverlay`
        - `renderFrame` is now `public` from `private`
        - `renderNames` is replaced by `Hud#isHidden`, `GuiRenderState#isHudHidden`
        - `getToastManager` -> `Gui#toastManager`
        - `getWaypointStyles` -> `Hud#getWaypointStyles`
        - `getSplashManager` -> `Gui#splashManager`
        - `getOverlay` -> `Gui#overlay`
        - `windowSurface` - Gets the `GpuSurface` for the current game window.
        - `getChatListener` -> `Gui#chatListener`
        - `commandHistory` -> `ChatComponent#commandHistory`, now `private` from `public`
        - `invalidateSurfaceConfiguration` - Marks the `GpuSurface` as needing configuration, typically after a change to how the framebuffer renders (e.g., screen resize).
        - `buildInitialScreens` -> `Gui#buildInitialScreens`, now `public` from `private`
        - `getMainRenderTarget` -> `GameRenderer#mainRenderTarget`
        - `useShaderTransparency` -> `GameRenderState#useShaderTransparency`
        - `renderBuffers` is removed
    - `Options`
        - `hideGui` is replaced by `Hud#isHidden`, `GuiRenderState#isHudHidden`
        - `preferredGraphicsBackend` - Gets the preferred graphics library to use when rendering the game.
        - `isRestartRequiredToApplyVideoSettings` - Whether a restart is required to apply the desired graphics settings.
    - `PreferredGraphicsApi` - The graphics libraries vanilla supports to render the game.
    - `RotatingSectionStorage` - A class for managing the order of sections in relation to a center position.
    - `SectionUpdateTracker` - A class for tracking whether a section needs to be updated.
- `net.minecraft.client.color.block.BlockTintCache$LatestCacheInfo#x`, `z` are now `private` from `public`
- `net.minecraft.client.gui`
    - `ComponentPath#leafComponent` - The listener at the end of the component path branch.
    - `Font`
        - `drawInBatch` methods are removed
            - Replaced by `Font$PreparedText` and `Font$GlyphVisitor`
        - `drawInBatch8xOutline` -> `prepare8xTextOutline`, only taking in the `FormattedCharSequence`, XY position, and outline color; not one-to-one
        - `$GlyphVisitor`
            - `forMultiBufferSource` is removed
            - `acceptRenderable` - Accepts the `TextRenderable` to operate upon, usually for rendering.
        - `$PreparedTextBuilder#discardEffects` - Removes the text's effects.
    - `Gui` now takes in the `Hud` and `GuiRenderState`
        - `SAVING_TEXT` -> `SAVING_LEVEL`, not one-to-one
        - `hud` - The heads-up display of the user interface.
        - `resetTitleTimes` -> `Hud#resetTitleTimes`
        - `extractRenderState` no longer takes in the `GuiGraphicsExtract`, now taking in two `boolean`s of whether the level should be rendered, and whether the resources are loaded
        - `extractDebugOverlay` -> `Hud#extractDebugOverlay`
        - `registerReloadListeners` - Registers any reload listeners used by the GUI.
        - `extractDeferredSubtitles` -> `Hud#extractDeferredSubtitles`
        - `tick` no longer takes in whether the screen is paused `boolean`
        - `update` - Updates the GUI components.
        - `getMobEffectSprite` -> `Hud#getMobEffectSprite`
        - `overlay` - Returns the current screen overlay.
        - `addSocialInteractionsToast` - Creates and adds a social interactions toast.
        - `setPauseScreen` - Sets the pause screen with the appropriate configuration.
        - `handleKeybinds` - Handles any keybinds that directly affects the GUI.
        - `openChatAndAddText` - Opens the chat screen and inserts the specified text.
        - `setNowPlaying` -> `Hud#setNowPlaying`
        - `setOverlayMessage` -> `Hud#setOverlayMessage`
        - `setTimes` -> `Hud#setTimes`
        - `setSubtitle` -> `Hud#setSubtitle`
        - `setTitle` -> `Hud#setTitle`
        - `clearTitles` -> `Hud#clearTitles`
        - `getChat` -> `Hud#getChat`
        - `getGuiTicks` -> `Hud#getGuiTicks`
        - `getFont` -> `Hud#getFont`
        - `getSpectatorGui` -> `Hud#getSpectatorGui`
        - `getTabList` -> `Hud#getTabList`
        - `onDisconnected` -> `Hud#onDisconnected`
        - `getBossOverlay` -> `Hud#getBossOverlay`
        - `getDebugOverlay` -> `Hud#getDebugOverlay`
        - `clearCache` -> `Hud#clearCache`
        - `extractSavingIndicator` -> `Hud#extractSavingIndicator`
        - `canInterruptScreen` - Whether the screen can be closed by an external event not from user input.
        - `setClientLevelTeardownInProgress` - Sets that the current client level is being destroyed.
    - `GuiGraphicsExtractor`
        - `entity` now takes in `Vector3fc` and `Quaternionfc`s instead of `Vector3f` and `Quaternionf`s
        - `skin` now takes in a `Model$Simple` instead of a `PlayerModel` for the model
        - `$ScissorStack#push`, `pop` now return nothing instead of the `ScreenRectangle`
    - `Hud` - The heads-up display that's rendered atop the GUI when in-game.
- `net.minecraft.client.gui.components`
    - `AbstractContainerWidget` now has an overload that defaults the `AbstractScrollArea$ScrollbarSettings` to no scrolling
    - `AbstractScrollArea$ScrollbarSettings#NO_SCROLL` - Disables scrollbar scrolling for the scroll area.
    - `AbstractStringWidget#handleStyleClick` - Handles if the `Style` has a click event, returning `true` if handled.
    - `AbstractWidget#extractTooltipForNextRenderPass` - Extracts the tooltip to render during the next pass.
    - `DebugScreenOverlay#render3dCrosshair` is replaced by `DebugCrosshairRenderer`
    - `EditBox` now as an overload that defaults the width and height to 150x20
    - `FocusableTextWidget#setNarrateMessage`, `setUsageNarration` - Handles the display of the narration text.
    - `OptionsList`
        - `addBig` now has an overload that takes in an `AbstractWidget`
        - `Entry$big` now has an overload that takes in an `AbstractWidget` and the current `Screen`
    - `PlayerTabOverlay` now takes in the `Hud` instead of the `Gui`
    - `ScrollableLayout` now hsa an overload that allows setting the `ScrollableLayout$ReserveStrategy`
        - `setScrollbarSpacing` - Sets the spacing of the scrollbar.
        - `$Container#refreshChildren` - Refreshes the current list of entries in the container.
    - `SpriteIconButton` now takes in the `int` XY offsets along with a `boolean` whether to switch to the loading state after pressing the button
        - `spriteOffsetX`, `spriteOffsetY` - The offsets of the sprite.
        - `setLoading` - Sets whether the sprite icon is loading.
        - `extractLoadingStateIfLoading` - Extracts the state of the button if loading.
        - `$Builder` constructor is now `private` from `public`
            - `spriteOffset` - Sets the XY offset of the sprite.
            - `tooltip` - Sets the tooltip to display when hovering.
            - `switchToLoadingAfterPress` - Whether to switch to the loading state after pressing the button.
        - `$CenteredIcon` now takes in the `int` XY offsets along with a `boolean` whether to switch to the loading state after pressing the button
        - `$TextAndIcon` now takes in the `int` XY offsets along with a `boolean` whether to switch to the loading state after pressing the button
    - `TabButton#extractMenuBackground` -> `MenuTabBar#renderMenuBackground`
- `net.minecraft.client.gui.components.debug.DebugEntryVersion` class is now `public` from package-private
- `net.minecraft.client.gui.components.tabs`
    - `MenuTabBar` - A panel with navigation tabs for some menu.
    - `Tab#getLayout` - Returns the `Layout` of the tab.
    - `TabManager#setCurrentTab` now has an overload of whether to add the children widgets when set
    - `TabNavigationBar` now extends `AbstractContainerWidget` instead of `AbstractContainerEventHandler`
        - The constructor is now `protected` from `private`, taking in the XY, height, `TabButton`s and `Tab`s
            - The original constructor is now the constructor for `MenuTabBar`
        - `layout` is now `protected` from `private`, a `FrameLayout` instead of a `LinearLayout`
        - `tabs`, `tabButtons` are now `protected` from `private`
        - `builder` now takes in the XY and height
        - `updateWidth` -> `arrangeElements`, not one-to-one
        - `arrangeElements()` is removed
        - `$Builder` is now `protected` from `private`, taking in the XY and height
            - All fields are now `protected` from `private`
            - `x`, `y` - The offsets of the navigation bar.
            - `height` - The height of the bar.
            - `tabButtons` - The buttons the user can click on to navigate.
            - `addTabs` now takes in a `TabButton` along with its `Tab` instead of only a varargs of `Tab`s
                - The original method has been moved to `MenuTabBar$Builder#addTabs`, assuming the tab uses a `MenuTabBar$MenuTabButton`
- `net.minecraft.client.gui.layouts.Layout#removeChildren` - Removes any children in the layout.
- `net.minecraft.client.gui.font.FontTexture$Node#insert` is now `public` from package-private
- `net.minecraft.client.gui.components.toasts.SystemToast`
    - `multiline` is replaced by the default constructor calling `update`
    - `recalculateWidth` - Recalculates the maximum width of the toast, up to 190.
- `net.minecraft.client.gui.contextualbar`
    - `ContextualBarRenderer` -> `ContextualBar`
    - `ExperienceBarRenderer` -> `ExperienceBar`
    - `JumpableVehicleBarRenderer` -> `JumpableVehicleBar`
    - `LocatorBarRenderer` -> `LocatorBar`
- `net.minecraft.client.gui.font.GlyphRenderTypes#createForIntensityTexture` -> `createForGrayscaleTexture`
- `net.minecraft.client.gui.render`
    - `GuiItemAtlas` no longer takes in the `SubmitNodeCollector` nor `MultiBufferSource$BufferSource`
    - `GuiRenderer` no longer takes in the `SubmitNodeCollector` nor `MultiBufferSource$BufferSource`
        - `CLEAR_COLOR` is now a `Vector4fc` instead of an `int`
        - `render` no longer takes in the `GpuBufferSlice` for the fog buffer
- `net.minecraft.client.gui.renderer.pip` no longer takes in the `MultiBufferSource$BufferSource` in any constructor
    - `PictureInPictureRenderer` no longer takes in the `MultiBufferSource$BufferSource`
        - `bufferSource` is removed
        - `prepare` now takes in the `FeatureRenderDispatcher`
        - `renderToTexture` now takes in the `SubmitNodeCollector`
- `net.minecraft.client.gui.screens`
    - `ConfirmScreen`
        - `message` is now `protected` from `private`
        - `addMessage` - Creates a new `MultiLineTextWidget` with the screen fields.
    - `OptionsScreen` no longer implements `HasDifficultyReaction`
    - `Overlay#isPauseScreen` -> `isPausing`
    - `Screen#panoramaShouldSpin` replaced by `Panorama#startSpin`, `holdSpin`; not one-to-one
- `net.minecraft.client.gui.screens.advancements`
    - `AdvancementsScreen#extractWindow` no longer takes in the XY position to place
    - `AdvancementTab`
        - `tick` - Ticks the tab display based on the mouse's relative position.
        - `extractTooltips` no longer takes in the mouse XY
    - `AdvancementTabType` enum is now `public` from package-private
- `net.minecraft.client.gui.screens.inventory`
    - `AbstractCommandBlockEditScreen`
        - `getCommandBlock` is now `protected` from package-private
        - `getPreviousY` is now `protected` from package-private
    - `AbstractContainerScreen#onMouseClickAction` is now `protected` from package-private
    - `AbstractSignEditScreen#getSignTextScale` now returns a `Vector3fc` instead of a `Vector3f`
- `net.minecraft.client.gui.screens.multiplayer.ServerSelectionList$Entry#matches` is now `protected` from package-private
- `net.minecraft.client.gui.screens.options.WorldOptionsScreen`
    - `GAME_MODE_DISABLED_HARDCORE_TOOLTIP` - The component to display if the game mode cannot be changed.
    - `ALLOW_COMMANDS_DISABLED_TOOLTIP` - The component to display if commands are disabled.
- `net.minecraft.client.gui.screens.options.controls.KeyBindsList$Entry#refreshEntry` is now `public` from package-private
- `net.minecraft.client.main.GameConfig$GameData` now takes in `boolean`s for whether to use the validation layers for Vulkan, and the graphics api forced by launch argument
    - `vulkanValidation` - Whether to use the validation layers for Vulkan.
    - `forcedGraphicsApi` - The graphics library forced by launch argument.
- `net.minecraft.client.model`
    - `Model#renderToBuffer(PoseStack, VertexConsumer, int, int)` is removed
    - `QuadrupedModel#createLegs` is now `public` from package-private
- `net.minecraft.client.model.animal.cow.CowModel#createBaseCowModel` is now `public` from package-private
- `net.minecraft.client.model.geom.builders.CubeDefinition` is now `public` from `protected`
- `net.minecraft.client.model.monster.piglin.AbstractPiglinModel#getDefaultEarAngleInDegrees` is now `protected` from package-private
- `net.minecraft.client.model.monster.slime`
    - `SulfurCubeModel` - The entity model for the sulfur cube.
    - `SmallSulfurCubeModel` - The entity model for a small sulfur cube.
- `net.minecraft.client.multiplayer`
    - `ClientChunkCache#getChunk` is now `public` from `protected`
    - `ClientLevel` now takes in a `LevelExtractor` instead of the `LevelRenderer`
        - `onSectionBecomingNonEmpty` is removed
    - `ClientPacketListener#getPlayerCompiledSectionCallback` - Gets the callback for when the section the player is on the client has finished loading.
    - `LevelLoadTracker`
        - `startClientLoad` no longer takes in the `LevelRenderer`
        - `getPlayerCompiledSectionCallback` - Gets the callback for when the section the player is on the client has finished loading.
- `net.minecraft.client.particle`
    - `BreakingItemParticle$SulfurCubeProvider` - A breaking item particle provider for the sulfur cube.
    - `NoxiousGasCloudParticle` - A particle for the noxious gas cloud.
    - `NoxiousGasParticle` - A particle for the noxious gas.
    - `ParticleEngine#getRandom` - Returns the random source of the particle engine.
    - `ParticleGroup`
        - `add` now returns whether the particle was successfully added
        - `getAll` is removed
    - `ParticleRenderType` now takes in an additional shorthand `String`
    - `SulfurBubbleParticle` - A particle for the sulfur bubbles.
- `net.minecraft.client.profiling.ClientMetricsSamplersProvider` now takes in a `LevelExtractor`
- `net.minecraft.client.renderer`
    - `BindGroupLayouts` - A collection of common uniform and sampler layouts used by a renderer.
    - `DebugCrosshairRenderer` - A renderer for the debug 3D crosshair reticle.
    - `DynamicUniforms#writeTransform` now has a view overloads for taking in the various transform parameters, or the `$Transform` object itself
    - `GameRenderer` no longer takes in the `RenderBuffers`
        - `renderBuffers` - The render buffers used for sections, outlines, and crumbling overlays.
        - `getSubmitNodeStorage` is removed
        - `getFeatureRenderDispatcher` -> `featureRenderDispatcher`
        - `getGameRenderState` -> `gameRenderState`
        - `getNightVisionScale` -> `nightVisionScale`
        - `update` no longer takes in whether to advance the game time
        - `getMinecraft` is removed
        - `getBossOverlayWorldDarkening` -> `bossOverlayWorldDarkening`
        - `getMainCamera` -> `mainCamera`
        - `getGlobalSettingsUniform` is removed
        - `getLighting` -> `lighting`
        - `getPanorama` -> `panorama`
    - `ItemInHandRenderer`
        - `renderHandsWithItems` -> `submitHandsWithItems`
        - `$HandRenderSelection#renderMainHand`, `renderOffHand` are now `public` from package-private
    - `LevelRenderer` has been split between this class and `LevelExtractor`
        - The class no longer implements `ResourceManagerReloadListener`
        - `LevelRenderer(Minecraft, EntityRenderDispatcher, BlockEntityRenderDispatcher, RenderBuffers, GameRenderState, FeatureRenderDispatcher)` -> `LevelRenderer(EntityRenderDispatcher, BlockEntityRenderDispatcher, ModelManager, TextureManager, AtlasManager, ShaderManager, GameRenderer, int, int)`
        - `SECTION_SIZE` -> `SectionPos#SECTION_SIZE`
        - `HALF_SECTION_SIZE` -> `SectionOcclusionGraph#HALF_SECTION_SIZE`
        - `NEARBY_SECTION_DISTANCE_IN_BLOCKS` -> `SectionRenderDispatcher#NEARBY_SECTION_DISTANCE_IN_BLOCKS`
        - `debugRenderer` -> `LevelExtractor#debugRenderer`
        - `gameTestBlockHighlightRenderer` -> `LevelExtractor#gameTestBlockHighlightRenderer`
        - `destructionProgress` -> `ClientLevel#destructionProgress`
        - `initOutline` is removed, merged into the main constructor
        - `shouldShowEntityOutlines` -> `LevelExtractor#shouldShowEntityOutlines`, now `private` from `public`
        - `setLevel` -> `resetLevelRenderData`, not one-to-one
        - `allChanged` -> `invalidateCompiledGeometry`, not one-to-one
        - `getSectionStatistics` -> `LevelExtractor#sectionStatistics`
        - `getSectionRenderDispatcher` -> `sectionRenderDispatcher`
        - `getTotalSections` -> `LevelExtractor#totalSections`
        - `getLastViewDistance` -> `LevelExtractor#lastViewDistance`
        - `countRenderedSections` -> `LevelExtractor#countRenderedSections`
        - `resetSampler` -> `LevelExtractor#resetSampler`, not one-to-one
        - `getEntityStatistics` -> `LevelExtractor#entityStatistics`
        - `offsetFrustum` -> `SectionOcclusionGraph#offsetFrustum`, now `private` from `public`
        - `addRecentlyCompiledSection` is removed, now passed into `SectionRenderDispatcher`
        - `update` is removed
            - Closest use is `RotatingSectionStorage#repositionCenter` calls
        - `renderLevel` -> `render`, no longer taking in the `ChunkSectionsToRender`
        - `extractLevel` -> `LevelExtractor#extract`, not one-to-one
        - `tick` is removed
            - Logic moved to `ClientLevel#tick`
        - `blockChanged` -> `LevelExtractor#blockChanged`, no longer taking in the `BlockGetter` or `BlockState`s
        - `setBlocksDirty` -> `LevelExtractor#setBlocksDirty`
        - `setSectionDirtyWithNeighbors` -> `LevelExtractor#setSectionDirtyWithNeighbors`
        - `setSectionRangeDirty` -> `LevelExtractor#setSectionRangeDirty`
        - `setSectionDirty` -> `LevelExtractor#setSectionDirty`
        - `onSectionBecomingNonEmpty` is removed
        - `destroyBlockProgress` -> `Level#destroyBlockProgress`
        - `onChunkReadyToRender` is removed
        - `needsUpdate` is removed
        - `getLightCoords` -> `LightCoordsUtil#getLightCoords`
        - `entityRenderDispatcher` - Gets the dispatcher for rendering entities.
        - `blockEntityRenderDispatcher` - Gets the dispatcher for rendering block entities.
        - `getTranslucentTarget` -> `translucentTarget`
        - `getItemEntityTarget` -> `itemEntityTarget`
        - `getParticlesTarget` -> `particlesTarget`
        - `getWeatherTarget` -> `weatherTarget`
        - `getCloudsTarget` -> `cloudsTarget`
        - `getVisibleSections` -> `visibleSections`
        - `getSectionOcclusionGraph` -> `sectionOcclusionGraph`
        - `getCloudRenderer` -> `cloudRenderer`
        - `skyRenderer` - Gets the sky renderer.
        - `weatherEffectRenderer` - Gets the weather renderer.
        - `worldBorderRenderer` - Gets the world border renderer.
        - `viewArea` - Gets the currently viewed area of the camera.
        - `nearbyVisibleSections` - Gets the visible sections that are nearby to the camera.
        - `collectPerFrameGizmos` -> `collectPerFrameRenderThreadGizmos`
        - `addMainThreadGizmos` - Adds gizmos from the main thread.
        - `expectedChunks` - The chunks that are expected to be rendered.
        - `$BrightnessGetter` -> `LightCoordsUtil$BrightnessGetter`
    - `MultiBufferSource` interface is removed
        - Closest replacement is `PreparedRenderType` in the feature system
    - `OrderedSubmitNodeCollector`
        - `submitModelPart` no longer takes in the `boolean`s for whether its sheeted or has foil
        - `submitShapeOutline` - Submits a voxel shape to draw an outline of.
        - `submitNameTag` no longer takes in the squared distance to the camera `double`
        - `submitBreakingBlockModel` now takes in a list of `BlockStatModelPart`s instead of a `BlockStateModel` and a `long` seed
        - `submitParticleGroup` now takes in a `QuadParticleRenderState` instead of a `SubmitNodeCollector$ParticleGroupRenderer`
        - `submitGizmoPrimitives` - Submits the gizmo primitive to render.
        - `submitMovingBlock` now takes in the `int` outline color
    - `OutlineBufferSource` class is removed
    - `Panorama#extractRenderState` no longer takes in a `boolean` of whether the panorama should spin
    - `RenderBuffers` is now `AutoCloseable`
        - `endFrame` - When everything that should be uploaded is done for the frame.
        - `crumblingBufferSource` is removed
        - `bufferSource`, `outlineBufferSource` replaced by `stagedVertexBuffer`, not one-to-one
    - `RenderPipelines`
        - Pipelines using the `DepthStencilState` have their values inverted
            - `CompareOp` depth test uses `GREATER_THAN_OR_EQUAL` instead of `LESS_THAN_OR_EQUAL`
            - Values for the two `int`s are now their additive inverses
        - `TEXT_INTENSITY` -> `TEXT_GRAYSCALE`
        - `GUI_TEXT_INTENSITY` -> `GUI_TEXT_GRAYSCALE`
        - `TEXT_INTENSITY_SEE_THROUGH` -> `TEXT_GRAYSCALE_SEE_THROUGH`
        - `DRAGON_RAYS_DEPTH` is removed
            - Merged with `DRAGON_RAYS`
        - `MATRICES_PROJECTION_SNIPPET` -> `BindGroupLayouts#MATRICES_PROJECTION`, not one-to-one
        - `FOG_SNIPPET` -> `BindGroupLayouts#FOG`, not one-to-one
        - `GLOBALS_SNIPPET` -> `BindGroupLayouts#GLOBALS`, not one-to-one
        - `TEXT_GRAYSCALE_POLYGON_OFFSET` - A pipeline for displaying grayscale text with some polygon offset.
    - `SectionBufferBuilderPool` is now `AutoCloseable`
    - `SectionOcclusionGraph`
        - `invalidateIfNeeded` - Invalidates the current graph based on the camera.
        - `onChunkReadyToRender` is removed
        - `update` now takes in the `CameraRenderState` and fov instead of the `Camera` params, and the `ChunkLoadingRenderState`
        - `updateEmptySections` - Updates the newly empty and non-empty sections.
        - `expectedChunks` - The chunks that are expected to be rendered.
        - `updateLoadedChunks` - Updates the newly added and removed loaded chunks.
        - `$GraphStorage#sectionsWaitingForChunkLoads` - The list of sections currently waiting to be chunk-loaded.
    - `ScreenEffectRenderer` no longer takes in the `MultiBufferSource$BufferSource`
        - `renderScreenEffect` -> `submit`
    - `ShapeRenderer` -> `ShapeOutlineFeatureRenderer`, not one-to-one
    - `Sheets`
        - `DECORATED_POT_SPRITES` -> `DecoratedPotRenderer#DECORATED_POT_SPRITES`, now `private` from `public`
        - `BED_SHEET`, `BED_MAPPER`, `getBedSprite`, `createBedSprite` are removed
        - `cutoutBlockSheet`, `translucentBlockSheet` are removed
            - Replaced by direct construction calls or `*ItemSheet` variants
        - `getDecoratedPotSprite` -> `DecoratedPotRenderer#getSideSprite`, now `private` from `public`, not one-to-one
        - `colorToResourceSprite` is removed
    - `SkyRenderer` now takes in the `RenderTarget`
    - `StagedVertexBuffer` - A vertex buffer for staging draws to upload at a later point in time.
    - `SubmitNodeCollection`
        - All submit list fields are now `public` from `private`, renamed from `*Submits` -> `*`, and are some `FeatureRenderPhase` subtype
        - `get*Submits` are replaced by their field calls, not one-to-one
        - `getModelPartSubmits` is removed
        - `wasUsed` replaced by `FeatureRenderPhase#isEmpty`, not one-to-one
        - `allPhases` - The list of feature render phases containing the submitted elements.
    - `SubmitNodeCollector$ParticleGroupRenderer` interface is removed
    - `SubmitNodeStorage`
        - `clear`, `endFrame` are removed
        - `drawPhases` - Operates on the render phases containing the submitted elements, removing them if the phase contains nothing.
        - `$*Submit` storage classes have been moved to their feature renderer class under `<feature renderer>$Sumbit`
        - `$CustomGeometrySubmit` -> `CustomFeatureRenderer$Submit`
            - The constructor now takes in a `RenderType` and outline color
        - `$ModelPartSubmit` record is removed
    - `ViewArea` no longer takes in the `Level` or `LevelRenderer`, instead taking in the min and max Y and section Y, along with the `SectionOcclusionGraph`
        - `levelRenderer` is removed
        - `level` is removed
        - `sectionGridSizeY`, `sectionGridSizeX`, `sectionGridSizeZ` are removed
        - `sections` is now `private` from `public`, not one-to-one
        - `setViewDistance`
        - `size` - The number of sections in the storage.
        - `minY`, `maxY` - The height bounds of the level.
        - `minSectionY`, `maxSectionY` - The section height bounds of the level.
        - `sectionCount` - The number of sections per chunk.
        - `getLevelHeightAccessor` is removed
        - `setDirty` is removed
        - `getRenderSectionAt` is now `public` from `protected`
    - `WeatherEffectRenderer` is now `AutoCloseable`
        - `tickRainParticles` -> `ClientLevel#tickWeatherEffects`, not one-to-one
        - `getPrecipitationAt` -> `ClientLevel#getPrecipitationAt`, now `public` from` private`
        - `extractRenderState` now takes in the `ClientLevel` instead of the `Level`, and no longer takes in the number of ticks the game has ran
    - `WorldBorderRenderer` now implements `AutoCloseable`
- `net.minecraft.client.renderer.block.BlockModelRenderState#blockLightCoords` - The packed light coordinates of the block.
- `net.minecraft.client.renderer.blockentity`
    - `BannerRenderer#submitPatternLayer` now takes in an `OrderedSubmitNodeCollector` instead of a `SubmitNodeCollector`
    - `BedRenderer` class is removed
    - `BlockEntityRenderDispatcher#tryExtractRenderState` now takes in whether the block entity is globally rendered
    - `TrialSpawnerRenderer#extractSpawnerData` is now `public` from package-private
- `net.minecraft.client.renderer.chunk`
    - `CompileTaskDynamicQueue` -> `SectionTaskDynamicQueue`
    - `RenderSectionRegion` is now `public` from package-private
    - `SectionCompiler` no longer takes in the `BlockEntityRenderDispatcher`
    - `SectionCopy` is now `public` from package-private
    - `SectionRenderDispatcher` no longer takes in the `ClientLevel` and `LevelRenderer`, instead taking in a consumer for a listener on when the section mesh has been updated
        - `setLevel` -> `setCompiler`, not one-to-one
        - `uploadGlobalGeomBuffersToGPU` -> `uploadTerrainBuffersToGpu`
        - `rebuildSectionSync` is removed
        - `$RenderSection` now implements `RotatingSectionStorage$Value`
            - `SIZE` -> `SectionPos#SECTION_SIZE`
            - `hasAllNeighbors` is removed
            - `setDirty`, `setNotDirty`, `isDirty`, `isDirtyFromPlayer` are removed
            - `resortTransparency` no longer takes in the `SectionRenderDispatcher`
            - `cancelTasks` is now `private` from `protected`
            - `createCompileTask` is now `private` from `public`, now taking in the `RenderSectionRegion` instead of the `RenderRegionCache`
            - `rebuildSectionAsync` -> `compileAsync`, now taking in the `RenderSectionRegion` instead of the `RenderRegionCache`
            - `compileSync` now takes in the `RenderSectionRegion` instead of the `RenderRegionCache`
            - `$CompileTask` -> `SectionRenderDispatcher$SectionTask`
                - `isRecompile` is now `private` from `protected`
                - `name` is removed
            - `$RebuildTask` -> `$CompileTask`
                - `region` is now `private` from `protected`
- `net.minecraft.client.renderer.entity`
    - `AbstractCubeMobRenderer` - An entity renderer for cube-like mobs.
    - `CamelRenderer#extractAdditionalState` is now `public` from package-private
    - `EntityRenderer#extractNameplates` -> `extractNameTags`
    - `MagmaCubeRenderer` now extends `AbstractCubeMobRenderer`
    - `SlimeRenderer` now extends `AbstractCubeMobRenderer`
    - `SulfurCubeRenderer` - The entity renderer for a sulfur cube.
    - `TntRenderer`
        - `getSwellAmount` - How much to inflate the TNT model by, given the fuse timer.
        - `isLit` - Whether the TNT has been lit.
- `net.minecraft.client.renderer.entity.layers.SulfurCubeInnerLayer` - The inner layer of a sulfur cube.
- `net.minecraft.client.renderer.entity.state.SulfurCubeRenderState` - The render state for a sulfur cube.
- `net.minecraft.client.renderer.extract.LevelExtractor` - A class that extracts the render state for level rendering.
- `net.minecraft.client.renderer.feature`
    - All `*FeatureRenderer` classes now implement `FeatureRenderer<SUBMIT>` where `SUBMIT` is the type of the storage passed in.
        - All `*FeatureRenderer` classes except `QuadParticleFeatureRenderer` extend `RenderTypeFeatureRenderer`
        - `TYPE` - The identifier for the feature renderer
        - `$Submit` now implements a `SubmitNode` subtype: either `SubmitNode`, `BatachableSubmit`, or `TranslucentSubmit`
        - `render*` methods replaced with `RenderTypeFeatureRenderer#buildGroup`
            - `QuadParticleFeatureRenderer#render*` methods replaced by `FeatureRenderer#prepareGroup`, `executeGroup`, `finishPrepare`, `finishExecute`
    - `BlockFeatureRenderer` -> `BlockModelFeatureRenderer`
        - `renderMovingBlockSubmits` -> `MovingBlockFeatureRenderer`, not one-to-one
    - `CustomFeatureRenderer`
        - `$Storage#add` now takes in the outline color
    - `FeatureFrameContext` - A record that contains the context for rendering a feature.
    - `FeatureRenderDispatcher` no longer takes in the `SubmitNodeStorage`, `MultiBufferSource$BufferSource`, or `OutlineBufferSource`; now taking in the `RenderBuffers`
        - `createFrameContext` replaced by `prepareFrame`, not one-to-one
        - `renderSolidFeatures` -> `$PreparedFrame#executeSolid`
        - `renderTranslucentFeatures` -> `$PreparedFrame#executeTranslucent`
        - `renderTranslucentParticles` -> `$PreparedFrame#executeTranslucentAfterTerrain`
        - `clearSubmitNodes` is removed
        - `renderAllFeatures` now takes in the `SubmitNodeStorage` to render
        - `endFrame` is removed
        - `getSubmitNodeStorage` is removed
        - `$PhaseSubmitGrouper` - A group of features to be rendered in a given phase.
        - `$PreparedFrame` - The prepared features to render and execute for each phase.
        - `$PreparedGroup` - A sub-list of a given submitted feature to be prepared and executed for rendering.
    - `FeatureRenderer` - The base interface representing a renderer for a specific feature.
    - `FeatureRendererMap` - A registry of feature renderers.
    - `FeatureRendererType` - A unique identifier for a feature renderer.
    - `GizmoFeatureRenderer` - A feature renderer for `Gizmo`s.
    - `ItemFeatureRenderer` 
        - `getFoilBuffer(MultiBufferSource, RenderType, boolean, boolean)` is removed
            - Use the other `getFoilBuffer` instead
        - `getFoilRenderType` is removed
            - Directly merged into the other `getFoilBuffer`
    - `ModelPartFeatureRenderer` class is removed
    - `MovingBlockFeatureRenderer` - A feature renderer for moving blocks.
    - `ParticleFeatureRenderer` -> `QuadParticleFeatureRenderer`
        - `endFrame` is removed
    - `RenderTypeFeatureRenderer` - A feature renderer that prepares and renders elements using the submitted `RenderType`.
    - `ShapeOutlineFeatureRenderer` - A feature renderer for `VoxelShape` outlines.
        - `$Submit` - A submitted shape outline.
    - `TextFeatureRenderer#renderTranslucent` now takes in a `FeatureFrameContext` instead of the `MultiBufferSource$BufferSource`
- `net.minecraft.client.renderer.feature.phase`
    - `FeatureRenderPhase` - A group of submitted features for a single type to render.
    - `SimpleFeatureRenderPhase` - A basic implementation of the render phase storage for features.
    - `TranslucentFeatureRenderPhase` - An implementation of the render phase for features with translucency.
- `net.minecraft.client.renderer.feature.submit`
    - `BatchableSubmit` - An interface that represents a submitted feature that can be batched together in one render upload.
    - `SubmitNode` - An interface that represents a submitted feature.
    - `TranslucentSubmit` - An interface that represents a submitted feature with translucency.
- `net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives`
    - `render` replaced by `submit`
    - `isEmpty` is removed
    - `$Group` is now `public` from `private`
        - `render` is removed
    - `$Line`, `$Point`, `$Quad`, `$Text`, `$TriangleFan` are now `public` from `private`
- `net.minecraft.client.renderer.item.properties.numeric`
    - `CompassAngleState#get` is now `public` from package-private
    - `Time#get` is now `public` from package-private
- `net.minecraft.client.renderer.rendertype`
    - `LayeringTransform`, `getModifier` now deals with a `Matrix4f` consumer instead of a `Matrix4fStack`
    - `PreparedRenderType` - The state of a `RenderType` with all of the necessary components to draw the buffered data to the output.
    - `RenderSetup` no longer takes in the buffer size
        - `getTextures` replaced by `prepareTextures`, not one-to-one
        - `$RenderSetupBuilder#bufferSize` is removed
        - `$TextureAndSampler` record is removed
    - `RenderType`
        - `draw` -> `PreparedRenderType#drawFromBuffer`, now taking in the `StagedVertexBuffer$ExecuteInfo`; not one-to-one
        - `drawFromBuffer` -> `PreparedRenderType#drawFromBuffer`, not one-to-one
        - `bufferSize` is removed
        - `prepare` - Constructs the `PreparedRenderType` used to draw the buffered data to the output.
        - `mode` -> `primitiveTopology`
    - `RenderTypes`
        - `textIntensity` -> `textGrayscale`
        - `textIntensityPolygonOffset` -> `textGrayscalePolygonOffset`
        - `textIntensitySeeThrough` -> `textGrayscaleSeeThrough`
        - `dragonRaysDepth` is removed
            - Merged with `dragonRays`
    - `TextureTransform#getMatrix` -> `createMatrix`
- `net.minecraft.client.renderer.special.BedSpecialRenderer` class is removed
- `net.minecraft.client.renderer.state`
    - `OptionsRenderState`
        - `hideGui` -> `GuiRenderState#isHudHidden`
        - `chunkSectionFadeInTime` - The amount of seconds that should be taken for a chunk to fade in when first rendered.
        - `prioritizeChunkUpdates` - What chunk updates to prioritize.
        - `fov` - The field of view of the camera.
    - `WindowRenderState#isResized` is removed
- `net.minecraft.client.renderer.state.gui`
    - `BlitRenderState` now takes in a `Matrix3x2fc` instead of a `Matrix3x2f` for the pose
    - `GuiRenderState#clearColorOverride` is now a `Vector4f` instead of an `int`
    - `GuiTextRenderState#font`, `text`, `x`, `y`, `color`, `backgroundColor`, `dropShadow` are now `private` from `public`
- `net.minecraft.client.renderer.state.gui.pip`
    - `GuiEntityRenderState` now takes in `Vector3fc` and `Quaternionfc`s instead of `Vector3f` and `Quaternionf`s
    - `GuiSkinRenderState` now takes in a `Model$Simple` instead of a `PlayerModel` for the model
    - `PictureInPictureRenderState#IDENTITY_POSE`, `pose` now returns a `Matrix3x2fc` instead of a `Matrix3x2f`
- `net.minecraft.client.renderer.state.level`
    - `CameraRenderState`
        - `isFrustumCaptured` - Whether the frustum has been captured.
        - `smartCull` - Whether to use smart culling.
    - `ChunkLoadingRenderState` - The render state of the current chunks and sections being added and removed.
    - `LevelRenderState`
        - `render3dCrosshair` - Whether to render the 3d crosshair reticle.
        - `chunkSectionsToRender` is removed
        - `sectionUpdateRenderStates` - The render states of the sections to update.
        - `playerCompiledSectionCallback` - Gets the callback for when the section the player is on the client has finished loading.
        - `chunkLoadingRenderState` - The changes between sections and chunks.
        - `shouldResetChunkLayerSampler` - Whether the chunk layer sampler should be reset.
        - `shouldShowEntityOutlines` - Whether to show the outlines of entities.
        - `shouldResetSkyRenderer` - Whether the sky renderer should be reinitialized.
        - `hasGlowingEntities` is removed
    - `ParticlesRenderState#submit` now takes in a `SubmitNodeCollector` instead of the `SubmitNodeStorage`
    - `QuadParticleRenderState` no longer implements `SubmitNodeCollector$ParticleGroupRenderer`
        - `prepare` replaced by `buildLayer`, not one-to-one
        - `render` -> `QuadParticleFeatureRenderer#render`, not one-to-one
        - `layers` - The set of quad layers to render.
        - `$PreparedBuffers`, `$PreparedLayer` are removed
    - `SectionUpdateRenderState` - The render state of a section to update.
- `net.minecraft.client.renderer.texture`
    - `AbstractTexture#releaseTextures` - Closes the texture and its view if open.
    - `TextureAtlasSprite#isAnimated` is now `public` from package-private
- `net.minecraft.client.resources.model`
    - `BlockStateDefinitions#definitionLocationToBlockStateMapper` is now `public` from package-private
    - `ModelManager`
        - `$BlockOnlyMaterialBaker` - An implemented `MaterialBaker` for a block.
        - `$CombinedBlockItemMaterialBaker` - An implemented `MaterialBaker` for a block item.
    - `SimpleModelWrapper#findNonBlockSprites` - Returns a map of sprites that are not in the block texture atlas.
- `net.minecraft.client.resources.model.geometry.BakedQuad$MaterialFlags` can now only target `ElementType#TYPE_USE`
- `net.minecraft.client.resources.model.sprite`
    - `MaterialBaker` is now an abstract `class` instead of an `interface`
        - `replacementForMissingMaterial` - Gets the baked material that represents the missing sprite.
        - `bake` - Bakes the `Material`.
        - `bakeForAtlas` - Bakes the `Material` within the given atlas.
        - `logMissingTextures` - Logs any missing textures or references in the model.
    - `SpriteId#buffer` are removed
- `net.minecraft.client.telemetry`
    - `TelemetryEventType#GRAPHICS_CAPABILITIES` - The capabilities of the graphics card.
    - `TelemetryProperty`
        - `BACKEND_NAME` - The name of the graphics backend.
        - `BACKEND_FAILURE_MESSAGE` - The message provided by the backend when failing during construction.
        - `BACKEND_FAILURE_REASON` - The reason the backend failed during construction.
        - `BACKEND_FAILURE_MISSING_CAPABILITIES` - The backend failed during construction due to missing capabilities.
- `net.minecraft.network.chat`
    - `CommonComponents#GUI_REMOVE` - A component for a remove option.
    - `MutableComponent#withColor` - Sets the color style of the component.
    - `TextColor` now has constants representing the color `ChatFormatting`
        - `named` - Creates a new text color with the given name and RGB value.

## Object Collections

Many vanilla objects can be considered variants of each other but are independent in nature (e.g. different colors, different copper states). Given that most of these objects have the same properties and components, vanilla created object collections to store all the variants together and operate upon them at once. As such, fields within certain registries have been condensed into a single collection (e.g. `Items#_*DYE` is now a `Items#DYE` collection).

Vanilla currently provides two collections: a `ColorCollection` for all 16 dye colors, and `WeatheringCopperCollection` for weathered and waxed blocks. The collections can be created through the constructor, but there are static constructors for blocks (`registerBlocks`) and items (`registerItems`). The collection also contain methods for common usecases, like looping through each entry (`forEach`) or picking a specific entry from its collection method (`pick`).


```java
// Create colored blocks
public static final ColorCollection<Block> EXAMPLE_BLOCK = ColorCollection.registerBlocks(
    // The base identifier for the blocks.
    // The colors will be prefixed when creating (e.g. 'white_example_block').
    "example_block",
    // The method to register the block. Takes in:
    // - The name created from the identifier.
    // - The factory taking in the properties to create the block.
    // - The block properties.
    // And returns the block.
    (name, factory, props) -> Registry.register(
        BuiltInRegistries.BLOCK,
        Identifier.fromNamespaceAndPath("examplemod", name),
        factory.apply(props)
    ),
    // The factory that creates the block. Takes in:
    // - The dye color applied to the block.
    // - The block properties.
    // And returns the block.
    (color, props) -> new Block(props),
    // Supplies the properties for the given color. Takes in:
    // - The dye color applied to the block.
    // And returns the block properties.
    color -> BlockBehavior.Properties.of()
);

// Do something for each object in the collection
EXAMPLE_BLOCK.forEach(block -> {
    // ...
});

// Map a collection to another collection
ColorCollection<Item> items = ColorCollection.map(Block::asItem);

// Map two collections together, zipping them per entry
ColorCollection<Item> items = ColorCollection.zipMap(EXAMPLE_BLOCK, ColorCollection.VALUES, (exampleBlock, dyeColor) -> {
    val id = BuiltInRegistries.BLOCK.getKey(exampleBlock);
    return Registry.register(
        BuiltInRegistries.ITEM, id,
        new Item(new Item.Properties()
            .setId(ResourceKey.create(BuiltInRegistries.ITEM, id))
            .component(DataComponents.DYED_COLOR, new DyedItemColor(dyeColor.getTextureDiffuseColor))
        )
    );
});

// Do something given two collections, zipping them per entry
ColorCollection.zipApply(EXAMPLE_BLOCK, Items.DYE, (exampleBlock, dye) -> {
    // ...
});

// Get a specific object based on the collection variant
Block pink = EXAMPLE_BLOCK.pick(DyeColor.PINK);
```

- `net.minecraft.client.data.models.BlockModelGenerators`
    - `createBanner` no longer takes in the `Block`s for the ground and wall variants
    - `createBanners` is removed
    - `createBed` no longer takes in the `Block`s for the bed and particle
    - `createBeds` is removed
- `net.minecraft.client.renderer.Sheets#CHEST_COPPER_*` merged into `CHEST_COPPER` collection
- `net.minecraft.client.renderer.block.BuiltInBlockModels#createBanners` no longer takes in the `Block`s for the ground and wall variants
- `net.minecraft.client.renderer.special.ChestSpecialRenderer#COPPER_*` merged into `COPPER` collection
- `net.minecraft.data.BlockFamilies`
    - `COPPER_BLOCK`, `WAXED_COPPER_BLOCK`, `EXPOSED_COPPER`, `WAXED_EXPOSED_COPPER`, `WEATHERED_COPPER`, `WAXED_WEATHERED_COPPER`, `OXIDIZED_COPPER`, `WAXED_OXIDIZED_COPPER` merged into `COPPER_BLOCK`, not one-to-one
    - `CUT_COPPER`, `WAXED_CUT_COPPER`, `EXPOSED_CUT_COPPER`, `WAXED_EXPOSED_CUT_COPPER`, `WEATHERED_CUT_COPPER`, `WAXED_WEATHERED_CUT_COPPER`, `OXIDIZED_CUT_COPPER`, `WAXED_OXIDIZED_CUT_COPPER` merged into `CUT_COPPER`, not one-to-one
- `net.minecraft.data.loot.EntityLootSubProvider#createSheepDispatchPool` now takes in a `ColorCollection` instead of a map
- `net.minecraft.data.loot.packs.LootData` is removed
- `net.minecraft.world.item`
    - `Items` that have different variants may have been merged into a single entry, usually represented by some `*Collection` class
    - `WeatheringCopperItems` -> `WeatheringCopperCollection`, not one-to-one
- `net.minecraft.world.item.trading.VillagerTrades` that have different variants may have been merged into a single entry, usually represented by some `*Collection` class
- `net.minecraft.world.level.block`
    - `Blocks` that have different variants may have been merged into a single entry, usually represented by some `*Collection` class
    - `ColorCollection` - A collection of objects whose variants represent one of sixteen colors.
    - `WeatheringCopperBlocks` -> `WeatheringCopperCollection`, not one-to-one
- `net.minecraft.world.level.storage.loot.BuiltInLootTables` map fields are replaced by `ColorCollection`s

## Advancements and Entity Sub Predicates

`net.minecraft.advancements` has reorganized the location of its criteria, triggers, and predicates. Criteria and triggers are now located within `net.minecraft.advancements.triggers`, whereas predicates can be found within `net.minecraft.advancements.predicates`. `EntityPredicate` along with `EntitySubPredicate` and its implementations are within `net.minecraft.advancements.predicates.entity`.

Additionally, `EntityPredicate` is now a wrapper around a map of `EntitySubPredicate`s. Since not all predicates used by the `EntityPredicate` were `EntitySubPredicate`s (e.g. `DistancePredicate`, `SlotsPredicate`), vanilla added new `EntitySubPredicate`s that wrap around these generic implementations (e.g. `DistancePredicate` -> `DistanceToPlayerPredicate`, `SlotsPredicate` -> `EntitySlotsPredicate`). Predicates that were recursive in nature (e.g. `vehicle`, `passenger`) also have an object to wrap around the `EntityPredicate` (e.g. `vehicle` now a `VehiclePredicate`, `passenger` now a `PassengerPredicate`).

`EntitySubPredicate` has also changed slightly as its now just a functional interface with `matches`. `Registries#ENTITY_SUB_PREDICATE_TYPE` now registers a `Codec` of the predicate, which is used as the key of the `EntityPredicate` map.

```java
// Create a sub predicate to map.
public class TeamColorPredicate(int color) implements EntitySubPredicate {

    // The codec to register.
    public static final Codec<TeamColorPredicate> CODEC = ExtraCodecs.STRING_RGB_COLOR.xmap(
        TeamColorPredicate::new, TeamColorPredicate::color
    );

    public TeamColorPredicate {
        this.color = ARGB.opaque(color);
    }

    @Override
    public boolean matches(Entity entity, ServerLevel level, @Nullable Vec3 position) {
        return ARGB.opaque(entity.getTeamColor()) == this.color;
    }
}

// Register the codec.
Registry.register(
    BuiltInRegistries.ENTITY_SUB_PREDICATE_TYPE,
    Identifier.fromNamespaceAndPath("examplemod", "team_color"),
    TeamColorPredicate.CODEC
);
```

So, if our `EntityPredicate` looks something like:

```java
var predicate = new EntityPredicate(Map.of(
    TeamColorPredicate.CODEC, new TeamColorPredicate(0x00FF00),
    DistanceToPlayerPredicate.CODEC, new DistanceToPlayerPredicate(DistancePredicate.vertical(MinMaxBounds.Doubles.exactly(1d)))
));
```

Then, it will be serialized into the advancement JSON as:

```json5
// In some advancement JSON

// Only the `EntityPredicate` part
{
    "examplemod:team_color": "#00FF00",
    "minecraft:distance": {
        "y": 1
    }
}
```

- `net.minecraft.advancements`
    - `CriteriaTriggers` -> `.advancements.triggers.CriteriaTriggers`
    - `Criterion` -> `.advancements.triggers.Criterion`
    - `CriterionTrigger` -> `.advancements.triggers.CriterionTrigger`
- `net.minecraft.advancements.criterion.*`
    - `*Predicate` -> `.advancements.predicates.*Predicate`
    - `*Trigger` -> `.advancements.triggers.*Trigger`
    - `BlockPredicate` -> `.advancements.predicates.BlockPredicate`
    - `CollectionContentsPredicate` -> `.advancements.predicates.CollectionContentsPredicate`
    - `CollectionCountsPredicate` -> `.advancements.predicates.CollectionCountsPredicate`
    - `CollectionPredicate` -> `.advancements.predicates.CollectionPredicate`
    - `ContextAwarePredicate` -> `.advancements.predicates.ContextAwarePredicate`
    - `DamagePredicate` -> `.advancements.predicates.DamagePredicate`
    - `DamageSourcePredicate` -> `.advancements.predicates.DamageSourcePredicate`
    - `DataComponentMatchers` -> `.advancements.predicates.DataComponentMatchers`
    - `DistancePredicate` -> `.advancements.predicates.DistancePredicate`
    - `EnchantmentPredicate` -> `.advancements.predicates.EnchantmentPredicate`
    - `EntityEquipmentPredicate` -> `.advancements.predicates.entity.EntityEquipmentPredicate`
        - Now implements `EntitySubPredicate`
    - `EntityFlagsPredicate` -> `.advancements.predicates.entity.EntityFlagsPredicate`
        - Now implements `EntitySubPredicate`
    - `EntityPredicate` -> `.advancements.predicates.entity.EntityPredicate`
        - The constructor now takes in a map of `EntitySubPredicate`s
        - `entityType` replaced by adding an `EntityTypePredicate` to the map
        - `distanceToPlayer` replaced by adding a `DistanceToPlayerPredicate` to the map
        - `movement` replaced by adding a `DistanceToPlayerPredicate` to the map
        - `location` replaced by adding some of the following to the map:
            - `EntityLocationPredicate`
            - `SteppingOnPredicate`
            - `MovementAffectedByPredicate`
        - `effects` replaced by adding a `EntityEffectsPredicate` to the map
        - `nbt` replaced by adding a `EntityNbtPredicate` to the map
        - `flags` replaced by adding a `EntityFlagsPredicate` to the map
        - `equipment` replaced by adding a `EntityEquipmentPredicate` to the map
        - `subPredicate` -> `parts`, now `private` from `public`, not one-to-one
        - `periodicTick` replaced by adding a `PeriodicEntityTickPredicate` to the map
        - `vehicle` replaced by adding a `VehiclePredicate` to the map
        - `passenger` replaced by adding a `PassengerPredicate` to the map
        - `targetedEntity` replaced by adding a `TargetedEntityPredicate` to the map
        - `team` replaced by adding a `TeamPredicate` to the map
        - `slots` replaced by adding a `EntitySlotsPredicate` to the map
        - `components` replaced by adding either `EntityExactDataComponentsPredicate` or `EntityPartialComponentsPredicate` to the map
        - `$Builder`
            - `components` now has an overload that takes in a map of `DataComponentType`s to `DataComponentPredicate`s
            - `lightingBolt` - Adds a predicate for a lightning bolt.
            - `player` - Adds a predicate for a player.
            - `sheep` - Adds a predicate for a sheep.
            - `cubeMob` - Adds a predicate for a cube mob.
            - `raider` - Adds a predicate for a raider.
            - `fishingHook` - Adds a predicate for a fishing hook.
        - `$LocationWrapper` record is removed
    - `EntitySubPredicate` -> `.advancements.predicates.entity.EntitySubPredicate`
        - `CODEC` -> `EntityPredicate#MAP_CODEC`, not one-to-one
        - `codec` is removed
        - `ALWAYS_TRUE` - A predicate that returns true.
        - `and` - Chains another predicate with this one using an `AND` statement.
    - `EntitySubPredicates` -> `.advancements.predicates.entity.EntitySubPredicates`
        - Static fields are now inlined into `bootstrap`
    - `EntityTypePredicate` -> `.advancements.predicates.entity.EntityTypePredicate`
        - Now implements `EntitySubPredicate`
    - `FishingHookPredicate` -> `.advancements.predicates.entity.FishingHookPredicate`
    - `FluidPredicate` -> `.advancements.predicates.FluidPredicate`
    - `FoodPredicate` -> `.advancements.predicates.FoodPredicate`
    - `GameTypePredicate` -> `.advancements.predicates.GameTypePredicate`
    - `InputPredicate` -> `.advancements.predicates.InputPredicate`
    - `ItemPredicate` -> `.advancements.predicates.ItemPredicate`
    - `LightningBoltPredicate` -> `.advancements.predicates.entity.LightningBoltPredicate`
    - `LightPredicate` -> `.advancements.predicates.LightPredicate`
    - `LocationPredicate` -> `.advancements.predicates.LocationPredicate`
    - `MinMaxBounds` -> `.advancements.predicates.MinMaxBounds`
        - `createCodec`, `createStreamCodec` are now `public` from package-private
    - `MobEffectsPredicate` -> `.advancements.predicates.MobEffectsPredicate`
    - `MovementPredicate` -> `.advancements.predicates.entity.MovementPredicate`
        - Now implements `EntitySubPredicate`
    - `NbtPredicate` -> `.advancements.predicates.NbtPredicate`
    - `PlayerPredicate` -> `.advancements.predicates.entity.PlayerPredicate`
    - `RaiderPredicate` -> `.advancements.predicates.entity.RaiderPredicate`
    - `SheepPredicate` -> `.advancements.predicates.entity.SheepPredicate`
    - `SingleComponentItemPredicate` -> `.advancements.predicates.SingleComponentItemPredicate`
    - `SlimePredicate` -> `.advancements.predicates.entity.CubeMobPredicate`
    - `SlotsPredicate` -> `.advancements.predicates.SlotsPredicate`
    - `StatePropertiesPredicate` -> `.advancements.predicates.StatePropertiesPredicate`
    - `TagPredicate` -> `.advancements.predicates.TagPredicate`
- `net.minecraft.advancements.predicates.entity`
    - `DistanceToPlayerPredicate` - An entity sub predicate that checks a `DistancePredicate`.
    - `EntityEffectsPredicate` - An entity sub predicate that checks a `MobEffectsPredicate`.
    - `EntityLocationPredicate` - An entity sub predicate that checks a `LocationPredicate`.
    - `EntityNbtPredicate` - An entity sub predicate that checks a `NbtPredicate`.
    - `EntityPartialComponentsPredicate` - An entity sub predicate that checks if the specified data components match the `DataComponentPredicate`.
    - `EntitySlotsPredicate` - An entity sub predicate that checks a `SlotsPredicate`.
    - `EntityTagPredicate` - An entity sub predicate that checks if an entity is in or not in `Entity#entityTags`.
    - `MovementAffectedByPredicate` - An entity sub predicate that checks a `LocationPredicate` on `Entity#getBlockPosBelowThatAffectsMyMovement`.
    - `PassengerPredicate` - An entity sub predicate that checks an `EntityPredicate` on all passengers.
    - `SteppingOnPredicate` - An entity sub predicate that checks a `LocationPredicate` on `Entity#getOnPos`.
    - `TargetedEntityPredicate` - An entity sub predicate that checks an `EntityPredicate` on `Mob#getTarget`.
    - `TeamPredicate` - An entity sub predicate that checks if an entity is on the specified team.
    - `VehiclePredicate` - An entity sub predicate that checks an `EntityPredicate` on `Entity#getVehicle`.
- `net.minecraft.core.registries.BuiltInRegistries`, `Registries#ENTITY_SUB_PREDICATE_TYPE` is now a `Codec` instead of a `MapCodec`
- `net.minecraft.server.PlayerAdvancements`
    - `stopListening` -> `clearTriggers`, not one-to-one
    - `getTriggerMapForType` - Gets the map of advancement to trigger value for the given `CriterionTrigger`.
    - `$TriggerInstanceKey` - A record that holds the advancement and associated string criterion.

## More Resource Keys and Separating Registry Objects

`Block`s, `Item`s, `EntityType`s, `BlockEntityType`s, `Fluid`s, and `Potion`s have had their `ResourceKey`s stored in a separate `*Ids` class, which is then referenced whenever an identifier is needed. Blocks with an item representation, known as block items, have their own identifiers stored as a `BlockItemId`, which is just a pair of `ResourceKey`s, in `BlockItemIds`.

Additionally, the `BlockEntityType` and `EntityType` registry entries have been moved into their own separate class: `BlockEntityTypes` and `EntityTypes`, respectively.

### Tags Provider Changes

Since all registry objects in a tag has an accessible `ResourceKey`, all utility subclasses of `TagsProvider` have been removed (i.e. `HolderTagProvider`, `IntrinsicHolderTagsProvider`, `KeyTagProvider`), with the two `KeyTagProvider#tag` methods moved into `TagsProvider`. In addition, the `TagAppender` now only takes in `ResourceKey`s, the arbitrary element mapping completely removed.

This means that either a `ResourceKey` or `TagKey` must be provided to add an element to a tag through `TagsProvider`s.

For block items and block item tags (tags that exist within the block and item registry), `BlockItemTagsProvider` has been updated to use `BlockItemId`s and `BlockItemTagId`s, which are functionally records that hold the names of the object in the block and item registries. Entries are added in the `run` block, which is then called within the block or item `TagsProvider`, respectively.

- `net.minecraft.data.tags`
    - All `*TagsProvider` that extended `TagsProvider` or one of its subclasses now extend `TagsProvider`
    - `BlockItemTagAppender` - A `TagAppender` that converts a `BlockItemId` to its appropriate object resource key, usually for a `Block` or `Item`.
    - `BlockItemTagsProvider` now takes in a function with a `BlockItemTagId` and returning a `$CombinedAppender`
        - The original behavior of this class has moved to `VanillaBlockItemTagsProvider`
        - `tag` now takes in a `BlockItemTagId` instead of the `TagKey`s for each
        - `run` is now abstract
        - `wrapForBlocks`, `wrapForItems` - Wraps around an existing block or item `TagAppender` to take in a `BlockItemId` or `BlockItemTagId`.
        - `$CombinedAppender` - An appender that allows for `BlockItemId`s and `BlockItemTagId`s to be added.
    - `HolderTagProvider` class is removed
    - `IntrinsicHolderTagsProvider` class is removed
    - `KeyTagProvider` class is removed
        - `tag` -> `KeyTagProvider#tag`
    - `TagAppender` no longer takes in the `E` element generic
        - The `E` generic in methods have been replaced with `ResourceKey<T>`
        - `map` is removed
- `net.minecraft.references`
    - `BlockIds` now contain `ResourceKey`s for all blocks without item representations available in a tag
    - `BlockItemId` - An identifier that applies to a block with an item representation.
    - `BlockItemIds` - Identifiers for vanilla blocks with an item representation.
    - `ItemIds` now contain `ResourceKey`s for all non-block items available in a tag
- `net.minecraft.resources.ResourceKey#dependent` - Creates a new resource key by modifying the path of the object `Identifier`, either via a suffix or a `UnaryOperator`.
- `net.minecraft.tags`
    - `BlockItemTagId` - An identifier that applies to a block tag with an item tag representation.
    - `BlockItemTags` - Identifiers for block tags with an item tag representation.
- `net.minecraft.world.entity`
    - `EntityType` registry objects have been moved to `EntityTypes`
        - `byString` is removed
    - `EntityTypeIds` - Identifiers for entity types.
    - `EntityTypes` - All vanilla entity types.
- `net.minecraft.world.item.alchemy.PotionIds` - Identifiers for vanilla potions.
- `net.minecraft.world.level.block.entity`
    - `BlockEntityType` registry objects have been moved to `BlockEntityTypes`
        - The constructor is now `public` from `private`
        - `$BlockEntitySupplier` is now `public` from `private`
    - `BlockEntityTypeIds` - Identifiers for block entity types.
    - `BlockEntityTypes` - All vanilla block entity types.
- `net.minecraft.world.level.material.FluidIds` - Identifiers for fluids.

## Minor Migrations

The following is a list of useful or interesting additions, changes, and removals that do not deserve their own section in the primer.

### Sulfur Cube Archetypes

Sulfur cubes behave differently depending on the item it absorbed. What behavior the cube exhibits depends on the `SulfurCubeArchetype`, a datapack registry object, containing the corresponding `Item`.

```json5
// A file located at:
// - `data/examplemod/sulfur_cube_archetype/example_archetype.json`
{
    // The held items the sulfur cube must contain for this archetype to apply.
    // Can either be an item id, such as "minecraft:apple",
    // or a list of item ids, such as ["minecraft:apple", "minecraft:carrot", ...],
    // or an entity type tag, such as "#minecraft:sulfur_cube_food".
    "items": "#minecraft:sulfur_cube_food",
    // A list of attribute modifiers to apply to the sulfur cube when holding
    // the item.
    "attribute_modifiers": [
        {
            // A unique identifier representing the modifier.
            "id": "examplemod:example_arch_add_resistance",
            // The registry name of the attribute to apply.
            "attribute": "minecraft:knockback_resistance",
            // The amount to modify the attribute value.
            "amount": -2.0,
            // The operation to use when applying the modifier.
            // - 'add_value': Adds the amount to the current value.
            // - 'add_multiplied_base': Multiplies the amount to the base value after additions.
            // - 'add_multiplied_total': Multiplies the amount to the value after all modifications.
            "operation": "add_value"
        }
    ],
    // When `true`, the sulfur cube will float in liquids.
    "buoyant": true,
    // When present, a sulfur cube can be primed.
    "explosion": {
        // The maximum radius of the explosion. (e.g., 4.0 for regular tnt)
        "power": 4,
        // When `true`, the explosion can set other blocks on fire.
        "causes_fire": true,
        // The number of ticks before the sulfur cube explodes.
        "fuse": 120
    },
    // When present, the sulfur cube will deal damage to entities it comes in contact with.
    "contact_damage": {
        // The float provider specifying how many half-hearts to remove.
        "amount": 2,
        // The registry name of the damage type.
        "damage_type": "minecraft:cactus",
        // When `true`, the damage is attributed to the sulfur cube.
        "attribute_to_source": false
    },
    // Specifies the knockback the sulfur cube receives when hit.
    "knockback_modifiers": {
        // The base delta movement to apply along the XZ axis.
        "horizontal_power": 0.33,
        // The base delta movement to apply along the Y axis.
        "vertical_power": 0.06
    },
    // Specifies the sounds made by the sulfur cube.
    "sound_settings": {
        // The sound made when the sulfur cube receives knockback (on hit).
        "hit_sound": "minecraft:entity.sulfur_cube.regular.hit",
        // The sound made whent the sulfur cube collides with the player's pickup area.
        "push_sound": "minecraft:entity.sulfur_cube.regular.push",
        // The number of seconds to wait before playing the push sound again.
        "push_sound_cooldown": 0.5,
        // The square root of the velocity threshold for the push sound to be played.
        "push_sound_impulse_threshold": 0.2
    }
}
```

- `net.minecraft.core.registries.Registries#SULFUR_CUBE_ARCHETYPE` - A resource key for the archetypes of a sulfur cube.
- `net.minecraft.world.entity`
    - `SulfurCubeArchetype` - The behavior of a sulfur cube when holding the given item.
    - `SulfurCubeArchetypes` - All vanilla sulfur cube archetypes.
- `net.minecraft.world.item.component.SulfurCubeContent` - The absorbed block stored in the sulfur cube.

### Shears Breaking Speed

Shears now use three tags to determine how fast it should break an block:

- `minecraft:shears_extreme_breaking_speed` - Blocks are mined with a base of `15` speed, higher than all other vanilla tools.
- `minecraft:shears_major_breaking_speed` - Blocks are mined with a base of `5` speed, the same speed as copper tools.
- `minecraft:shears_minor_breaking_speed` - Blocks are mined with a base of `2` speed, the same speed as wooden tools.

Cobweb is the only block hardcoded to use the extreme breaking speed value.

- `net.minecraft.world.entity.Entity#attemptToShearEquipment` -> `Mob#attemptToShearEquipment`, `shearItem`; now `protected` from `private`

### Structure Processor Unrolling

`StructureProcessor`s no longer use `StructureProcessorType` as its registered instance, instead now directly using the `MapCodec` used as part of the serialization and deserialization process. `StructureProcessor` is no longer as a class as well, instead being represented by an interface. As such, all new processors should implement the `StructureProcessor` interface, replacing `getType` with `codec`:

```java
public class ExampleProcessor implements StructureProcessor {

    // Create the map codec to register
    public static final MapCodec<ExampleProcessor> MAP_CODEC = MapCodec.unit(ExampleProcessor::new);

    @Nullable
    @Override
    public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos targetPosition, BlockPos referencePos, StructureTemplate.StructureBlockInfo originalBlockInfo, StructureTemplate.StructureBlockInfo processedBlockInfo, StructurePlaceSettings settings) {
        // Modify the structure block info
        return processedBlockInfo;
    }

    // Replaces getType
    @Override
    public MapCodec<ExampleProcessor> codec() {
        return MAP_CODEC;
    }
}

// Register the map codec to the registry
Registry.register(BuiltInRegistries.STRUCTURE_PROCESSOR, Identifier.fromNamespaceAndPath("examplemod", "example_processor"), ExampleProcessor.MAP_CODEC);
```

- `net.minecraft.core.registries.BuiltInRegistries`, `Registries`
    - `STRUCTURE_PROCESSOR` now holds a `StructureProcessor` map codec instead of a `StructureProcessorType`
- `net.minecraft.world.level.levelgen.structure.templatesystem`
    - All classes now implement `StructureProcessor` instead of extending
    - `CODEC` fields are renamed to `MAP_CODEC`
    - `ProtectedBlockProcessor` is now a `record`
        - `cannotReplace` now takes in a `HolderSet` of `Block`s instead of the referenced `TagKey`
    - `StructureProcessor` is now an `interface` instead of a `class`
        - `getType` -> `codec`, now returning a `MapCodec`
    - `StructureProcessorType` no longer has a generic
        - Fields for each type are now inlined into `StructureProcessorTypes#bootstrap`
        - `codec` -> `StructureProcessor#codec`
        - `register` inlined into `StructureProcessorTypes#bootstrap`
    - `StructureProcessorTypes` - All vanilla structure processor codecs.

### Game Test Entity Builders

With the growing number of setup entities require when spawning for a game test, vanilla has split the construction of such entities into builder classes: `GameTestEntityBuilder` for generic entities, and `GameTestMobBuilder` for mobs. The builders can be acquired by calling `spawnEntity` and `spawnMob`, respectively, taking in the `EntityType` and some relative position in the test.

```java
// For some game test...
public void exampleTest(GameTestHelper helper) {
    // Spawn an entity
    ArmorStand entity = helper.spawnEntity(EntityType.ARMOR_STAND, 0, 0, 0)
        // Why the entity has spawned.
        .spawnReason(EntitySpawnReason.SPAWN_ITEM_USE)
        // How the entity should be rotated relative to the test.
        .rotation(Rotation.CLOCKWISE_90)
        // Whether the entity should be persisted.
        .requirePersistence(true)
        // Spawn the entity.
        .spawn();
    
    // Spawn a mob
    Pig mob = helper.spawnMob(EntityType.PIG, 1, 0, 1)
        // Spawns the entity with no AI.
        .withNoFreeWill()
        // Can use the entity builder methods.
        .spawnReason(EntitySpawnReason.NATURAL)
        .spawn();
}
```

- `net.minecraft.gametest.framework`
    - `GameTestEntityBuilder` - A builder that creates a mock entity for a test.
    - `GameTestHelper`
        - `makeMockServerPlayer` - Creates a mock server player in the desired `GameType`.
        - `spawn` now has overloads that take in `BlockPos` for the position
        - `spawn(EntityType, int, int, int, EntitySpawnReason)` -> `spawn(EntityType, double, double, double, EntitySpawnReason)`
        - `spawnEntity` - Creates a builder for spawning some entity at the given position.
        - `spawnMob` - Creates a builder for spawning some mob at the given position.
    - `GameTestMobBuilder` - A builder that creates a mock mob for a test.

### Xbox Friend List

Vanilla has now integrated the Xbox friend list into the game, allowing users to send requests and view other's status while in-game.

- `net.minecraft.SharedConstants#DEBUG_CHAT_FRIENDS_ONLY` - Sets that chat messages can only be exchanged with friends.
- `net.minecraft.client`
    - `Minecraft`
        - `friendsEnabled` - If the friends feature is enabled.
        - `allowFriendRequests` - Can the user accept friend invites.
        - `allowChatOnlyWithFriend` - Is the user only allowed to chat with friends.
        - `isFriendOnlyRestricted` - If the user cannot send or receive messages from the player is not on the friends list.
    - `Options`
        - `keyFriends` - The key that opens the friends list.
        - `inGameNotification` - Whether the user can receive notifications from their friends in-game.
        - `sharePresence` - What information is broadcast to friends who are also online.
    - `PresenceSharing` - An enum that denotes what information is shared with friends.
- `net.minecraft.client.gui.components`
    - `CommonButtons#friends` - Creates the friend list button.
    - `FriendsButton` - The button for showing the friends list.
    - `PlayerFaceExtractor#extractRenderState` now has an overload that takes in a `ResolvableProfile` for the skin texture
    - `PlayerFaceWidget` - A widget for the player face.
- `net.minecraft.client.gui.components.toasts`
    - `FriendToast` - A toast for showing activity of a user on the friends list.
    - `SystemToast$SystemToastId#FRIEND_SYSTEM_NOTIFICATION` - A notification from the friend system.
- `net.minecraft.client.gui.screens`
    - `PrivacyConfirmLinkScreen` - A screen for confirming the privacy settings for peer-to-peer communication.
    - `ShareToLanScreen` -> `MultiplayerOptionsScreen`, not one-to-one
- `net.minecraft.client.gui.screens.friends`
    - `AbstractFriendsEntryContainerWidget` - An abstract container for representing a friend within the friends list. 
    - `AbstractFriendsTab` - An abstract tab that represents a group of users in the friends list.
    - `AddFriendWidget` - A widget that handles sending a friend request to some user.
    - `FriendEntry` - An entry representing a friend.
    - `FriendsListConfirmScreen` - A confirmation screen that the user has read and understood the Xbox friends privacy information.
    - `FriendsOverlayScreen` - An overlay showing the user's friends and friend requests.
    - `FriendsOverlayTabButton` - A button to switch between friend tabs.
    - `FriendsTab` - A tab containing the user's friends.
    - `IncomingEntry` - An entry representing an incoming friend request.
    - `OutgoingEntry` - An entry representing an outgoing friend request.
    - `PendingTab` - A tab containing the user's pending friend requests.
- `net.minecraft.client.gui.screens.options.OnlineOptionsScreen#confirmFriendsListEnabled` - Sets the current screen to confirm whether the friends list should be enabled, if not enabled.
- `net.minecraft.client.gui.screens.social`
    - `PlayerSocialManager` now takes in the `FriendsService` and the `RemoteFriendListUpdateHandler`
        - `addFriendListUpdateListener`, `removeFriendListUpdateListener` - Handles listeners when the friend list is updated.
        - `getFriends` - Gets the current friends of the user.
        - `isFriend` - Whether the given user id is a friend of this user.
        - `getIncomingRequests`, `getOutgoingRequests` - The friends requests of the user.
        - `getFriendListState` - The current state of the friends list.
        - `sendFriendRequest`, `removeFriend`, `acceptIncomingFriendRequest`, `declineIncomingFriendRequest`, `revokeOutgoingFriendRequest`, `updateFriendSettings` - Requests for updating the user's friends list.
        - `isFriendListEnabled`, `setFriendListEnabled` - Handles the enabling of the friends list.
        - `isAllowFriendRequests`, `setAllowFriendRequests` - Handles whether friend requests are allowed.
        - `getPresenceHandler` - Returns the presence handler of the user and friends current state.
        - `$PlayerData` - Common data associated with a given user.
    - `PresenceHandler` - A handler for broadcasting the current state of the user in-game to other friends.
    - `RemoteFriendListUpdateHandler` - A handler for capturing the current state of the user's friends in-game.
- `net.minecraft.client.multiplayer.ClientPacketListener#onlineMode` - Whether the user is currently in online mode.
- `net.minecraft.client.server.IntegratedServer`
    - `setWorldGameType` - Sets the default game mode and applies it to the players.
    - `setGameTypeForOtherPlayers`, `getGameTypeForOtherPlayers` - Handles the game mode for the players that do not host the server.
    - `setWorldAllowCommands` - Sets whether players can run commands.
    - `commandsAllowedForOtherPlayers`, `setCommandsAllowedForOtherPlayers` - Handles whether players that do not host the server are allowed to run commands.
    - `getMultiplayerScope` - Gets the current scope of the server and who it's available for.
    - `publishServer` - Publishes the singleplayer server for the given scope at the specified port.
- `net.minecraft.network.Connection`
    - `isEncrypted` is removed
    - `fromChannel` - Creates a new connection from a channel.
    - `setIntendedProfileId`, `getIntendedProfileId` - Handles the profile UUID.
- `net.minecraft.network.protocol.game.ClientboundLoginPacket#onlineMode` - If the user is trying to login to a peer-to-peer connection.
- `net.minecraft.server.MinecraftServer`
    - `publishServer` now takes in the `$MultiplayerScope`
    - `unpublishServer` - Unpublishes the server from peer-to-peer connections.
    - `$MultiplayerScope` - The current scope of the server and who it's available for.
- `net.minecraft.server.commands.UnpublishCommand` - Unpublishes a server, stopping discovery and others from connecting.
- `net.minecraft.server.network.ServerConnectionListener`
    - `acceptChannel` - Accepts the channel to broadcast packets as the server.
    - `stopTcpServerListener` - Closes the channel, if this server is not broadcasting locally.
- `net.minecraft.util.CommonLinks#PRIVACY_AND_ONLINE_SETTINGS` - A link to the privacy and safety settings.

### Data Component Additions

- `sulfur_cube_content` - The absorbed block stored in the sulfur cube.

### Entity Attribute Additions

- `air_drag_modifier` - The drag applied to an entity when traveling through the air. Must be between `[0, 2048]` and defaults to `1`.
- `bounciness` - How much the entity should bounce after colliding with another bounding box. Must be between `[0, 1]` and defaults to `0`.
- `friction_modifier` - A scalar that used to modify the block friction when traveling in air. Must be between `[0, 2048]` and defaults to `1`.
- `knockback_resistance` now allows values between `[-2, 1]`
- `below_name_distance` - The maximum distance that the additional information below an entity's name tag can be seen. Must be between `[0, 512]` and defaults to `10`.
- `name_tag_distance` - The maximum distance that an entity's name tag can be seen. Must be between `[0, 512]` and defaults to `64`.

### Tag Changes

- `minecraft:block`
    - `glazed_terracotta`
    - `concrete`
    - `shears_extreme_breaking_speed`
    - `shears_major_breaking_speed`
    - `shears_minor_breaking_speed`
    - `suppresses_bounce`
    - `speleothems`
    - `sulfur_spike_replaceable_blocks`
    - `fox_immune_to`
    - `polar_bear_immune_to`
    - `snow_golem_immune_to`
    - `stray_immune_to`
    - `wither_immune_to`
    - `wither_skeleton_immune_to`
    - `default_immune_to`
    - `causes_periodic_geyser_eruptions`
    - `causes_continuous_geyser_eruptions`
- `minecraft:damage_type`
    - `sulfur_cube_with_block_immune_to`
- `minecraft:entity_type`
    - `not_affected_by_geysers`
- `minecraft:item`
    - `glazed_terracotta`
    - `concrete`
    - `concrete_powders`
    - `sulfur_cube_archetype/bouncy`
    - `sulfur_cube_archetype/regular`
    - `sulfur_cube_archetype/slow_flat`
    - `sulfur_cube_archetype/fast_flat`
    - `sulfur_cube_archetype/light`
    - `sulfur_cube_archetype/fast_sliding`
    - `sulfur_cube_archetype/slow_sliding`
    - `sulfur_cube_archetype/sticky`
    - `sulfur_cube_archetype/high_resistance`
    - `sulfur_cube_archetype/explosive`
    - `sulfur_cube_archetype/slow_bouncy`
    - `sulfur_cube_archetype/hot`
    - `sulfur_cube_swallowable`
    - `sulfur_cube_food`

### List of Additions

- `net.minecraft.ExitCodes` - A list of exit codes Minecraft can throw when crashing.
- `net.minecraft.client`
    - `Minecraft`
        - `getProfileResult` - Gets the loaded profile of the user, or `null`.
        - `getMetricsRecorder` - Gets the recorder for the game metrics.
        - `showDebugChat` - Displays a client system message.
        - `handleGlobalKeyPress` - Handles a global keymapping.
    - `OptionInstance`
        - `NO_ACTION` - A listener that ignores the changed value.
        - `$ValueUpdateListener` - A listener that handles what to do when an option value is changed.
- `net.minecraft.client.data.models.BlockModelGenerators`
    - `createBed` - Creates a bed with variants for the head and foot.
    - `createSign` - Creates a sign with variants depending on rotation.
    - `$BlockFamilyProvider`
        - `customHangingSign` - Creates the hanging sign model using a custom wall variant.
        - `hangingSign` - Creates the hanging sign model.
        - `pillar` - Creates a rotated pillar with a horizontal variant model.
- `net.minecraft.client.data.models.model`
    - `ModelTemplates`
        - `BED_HEAD`, `BED_FOOT` - Model templates for the bed parts.
        - `SIGN_ROT_*` - Model templates for sign rotations.
        - `WALL_SIGN`, `WALL_HANGING_SIGN` - Model templates for wall signs.
        - `HANGING_SIGN_ROT_*` - Model templates for hanging sign rotations.
        - `ATTACHED_HANGING_SIGN_ROT_*` - Model templates for attached hanging sign rotations.
    - `TextureMapping#bed` - Creates a texture map for a bed-like model.
- `net.minecraft.client.multiplayer`
    - `ClientChunkCache`
        - `addedLoadedChunks`, `removedLoadedChunks` - Gets the loaded chunk changes.
        - `flipUpdateTrackingSets` - Updates the section arrays to their next index, clearing them for use. 
    - `MultiPlayerGameMode#spectatorNoAction` - Sends a message saying that the client spectator is not spectating an entity.
- `net.minecraft.client.particle`
    - `GeyserBaseParticle` - The base particle for a geyser.
    - `GeyserEruptionParticle` - The particle for when a geyser is erupting.
    - `GeyserPlumeParticle` - The particle for when a plume emanates from a geyser.
- `net.minecraft.client.telemetry`
    - `TelemetryEventType#selfTest` - Returns whether there are missing translations for the telementry event and properties.
    - `TelemetryProperty#SERVER_SESSION_ID` - A property containing the server session UUID.
- `net.minecraft.client.telemetry.events.WorldLoadEvent#wasSent` - Whether the event was sent.
- `net.minecraft.commands.arguments.selector.options`
    - `InvertableSetOptionState` - A parsed option state that represents a value that can include or exclude.
    - `SetOnceOptionState` - A parsed option state that can only be set once.
- `net.minecraft.core.BlockPos#neighborColumn` - An iterable of positions that starts at some given XYZ, goes until some other Y, and then loop through the same column space in the four horizontally orthogonal directions.
- `net.minecraft.core.dispenser`
    - `FlintAndSteelDispenseItemBehavior` - The dispense behavior of flint and steal.
    - `SulfurCubeBlockDispenseItemBehavior` - The dispense behavior allowing sulfur cubes to equip items.
- `net.minecraft.core.particles`
    - `GeyserBaseParticleOptions` - The base particle options for a geyser.
    - `GeyserParticleOptions` - The particle options for a geyser.
- `net.minecraft.data`
    - `BlockFamilies`
        - `SULFUR`, `POLISHED_SULFUR`, `SULFUR_BRICKS` - Sulfur block variants.
        - `CINNABAR`, `POLISHED_CINNABAR`, `CINNABAR_BRICKS` - Cinnabar block variants.
    - `BlockFamily`
        - `shouldGenerateSmeltingRecipe` - Whether smelting recipes should be generated for this family.
        - `$Builder`
            - `customHangingSign` - Sets the hanging sign block using a custom wall variant.
            - `hangingSign` - Sets the hanging sign block.
            - `log` - Sets the log block.
            - `strippedLog` - Sets the stripped log block.
            - `pillar` - Sets the pillar block.
            - `dontGenerateSmeltingRecipe` - Prevents any smelting recipes from generating for this family.
        - `$Variant`
            - `CUSTOM_HANGING_SIGN` - A custom hanging sign variant.
            - `HANGING_SIGN` - A hanging sign variant.
            - `LOG` - A log variant.
            - `STRIPPED_LOG` - A stripped log variant.
            - `CUSTOM_WALL_HANGING_SIGN` - A custom hanging wall sign variant.
            - `WALL_HANGING_SIGN` - A hanging wall sign variant.
            - `PILLAR` - A pillar variant.
            - `getBaseVariantForCrafting` - Gets the base variant used for crafting this variant.
            - `getPrefixedRecipeGroup` - Returns the recipe group with the given prefix.
- `net.minecraft.data.recipes.RecipeProvider`
    - `pillarBuilder` - Creates the basic recipe builder for a pillar variant.
    - `generateSmeltingRecipe` - Generates the smelting recipe for the cracked and cobbled variants.
    - `getCraftingCriterionName` - Returns the crafting criterion name the given family variant.
- `net.minecraft.data.worldgen`
    - `BiomeDefaultFeatures#addSulfurCavesFeatures` - Features for the sulfur caves biome.
    - `TerrainProvider#peaksAndValleys` - Calculates the peaks and valleys scalar given the weirdness.
- `net.minecraft.data.worldgen.biome.OverworldBiomes#sulfurCaves` - The sulfur caves biome.
- `net.minecraft.gametest.framework`
    - `GameTestHelper#assertValueInBetween` - Assert the `Comparable` value is between some bounds.
    - `TestEnvironmentDefinition$Difficulty` - A test environment that sets the world difficulty.
- `net.minecraft.locale.Language#DEFAULT_INSTANCE` - The default language instance.
- `net.minecraft.nbt.NbtOps#getBooleanValue` - Gets the `boolean` result from the given `Tag`.
- `net.minecraft.server.MinecraftServer`
    - `SERVER_THREAD_NAME` - The name of the server thread.
    - `getCommandSpamThresholdSeconds` - The number of commands the user must send to be flagged as spam, decreasing once per second.
    - `getChatSpamThresholdSeconds` - The number of messages the user must send to be flagged as spam, decreasing once per second.
- `net.minecraft.server.dedicated.DedicatedServerProperties`
    - `commandSpamThresholdSeconds` - The number of commands the user must send to be flagged as spam, decreasing once per second.
    - `chatSpamThresholdSeconds` - The number of messages the user must send to be flagged as spam, decreasing once per second.
- `net.minecraft.server.jsonrpc`
    - `JsonRpc` - A utility for constructing the `ManagementServer`.
    - `ManagementServer#scheduleHeartbeat` - Sets the number of seconds between each heartbeat check.
- `net.minecraft.server.level`
    - `ChunkMap#hasEntityWithId` - Whether the chunk contains an entity with the given network identifier.
    - `ServerChunkCache#hasEntityWithId` - Whether the chunk contains an entity with the given network identifier.
    - `WorldGenRegion#isWithinWriteZone` - If the given position is within the write radius of the region.
- `net.minecraft.server.network.ServerConnectionListener#getSessionId` - Returns the session UUID of the server.
- `net.minecraft.server.notifications.NotificationManager#setServer`, `server` - Handles the `DedicatedServer` consuming the `NotificationManager`
- `net.minecraft.server.players.PlayerList#getPlayersByUUID` - Gets a map of UUID to their players.
- `net.minecraft.util`
    - `ARGB#setVector4fFromARGB32` - Directly sets the vector data with the color.
    - `CubicSpline`
        - `minValue`, `maxValue` - The bounds of the spline.
        - `sample`, `$Multipoint#sample` - Samples the spline at the given coordinates.
        - `asSampler` - Returns the sampler for the spine.
        - `$Multipoint#codec` - Gets the codec for the spline.
    - `ExtraCodecs#optionalAlwaysPresentFieldOf` - Constructs a optional `MapCodec` that, while should be present, can default to the specified value.
    - `TimeSource#constant` - A source that always returns the given `long` value.
    - `Util`
        - `CONTROL_CHARACTER_ESCAPER` - An escaper for control characters.
        - `join` - Flattens lists of elements into a single list.
        - `dumpThreadInfo` - Dumps the info for all live platform threads.
        - `timeSource`, `setTimeSource` - Handles the source keeping track of the time.
        - `shutdownTimeSource` - Sets the source to return the time of shutdown.
- `net.minecraft.util.profiling.jfr.stats.ThreadAllocationStat#LOGGER` - The stat logger.
- `net.minecraft.util.profiling.metrics.MetricSampler#samplingPhase`, `$MetricSamplerBuilder#withSamplingPhase`, `$SamplingPhase` - The phase when the metric sampling should occur.
- `net.minecraft.util.profiling.metrics.profiling.MetricsRecorder#sampleDuringExtract` - Samples the metrics, marking the phase as during render state extraction.
- `net.minecraft.world.attribute.LerpFunction#CONSTANT` - A function that returns the original value, only returning the next value once the alpha is `1`.
- `net.minecraft.world.entity`
    - `AgeableMob#canBeABaby` - Whether the mob can have a baby variant.
    - `Entity`
        - `DEFAULT_NAME_TAG_DISTANCE` - The default maximum distance an entity name tag can be seen from.
        - `DEFAULT_BELOW_NAME_DISTANCE` - The default maximum distance that additional name tag information on an entity can be seen from.
        - `MAX_NAME_TAG_DISTANCE` - The true maximum distance a name tag can be seen from.
        - `INVALID_ENTITY_ID` - A network identifier that is invalid for an entity.
        - `getEntityBounciness` - How bouncy the entity is after a collision.
        - `getEffectiveGravity` - Gets the gravity currently being applied to the entity.
        - `omnidirectionalAirMover` - Whether the entity can omnidirectionally move in the air.
        - `getAirDrag` - The drag applied to an entity when traveling through the air.
        - `syncPosition` - When true, tells the server entity to sync this entity's position.
        - `isInvulnerableToPiercingWeapon` - If the entity is invulnerable to damage from a piercing weapon.
        - `canBePickedFromInside` - Whether the entity can be picked when obstructing the player's eyes.
    - `EntityEvent#TNT_PRIME` - An event id for when a TNT is primed.
    - `EntitySpawnRequest` - An object containing the spawn reason and whether to ignore any requirements for spawning.
    - `EntityType#canSpawn` - Checks whether the entity can spawn in the given level.
    - `LivingEntity`
        - `BASE_HORIZONTAL_AIR_DRAG` - The base drag when within the air.
        - `BASE_VERTICAL_AIR_DRAG` - The base air drag when a non-flyable entity is moving vertically.
        - `HURT_DURATION_TICKS` - The number of ticks a living entity has invulnerability for after being hurt.
        - `WATER_DRAG` - The drag when swimming in water.
        - `SPRINTING_WATER_DRAG` - The drag when sprint swimming in water.
        - `LAVA_DRAG` - The drag when swimming in lava.
        - `LAVA_SHALLOW_VERTICAL_DRAG` - The drag when swimming vertically near the top of lava.
        - `DOLPHINS_GRACE_WATER_DRAG` - The drag when swimming with dolphin's grace in water.
        - `FLYING_AIR_DRAG` - The drag when flying through the air.
        - `FLYING_VERTICAL_AIR_DRAG` - The drag when flying through the air vertically.
        - `FLYING_LAVA_DRAG` - The drag when flying through lava.
        - `FLYING_WATER_DRAG` - The drag when flying through water.
        - `ELYTRA_HORIZONTAL_AIR_DRAG` - The drag when flying horizontally with an elytra.
        - `ELYTRA_VERTICAL_AIR_DRAG` - The drag when flying vertically with an elytra.
        - `BASE_SWIM_SPEED` - The base swim speed.
        - `handleKillingBlow` - Handles when the killing blow has been made to this entity.
        - `dealDefaultKnockback` - Applies the default knockback to this entity.
        - `createDamageSource` - Creates the default mob attack damage source from this entity.
        - `isInShallowFluid` - Whether the fluid covering the entity is less than the jump threshold.
    - `Mob#TAG_PERSISTENCE_REQUIRED` - A tag that marks whether the entity should be persisted.
    - `MobCategory#getDebugAbbreviation` - An abbreviation of the category when looking through one of the debug menus.
    - `PostSpawnProcessor` - A consumer that modifies the entity data after spawning into the level.
- `net.minecraft.world.entity.ai.navigation.PathNavigation#getMaxVerticalDistanceToWaypoint` - Gets the maximum vertical distance from the current node the entity pathfinding through.
- `net.minecraft.world.entity.animal.turtle.Turtle#getHomePos` - Gets the home position of the turtle.
- `net.minecraft.world.entity.item.PrimedTnt`
    - `NO_FUSE` - The value if a primed explosion object has no fuse.
    - `getRandomShortFuse` - Returns between an eighth and three eighths of the original fuse time.
- `net.minecraft.world.entity.monster.cubemob`
    - `AbstractCubeMob` - A cube-like mob.
    - `SulfurCube` - A sulfur cube entity.
- `net.minecraft.world.entity.monster.zombie`
    - `Drowned#isSearchingForLand` - If the drowned is searching for land.
    - `ZombieVillager#getVillagerDataFinalized`, `setVillagerDataFinalized` - Handles whether the villager data has been finalized, typically after conversion.
- `net.minecraft.world.entity.monster.npc`
    - `VillagerData#DEFAULT_TYPE` - The key of the default villager type.
    - `VillagerDataHolder`
        - `getVillagerDataFinalized`, `setVillagerDataFinalized` - Handles whether the villager data has been finalized, typically after conversion.
        - `finalizeVillagerType` - Finalizes the villager type after spawning into the world.
- `net.minecraft.world.inventory.Slot#safeClone` - Safely clones the item in the slot, typically with the max stack size for the associated container input.
- `net.minecraft.world.item`
    - `BucketItem#getFluidContext` - Gets the fluid clip context based on its contents.
    - `ItemStackTemplate#fromStack` - Creates the template directly from the stack.
    - `DyeColor#getTerracottaColor` - The `MapColor` of a terracotta block dyed this color.
- `net.minecraft.world.level`
    - `BaseSpawner#SET_DISPLAY_ENTITY_ID` - Sets the entity id to `-1`.
    - `ChunkPos#fromSectionNode` - Packs a packed section position into a chunk position.
    - `Level`
        - `ACROSS_THE_WHOLE_WORLD` - The maximum diameter of a level.
        - `getNextEntityId` - Returns the next free entity network identifier.
    - `LevelSettings#withAllowCommands` - Whether to allow commands in the level.
    - `SignalGetter#getBestOwnOrNeighbourSignal` - Returns the best redstone signal from the current block position or its surrounding neighbors.
- `net.minecraft.world.level.block`
    - `CopperChestBlock#getHingeSound` - Gets the hinge sound to play based on the current state of the chest.
    - `LevelEvent#SOUND_SULFUR_SPIKE_LAND` - The level event that plays a sound when an entity lands on a sulfur spike.
    - `PotentSulfurBlock` - A sulfur block that can apply noxious gas effects.
    - `SpeleothemBlock` - An abstract representation of a speleothem.
    - `SulfurSpikeBlock` - A sulfur spike speleothem.
    - `WeatheringCopper$WeatherState#forEach` - Loops through the available weather states.
- `net.minecraft.world.level.block.entity`
    - `BeaconBlockEntity#validateEffects` - Validates that the primary and secondary mob effects can be set for the given beacon level.
    - `BlockEntityTicker#andThen` - Chains two tickers together.
    - `DecoratedPotPatterns#itemToPatternMappings` - Operates on the keys for an item and its associated pot pattern.
    - `PotentSulfurBlockEntity` - A sulfur block entity that can apply noxious gas effects.
- `net.minecraft.world.level.block.state.BlockBehaviour`
    - `bounceRestitution`, `$Properties#bounceRestitution` - How much the entity should bounce after colliding with this block.
    - `ownSignal`, `$BlockStateBase#getOwnSignal` - The redstone signal this block is outputting.
- `net.minecraft.world.level.block.state.properties.BlockStateProperties#POTENT_SULFUR_STATE`, `PotentSulfurState` - The state of the potent sulfur.
- `net.minecraft.world.level.chunk`
    - `ChunkAccess#collectBiomesInPalette` - Collects all the biomes in the chunk sections.
    - `PalettedContainerRO#forEachInPalette` - Loop through all elements in the palette.
- `net.minecraft.world.level.levelgen`
    - `DensityFunction#mapChildren` - Maps the children density functions used by this function.
    - `DensityFunctions#intervalSelect`, `$IntervalSelect` - Computes the density using the given function if its corresponding threshold is above the input value.
    - `SurfaceRules$Context`
        - `getBiome` - Gets the biome at the context position.
        - `getNoiseSampler` - Returns the sampler to use for the noise given whether it should be three-dimensional.
- `net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate#matchesBiomes`, `$MatchingBiomesPredicate` - Checks whether the give block position is in the given set of biomes.
- `net.minecraft.world.level.levelgen.feature`
    - `SequenceFeature` - A feature made up of other placed features, applying them in the order they are given until either one of them fails or all succeeds.
    - `TemplateFeature` - A feature that attempts to place a structure template in the world.
    - `WeightedRandomSelectorFeature` - A feature that selects a random `PlacedFeature` from a weighted list to generate.
- `net.minecraft.world.level.levelgen.feature.configurations`
    - `TemplateFeatureConfiguration` - A configuration for the `TemplateFeature`.
    - `WeightedRandomFeatureConfiguration` - A configuration for the `WeightedRandomSelectorFeature`.
- `net.minecraft.world.level.levelgen.presets.WorldPresets$Bootstrap`
    - `makeNether` - Sets the `ChunkGenerator` for the nether dimension.
    - `makeEnd` - Sets the `ChunkGenerator` for the end dimension.
- `net.minecraft.world.level.levelgen.structure.templatesystem`
    - `RuleTest#testAgainstWorldState` - Tests the `BlockState` at the given `BlockPos` in the world.
    - `StructureProcessor#evaluatesEntirePieceState` - Whether the structure can process outside of the current chunk.
- `net.minecraft.world.level.storage`
    - `ServerLevelData#setAllowCommands` - Whether commands can be sent in the level.
    - `WorldData#setAllowCommands` - Whether commands can be sent in the world.
- `net.minecraft.world.level.storage.loot.LootPool#addAll` - Adds all pool entries.
- `net.minecraft.world.phys.Vec2#rotate` - Rotates the vector the given angle in radians.
- `net.minecraft.world.phys.shapes`
    - `CollisionContext#positionContext` - Creates a context with a given Y position.
    - `PositionCollisionContext` - A collision context for a given Y position.
- `net.minecraft.world.scores`
    - `PlayerTeam$OptionFlags` - An annotation that marks whether a given byte defines the usage flags of the team options.
    - `TeamColor` - The color representing a player team.

### List of Changes

- `com.mojang.blaze3d.platform.ClientShutdownWatchdog#startShutdownWatchdog` now takes in the `String` callsite and `GameConfig` instead of the `File` game directory
- `com.mojang.math.MatrixUtil`
    - `eigenvalueJacobi` now takes in the result `Quaternionf` instead of returning it
    - `svdDecompose` now takes in the input, translation, and USV `Quanternionf`s and `Vector3f` instead of returning the USV
- `net.minecraft`
    - `CrashReportCategory$Entry` is now a record and `public` from `private`
    - `SystemReport#setDetail` now takes in a `CrashReportDetail` instead of a `String` supplier
- `net.minecraft.advancements.triggers.PickedUpItemTrigger#thrownItemPickedUpByEntity` now takes in an optional `ContextAwarePredicate` for the player rather than the raw predicate
- `net.minecraft.client`
    - `Minecraft`
        - `saveReport` now has an overload that takes in the crash report exit code `int`
        - `crash` now takes in the crash report exit code `int`
        - `saveReportAndShutdownSoundManager` is now `public` from `private`, taking in the crash report exit code `int`
        - `destroy` -> `exitWorldAndClose`, not one-to-one
        - `isMultiplayerServer` is now `public` from `private`
        - `getRunningThread` has been expanded to `public` from `protected`
        - `$GameLoadCookie` -> `GameLoadCookie`, now `public` from `private`
    - `KeyMapping#matches` now has an overload that takes in an `InputConstants$Key`
    - `OptionInstance` constructor, `#createBoolean`, `createButton` now take in a `$ValueUpdateListener` instead of a consumer
        - `$CycleableValueSet` is now `public` from package-private
        - `$IntRangeBase` is now `public` from package-private
        - `$SliderableOrCyclableValueSet` is now `public` from package-private
        - `$SliderableValueSet` is now `public` from package-private
        - `$ValueSet` is now `public` from package-private
            - `createButton` now takes in a `$ValueUpdateListener` instead of a consumer
    - `Screenshot#grab` now has an overload that takes in the `Minecraft` instance and a `boolean` of whether a debug panorama is requested
- `net.minecraft.client.data.models.BlockModelGenerators`
    - `createColoredBlockWithRandomRotations`, `createColoredBlockWithStateRotations` now take in a list of `Block`s instead of a varargs
    - `createPointedDripstoneVariant` -> `createSpeleothemVariant`, now taking in a `Block`
    - `createBanner` no longer takes in the `Block`s for the standalone and wall variant
    - `createPointedDripstone` -> `createSpeleothem`, now taking in a `Block`
    - `createHangingSign` now takes in a block and the `MultiVariant`s for rotation and attachment instead of the blocks for the hanging and wall hanging sign
- `net.minecraft.client.data.models.model.ItemModelUtils#plainModel` now has an overload that takes in a `Transformation`
- `net.minecraft.client.gui.screens.inventory.SignEditScreen#MAGIC_SCALE_NUMBER` replaced by `MAGIC_BACKGROUND_SCALE`, not one-to-one
- `net.minecraft.client.input`
    - `InputWithModifiers$Modifiers` can now only target `ElementType#TYPE_USE`
    - `KeyEvent$Action` can now only target `ElementType#TYPE_USE`
    - `MouseButtonInfo`
        - `$Action` can now only target `ElementType#TYPE_USE`
        - `$MouseButton` can now only target `ElementType#TYPE_USE`
- `net.minecraft.client.multiplayer.ClientChunkCache#getLoadedEmptySections` split into `addedEmptySections`, `removedEmptySections`
- `net.minecraft.client.multiplayer.resolver.ServerAddress#parsePort` is now `public` from package-private
- `net.minecraft.client.telemetry`
    - `ClientTelemetryManager#createWorldSessionManager` now takes in the session UUID
    - `WorldSessionTelemetryManager` now takes in the session UUID
- `net.minecraft.client.telemetry.events.WorldLoadEvent#send` now takes in a `boolean` for whether to attempt to send the event even if the server brand is null
- `net.minecraft.commands.arguments.ColorArgument` -> `TeamColorArgument`
- `net.minecraft.commands.arguments.selector.EntitySelectorParser`
    - `hasNameEquals`, `setHasNameEquals`, `hasNameNotEquals`, `setHasNameNotEquals` -> `nameOption`, not one-to-one
    - `isLimited`, `setLimited` -> `limitedOption`, not one-to-one
    - `isSorted`, `setSorted` -> `sortedOption`, not one-to-one
    - `hasGamemodeEquals`, `setHasGamemodeEquals`, `hasGamemodeNotEquals`, `setHasGamemodeNotEquals`, -> `gamemodeOption`, not one-to-one
    - `hasTeamEquals`, `setHasTeamEquals`, `hasTeamNotEquals`, `setHasTeamNotEquals` -> `teamOption`, not one-to-one
    - `setTypeLimitedInversely`, `isTypeLimited`, `isTypeLimitedInversely` -> `typeOption`, not one-to-one
    - `hasScores`, `setHasScores` -> `scoresOption`, not one-to-one
    - `hasAdvancements`, `setHasAdvancements` -> `advancementsOption`, not one-to-one
- `net.minecraft.core.Holder` is now `sealed` to `$Direct` and `$Reference`
    - `$Reference` is now `non-sealed`
- `net.minecraft.core.cauldron.CauldronInteractions`
    - `addDefaultInteractions` is now `public` from package-private
    - `fillBucket`, `emptyBucket` are now `public` from package-private
- `net.minecraft.data.HashCache$ProviderCacheBuilder(String)` is now `public` from package-private
- `net.minecraft.data.recipes.RecipeProvider`
    - `hangingSign` -> `hangingSignBuilder`, now taking in an `Ingredient` instead of an `ItemLike` for the ingredient, and returning the `RecipeBuilder`
- `net.minecraft.data.worldgen`
    - `SurfaceRuleData` methods now take in the `HolderGetter<Biome>`
    - `TerrainProvider` methods no longer bind the `BoundedFloatFunction` generic
        - `buildErosionOffsetSpline` now takes in a `Float2FloatFunction` instead of a `BoundedFloatFunction`
- `net.minecraft.data.worldgen.features.TreeFeatures` methods that return a `TreeConfiguration$TreeConfigurationBuilder` now take in a `BlockStateProvider` for the block below the trunk
- `net.minecraft.gametest.framework`
    - `ExahustedAttemptsException` is now `public` from package-private
    - `GameTestEvent` class and static methods are now `public` from package-private
    - `GameTestInfo`
        - `getTick` is now `public` from package-private
        - `createSequence` is now `public` from package-private
    - `GameTestSequence` constructor is now `public` from package-private
        - `$Condition#trigger` is now `public` from package-private
    - `ReportGameListener` class is now `public` from package-private
    - `TestEnvironmentDefinition$Type#apply` is now `public` from package-private
- `net.minecraft.nbt.CompoundTag#shallowCopy` is now package-private from `protected`
- `net.minecraft.network.codec.ByteBufCodecs#collection` now has an overload that takes in the maximum size of the collection
- `net.minecraft.network.protocol.game`
    - `ClientboundSetPlayerTeamPacket$Parameters` is now a record
    - `GamePacketTypes#SERVERBOUND_SPECTATE_ENTITY` -> `SERVERBOUND_SPECTATOR_ACTION`
    - `ServerboundSpectateEntityPacket` -> `ServerboundSpectatorActionPacket`, not one-to-one
    - `ServerGamePacketListener#handleSpectateEntity` -> `handleSpectatorAction`
- `net.minecraft.network.protocol.login.ClientboundLoginFinishedPacket` now takes in the session UUID
- `net.minecraft.server`
    - `Bootstrap#getMissingTranslations` now takes in a `Language`
    - `MinecraftServer` now takes in the `NotificationManager`
- `net.minecraft.server.commands.SpreadPlayersCommand$Position#dist`, `normalize`, `getLength` are now `public` from package-private
- `net.minecraft.server.dedicated.DedicatedServer` now takes in the json rpc `ManagementServer` and `NotificationManager`
    - `setStatusHeartbeatInterval` now returns whether the heartbeat was scheduled successfully
- `net.minecraft.server.jsonrpc`
    - `JsonRPCUtils#createRequest` now has an overload that takes in a `String` for the method instead of an `Identifier`
    - `IncomingRpcMethod$Attributes`, `$IncomingRpcMethodBuilder#allowPreServerInit` now handles whether the method can be dispatched prior to server initialization
    - `ManagementServer` now takes in an `EventGroupLoop` instead of a `NioEventLoopGroup`
    - `OutgoingRpcMethod$Attributes`, `$OutgoingRpcMethodBuilder#allowPreServerInit` now handles whether the method can be dispatched prior to server initialization
- `net.minecraft.server.jsonrpc.internalapi.MinecraftApi#of`, `Minecraft*Impl` classes now take in the `NotificationManager` instead of the `DedicatedServer`
- `net.minecraft.server.level`
    - `BlockDestructionProgress#updateTick`, `getUpdatedRenderTick` now deal with `long`s instead of `int`s
    - `ChunkMap`
        - `collectSpawningChunks`, `forEachBlockTickingChunk` are now `public` from package-private
        - `anyPlayerCloseEnoughForSpawning`, `anyPlayerCloseEnoughTo` are now `public` from package-private
    - `ChunkTrackingView#squareIntersects` is now package-private from `protected`
    - `LoadingChunkTracker` class is now `public` from package-private
    - `PlayerSpawnFinder#getOverworldRespawnPos` -> `getLevelRespawnPos`
    - `ServerEntityGetter#getNearestEntity` now has an overload that takes in the list of `Entity`s to loop through along with the center position as three `double`s
    - `TicketType$Usage` can now only target `ElementType#TYPE_USE`
- `net.minecraft.server.packs.resources.FallbackResourceManager$EntryStack` constructor is now `public` from package-private
- `net.minecraft.server.players.StoredUserEntry#hasExpired` is now `public` from package-private
- `net.minecraft.server.rcon.thread.RconClient` is now `public` from package-private
- `net.minecraft.tags.TagNetworkSerialization$NetworkPayload` is now `public` from package-private
- `net.minecraft.util`
    - `AbstractListBuilder` class is now `public` from package-private
    - `BoundedFloatFunction#createUnlimited` -> `constant`, not one-to-one
    - `CubicSpline` is now `sealed` to `$Multipoint` and `$Constant`
        - The generic `C` is removed, while the generic `I` is no longer bounded to a `BoundedFloatFunction`
        - `mapAll` -> `mapCoordinates`, not one-to-one
        - `builder(I, BoundedFloatFunction<Float>)`, `$Builder` now takes in a `Float2FloatFunction` instead of a `BoundedFloatFunction`
        - `$Builder` constructors are now `private` from `protected`
        - `$CoordinateVisitor` is removed
            - Use a standard `UnaryOperator` instead
    - `FileUtil#isEmptyPath` is now `public` from package-private
    - `Mth`
        - `*_AXIS` are now `Vector3fc`s instead of `Vector3f`s
        - `rotationAroundAxis` now takes in a `Vector3fc` instead of a `Vector3f`
        - `isPowerOfTwo` now has an overload that takes in a `long` input
        - `roundToward` now has an overload that takes in `long`s
        - `positiveCeilDiv` now has an overload that takes in `long`s
    - `NativeModuleLister`
        - `tryGetVersion` -> `tryGetModuleVersion`, now `public` from `private`
        - `$NativeModuleInfo`, `$NativeModuleVersion` are now `record`s
    - `Util#timeSource` is now `private` from `public`
- `net.minecraft.util.filefix.access.ChunkNbt#updateChunk` now returns a `CompletableFuture`
- `net.minecraft.util.filefix.virtualfilesystem.DirectoryNode` constructor is now `public` from package-private
- `net.minecraft.util.profiling.TracyZoneFiller$PlotAndValue#set`, `add` are now `public` from package-private
- `net.minecraft.util.profiling.jfr.event.PacketEvent` is now `public` from package-private
- `net.minecraft.util.profiling.jfr.stats`
    - `GcHeapStat$Timing` is now `public` from package-private
    - `IoSummary$CountAndSize#add` is now `public` from package-private
- `net.minecraft.util.profiling.metrics.MetricSampler` now takes in a `$SamplingPhase`
    - `create(String, MetricCategory, T, ToDoubleFunction<T>)` -> `createExtractSampler(String, MetricCategory, DoubleSupplier)`
    - `getSampler` is now `public` from package-private
- `net.minecraft.util.profiling.metrics.profiling.ServerMetricsSamplersProvider$CpuStats` is now `public` from package-private
- `net.minecraft.util.random.WeightedList#of` now has an overload that takes in a vararg of elements
- `net.minecraft.util.valueproviders.FloatProviders#codec` now has an overload that only specifies a minimum `float` value
- `net.minecraft.world`
    - `InstantenousMobEffect` -> `InstantaneousMobEffect`
    - `InteractionResult$ItemContext#NONE`, `DEFAULT` are now `public` from package-private
    - `MobEffect`
        - `applyInstantenousEffect` -> `applyInstantaneousEffect`
        - `isInstantenous` -> `isInstantaneous`
- `net.minecraft.world.entity`
    - `AgeableMob#isBaby`, `setBaby` are now `final`
        - Override `canBeABaby` instead
    - `ConversionType#convert` is now `public` from package-private
    - `Entity`
        - `collideBoundingBox` now has an overload that takes in a `CollisionContext` instead of an `Entity`
        - `$Flags` can now only target `ElementType#TYPE_USE`
    - `EntityType`
        - `immuneTo`, `$Builder#immuneTo` now take in a `TagKey` instead of a `ImmutableSet` for the blocks the entity is immune to damage from
        - `createDefaultStackConfig` now returns a `PostSpawnProcessor` instead of a `Consumer`
        - `appendDefaultStackConfig` now deals with `PostSpawnProcessor`s instead of `Consumer`s
        - `appendComponentsConfig` now deals with `PostSpawnProcessor`s instead of `Consumer`s
        - `appendCustomEntityStackConfig` now deals with `PostSpawnProcessor`s instead of `Consumer`s
        - `spawn`, `create` now take in a `PostSpawnProcessor` instead of a `Consumer`
        - `create` now has an overload that takes in an `EntitySpawnRequest` instead of an `EntitySpawnReason`
        - `create(ValueInput, Level, EntitySpawnReason)` -> `create(ValueInput, Level, EntitySpawnRequest)`
        - `loadEntityRecursive` can now take in an `EntitySpawnRequest` instead of an `EntitySpawnReason`
        - `loadPassengersRecursive` can now take in an `EntitySpawnRequest` instead of an `EntitySpawnReason`
    - `Leashable$Wrench`
        - `ZERO` is now `public` from package-private
        - `torqueFromForce`, `accumulate` are now `public` from package-private
    - `LivingEntity`
        - `WATER_FLOAT_IMPULSE` -> `SWIMMING_VERTICAL_SPEED`, now `protected` from `private`
        - `travelInFluid` is now `protected` from `private`
        - `collectEquipmentChanges` is now `protected` from `private`, taking in the map of slots to last equipment stacks
        - `blockUsingItem`, `blockedByItem` now take in the `DamageSource` and `float` damage
        - `knockback` now takes in the `DamageSource` and `float` damage, and optionally whether the knockback was from some cause rather than a default application
        - `causeExtraKnockback` now takes in the `DamageSource` and `float` damage, and optionally whether the knockback was from some cause rather than a default application
        - `getEquipmentSlotForItem`, `isEquippableInSlot` are no longer `final`
    - `MobCategory` now takes in a `String` for the debug abbreviation
- `net.minecraft.world.entity.ai.behavior.AcquirePoi$JitteredLinearRetry` constructor is now `public` from package-private
- `net.minecraft.world.entity.ai.control`
    - `FlyingMoveControl` now takes in a generic for the `Mob`
    - `MoveControl` now takes in a generic for the `Mob`
    - `SmoothSwimmingMoveControl` now takes in a generic for the `Mob`
- `net.minecraft.world.entity.ai.sensing.Sensor#rememberPositives` is now `public` from package-private
- `net.minecraft.world.entity.ai.village.poi`
    - `PoiManager$DistanceTracker` constructor is now package-private from `protected`
    - `PoiSection#isValid` is now `public` from package-private
- `net.minecraft.world.entity.animal`
    - `Bucketable` -> `.entity.Bucketable`
    - `FlyingAnimal` replaced by `Entity#omnidirectionalAirMover`
        - `isFlying` still exists on classes that used to implement the interface
- `net.minecraft.world.entity.animal.bee.Bee` no longer implements `FlyingAnimal`
    - `getGoalSelector` -> `Mob#getGoalSelector`
    - `$BeeAttackGoal` constructor is now `public` from package-private
    - `$BeeBecomeAngryTargetGoal` constructor is now `public` from package-private
    - `$BeeGoToHiveGoal` constructor is now `public` from package-private
    - `$BeeGoToKnownFlowerGoal` constructor is now `public` from package-private
    - `$BeeHurtByOtherGoal` constructor is now `public` from package-private
    - `$BeeLookControl` constructor is now `public` from package-private
    - `$BeePollinateGoal` constructor is now `public` from package-private
    - `$BeeWanderGoal` constructor is now `public` from package-private
- `net.minecraft.world.entity.animal.dolphin.Dolphin`
    - `$DolphinSwimToTreasureGoal` constructor is now `public` from package-private
    - `$DolphinSwimWithPlayerGoal` constructor is now `public` from package-private
    - `$PlayWithItemsGoal` constructor is now `public` from package-private
- `net.minecraft.world.entity.animal.equine.AbstractHorse#createOffspringAttribute` is now `public` from package-private
- `net.minecraft.world.entity.animal.fish.AbstractFish$FishMoveControl` constructor is now `public` from package-private
- `net.minecraft.world.entity.animal.fox.Fox#canMove` is now `public` from `private`
- `net.minecraft.world.entity.animal.frog.Frog`
    - `$FrogLookControl` constructor is now `public` from package-private
    - `$FrogPathNavigation` constructor is now `public` from package-private
- `net.minecraft.world.entity.animal.goat.Goat#LONG_JUMPING_DIMENSIONS` replaced by `LONG_JUMPING_DIMENSION_SCALE_FACTOR`, `BABY_DIMENSIONS` (private), not one-to-one
- `net.minecraft.world.entity.animal.happyghast.HappyGhast$HappyGhastLookControl#wrapDegrees90` -> `Mth#wrapDegrees90`
- `net.minecraft.world.entity.animal.parrot.Parrot` no longer implements `FlyingAnimal`
- `net.minecraft.world.entity.animal.sniffer.SnifferAi#updateActivity` is now `public` from package-private
- `net.minecraft.world.entity.animal.turtle.Turtle`
    - `$TurtleBreedGoal` constructor is now `public` from package-private
    - `$TurtleGoHomeGoal` constructor is now `public` from package-private
    - `$TurtleLayEggGoal` constructor is now `public` from package-private
    - `$TurtleMoveControl` constructor is now `public` from package-private
    - `$TurtlePanicGoal` constructor is now `public` from package-private
    - `$TurtlePathNavigation` constructor is now `public` from package-private
    - `$TurtleTravelGoal` constructor is now `public` from package-private
- `net.minecraft.world.entity.item.PrimedTnt`
    - `DEFAULT_FUSE_TIME` is now `public` from `private`
    - `USED_PORTAL_DAMAGE_CALCULATOR` is now `public` from `private`
- `net.minecraft.world.entity.monster`
    - `Guardian#setMoving` is now `public` from `private`
    - `MagmaCube` -> `.monster.cubemob.MagmaCube`
        - The class now extends `AbstractCubeMob` and implements `Enemy`
    - `Slime` -> `.monster.cubemob.Slime`
        - The class now extends `AbstractCubeMob` and implements `Enemy`
    - `Strider$StriderPathNavigation` constructor is now `public` from package-private
    - `Vex` now implements `OwnableEntity`
- `net.minecraft.world.entity.monster.breeze.BreezeAi#updateActivity` is now `public` from package-private
- `net.minecraft.world.entity.monster.creaking.Creaking$CreakingPathNavigation` constructor is now `public` from package-private
- `net.minecraft.world.entity.monster.skeleton.AbstractSkeleton#getStepSound` is now `protected` from package-private
- `net.minecraft.world.entity.monster.zombie`
    - `Drowned#wantsToSwim` is now `public` from `private`
    - `Zombie$ZombieAttackTurtleEggGoal` is now `public` from package-private
- `net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader$WanderToPositionGoal` constructor is now `public` from package-private
- `net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge(EntityType, double, double, double, Vec3, Level)` is now `protected` from package-private
- `net.minecraft.world.entity.raid.Radier$RaiderCelebtration` constructor is now `public` from package-private
- `net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior$TrackIteration` fields are now `public` from package-private
- `net.minecraft.world.inventory`
    - `AbstractFuranceMenu` no longer takes in the `RecipeType`
    - `BeaconMenu#updateEffects` now returns a `boolean` of whether the effects were successfully set.
    - `ArmSlot` class is now `public` from package-private
- `net.minecraft.world.item`
    - `BucketItem#content` is now `protected` from `private`
    - `DyeColor` now takes in the `MapColor` for the terracotta variant
    - `ItemStack#getDamageSource` no longer takes in the supplied default `DamageSource`
- `net.minecraft.world.item.crafting.RecipePropertySet#create` is now `public` from package-private
- `net.minecraft.world.item.trading`
    - `TradeRebalanceVillagerTrades` now extends `VillagerTrades`
    - `VillagerTrade` one constructor now takes in the raw `HolderSet` of `Enchantment`s instead of being optionally-wrapped
- `net.minecraft.world.level`
    - `ChunkPos#MAX_COORDINATE_VALUE` -> `ChunkPyramid#MAX_CHUNK_COORDINATE_VALUE`
    - `CollisionGetter#getBlockCollisionsFromContext` is now `public` from `private`
    - `NaturalSpawner#getFilteredSpawningCategories` no longer takes in the `boolean` for whether to spawn friendlies
- `net.minecraft.world.level.biome`
    - `Climate`
        - `$ParameterPoint#parameterSpace` is now package-private from `protected`
        - `$SubTree` constructors are now `public` from `protected`
        - `$TargetPoint#toParameterArray` is now package-private from `protected`
    - `OverworldBiomeBuilder#addBiomes` is now package-private from `protected`
- `net.minecraft.world.level.block`
    - `Block$UpdateFlags` can now only target `ElementType#TYPE_USE`
    - `BedBlock` no longer implements `EntityBlock`
    - `Block#updateEntityMovementAfterFallOn` replaced by `getBounceRestitution`
    - `CopperGolemStatueBlock#updatePose` is now `protected` from package-private
    - `PointedDripstoneBlock` now extends `SpeleothemBlock`
        - The constructor now takes in the `BlockState` it can grow on
        - `TIP_DIRECTION` -> `SpeleothemBlock#TIP_DIRECTION`
        - `THICKNESS` -> `SpeleothemBlock#THICKNESS`
        - `WATERLOGGED` -> `SpeleothemBlock#WATERLOGGED`
        - `growStalactiteOrStalagmiteIfPossible` -> `SpeleothemBlock#growStalactiteOrStalagmiteIfPossible`, now an instance method
        - `canDrip` -> `isFreeHangingStalactite`, now `protected` from `public`
- `net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerStateData#getDispensingItems` is now `public` from package-private
- `net.minecraft.world.level.block.entity.vault`
    - `VaultClientData` constructor is now `public` from package-private
        - `updateDisplayItemSpin` is now `public` from package-private
    - `VaultConfig` constant fields are now `public` from package-private
    - `VaultServerData` constructors are now `public` from package-private
        - Constant fields are now `public` from package-private
    - `VaultSharedData` constructors are now `public` from package-private
        - Constant fields are now `public` from package-private
- `net.minecraft.world.level.block.state.BlockBehaviour`
    - `$BlockStateBase#emissiveRendering` no longer takes in the `BlockGetter` and `BlockPos`
    - `$Cache#collisionShape`, `largeCollisionShape`, `isCollisionShapeFullBlock` are now `public` from `protected`
    - `$Properties#emissiveRendering` now takes in a `BlockState` predicate instead of a `$StatePredicate`
- `net.minecraft.world.level.block.state.properties`
    - `BlockStateProperties#DRIPSTONE_THICKNESS` -> `SPELEOTHEM_THICKNESS`
    - `DripstoneThickness` -> `SpeleothemThickness`
- `net.minecraft.world.level.chunk`
    - `ChunkAccess#markPosForPostprocessing` -> `markPosForPostProcessing`
    - `LevelChunkSection`
        - `SECTION_WIDTH`, `SECTION_HEIGHT` replaced by `SectionPos#SECTION_SIZE`
        - `SECTION_SIZE` -> `SectionPos#SECTION_BLOCK_COUNT`
- `net.minecraft.world.level.chunk.status.ChunkStatusTasks`
    - `passThrough` is now `public` from package-private
    - `generateStructureStarts`, `loadStructureStarts` are now `public` from package-private
    - `generateStructureReferences` is now `public` from package-private
    - `generateBiomes` is now `public` from package-private
    - `generateNoise` is now `public` from package-private
    - `generateSurface` is now `public` from package-private
    - `generateCarvers` is now `public` from package-private
    - `generateFeatures` is now `public` from package-private
    - `initializeLight` is now `public` from package-private
    - `light` is now `public` from package-private
    - `generateSpawn` is now `public` from package-private
    - `full` is now `public` from package-private
- `net.minecraft.world.level.chunk.RegionFileStorage` constructor is now `public` from package-private
    - `write` is now `public` from `protected`
- `net.minecraft.world.level.dimension.DimensionType#infiniburn` now takes in a `HolderSet` of `Block`s instead of the referenced `TagKey`
- `net.minecraft.world.level.levelgen`
    - `Column$Range` constructor is now `public` from `protected`
    - `DensityFunction#mapAll` is now default, visiting all functions and their wrapped children.
    - `DensityFunctions`
        - `MAX_REASONABLE_NOISE_VALUE` is now package-private from `protected`
        - `weirdScaledSampler`, `$WeirdScaledSampler` replaced by `NoiseRouterData$QuantizedSpaghettiRarity#wrapRarity*d` methods, not one-to-one
            - `$RarityValueMapper`
                - `TYPE1` -> `NoiseRouterData$QuantizedSpaghettiRarity#wrapRarity3d`
                - `TYPE2` -> `NoiseRouterData$QuantizedSpaghettiRarity#wrapRarity2d`
        - `$BeardifierMarker` is now package-private from `protected`
        - `$BlendAlpha` is now package-private from `protected`
        - `$BlendDensity` replaced by `$Marker` with `$Marker$Type#BlendDensity`
        - `$BlendOffset` is now package-private from `protected`
        - `$Spline` is now a static final `class` instead of a `record`
        - `$Coordinate` now holds the raw `DensityFunction` instead of being `Holder`-wrapped
        - `$Mapped$Type` enum is now `public` from package-private
        - `$Marker` is now package-private from `protected`
            - `$Type` enum is now `public` from package-private
        - `$MulOrAdd$Type` enum is now `public` from package-private
        - `$ShiftNoise` interface is now `protected` from package-private
        - `$TwoArgumentSimpleFunction` interface is now `public` from package-private
    - `GeodeBlockSettings` is now a `record`
        - `cannotReplace`, `invalidBlocks` now take in a `HolderSet` of `Block`s instead of the referenced `TagKey`
    - `NoiseBasedChunkGenerator#buildSurface` now takes in a set of `Holder<Biome>`s instead of the `Registry<Biome>`
    - `NoiseRouterData$QuantizedSpaghettiRarity`
        - `getSphaghettiRarity2D` -> `wrapRarity2d`, not one-to-one
        - `getSpaghettiRarity3D` -> `wrapRarity3d`, not one-to-one
    - `NoiseSettings#*_SETTINGS` constants are now package-private from `protected`
    - `OreVeinifier#create` is now package-private from `protected`
    - `SurfaceRules`
        - `isBiome(ResourceKey<Biome>...)` now takes in a `HolderGetter<Biome>`
        - `noiseCondition` split into `noiseCondition2d`, `noiseCondition3d`
        - `$ConditionSource#codec` is now a `MapCodec` instead of a `KeyDispatchDataCodec`
        - `$Context` now takes in now takes in a set of `Holder<Biome>`s instead of the `Registry<Biome>`
            - `updateY` no longer takes in the block XZ `int`s
    - `SurfaceSystem#buildSurface` now takes in a set of `Holder<Biome>`s instead of the `Registry<Biome>`
- `net.minecraft.world.level.levelgen.blockpredicates`
    - `AllOfPredicate` class is now `public` from package-private
    - `AnyOfPredicate` class is now `public` from package-private
    - `CombiningPredicate` class is now `public` from package-private
    - `MatchingBlocksPredicate` class is now `public` from package-private
    - `MatchingFluidsPredicate` class is now `public` from package-private
    - `NotPredicate` class is now `public` from package-private
    - `ReplaceablePredicate` class is now `public` from package-private
    - `TrueBlockPredicate` class is now `public` from package-private
    - `UnobstructedPredicate` class is now `public` from package-private
- `net.minecraft.world.level.levelgen.carver.CaveCarverConfiguration#floorLevel` is now `public` from package-private
- `net.minecraft.world.level.levelgen.feature`
    - `DripstoneClusterFeature` -> `SpeleothemClusterFeature`
    - `DripstoneUtils` -> `SpeleothemUtils`
    - `Feature`
        - `DRIPSTONE_CLUSTER` -> `SPELEOTHEM_CLUSTER`
        - `POINTED_DRIPSTONE` -> `SPELEOTHEM`
    - `LakeFeature$Configuration` now takes in `BlockPredicate`s for where the feature can be placed, what blocks can be replaced with air or fluid, and what blocks can be replaced with the border/barrier blocks
    - `MultifaceGrowthFeature` now takes in the `MultifaceSpreadeableBlock`
    - `PointedDripstoneFeature` -> `SpeleothemFeature`
    - `ScatteredOreFeature` constructor is now `public` from package-private
    - `WeightedPlacedFeature` is now a record and deprecated
        - Use `WeightedRandomSelectorFeature` instead
- `net.minecraft.world.level.levelgen.feature.configurations`
    - `DripstoneClusterConfiguration` -> `SpeleothemClusterConfiguration`
    - `GeodeConfiguration` is now a record
    - `LargeDripstoneFeature` now takes in a `HolderSet` of `Block`s that can be replaced
    - `MultifaceGrowthConfiguration` can now take in a `Block` instead of a `MultifaceSpreadeableBlock` for the placing block
    - `PointedDripstoneConfiguration` -> `SpeleothemConfiguration`
    - `RandomFeatureConfiguration` is now a record and deprecated
        - Use `WeightedRandomFeatureConfiguration` instead
    - `RootSystemConfiguration` is now a `record`, now taking in `int`s for the test distance and Y deviation for whether there is space for the root
        - `rootReplaceable` now takes in a `HolderSet` of `Block`s instead of the referenced `TagKey`
    - `SimpleRandomFeatureConfiguration` -> `CompositeFeatureConfiguration`
    - `TreeConfiguration`
        - `CAN_PLACE_BELOW_OVERWORLD_TRUNKS` -> `CAN_PLACE_BELOW_TREE_TRUNKS`
        - `PLACE_BELOW_OVERWORLD_TRUNKS` -> `defaultPlaceBelowTreeTrunkProvider`
        - `$TreeConfigurationBuilder` must now always take in the `BlockStateProvider` for the block below the tree trunk
    - `VegetationPatchConfiguration` is now a `record`
        - `replaceable` now takes in a `HolderSet` of `Block`s instead of the referenced `TagKey`
- `net.minecraft.world.level.levelgen.flat.FlatLayerInfo` now has an overload that takes in a holder-wrapped `Block`
- `net.minecraft.world.level.levelgen.presets.WorldPresets#createFlatWorldDimensions` -> `createTestWorldDimensions`
- `net.minecraft.world.level.levelgen.structure.structures.RuinedPortalPiece` now takes in the `HolderLookup$Provider` of registries
    - The other constructor now takes in a `StructurePieceSerializationContext` instead of the `StructureTemplateManager`
    - `$Properties` is now a `record`
- `net.minecraft.world.level.levelgen.structure.pools.alias`
    - `DirectPoolAlias#CODEC` is now `public` from package-private
    - `RandomGroupPoolAlias#CODEC` is now `public` from package-private
    - `RandomPoolAlias#CODEC` is now `public` from package-private
- `net.minecraft.world.level.levelgen.structure.templatesystem`
    - `ProcessorRule#test` now takes in the `LevelReader` and now the `BlockState` for the location state
    - `StructureProcessor#processBlock` now takes in a `BlockPos` instead of the `StructureTemplate$StructureBlockInfo` for the relative template position
- `net.minecraft.world.level.lighting`
    - `LightEngine#getLightBlockInto` -> `getLightDampeningInto`
    - `SkyLightEngine` constructor is now package-private from `protected`
    - `SpatialLongSet$InternalMap` is now package-private from `protected`
- `net.minecraft.world.level.redstone.CollectingNeighborUpdater$MultiNeighborUpdate` constructor is now `public` from package-private
- `net.minecraft.world.level.storage.LevelStorageException` now has an overload that takes in the `Exception` cause
- `net.minecraft.world.level.storage.loot.entries`
    - `AlternativesEntry` constructor is now `public` from package-private
    - `ComposableEntryContainer` interface is now `public` from package-private
    - `EntryGroup` constructor is now `public` from package-private
    - `SequentialEntry` constructor is now `public` from package-private
- `net.minecraft.world.level.storage.loot.functions`
    - `EnchantRandomlyFunction$Builder#withOptions` now takes in the raw `HolderSet` of `Enchantment`s instead of being optionally-wrapped
    - `SetOminousBottleAmplifierFunction#MAP_CODEC` is now `public` from package-private
    - `SetRandomPotionFunction#fromTagKey` now takes in the raw `HolderSet` of `Potion`s instead of being optionally-wrapped
- `net.minecraft.world.level.storage.loot.predicates.EntityHasScoredCondition#hasScore` is now `private` from `protected`
- `net.minecraft.world.level.validation.PathAllowList$ConfigEntry#parse` is now `public` from package-private
- `net.minecraft.world.phys.shapes`
    - `ArrayVoxelShape` constructor is now package-private from `protected`
    - `BitSetDiscreteVoxelShape`
        - `getIndex` is now `private` from `protected`
        - `join` is now `public` from package-private
        - `join` is now package-private from `protected`
    - `CubeVoxelShape` constructor is now `public` from `protected`
    - `IndexMerger` interface is now `public` from package-private
    - `Shapes`
        - `findBits` is now package-private from `protected`
        - `lcm` is now package-private from `protected`
        - `createIndexMerger` is now package-private from `protected`
    - `SubShape` constructor is now package-private from `protected`
- `net.minecraft.world.scores`
    - `DisplaySlot#teamColorToSlot` replaced by `TeamColor`, not one-to-one
    - `PlayerTeam`
        - `packOptions`, `unpackOptions` now deals with a `byte` instead of an `int`
        - `setColor`, `getColor` now deals with an optional `TeamColor`
        - `$Packed` now takes in an optional `TeamColor` instead of a `ChatFormatting`
    - `Team#getColor` now returns an optional `TeamColor` instead of a `ChatFormatting`
- `net.minecraft.world.scores.criteria.ObjectiveCriteria#TEAM_KILL`, `KILLED_BY_TEAM` are now `TeamColor` to `ObjectiveCriteria` maps instead of arrays of `ObjectiveCriteria`
- `net.minecraft.world.timeline.Timelines#NIGHT_FOG_COLOR_MULTIPLIER` split into `NIGHT_FOG_COLOR_MULTIPLIER_START`, `NIGHT_FOG_COLOR_MULTIPLIER_END`

### List of Removals

- `net.minecraft.CrashReportCategory`
    - `formatLocation(double, double, double)`
    - `trimStacktrace`
- `net.minecraft.client`
    - `Minecraft`
        - `getVersionType`
        - `isSingleplayer`
    - `Options#touchscreen`
- `net.minecraft.client.data.models.model.ModelTemplates#BED_INVENTORY`
- `net.minecraft.client.geom.ModelLayers#BED_FOOT`, `BED_HEAD`
- `net.minecraft.client.gui.GuiGraphicsExtractor#sign`
- `net.minecraft.client.gui.render.pip.GuiSignRenderer`
- `net.minecraft.client.gui.screens.inventory.AbstractContainerScreen`
    - `extractSnapbackItem`
    - `clearDraggingState`
- `net.minecraft.client.model.geom.ModelLayers`
    - `createStandingSignModelName`
    - `createWallSignModelName`
    - `createHangingSignModelName`
- `net.minecraft.client.renderer.Sheets`
    - `SIGN_SHEET`
    - `SIGN_MAPPER`, `HANGING_SIGN_MAPPER`
    - `SIGN_SPRITES`, `HANGING_SIGN_SPRITES`
    - `getSignSprite`, `getHangingSignSprite`
- `net.minecraft.client.renderer.block.BuiltInBlockModels`
    - `createStandingSign`
    - `createWallSign`
    - `createCeilingHangingSign`
    - `createWallHangingSign`
    - `createSigns`
- `net.minecraft.client.renderer.blockentity`
    - `AbstractSignRenderer`
        - `getSignModel`
        - `getSignSprite`
        - `submitSign`
    - `HangingSignRenderer`
        - `createSignModel`
        - `submitSpecial`
        - `createHangingSignLayer`
    - `StandingSignRenderer`
        - `createSignModel`
        - `submitSpecial`
        - `createSignLayer`
    - `SignRenderState`
        - `woodType`
        - `$SignTransformations#body`
- `net.minecraft.client.renderer.special`
    - `HangingSignSpecialRenderer`
    - `StandingSignSpecialRenderer`
- `net.minecraft.client.renderer.state.gui.pip.GuiSignRenderState`
- `net.minecraft.client.resources.language.I18n`
    - `setLanguage`
    - `exists`
- `net.minecraft.core.BlockPos#getCenter`, `getBottomCenter`
    - Use `Vec3` methods that it delegated from
- `net.minecraft.data.AtlasIds`
    - `BEDS`
    - `SIGNS`
- `net.minecraft.util.Tuple`
- `net.minecraft.util.thread.BlockableEventLoop#hasDelayedCrash`
- `net.minecraft.world.entity`
    - `LivingEntity`
        - `TAG_HURT_BY_TIMESTAMP`
        - `currentExplosionCause`
    - `Mob#setBodyArmorItem`
- `net.minecraft.world.entity.ai.navigation.GroundPathNavigation#hasValidPathType`
- `net.minecraft.world.entity.npc.villager.Villager`
    - `Villager(EntityType, Level, ResourceKey)`
    - `Villager(EntityType, Level, Holder)`
- `net.minecraft.world.level.block.entity`
    - `BedBlockEntity`
    - `DecoratedPotPatterns#getPatternFromItem`
- `net.minecraft.world.level.levelgen`
    - `DensityFunction`
        - `DIRECT_CODEC`, `HOLDER_HELPER_CODEC`
        - `$FunctionContext#getBlender`
    - `SurfaceRules#isBiome(List<ResourceKey<Biome>>)`
- `net.minecraft.world.scores.ReadOnlyScoreInfo#safeFormatValue`
- `net.minecraft.world.waypoints.Waypoint#MAX_RANGE`
