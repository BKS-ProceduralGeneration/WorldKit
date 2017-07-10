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
    float minBorderDist = borderDistanceScale * 0.0065;
    float minBorderDistInverse = 1.0 - minBorderDist;
    if (mountainBorderDistance > minBorderDistInverse) {
        colorOut = vec4(1.0, 1.0, 1.0, 1.0);
    } else {
        float riverBorderDistance = texture(riverBorderDistanceMask, VertexIn.uv).r;
        if (riverBorderDistance > minBorderDistInverse) {
            float height = (minBorderDist - (riverBorderDistance - minBorderDistInverse)) * 35 + 0.00001;
            colorOut = vec4(height, height, height, 1.0);
        } else {
            float coastDistance = texture(coastDistanceMask, VertexIn.uv).r;
            if (coastDistance > 1.0 - (0.001 * borderDistanceScale)) {
                colorOut = vec4(0.4, 0.4, 0.4, 1.0);
            } else {
                float height = texture(noiseMask1, VertexIn.uv * textureScale).r;
                colorOut = vec4(height, height, height, 1.0);
            }
        }
    }
}