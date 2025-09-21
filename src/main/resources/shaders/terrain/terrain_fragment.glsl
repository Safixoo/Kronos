#version 110

varying vec3 v_Color;
varying vec2 v_TextureUv;
varying float v_Distance;

uniform sampler2D u_TexId;

uniform float u_FogEnd;
uniform float u_FogStart;
uniform vec3 u_FogColor;

void main() {
    vec4 blockTexture = texture2D(u_TexId, v_TextureUv);
    vec3 blockColor = v_Color * blockTexture.rgb;
    float factor = smoothstep(u_FogStart, u_FogEnd, v_Distance);

    gl_FragColor = vec4(mix(blockColor, u_FogColor, factor), blockTexture.a);
}
