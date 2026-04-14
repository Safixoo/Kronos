#version 330

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

uniform mat4 u_FogMat;

out vec4 fragColor;

float getDistance() {
    vec4 pos = u_FogMat * vec4(gl_FragCoord.xyz, 1.0);
    return length(pos.xyz / pos.w);
}

void main() {
    float dist = getDistance();

    #ifdef FOG_LINEAR
    // Optimized GL_LINEAR formula.
    // = (end - dist) / (end - start)
    // = (end - dist) / radius
    // = (end - dist) * invRadius
    // = constant -> (end * invRadius) + (-dist * invRadius)
    // = endInvRad + (dist * -invRadius)
    // = fma(dist, -invRadius, endInvRad).
    float factor = clamp((u_FogNegInvRadius * dist) + u_FogEndInvRad, 0.0, 1.0);
    #else
    // GL_EXP
    float factor = clamp(exp(-u_FogDensity * dist), 0.0, 1.0);
    #endif

    vec4 texColor = texture(u_TexId, v_TextureUv);
    vec3 rgbColor = texColor.rgb * v_Color;

    fragColor = vec4(mix(u_FogColor, rgbColor, factor), texColor.a);
}
