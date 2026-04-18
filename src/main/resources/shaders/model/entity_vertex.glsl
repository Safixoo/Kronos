#version 430

out vec2 v_Uv;
out vec3 v_Pos;

uniform mat4 u_ProjectionMat;
uniform mat4 u_InvViewMat;

uniform int u_MatrixIndex;

layout (location = 0) in vec3 a_Pos;
layout (location = 1) in vec2 a_Uv;
layout (location = 2) in vec3 a_Normal;

layout(std430, binding = 0) buffer Matrices {
    mat4 modelView[];
};

void main() {
    mat4 modelViewMat = modelView[u_MatrixIndex];
    vec4 viewPosition = modelViewMat * vec4(a_Pos, 1.0);

    gl_Position = u_ProjectionMat * viewPosition;
    vec4 worldPosition = u_InvViewMat * viewPosition;

    v_Uv = a_Uv;
    v_Pos = worldPosition.xyz;
}
