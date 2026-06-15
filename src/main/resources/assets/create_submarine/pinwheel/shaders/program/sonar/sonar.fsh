uniform sampler2D DiffuseSampler0;
uniform sampler2D DiffuseDepthSampler;
uniform vec2 InSize;
uniform float Time;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

void main() {
    vec3 color = texture(DiffuseSampler0, texCoord).rgb;
    float depth = texture(DiffuseDepthSampler, texCoord).r;

    float luminance = dot(color, vec3(0.299, 0.587, 0.114));

    float green = luminance * 0.8 + 0.2;
    float red = luminance * 0.15;
    float blue = luminance * 0.1;

    red *= 1.0 + 0.3 * sin(texCoord.y * InSize.y * 0.5);
    green *= 1.0 - 0.2 * sin(texCoord.y * InSize.y * 0.5);
    blue *= 1.0 + 0.1 * sin(texCoord.y * InSize.y * 0.5);

    float scanline = sin(texCoord.y * InSize.y * 3.14159 * 1.5);
    scanline = abs(scanline);
    scanline = 0.7 + 0.3 * (1.0 - scanline);

    float noise = hash(texCoord * InSize + floor(Time * 20.0));
    noise = noise * 0.05;

    float intensity = smoothstep(0.0, 0.5, luminance);

    vec3 sonarColor = vec3(red * scanline + noise, green * scanline + noise, blue * scanline + noise);

    float alpha = step(depth, 0.99);

    sonarColor = pow(sonarColor, vec3(1.0 / 2.2));

    fragColor = vec4(sonarColor, alpha);
}
