#version 330
#extension GL_ARB_gpu_shader5 : enable

in uvec2 a_Position;
in vec2 a_Uv;
in vec3 a_Color;
in uint a_Lightmap;

out vec3 v_Color;
out vec2 v_TextureUv;
out vec3 v_VertPos;

uniform float u_Time;
uniform int u_Pass;

uniform vec3 u_RegionPos;
uniform mat4 u_ProjMat;
uniform mat4 u_ModelViewMat;

uniform sampler2D u_LightTex;

const float POSITION_SCALE = 1u << 20u;
const float RADIUS = 0.1;

const float REGION_SIZE_X = 128u + RADIUS * 2;
const float REGION_SIZE_Y = 64u  + RADIUS * 2;
const float REGION_SIZE_Z = 128u + RADIUS * 2;

const float REGION_SCALE_X = REGION_SIZE_X / POSITION_SCALE;
const float REGION_SCALE_Y = REGION_SIZE_Y / POSITION_SCALE;
const float REGION_SCALE_Z = REGION_SIZE_Z / POSITION_SCALE;

#define REGION_SCALE vec3(REGION_SCALE_X, REGION_SCALE_Y, REGION_SCALE_Z)

vec3 extractBlockPos(uvec2 atPosition) {
    uvec3 lowHalf = (uvec3(atPosition.x) >> uvec3(0u, 10u, 20u)) & 0x3FFu;
    uvec3 topHalf = (uvec3(atPosition.y) >> uvec3(0u, 10u, 20u)) & 0x3FFu;

    #if GL_ARB_gpu_shader5
        return fma(vec3(fma(topHalf, uvec3(1u << 10u), lowHalf)), REGION_SCALE, u_RegionPos);
    #else
        return (lowHalf | topHalf << 10u) * REGION_SCALE + u_RegionPos;
    #endif
}

vec2 lightmapUv(uint lightmap) {
    uvec2 uv = (uvec2(a_Lightmap) >> uvec2(4, 0)) & 0xF;
    return max(vec2(1.0), uv - 0.5) * (1.0 / 15.0);
}

#define u_wave_speed 0.000003
#define u_wave_amplitude 0.11
#define u_wave_frequency 3.5

void main() {
    vec3 blockPosition = extractBlockPos(a_Position);

    vec4 modeLViewPosition = u_ModelViewMat * vec4(blockPosition, 1.0);

    vec2 sineWave = sin(blockPosition.xz * u_wave_frequency + (vec2(u_Time) * u_wave_speed));
    float extraY = u_Pass * sineWave.x * sineWave.y * u_wave_amplitude;

    gl_Position = u_ProjMat * (modeLViewPosition + vec4(0, extraY, 0, 0));

    v_VertPos = modeLViewPosition.xyz;
    v_Color = a_Color * texture(u_LightTex, lightmapUv(a_Lightmap)).rgb;
    v_TextureUv = a_Uv * (1.0 / 65536.0);
}
