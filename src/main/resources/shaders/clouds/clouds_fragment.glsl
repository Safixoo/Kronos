#version 110
#extension GL_EXT_gpu_shader4 : enable

#define CLOUD_WIDTH 12.0

in float v_Distance;
in vec4 v_Color;

uniform int u_Distance;

void main() {
    float cellDistance = float(u_Distance);

    float factor = 1.0 - smoothstep(cellDistance * 0.75, cellDistance, v_Distance);
    gl_FragColor = vec4(v_Color.rgb, v_Color.a * factor);
}
