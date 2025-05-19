package turniplabs.examplemod.mixins;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.RenderGlobal;
import net.minecraft.client.render.camera.ICamera;
import net.minecraft.client.render.culling.CameraFrustum;
import net.minecraft.client.render.terrain.ChunkRenderer;
import net.minecraft.client.world.WorldClient;
import net.minecraft.core.util.helper.MathHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import turniplabs.examplemod.client.ComplexFrustum;
import turniplabs.examplemod.client.GlobalFlags;
import turniplabs.examplemod.client.renderer.gl.GlVertexBuffer;
import turniplabs.examplemod.client.util.Direction;
import turniplabs.examplemod.client.util.interfaces.mixin.IChunkRenderer;

import java.util.List;

@Mixin(value = RenderGlobal.class, remap = false)
public abstract class RenderGlobalMixin {
	@Shadow
	private ChunkRenderer[] sortedChunkRenderers;
	@Shadow
	private int renderersBeingRendered;
	@Shadow
	@Final
	private Minecraft mc;
	@Shadow
	private int renderersLoaded;
	@Shadow
	private WorldClient worldObj;
	@Shadow
	private int dummyInt0;
	@Shadow
	private ChunkRenderer[] chunkRenderers;
	private boolean shaderCreated = false;

	/**
	 * @author Safixo
	 * @reason No-op.
	 */
	@Overwrite
	public void clipRenderersByFrustum(CameraFrustum frustum, float partialTick) {}

	@Unique
	private static float withinRenderDistance(float x, float y, float z) {
		x += 8.0F;
		y += 8.0F;
		z += 8.0F;

		return (x * x) + (y * y) + (z * z);
	}

	private static Long2ReferenceMap<ChunkRenderer> positionMap;

	private static int activeFrame = 0;

	private static int getOutwardDirections(int playerChunkX, int playerChunkY, int playerChunkZ, ChunkRenderer render) {
		int planes = 0;

		planes |= (render.posX >> 4) <= playerChunkX ? Direction.set(Direction.WEST)  : 0;
		planes |= (render.posX >> 4) >= playerChunkX ? Direction.set(Direction.EAST)  : 0;

		planes |= (render.posY >> 4) <= playerChunkY ? Direction.set(Direction.DOWN)  : 0;
		planes |= (render.posY >> 4) >= playerChunkY ? Direction.set(Direction.UP)    : 0;

		planes |= (render.posZ >> 4) <= playerChunkZ ? Direction.set(Direction.NORTH) : 0;
		planes |= (render.posZ >> 4) >= playerChunkZ ? Direction.set(Direction.SOUTH) : 0;

		return planes;
	}



	/**
	 * @author Safixo
	 * @reason Avoid no sense and cull all together to avoid looping through the renderer too often.
	 */
	@Overwrite
	public boolean updateRenderers(ICamera camera) {
		this.bfsGraph(camera);

		return true;
	}

	private void bfsGraph(ICamera camera) {
		// TODO: Se podria optimizar la queue y la render list utilizando mi propio "queue".
		this.renderList.clear();

		float playerX = (float) camera.getX();
		float playerY = (float) camera.getY();
		float playerZ = (float) camera.getZ();

		int playerChunkX = MathHelper.floor(playerX) >> 4;
		int playerChunkY = MathHelper.clamp(MathHelper.floor(playerY) >> 4, 0, 15);
		int playerChunkZ = MathHelper.floor(playerZ) >> 4;

		activeFrame++;
		chunksUpdated = 0;
		positionMap = ((IChunkRenderer)this.sortedChunkRenderers[0]).chunkMap();

		float renderDistance = square(GL11.glGetFloat(GL11.GL_FOG_END));
		IChunkRenderer spawn = (IChunkRenderer) positionMap.get(asLong(playerChunkX, playerChunkY, playerChunkZ));

		final ObjectArrayList<IChunkRenderer> queue = new ObjectArrayList<>();

		if (spawn != null) {
			GlobalFlags.MESHING = true;
			if (spawn.isDirty()) {
				spawn.queueRebuild();
			}
			GlobalFlags.MESHING = false;

			exploreNodes(queue, spawn, spawn.getAdjacentMask(), 0);
			spawn.setFrame(activeFrame);
			this.renderList.add(spawn.getRender());
		}

		chunksUpdated = 0;

		int sectionIndex = 0;

		while (queue.size() > sectionIndex) {
			IChunkRenderer node = queue.get(sectionIndex++);
			ChunkRenderer render = node.getRender();

			float distX = render.posX - playerX;
			float distY = render.posY - playerY;
			float distZ = render.posZ - playerZ;

			if (!isSectionVisible(distX, distY, distZ, renderDistance)) {
				continue;
			}

			this.renderList.add(node.getRender());

			// TODO: Esto se podia optimizar con bitmath perturbadora.
			int outwardDirections = getOutwardDirections(playerChunkX, playerChunkY, playerChunkZ, node.getRender());
			int angleMask = 0;

			// TODO: Porque esto sería necesario en primer lugar?
			if (playerChunkY != render.posY >> 4) {
				angleMask = getAngleVisibilityMask(distX, distY, distZ);
			}

			outwardDirections &= node.getAdjacentMask();

			exploreNodes(queue, node, outwardDirections, angleMask);
		}
	}

