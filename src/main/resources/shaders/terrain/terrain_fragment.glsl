#version 330

in float v_Distance;
in vec3 v_Color;
in vec2 v_TextureUv;

uniform sampler2D u_TexId;
uniform vec3 u_FogColor;

#ifdef FOG_LINEAR
uniform float u_FogEndInvRad;
uniform float u_FogNegInvRadius;
#else
uniform float u_FogDensity;
#endif

out vec4 fragColor;

void main() {
    // Optimized GL_LINEAR factor formula.
    // = (end - dist) / (end - start) = (end - dist) / radius = (end - dist) * invRadius
    // = (end * invRadius) + (-dist * invRadius) = endInvRad + (dist * -invRadius)
    // = fma(dist, -invRadius, endInvRad).
    #ifdef FOG_LINEAR
    // GL_LINEAR
    float factor = clamp((u_FogNegInvRadius * v_Distance) + u_FogEndInvRad, 0.0, 1.0);
    #else
    // GL_EXP
    float factor = clamp(1.0 / exp(u_FogDensity * v_Distance), 0.0, 1.0);
    #endif

    vec4 texColor = texture(u_TexId, v_TextureUv);
    vec3 rgbColor = texColor.rgb * v_Color;

    // Fog doesn't mix the alpha component.
    fragColor = vec4(mix(u_FogColor, rgbColor, factor), texColor.a);
}
