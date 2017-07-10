#version 330

uniform float textureScale;
uniform float borderDistanceScale;
uniform sampler2D riverBorderDistanceMask;
uniform sampler2D mountainBorderDistanceMask;
uniform sampler2D coastDistanceMask;
uniform sampler2D noiseMask1;

in VertexData {
    vec2 uv;
} VertexIn;

layout(location = 0) out vec4 colorOut;

void main() {
    float mountainBorderDistance = texture(mountainBorderDistanceMask, VertexIn.uv).r;
    float minBorderDist = borderDistanceScale * 0.017;
    float minBorderDistInverse = 1.0 - minBorderDist;
    if (mountainBorderDistance > minBorderDistInverse) {
        float multiplier = (mountainBorderDistance - minBorderDistInverse) * 25 + 0.001;
        float height = texture(noiseMask1, VertexIn.uv * textureScale).r * multiplier;
        colorOut = vec4(height, height, height, 1.0);
    } else {
        minBorderDist = borderDistanceScale * 0.007;
        minBorderDistInverse = 1.0 - minBorderDist;
        float riverBorderDistance = texture(riverBorderDistanceMask, VertexIn.uv).r;
        if (riverBorderDistance > minBorderDistInverse) {
            float height = (minBorderDist - (riverBorderDistance - minBorderDistInverse)) * 0.001 + 0.000000001;
            colorOut = vec4(height, height, height, 1.0);
        } else {
            float coastDistance = texture(coastDistanceMask, VertexIn.uv).r;
            if (coastDistance > 1.0 - (0.001 * borderDistanceScale)) {
                colorOut = vec4(0.00001, 0.00001, 0.00001, 1.0);
            } else {
                float height = texture(noiseMask1, VertexIn.uv * textureScale).r * 0.001;
                colorOut = vec4(height, height, height, 1.0);
            }
        }
    }
}