	private static int chunksUpdated = 0;

	private static void exploreNodes(ObjectArrayList<IChunkRenderer> queue, IChunkRenderer fatherNode, int directions, int angleMask) {
		if (directions == 0) {
			return;
		}

		int solidFaces = fatherNode.getSolidFaces();

		if (Direction.hasSet(directions, Direction.DOWN_SET) && notSolidFace(solidFaces, Direction.DOWN_SET | angleMask)) {
			IChunkRenderer adjacent = fatherNode.getAdjacent(Direction.DOWN);
			visitNode(queue, adjacent, activeFrame, Direction.DOWN);
		}

		if (Direction.hasSet(directions, Direction.UP_SET) && notSolidFace(solidFaces, Direction.UP_SET | angleMask)) {
			IChunkRenderer adjacent = fatherNode.getAdjacent(Direction.UP);
			visitNode(queue, adjacent, activeFrame, Direction.UP);
		}

		if (Direction.hasSet(directions, Direction.NORTH_SET) && notSolidFace(solidFaces, Direction.NORTH_SET | angleMask)) {
			IChunkRenderer adjacent = fatherNode.getAdjacent(Direction.NORTH);
			visitNode(queue, adjacent, activeFrame, Direction.NORTH);
		}

		if (Direction.hasSet(directions, Direction.SOUTH_SET) && notSolidFace(solidFaces, Direction.SOUTH_SET | angleMask)) {
			IChunkRenderer adjacent = fatherNode.getAdjacent(Direction.SOUTH);
			visitNode(queue, adjacent, activeFrame, Direction.SOUTH);
		}

		if (Direction.hasSet(directions, Direction.WEST_SET) && notSolidFace(solidFaces, Direction.WEST_SET | angleMask)) {
			IChunkRenderer adjacent = fatherNode.getAdjacent(Direction.WEST);
			visitNode(queue, adjacent, activeFrame, Direction.WEST);
		}

		if (Direction.hasSet(directions, Direction.EAST_SET) && notSolidFace(solidFaces, Direction.EAST_SET | angleMask)) {
			IChunkRenderer adjacent = fatherNode.getAdjacent(Direction.EAST);
			visitNode(queue, adjacent, activeFrame, Direction.EAST);
		}
	}

	private static void visitNode(ObjectArrayList<IChunkRenderer> queue, IChunkRenderer adj, int activeFrame, int dir) {
		if (adj.getFrame() != activeFrame && !adj.solidSection()) {
			if (chunksUpdated < 5 && adj.isDirty()) {
				GlobalFlags.MESHING = true;
				adj.queueRebuild();
				GlobalFlags.MESHING = false;

				adj.setDirty(false);
				chunksUpdated++;
			}

			adj.setFrame(activeFrame);
			queue.add(adj);
		}
	}

	private static boolean notSolidFace(int solidFaces, int dir) {
		return (solidFaces & dir) == 0;
	}

