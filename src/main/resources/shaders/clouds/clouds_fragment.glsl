#version 330

in float v_Distance;
in vec4 v_Color;
in vec2 v_Uv;

uniform sampler2D u_CloudTex;

uniform float u_FogEnd;
uniform vec4 u_FogColor;

out vec4 fragColor;

void main() {
    vec4 texColor = texture(u_CloudTex, v_Uv);
    float factor = smoothstep(u_FogEnd, u_FogEnd * 1.6, v_Distance);

    fragColor = mix(texColor * v_Color, u_FogColor, factor);
}
