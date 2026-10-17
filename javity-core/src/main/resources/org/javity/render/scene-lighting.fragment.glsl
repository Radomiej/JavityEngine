/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

/* Retained-location recovery and light formulas are pinned to libGDX 1.14.2
 * BaseShader/DefaultShader. Bundled upstream LICENSE/AUTHORS and THIRD_PARTY.md apply. */
#ifdef GL_ES
#define LOWP lowp
#define MED mediump
#define HIGH highp
precision mediump float;
#else
#define MED
#define LOWP
#define HIGH
#endif

#if defined(specularTextureFlag) || defined(specularColorFlag)
#define specularFlag
#endif

#ifdef normalFlag
varying vec3 v_normal;
#endif //normalFlag

#if defined(colorFlag)
varying vec4 v_color;
#endif

#ifdef blendedFlag
varying float v_opacity;
#ifdef alphaTestFlag
varying float v_alphaTest;
#endif //alphaTestFlag
#endif //blendedFlag

#if defined(diffuseTextureFlag) || defined(specularTextureFlag) || defined(emissiveTextureFlag)
#define textureFlag
#endif

#ifdef diffuseTextureFlag
varying MED vec2 v_diffuseUV;
#endif

#ifdef specularTextureFlag
varying MED vec2 v_specularUV;
#endif

#ifdef emissiveTextureFlag
varying MED vec2 v_emissiveUV;
#endif

#ifdef diffuseColorFlag
uniform vec4 u_diffuseColor;
#endif

#ifdef diffuseTextureFlag
uniform sampler2D u_diffuseTexture;
#endif

#ifdef specularColorFlag
uniform vec4 u_specularColor;
#endif

#ifdef specularTextureFlag
uniform sampler2D u_specularTexture;
#endif

#ifdef normalTextureFlag
uniform sampler2D u_normalTexture;
#endif

#ifdef emissiveColorFlag
uniform vec4 u_emissiveColor;
#endif

#ifdef emissiveTextureFlag
uniform sampler2D u_emissiveTexture;
#endif

varying vec3 v_scenePosition;
varying float v_eyeDistance;
uniform vec4 u_cameraPosition;
#ifdef sceneFogFlag
uniform vec4 u_fogColor;
uniform vec2 u_sceneFogRange;
#endif
#ifdef shininessFlag
uniform float u_shininess;
#else
const float u_shininess = 20.0;
#endif
#ifdef lightingFlag
uniform vec3 u_sceneAmbient;
struct SceneDirectional { vec3 color; vec3 direction; };
struct ScenePoint { vec3 color; vec3 position; float intensity; float range; };
struct SceneSpot { vec3 color; vec3 position; vec3 direction; float intensity; float range; float cutoff; float exponent; };
#if numDirectionalLights > 0
uniform SceneDirectional u_sceneDirectional[numDirectionalLights];
#endif
#if numPointLights > 0
uniform ScenePoint u_scenePoint[numPointLights];
#endif
#if numSpotLights > 0
uniform SceneSpot u_sceneSpot[numSpotLights];
#endif
// Zero distance and opposing half vectors must not introduce NaNs.
vec3 safeDirection(vec3 v) { return v * inversesqrt(max(dot(v,v), 0.00000001)); }
float attenuation(float distance, float range) {
    float falloff = max(0.0, 1.0 - distance / range);
    return falloff * falloff;
}
#endif