	@Redirect(method = "sortAndRender", at = @At(value = "INVOKE", target = "Ljava/util/List;contains(Ljava/lang/Object;)Z"))
	private boolean noopContains(List instance, Object o) {
		return true;
	}

	@Inject(method = "sortAndRender", at = @At(value = "INVOKE", target = "Ljava/util/Arrays;sort([Ljava/lang/Object;Ljava/util/Comparator;)V"))
	private void noopSort(ICamera camera, int renderPass, double partialTick, CallbackInfoReturnable<Integer> cir) {
		ChunkRenderer chunkRenderer = this.sortedChunkRenderers[0];

		this.sortedChunkRenderers = new ChunkRenderer[] {chunkRenderer};
	}

	/**
	 * @author Safixo
	 * @reason Smarter, faster, stronger..
	 */
	@Overwrite
	private int renderSortedRenderers(int min, int max, int renderPass, double partialTick) {
		int addedWorldRenderers = 0;

		// Disables fog when option is active.
		if (!this.mc.gameSettings.fog.value) {
			GL11.glDisable(GL11.GL_FOG);
		}

		// Look like terrain display lists have some of these states baked.
		// With my VBO rendering this isn't the case.
		if (renderPass == 1) {
			GL11.glColorMask(true, true, true, true);
			GL11.glEnable(GL11.GL_CULL_FACE);
		}

		float camX = (float) this.mc.activeCamera.getX((float) partialTick);
		float camY = (float) this.mc.activeCamera.getY((float) partialTick);
		float camZ = (float) this.mc.activeCamera.getZ((float) partialTick);

		if (!this.shaderCreated) {
			this.prepareAndCompileShader();
		}

		GL20.glUseProgram(this.programId);
//		GL15.glTranslatef(-camX, -camY, -camZ);

		this.setupUniforms(camX, camY, camZ);

		// Translate terrain based on player position.

		// Renders the front-to-back in solid and back-to-front in
		// translucent.
		if (renderPass == 0) {
			for (int i = 0; i < this.renderList.size(); i++) {
				this.renderSolidTerrain(i);
			}
		} else {
			for (int i = this.renderList.size() - 1; i >= 0; i--) {
				this.renderTranslucentTerrain(i);
			}
		}

		GL30.glBindVertexArray(0);

//		GL15.glTranslatef(camX, camY, camZ);
		GL20.glUseProgram(0);

		if (this.mc.gameSettings.fog.value) {
			GL11.glEnable(GL11.GL_FOG);
		}

		return addedWorldRenderers;
	}

	private int programId;
	private int tex, camPos;
	private static int angleMask;

	public void prepareAndCompileShader() {
		this.programId = GL20.glCreateProgram();
		int vertexShaderId = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
		int fragmentShaderId = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);

		GL20.glShaderSource(vertexShaderId, vertexShader);
		GL20.glShaderSource(fragmentShaderId, fragmentShader);

		GL20.glCompileShader(vertexShaderId);

		System.out.println("\nVertex Shader Errors:\n" + GL20.glGetShaderInfoLog(vertexShaderId, 250));

		GL20.glCompileShader(fragmentShaderId);

		System.out.println("\nFragment Shader Errors:\n" + GL20.glGetShaderInfoLog(fragmentShaderId, 250));

		GL20.glAttachShader(this.programId, vertexShaderId);
		GL20.glAttachShader(this.programId, fragmentShaderId);

		GL20.glLinkProgram(this.programId);

		GL20.glDeleteShader(vertexShaderId);
		GL20.glDeleteShader(fragmentShaderId);

		this.glGetUniformLocation();

