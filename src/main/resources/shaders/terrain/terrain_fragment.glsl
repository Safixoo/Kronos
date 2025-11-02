#version 330

in vec3 v_VertPos;
in vec3 v_Color;
in vec2 v_TextureUv;

uniform sampler2D u_TexId;

uniform float u_FogEndInvRad;
uniform float u_FogNegInvRadius;
uniform vec3 u_FogColor;

uniform mat4 u_ModelViewMat;
uniform vec3 u_SunPos;

uniform int u_Pass;

out vec4 fragColor;

void main() {
    // Optimized GL_LINEAR factor formula.
    // = (end - dist) / (end - start) = (end - dist) / radius = (end - dist) * invRadius
    // = (end * invRadius) + (-dist * invRadius) = endInvRad + (dist * -invRadius)
    // = fma(dist, -invRadius, endInvRad).
    float factor = clamp((u_FogNegInvRadius * length(v_VertPos)) + u_FogEndInvRad, 0.0, 1.0);

    vec3 dFdxPos = dFdx(v_VertPos);
    vec3 dFdyPos = dFdy(v_VertPos);
    vec3 facenormal = inverse(mat3(u_ModelViewMat)) * normalize(cross(dFdxPos, dFdyPos));

    vec4 texColor = texture(u_TexId, v_TextureUv);

    if (u_SunPos.x > 187000.5) {
        texColor = vec4(1);
    }

    float ndotl = clamp(dot(facenormal, u_SunPos), 0.55, 1.0);

    vec3 rgbColor = texColor.rgb * v_Color * ndotl;

    // Fog doesn't mix the alpha component.
    fragColor = vec4(mix(u_FogColor, rgbColor, factor), texColor.a - (u_Pass * 0.4));
}
