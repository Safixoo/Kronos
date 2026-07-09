#version 330

in float v_Distance;
in vec3 v_Color;
in vec2 v_TextureUv;

uniform sampler2D u_TexId;
uniform vec3 u_FogColor;

#ifdef FOG_LINEAR
uniform float u_FogAdd;
uniform float u_FogMul;
#else
uniform float u_FogDensity;
#endif

out vec4 fragColor;

void main() {
    #ifdef FOG_LINEAR
    // Optimized GL_LINEAR formula.
    float factor = clamp(u_FogMul * v_Distance + u_FogAdd, 0.0, 1.0);
    #else
    // GL_EXP
    float factor = clamp(exp(-u_FogDensity * v_Distance), 0.0, 1.0);
    #endif

    vec4 textureColor = texture(u_TexId, v_TextureUv);
    fragColor = vec4(mix(u_FogColor, textureColor.rgb * v_Color, factor), textureColor.a);
}
