#version 330

in float v_Distance;
in vec4 v_Color;

uniform float u_CloudEnd;
uniform vec4 u_FogColor;

out vec4 fragColor;

void main() {
    float factor = smoothstep(u_CloudEnd - 160, u_CloudEnd, v_Distance);
    fragColor = mix(v_Color, u_FogColor, factor);
}
