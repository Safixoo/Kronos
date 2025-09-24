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

const uint REGION_SCALE_X = (1u << (POSITION_BITS - REGION_BLOCK_SHIFT_X));
const uint REGION_SCALE_Y = (1u << (POSITION_BITS - REGION_BLOCK_SHIFT_Y));
const uint REGION_SCALE_Z = (1u << (POSITION_BITS - REGION_BLOCK_SHIFT_Z));

#define REGION_SCALE vec3(REGION_SCALE_X, REGION_SCALE_Y, REGION_SCALE_Z)
#define UV_SCALE vec2(1.0 / 65536)

vec3 extractBlockPos(uvec2 atPosition) {
    uint blockX = atPosition.x & 0x1FFFFFu; // bits [0–20]

    // Y uses 11 bits from atPosition.x, 10 bits from atPosition.y
    uint blockY_low  = (atPosition.x >> 21);          // bits [21–31] → 11 bits
    uint blockY_high = (atPosition.y & 0x3FFu);       // bits [32–41] → 10 bits
    uint blockY = (blockY_high << 11) | blockY_low;   // combine → 21 bits

    uint blockZ = (atPosition.y >> 10) & 0x1FFFFFu;   // bits [42–62]

    return uvec3(blockX, blockY, blockZ) / REGION_SCALE;
}

void main() {
    vec3 blockPosition = extractBlockPos(a_Position) + u_RegionPos;
    vec4 position = u_ModelViewMat * vec4(blockPosition, 1.0);

    gl_Position = u_ProjMat * position;

    v_Color = a_Color;
    v_TextureUv = a_Uv;
    v_Distance = length(position);
}
