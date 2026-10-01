#version 110

varying vec2 v_Uv;
varying float v_Distance;

void main() {
    vec4 position = gl_ModelViewMatrix * vec4(gl_Vertex.xyz, 1.0);

    gl_Position = gl_ProjectionMatrix * position;
    v_Uv = gl_MultiTexCoord0.st;
    v_Distance = length(position.xyz);
}
