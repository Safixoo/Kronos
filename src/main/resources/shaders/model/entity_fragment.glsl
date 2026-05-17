#version 330

in vec2 v_Uv;
in float v_Distance;
in vec3 v_Pos;
in vec3 v_Color;

uniform sampler2D u_ModelTex;

uniform float u_FogEndInvRad;
uniform float u_FogNegInvRadius;
uniform vec3 u_FogColor;

out vec4 fragColor;

#define VEC_LENGTH length(vec3(0.2, 1.0, 0.7))

#define LIGHT_0_POS (vec3(0.2, 1.0, -0.7) / VEC_LENGTH)
#define LIGHT_1_POS (vec3(-0.2, 1.0, 0.7) / VEC_LENGTH)

#define DIFFUSE_COLOR 0.6
#define MODEL_AMBIENT 0.4

void main() {
    vec4 textureColor = texture(u_ModelTex, v_Uv);

    if (textureColor.a < 0.1) {
        discard;
    }

    vec3 normal = normalize(cross(dFdx(v_Pos), dFdy(v_Pos)));
    normal.xy = -normal.xy;

    float diffuse0 = max(dot(-LIGHT_0_POS, normal), 0.0);
    float diffuse1 = max(dot(-LIGHT_1_POS, normal), 0.0);

    float diffuse = min((DIFFUSE_COLOR * (diffuse0 + diffuse1)) + MODEL_AMBIENT, 1.0);
    float fogFactor = clamp((u_FogNegInvRadius * v_Distance) + u_FogEndInvRad, 0.0, 1.0);
    vec3 color = textureColor.rgb * v_Color * diffuse;

    fragColor = vec4(mix(u_FogColor, color, fogFactor), textureColor.a);
}
