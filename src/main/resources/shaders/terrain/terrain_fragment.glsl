#version 330

in vec3 v_Color;
in vec2 v_TextureUv;

uniform sampler2D u_TexId;

uniform float u_FogEnd;
uniform float u_FogStart;
uniform vec3 u_FogColor;

uniform mat4 u_FragCoordToViewCoord;

out vec4 fragColor;

vec3 getViewCoords() {
    vec4 view = u_FragCoordToViewCoord * vec4(gl_FragCoord.xyz, 1.0);
    return view.xyz / view.w;
}

void main() {
    vec4 tex = texture(u_TexId, v_TextureUv);

    vec3 view = getViewCoords();
    float factor = smoothstep(u_FogStart, u_FogEnd, dot(view, view));

    fragColor = vec4(mix(v_Color * tex.rgb, u_FogColor, factor), tex.a);
}
