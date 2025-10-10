#version 330

in float v_Distance;
in vec3 v_Color;
in vec2 v_TextureUv;

uniform sampler2D u_TexId;

uniform float u_FogEnd;
uniform float u_FogStart;
uniform vec4 u_FogColor;

out vec4 fragColor;

void main() {
    vec4 tex = texture(u_TexId, v_TextureUv);
    float factor = smoothstep(u_FogStart, u_FogEnd, v_Distance);

    fragColor = vec4(mix(v_Color * tex.rgb, u_FogColor.rgb, factor), tex.a);
}
