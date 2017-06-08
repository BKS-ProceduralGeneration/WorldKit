#version 330

uniform sampler2D regionBorderDistanceMask;
uniform sampler2D coastDistanceMask;
uniform sampler2D noiseMask1;

in VertexData {
    vec2 uv;
} VertexIn;

layout(location = 0) out vec4 colorOut;

void main() {
    float regionBorderDistance = texture(regionBorderDistanceMask, VertexIn.uv).r;
    if (regionBorderDistance > 0.996) {
        float height = ((regionBorderDistance - 0.996) * 5.6) + 0.00001;
        colorOut = vec4(height, height, height, 1.0);
    } else {
        float coastDistance = texture(coastDistanceMask, VertexIn.uv).r;
        if (coastDistance > 0.999) {
            colorOut = vec4(0.4, 0.4, 0.4, 1.0);
        } else {
            float height = texture(noiseMask1, VertexIn.uv).r;
            colorOut = vec4(height, height, height, 1.0);
        }
    }
}
