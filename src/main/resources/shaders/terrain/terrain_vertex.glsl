#version 330

layout (location = 0) in uvec2 a_Position;
layout (location = 1) in vec2 a_Uv;
layout (location = 2) in uvec4 a_ColorAndLight;

out vec3 v_Color;
out vec2 v_TextureUv;
out float v_Distance;

uniform vec3 u_RegionPos;
uniform mat4 u_ProjModelViewMat;

uniform sampler2D u_LightTex;

vec3 getEyePosition(uvec2 atPosition) {
    uvec2 xy = atPosition & 0x1FFFFFu;
    uvec2 z = atPosition >> 21u;

    return vec3(xy, z.x | z.y << 11u) + u_RegionPos;
}

ivec2 lightmapTexelCoord(uint light) {
    return ivec2(light) >> ivec2(4, 0) & 0xF;
}

vec3 getLightColor(uint light) {
    return texelFetch(u_LightTex, lightmapTexelCoord(light), 0).rgb;
}

void main() {
    vec3 eyePosition = getEyePosition(a_Position);
    gl_Position = u_ProjModelViewMat * vec4(eyePosition, 1.0);

    vec3 color = vec3(a_ColorAndLight.xyz) * (1.0 / 255.0);

    v_Color = color * getLightColor(a_ColorAndLight.w);
    v_Distance = length(eyePosition);
    v_TextureUv = a_Uv * (1.0 / 65536.0);
}
