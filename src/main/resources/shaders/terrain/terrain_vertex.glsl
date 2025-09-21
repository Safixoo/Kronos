#version 110

attribute vec3 a_Position;
attribute vec2 a_Uv;
attribute vec4 a_Color;

varying vec3 v_Color;
varying vec2 v_TextureUv;
varying float v_Distance;
uniform vec4 u_CamPos;

void main() {
    vec4 position = gl_ModelViewMatrix * (vec4(a_Position, 1.0) + u_CamPos);
    gl_Position = gl_ProjectionMatrix * position;

    v_TextureUv = a_Uv;
    v_Color = a_Color.rgb;
    v_Distance = length(position);
}
