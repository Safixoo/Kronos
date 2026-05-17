#version 460
#extension GL_ARB_shader_draw_parameters : enable

out float v_Distance;
out vec2 v_Uv;
out vec3 v_Pos;
out vec3 v_Color;

uniform mat4 u_ProjectionMat;
uniform mat4 u_InvViewMat;

uniform sampler2D u_LightTex;

layout (location = 0) in vec3 a_Pos;
layout (location = 1) in vec2 a_Uv;
layout (location = 2) in vec3 a_Normal;

layout(std140, binding = 0) readonly buffer Matrices {
    mat4 modelView[];
};

ivec2 lightmapTexelCoord(uint light) {
    return ivec2(light) >> ivec2(4, 0) & 0xF;
}

void main() {
    int index = int(gl_BaseInstance) & 0xFFFF;
    int light = (int(gl_BaseInstance) >> 16) & 0xFF;

    vec4 modelViewPosition = modelView[index] * vec4(a_Pos, 1.0);

    gl_Position = u_ProjectionMat * modelViewPosition;
    vec4 worldPosition = u_InvViewMat * modelViewPosition;

    v_Distance = length(modelViewPosition);
    v_Uv = a_Uv;
    v_Pos = worldPosition.xyz;
    v_Color = texelFetch(u_LightTex, lightmapTexelCoord(light), 0).rgb;
}
