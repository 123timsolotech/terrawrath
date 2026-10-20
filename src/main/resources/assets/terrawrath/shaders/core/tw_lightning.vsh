#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    // TerraWrath: sky-scale disaster geometry stays visible far beyond the terrain fog
#ifndef TW_FOG_SCALE
#define TW_FOG_SCALE 1.0
#endif
    sphericalVertexDistance = fog_spherical_distance(Position) * TW_FOG_SCALE;
    cylindricalVertexDistance = fog_cylindrical_distance(Position) * TW_FOG_SCALE;
    vertexColor = Color;
}
