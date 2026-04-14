#version 110
#extension GL_EXT_gpu_shader4 : enable

attribute vec3 a_Position;
attribute vec3 a_Color;

varying vec4 v_Color;
varying float v_Distance;
varying vec2 v_Uv;

uniform int u_CloudData;
uniform vec3 u_CloudOffset;

#define CLOUD_WIDTH 12.0
#define CLOUD_ALPHA 0.8
#define CLOUD_SCALE vec3(CLOUD_WIDTH, 1.0, CLOUD_WIDTH)

void main() {
    vec4 position = vec4((a_Position + u_CloudOffset) * CLOUD_SCALE, 1.0);
    gl_Position = gl_ModelViewProjectionMatrix * position;

    vec3 color = vec3(ivec3(u_CloudData) >> ivec3(0, 8, 16) & 0xFF) / 255.0;

    v_Distance = length(position);
    v_Color = vec4(a_Color * color, CLOUD_ALPHA) * gl_Color;
}
