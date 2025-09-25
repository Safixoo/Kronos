#version 330

in uvec2 a_Position;
in vec2 a_Uv;
in vec3 a_Color;

out vec3 v_Color;
out vec2 v_TextureUv;
out float v_Distance;

uniform vec3 u_RegionPos;
uniform mat4 u_ProjMat;
uniform mat4 u_ModelViewMat;

const uint REGION_BLOCK_SHIFT_X = 7u;
const uint REGION_BLOCK_SHIFT_Y = 6u;
const uint REGION_BLOCK_SHIFT_Z = 7u;

const uint POSITION_BITS = 20u;

const uint REGION_SCALE_X = (1u << (POSITION_BITS - REGION_BLOCK_SHIFT_X)) - 1u;
const uint REGION_SCALE_Y = (1u << (POSITION_BITS - REGION_BLOCK_SHIFT_Y)) - 1u;
const uint REGION_SCALE_Z = (1u << (POSITION_BITS - REGION_BLOCK_SHIFT_Z)) - 1u;

#define REGION_SCALE vec3(1.0 / REGION_SCALE_X, 1.0 / REGION_SCALE_Y, 1.0 / REGION_SCALE_Z)
#define UV_SCALE (1.0 / 65535)

vec3 extractBlockPos(uvec2 atPosition) {
    uvec3 lowHalf = (uvec3(atPosition.x) >> uvec3(0u, 10u, 20u)) & 0x3FFu;
    uvec3 topHalf = (uvec3(atPosition.y) >> uvec3(0u, 10u, 20u)) & 0x3FFu;

    return vec3(lowHalf | topHalf << 10u) * REGION_SCALE;
}

void main() {
    vec3 blockPosition = extractBlockPos(a_Position) + u_RegionPos;
    vec4 position = u_ModelViewMat * vec4(blockPosition, 1.0);

    gl_Position = u_ProjMat * position;

    v_Color = a_Color;
    v_TextureUv = a_Uv * UV_SCALE;
    v_Distance = length(position);
}
