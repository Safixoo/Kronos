#version 110

attribute vec3 a_Position;
attribute vec3 a_Color;

varying vec4 v_Color;
varying float v_Distance;
varying vec2 v_Uv;

uniform vec3 u_CloudColor;

#define CLOUD_WIDTH 12.0
#define CLOUD_ALPHA 0.8

void main() {
    vec4 position = gl_ModelViewMatrix * vec4(a_Position * vec3(CLOUD_WIDTH, 1.0, CLOUD_WIDTH), 1.0);
    gl_Position = gl_ProjectionMatrix * position;

    v_Distance = length(position);
    v_Color = vec4(a_Color * u_CloudColor, CLOUD_ALPHA) * gl_Color;
}