void main() {
	#if defined(normalFlag)
		vec3 normal = normalize(v_normal);
	#endif // normalFlag

    #ifdef lightingFlag
    vec3 v_lightDiffuse = u_sceneAmbient;
    vec3 v_lightSpecular = vec3(0.0);
    vec3 viewVec = safeDirection(u_cameraPosition.xyz - v_scenePosition);
    #if numDirectionalLights > 0
    for (int i=0; i<numDirectionalLights; i++) {
        vec3 lightDir = -u_sceneDirectional[i].direction;
        vec3 value = u_sceneDirectional[i].color * max(dot(normal,lightDir),0.0);
        v_lightDiffuse += value;
        #ifdef specularFlag
        v_lightSpecular += value * pow(max(dot(normal,safeDirection(lightDir+viewVec)),0.0),u_shininess);
        #endif
    }
    #endif
    #if numPointLights > 0
    for (int i=0; i<numPointLights; i++) {
        vec3 delta = u_scenePoint[i].position - v_scenePosition;
        vec3 lightDir = safeDirection(delta);
        float falloff = attenuation(length(delta),u_scenePoint[i].range);
        vec3 value = u_scenePoint[i].color * u_scenePoint[i].intensity * falloff * max(dot(normal,lightDir),0.0);
        v_lightDiffuse += value;
        #ifdef specularFlag
        v_lightSpecular += value * pow(max(dot(normal,safeDirection(lightDir+viewVec)),0.0),u_shininess);
        #endif
    }
    #endif
    #if numSpotLights > 0
    for (int i=0; i<numSpotLights; i++) {
        vec3 delta = u_sceneSpot[i].position - v_scenePosition;
        vec3 lightDir = safeDirection(delta);
        float cosine = dot(u_sceneSpot[i].direction,-lightDir);
        float cone = cosine >= u_sceneSpot[i].cutoff ? pow(max(cosine,0.0),u_sceneSpot[i].exponent) : 0.0;
        float falloff = attenuation(length(delta),u_sceneSpot[i].range) * cone;
        vec3 value = u_sceneSpot[i].color * u_sceneSpot[i].intensity * falloff * max(dot(normal,lightDir),0.0);
        v_lightDiffuse += value;
        #ifdef specularFlag
        v_lightSpecular += value * pow(max(dot(normal,safeDirection(lightDir+viewVec)),0.0),u_shininess);
        #endif
    }
    #endif
    #endif

	#if defined(diffuseTextureFlag) && defined(diffuseColorFlag) && defined(colorFlag)
		vec4 diffuse = texture2D(u_diffuseTexture, v_diffuseUV) * u_diffuseColor * v_color;
	#elif defined(diffuseTextureFlag) && defined(diffuseColorFlag)
		vec4 diffuse = texture2D(u_diffuseTexture, v_diffuseUV) * u_diffuseColor;
	#elif defined(diffuseTextureFlag) && defined(colorFlag)
		vec4 diffuse = texture2D(u_diffuseTexture, v_diffuseUV) * v_color;
	#elif defined(diffuseTextureFlag)
		vec4 diffuse = texture2D(u_diffuseTexture, v_diffuseUV);
	#elif defined(diffuseColorFlag) && defined(colorFlag)
		vec4 diffuse = u_diffuseColor * v_color;
	#elif defined(diffuseColorFlag)
		vec4 diffuse = u_diffuseColor;
	#elif defined(colorFlag)
		vec4 diffuse = v_color;
	#else
		vec4 diffuse = vec4(1.0);
	#endif

	#if defined(emissiveTextureFlag) && defined(emissiveColorFlag)
		vec4 emissive = texture2D(u_emissiveTexture, v_emissiveUV) * u_emissiveColor;
	#elif defined(emissiveTextureFlag)
		vec4 emissive = texture2D(u_emissiveTexture, v_emissiveUV);
	#elif defined(emissiveColorFlag)
		vec4 emissive = u_emissiveColor;
	#else
		vec4 emissive = vec4(0.0);
	#endif

	#if (!defined(lightingFlag))
		gl_FragColor.rgb = diffuse.rgb + emissive.rgb;
	#elif (!defined(specularFlag))
		#if defined(ambientFlag) && defined(separateAmbientFlag)
			#ifdef shadowMapFlag
				gl_FragColor.rgb = (diffuse.rgb * (v_ambientLight + getShadow() * v_lightDiffuse)) + emissive.rgb;
				//gl_FragColor.rgb = texture2D(u_shadowTexture, v_shadowMapUv.xy);
			#else
				gl_FragColor.rgb = (diffuse.rgb * (v_ambientLight + v_lightDiffuse)) + emissive.rgb;
			#endif //shadowMapFlag
		#else
			#ifdef shadowMapFlag
				gl_FragColor.rgb = getShadow() * (diffuse.rgb * v_lightDiffuse) + emissive.rgb;
			#else
				gl_FragColor.rgb = (diffuse.rgb * v_lightDiffuse) + emissive.rgb;
			#endif //shadowMapFlag
		#endif
	#else
		#if defined(specularTextureFlag) && defined(specularColorFlag)
			vec3 specular = texture2D(u_specularTexture, v_specularUV).rgb * u_specularColor.rgb * v_lightSpecular;
		#elif defined(specularTextureFlag)
			vec3 specular = texture2D(u_specularTexture, v_specularUV).rgb * v_lightSpecular;
		#elif defined(specularColorFlag)
			vec3 specular = u_specularColor.rgb * v_lightSpecular;
		#else
			vec3 specular = v_lightSpecular;
		#endif

		#if defined(ambientFlag) && defined(separateAmbientFlag)
			#ifdef shadowMapFlag
			gl_FragColor.rgb = (diffuse.rgb * (getShadow() * v_lightDiffuse + v_ambientLight)) + specular + emissive.rgb;
				//gl_FragColor.rgb = texture2D(u_shadowTexture, v_shadowMapUv.xy);
			#else
				gl_FragColor.rgb = (diffuse.rgb * (v_lightDiffuse + v_ambientLight)) + specular + emissive.rgb;
			#endif //shadowMapFlag
		#else
			#ifdef shadowMapFlag
				gl_FragColor.rgb = getShadow() * ((diffuse.rgb * v_lightDiffuse) + specular) + emissive.rgb;
			#else
				gl_FragColor.rgb = (diffuse.rgb * v_lightDiffuse) + specular + emissive.rgb;
			#endif //shadowMapFlag
		#endif
	#endif //lightingFlag

	#ifdef sceneFogFlag
		gl_FragColor.rgb = mix(gl_FragColor.rgb, u_fogColor.rgb, clamp((v_eyeDistance - u_sceneFogRange.x) / (u_sceneFogRange.y - u_sceneFogRange.x), 0.0, 1.0));
	#endif // end sceneFogFlag

	#ifdef blendedFlag
		gl_FragColor.a = diffuse.a * v_opacity;
		#ifdef alphaTestFlag
			if (gl_FragColor.a <= v_alphaTest)
				discard;
		#endif
	#else
		gl_FragColor.a = 1.0;
	#endif

}
