#version 110

in vec2 v_Uv;
in float v_Distance;

uniform sampler2D u_Tex;

void main() {
    float factor = smoothstep(gl_Fog.start, gl_Fog.end, v_Distance);
    gl_FragColor = mix(texture2D(u_Tex, v_Uv), gl_Fog.color, factor) * gl_Color;
}
