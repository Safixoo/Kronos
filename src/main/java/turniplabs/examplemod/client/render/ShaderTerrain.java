package turniplabs.examplemod.client.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Unique;

public class ShaderTerrain {
	private boolean shaderCreated;
	private int programId;
	private int u_TexId, u_CamPos;
	private int u_FogEnd, u_FogStart, u_FogColor;

	public ShaderTerrain() {
		this.prepareAndCompileShader();
	}

	public void prepareAndCompileShader() {
		if (this.shaderCreated) {
			return;
		}

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

	public void glGetUniformLocation() {
		this.u_CamPos = GL20.glGetUniformLocation(this.programId, "u_CamPos");
		this.u_TexId = GL20.glGetUniformLocation(this.programId, "u_TexId");

		this.u_FogEnd = GL20.glGetUniformLocation(this.programId, "u_FogEnd");
		this.u_FogStart = GL20.glGetUniformLocation(this.programId, "u_FogStart");
		this.u_FogColor = GL20.glGetUniformLocation(this.programId, "u_FogColor");
	}

	public void unbindProgram() {
		GL20.glUseProgram(0);
	}

	public void bindProgram() {
		GL20.glUseProgram(this.programId);
	}

	public void setupUniforms(float posX, float posY, float posZ) {
		GL20.glUniform1i(this.u_TexId, 0);
		GL20.glUniform3f(this.u_CamPos, posX, posY, posZ);

		GL20.glUniform1f(this.u_FogEnd, GL11.glGetFloat(GL11.GL_FOG_END));
		GL20.glUniform1f(this.u_FogStart, GL11.glGetFloat(GL11.GL_FOG_START));

		float[] fogColor = new float[4];

		GL11.glGetFloatv(GL11.GL_FOG_COLOR, fogColor);
		GL20.glUniform3f(this.u_FogColor, fogColor[0], fogColor[1], fogColor[2]);
	}

	@Unique
	private static final String vertexShader =
			"  #version 110    																						\n" +
			"    																									\n" +
			"  varying vec3 v_Color;																				\n" +
			"  varying vec2 v_TextureUv;																			\n" +
			"  varying float v_Distance;																			\n" +
			"  uniform vec3 u_CamPos;          																		\n" +
			"     																									\n" +
			"  void main() {    																					\n" +
			"      vec4 position = gl_ModelViewMatrix * (gl_Vertex - vec4(u_CamPos, 0.0));	 		    			\n" +
			"      gl_Position = gl_ProjectionMatrix * position;	 		    									\n" +
			"	   					 																				\n" +
			"      v_TextureUv = gl_MultiTexCoord0.st;   															\n" +
			"	   v_Color = gl_Color.rgb; 																			\n" +
			"	   v_Distance = length(position); 																	\n" +
			"  } 																									\n" +
			"    																									\n" +
			"      																									\n";

	@Unique
	private static final String fragmentShader =
			"   #version 110																			  		  \n" +
			"   																		  						  \n" +
			"   varying vec3 v_Color;																			  \n" +
			"   varying vec2 v_TextureUv;																		  \n" +
			"   varying float v_Distance;																	      \n" +
			"  																									  \n" +
			"   uniform sampler2D u_TexId;															 		      \n" +
			"   																					 		      \n" +
			"   uniform float u_FogEnd;																 		      \n" +
			"   uniform float u_FogStart;																 		  \n" +
			"   uniform vec3 u_FogColor;																 		  \n" +
			"   																			  					  \n" +
			"   void main() {																			          \n" +
			"   	vec4 blockColor = vec4(v_Color, 1.0) * texture2D(u_TexId, v_TextureUv);						  \n" +
			"   	float factor = v_Distance < u_FogEnd ? smoothstep(u_FogStart, u_FogEnd, v_Distance) : 1.0;	  \n" +
			"   																								  \n" +
			"   	gl_FragColor = vec4(mix(blockColor.rgb, u_FogColor, factor), blockColor.a);	 				  \n" +
			"   }																			   					  \n" +
			"      																								  \n";
}
