#version 110

attribute vec3 a_Position;
attribute vec2 a_Uv;
attribute vec3 a_Color;

varying vec4 v_Color;
varying float v_Distance;
varying vec2 v_Uv;

#define TEX_SIZE 256.0

void main() {
    vec4 position = gl_ModelViewMatrix * vec4(a_Position, 1.0);
    gl_Position = gl_ProjectionMatrix * position;

    v_Uv = a_Uv / TEX_SIZE;
    v_Distance = length(position);
    v_Color = vec4(a_Color, 0.8) * gl_Color;
}
