#version 330

uniform float textureScale;
uniform float borderDistanceScale;
uniform sampler2D coastDistanceMask;
uniform sampler2D landMask;
uniform sampler2D noiseMask1;

in VertexData {
    vec2 uv;
} VertexIn;

layout(location = 0) out vec4 colorOut;

void main() {
    bool isWater = texture(landMask, VertexIn.uv).r < 0.5;
    if (isWater) {
        float coastDistance = 1 - texture(coastDistanceMask, VertexIn.uv).r;
//        float height = 1.0 - min(1.0, (1.0 - coastDistance * coastDistance) * 7 * borderDistanceScale);
        float height = clamp((1.0 / pow(1.0 + pow(2.7182818284590452353602875, ((120 * coastDistance) - 1)), 0.1)) + 0.0308, 0.0, 1.0);
//        float height = coastDistance < 0.0 ? 1.0 : 0.0;
        float noiseHeight = texture(noiseMask1, VertexIn.uv * textureScale * 0.6).r * 0.5;
        height = max(height, noiseHeight);
        colorOut = vec4(height, height, height, 1.0);
    } else {
        colorOut = vec4(1.0, 1.0, 1.0, 1.0);
    }
}
