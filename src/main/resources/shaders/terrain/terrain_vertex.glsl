#version 120
#extension GL_EXT_gpu_shader4 : enable

attribute vec3 a_Position;
attribute vec2 a_Uv;
attribute vec3 a_Color;

varying vec3 v_Color;
varying vec2 v_TextureUv;
varying float v_Distance;

uniform vec3 u_RegionPos;

#define VERT_SCALE vec3(128.0 / 65535.0, 64.0 / 65535.0, 128.0 / 65535.0)
#define UV_SCALE (1.0 / 65535.0)

void main() {
    vec3 blockPosition = (a_Position * VERT_SCALE) + u_RegionPos;
    vec4 position = gl_ModelViewMatrix * vec4(blockPosition, 1.0);

    gl_Position = gl_ProjectionMatrix * position;

    v_Color = a_Color;
    v_TextureUv = a_Uv;
    v_Distance = length(position);
}
