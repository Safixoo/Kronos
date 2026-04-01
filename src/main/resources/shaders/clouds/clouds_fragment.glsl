#version 110
#extension GL_EXT_gpu_shader4 : enable

in float v_Distance;
in vec4 v_Color;

uniform int u_CloudData;

out vec4 fragColor;

#define CLOUD_WIDTH 12.0

void main() {
    float cellDistance = float(int(u_CloudData) >> 24 & 0xFF) * CLOUD_WIDTH;

    float factor = 1.0 - smoothstep(cellDistance * 0.75, cellDistance, v_Distance);
    fragColor = vec4(v_Color.rgb, v_Color.a * factor);
}
