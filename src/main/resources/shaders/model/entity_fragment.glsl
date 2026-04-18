#version 330

in vec2 v_Uv;
in vec3 v_Pos;

uniform sampler2D u_ModelTex;

out vec4 fragColor;

#define VEC_LENGTH length(vec3(0.2, 1.0, 0.7))

#define LIGHT_0_POS (vec3(0.2, 1.0, -0.7) / VEC_LENGTH)
#define LIGHT_1_POS (vec3(-0.2, 1.0, 0.7) / VEC_LENGTH)

#define DIFFUSE_COLOR 0.6
#define MODEL_AMBIENT 0.4

void main() {
    vec4 textureColor = texture(u_ModelTex, v_Uv);
    vec3 normal = normalize(cross(dFdx(v_Pos), dFdy(v_Pos)));
    normal.xy = -normal.xy;

    float diffuse0 = max(dot(-LIGHT_0_POS, normal), 0.0);
    float diffuse1 = max(dot(-LIGHT_1_POS, normal), 0.0);

    float diffuse = min(MODEL_AMBIENT + (diffuse0 + diffuse1) * DIFFUSE_COLOR, 1.0);

    fragColor = vec4(vec3(diffuse) * textureColor.rgb, textureColor.a);
}