		this.shaderCreated = true;
	}

	@Unique
	public void glGetUniformLocation() {
		this.camPos = GL20.glGetUniformLocation(this.programId, "u_CamPos");
		this.tex = GL20.glGetUniformLocation(this.programId, "u_TexId");
	}

	@Unique
	public void setupUniforms(float posX, float posY, float posZ) {
		GL20.glUniform1i(this.tex, 0);
		GL20.glUniform3f(this.camPos, posX, posY, posZ);
	}

	@Unique
	private static final String vertexShader =
			"  #version 110    																						\n" +
			"    																									\n" +
			"  varying vec3 v_color;																				\n" +
			"  varying vec2 v_textureUv;																			\n" +
			"  uniform vec3 u_CamPos;          																		\n" +
			"     																									\n" +
			"  void main() {    																					\n" +
			"      gl_Position = gl_ModelViewProjectionMatrix * (gl_Vertex - vec4(u_CamPos, 0.0));	 		    	\n" +
			"	   					 																				\n" +
			"      v_textureUv = gl_MultiTexCoord0.st;   															\n" +
			"	   v_color = gl_Color.rgb; 																			\n" +
			"  } 																									\n" +
			"    																									\n" +
			"      																									\n";

	@Unique
	private static final String fragmentShader =
			"   #version 110																			  		  \n" +
			"   																		  						  \n" +
			"   varying vec3 v_color;																				  \n" +
			"   varying vec2 v_textureUv;																		  \n" +
			"  																									  \n" +
			"   uniform sampler2D u_TexId;															 		      \n" +
			"   uniform sampler2D u_LightId;															 		  \n" +
			"   																			  					  \n" +
			"   void main() {																			          \n" +
			"   	gl_FragColor = vec4(v_color, 1.0) * texture2D(u_TexId, v_textureUv);						  \n" +
			"   }																			   					  \n" +
			"      																								  \n";

	private final ObjectArrayList<ChunkRenderer> renderList = new ObjectArrayList<>();

	private void renderSolidTerrain(int index) {
		final ChunkRenderer sectionRender = this.renderList.get(index);
		final IChunkRenderer sectionRenderInterface = (IChunkRenderer) sectionRender;
		final GlVertexBuffer buffer = sectionRenderInterface.solidBuffer();

		if (buffer != null && buffer.vertexCount != 0) {
			buffer.bindVAO();
			buffer.draw();
			this.renderersLoaded++;
			this.renderersBeingRendered++;
		}
	}

	private void renderTranslucentTerrain(int index) {
		final ChunkRenderer sectionRender = this.renderList.get(index);
		final IChunkRenderer sectionRenderInterface = (IChunkRenderer) sectionRender;
		final GlVertexBuffer buffer = sectionRenderInterface.translucentBuffer();

		if (buffer != null && buffer.vertexCount != 0) {
			buffer.bindVAO();
			buffer.draw();
		}
	}

	private static float lastDistance;

	private static boolean isSectionVisible(float distX, float distY, float distZ, float renderDistance) {
		lastDistance = withinRenderDistance(distX, distY, distZ);
		return lastDistance < renderDistance && ComplexFrustum.testAab(distX, distY, distZ);
	}

	private static int getAngleVisibilityMask(float dx, float dy, float dz) {
		int angleOcclusionMask = 0;

		dx = Math.abs(dx - 8);
		dy = Math.abs(dy - 8);
		dz = Math.abs(dz - 8);

		if (dx > dy || dz > dy) {
			angleOcclusionMask |= Direction.DOWN_SET | Direction.UP_SET;
		}
		if (dx > dz || dy > dz) {
			angleOcclusionMask |= Direction.SOUTH_SET | Direction.NORTH_SET;
		}
		if (dy > dx || dz > dx) {
			angleOcclusionMask |= Direction.EAST_SET | Direction.WEST_SET;
		}

		return ~angleOcclusionMask;
	}



	private static long asLong(int x, int y, int z) {
		long l = 0L;
		l |= ((long)x & 4194303L) << 42;
		l |= ((long)y & 1048575L) << 0;
		l |= ((long)z & 4194303L) << 20;
		return l;
	}

	private static boolean isOpaque(int chunkX, int chunkY, int chunkZ) {
		ChunkRenderer render = positionMap.get(asLong(chunkX, chunkY, chunkZ));

		return render != null && ((IChunkRenderer) render).solidSection();
	}

	private static double intBound(double s, double ds) {
		if (ds == 0) return Double.POSITIVE_INFINITY;
		double s0 = Math.floor(s);
		return ds > 0 ? (s0 + 1.0 - s) / ds : (s - s0) / -ds;
	}


	private static float square(float x) {
		return x * x;
	}
}
