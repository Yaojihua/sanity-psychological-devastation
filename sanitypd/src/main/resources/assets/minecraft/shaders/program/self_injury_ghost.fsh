#version 150

// The double image the self-injury act leaves behind for five seconds.
//
// Modelled on vanilla's own `blur` program: same vertex shader (`sobel`, which is where oneTexel comes from),
// same single-pass shape. It draws the sharp view with one faded copy displaced up and to the right, and the
// displacement is a uniform rather than a constant here, so the Java side owns the number (and the probe can
// read it) instead of a designer value hiding in the shader. At zero offset the two samples coincide and the
// pass is the identity, which is what makes the five-second recovery smooth rather than a switch.

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform vec2 InSize;
uniform float GhostOffset;

out vec4 fragColor;

void main()
{
    vec4 base = texture(DiffuseSampler, texCoord);
    vec4 ghost = texture(DiffuseSampler, texCoord + oneTexel * vec2(GhostOffset, GhostOffset * 0.5));

    fragColor = vec4(mix(base.rgb, ghost.rgb, 0.42), base.a);
}
