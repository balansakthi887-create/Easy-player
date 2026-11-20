package com.maxter.player.player

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.maxter.player.data.model.SmartEnhanceConfig
import java.util.concurrent.atomic.AtomicReference

data class SmartEnhanceRenderState(
    val config: SmartEnhanceConfig = SmartEnhanceConfig(),
    val mirrorHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val nightModeActive: Boolean = false,
    val dividerR: Float = 1.0f,
    val dividerG: Float = 0.404f,
    val dividerB: Float = 0.0f
)

/**
 * Real-time GPU GLSL Shader Effect for AndroidX Media3 ExoPlayer.
 *
 * Uses an [AtomicReference] for lock-free live parameter updates on every frame,
 * eliminating MediaCodec surface teardown, black screens, and frame drops when
 * toggling presets or dragging intensity/split sliders.
 */
@UnstableApi
class SmartEnhanceGlEffect(
    private val liveState: AtomicReference<SmartEnhanceRenderState>
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return SmartEnhanceShaderProgram(liveState, useHdr)
    }
}

@UnstableApi
private class SmartEnhanceShaderProgram(
    private val liveState: AtomicReference<SmartEnhanceRenderState>,
    useHdr: Boolean
) : BaseGlShaderProgram(useHdr, 1) {

    private var glProgram: GlProgram? = null
    private var inputWidth = 1920
    private var inputHeight = 1080

    override fun configure(inputWidth: Int, inputHeight: Int): androidx.media3.common.util.Size {
        this.inputWidth = inputWidth.coerceAtLeast(1)
        this.inputHeight = inputHeight.coerceAtLeast(1)
        if (glProgram == null) {
            glProgram = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER).apply {
                setBufferAttribute(
                    "aFramePosition",
                    GlUtil.getNormalizedCoordinateBounds(),
                    GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
                )
            }
        }
        return androidx.media3.common.util.Size(this.inputWidth, this.inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val program = glProgram ?: return
        val state = liveState.get()
        val cfg = state.config

        program.use()
        program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
        program.setFloatsUniform(
            "uTexelSize",
            floatArrayOf(1f / inputWidth.toFloat(), 1f / inputHeight.toFloat())
        )
        program.setIntUniform("uEnhanceEnabled", if (cfg.enabled) 1 else 0)
        program.setIntUniform("uSplitActive", if (cfg.splitComparisonActive) 1 else 0)
        program.setFloatUniform("uSplitPos", cfg.splitPosition.coerceIn(0.02f, 0.98f))
        program.setFloatUniform("uIntensity", cfg.intensity.coerceIn(0f, 1f))
        program.setFloatUniform("uBrightness", cfg.brightness.coerceIn(-0.4f, 0.4f))
        program.setFloatUniform("uContrast", cfg.contrast.coerceIn(0.6f, 1.8f))
        program.setFloatUniform("uSharpness", cfg.sharpness.coerceIn(0f, 1.5f))
        program.setFloatUniform("uSaturation", cfg.saturation.coerceIn(0.5f, 1.8f))
        program.setFloatUniform("uDenoise", cfg.denoise.coerceIn(0f, 1f))
        program.setIntUniform("uMirrorX", if (state.mirrorHorizontal) 1 else 0)
        program.setIntUniform("uFlipY", if (state.flipVertical) 1 else 0)
        program.setIntUniform("uNightMode", if (state.nightModeActive) 1 else 0)
        program.setFloatsUniform(
            "uDividerColor",
            floatArrayOf(state.dividerR, state.dividerG, state.dividerB)
        )
        program.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    override fun release() {
        super.release()
        runCatching { glProgram?.delete() }
        glProgram = null
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aFramePosition;
            varying vec2 vTexSamplingCoord;
            uniform int uMirrorX;
            uniform int uFlipY;
            void main() {
                gl_Position = aFramePosition;
                vec2 uv = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
                if (uMirrorX == 1) uv.x = 1.0 - uv.x;
                if (uFlipY == 1) uv.y = 1.0 - uv.y;
                vTexSamplingCoord = uv;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform vec2 uTexelSize;
            uniform int uEnhanceEnabled;
            uniform int uSplitActive;
            uniform float uSplitPos;
            uniform float uIntensity;
            uniform float uBrightness;
            uniform float uContrast;
            uniform float uSharpness;
            uniform float uSaturation;
            uniform float uDenoise;
            uniform int uNightMode;
            uniform vec3 uDividerColor;
            varying vec2 vTexSamplingCoord;

            void main() {
                vec4 baseColor = texture2D(uTexSampler, vTexSamplingCoord);
                vec3 origRgb = baseColor.rgb;
                vec3 rgb = origRgb;

                bool applyEnhance = (uEnhanceEnabled == 1);
                if (uSplitActive == 1 && vTexSamplingCoord.x < uSplitPos) {
                    applyEnhance = false;
                }

                if (applyEnhance && uIntensity > 0.005) {
                    // 1. Sample 4-connected neighbors once for both denoise & CAS sharpening
                    vec3 n = texture2D(uTexSampler, vTexSamplingCoord + vec2(0.0, -uTexelSize.y)).rgb;
                    vec3 s = texture2D(uTexSampler, vTexSamplingCoord + vec2(0.0,  uTexelSize.y)).rgb;
                    vec3 e = texture2D(uTexSampler, vTexSamplingCoord + vec2( uTexelSize.x, 0.0)).rgb;
                    vec3 w = texture2D(uTexSampler, vTexSamplingCoord + vec2(-uTexelSize.x, 0.0)).rgb;
                    vec3 avg = (n + s + e + w) * 0.25;

                    // 2. Edge-aware bilateral luma denoise (smooths flat noise, preserves edges)
                    float edgeDiff = length(origRgb - avg);
                    float denoiseWeight = clamp(1.0 - edgeDiff * 7.5, 0.0, 1.0) * uDenoise * uIntensity * 0.65;
                    rgb = mix(origRgb, avg, denoiseWeight);

                    // 3. Contrast-Adaptive Sharpening (CAS) computed from original unblurred detail
                    //    with halo clamping to keep edges crisp without ringing artifacts
                    vec3 detail = origRgb - avg;
                    vec3 clampedDetail = clamp(detail, vec3(-0.22), vec3(0.22));
                    float casGain = uSharpness * uIntensity * 1.45;
                    rgb = clamp(rgb + clampedDetail * casGain, 0.0, 1.0);

                    // 4. Adaptive shadow-recovery luma lift (reveals dark detail without blowing highlights)
                    float lumaBefore = dot(rgb, vec3(0.299, 0.587, 0.114));
                    if (lumaBefore > 0.001) {
                        float shadowLift = pow(lumaBefore, mix(1.0, 0.88, uIntensity));
                        float liftRatio = clamp(shadowLift / lumaBefore, 0.85, 1.32);
                        rgb = clamp(rgb * liftRatio, 0.0, 1.0);
                    }

                    // 5. Brightness & S-curve contrast grading
                    rgb = clamp(rgb + (uBrightness * uIntensity), 0.0, 1.0);
                    float effContrast = mix(1.0, uContrast, uIntensity);
                    rgb = clamp((rgb - 0.5) * effContrast + 0.5, 0.0, 1.0);

                    // 6. Vibrance-weighted colour enhancement (natural skin tones, rich scenery)
                    float luma = dot(rgb, vec3(0.299, 0.587, 0.114));
                    float maxC = max(rgb.r, max(rgb.g, rgb.b));
                    float minC = min(rgb.r, min(rgb.g, rgb.b));
                    float curSat = maxC - minC;
                    float vibranceBoost = (1.0 - curSat * 0.45);
                    float effSat = mix(1.0, uSaturation, uIntensity * vibranceBoost);
                    rgb = clamp(mix(vec3(luma), rgb, effSat), 0.0, 1.0);
                }

                if (uNightMode == 1) {
                    rgb.b *= 0.72;
                    rgb.g *= 0.90;
                    rgb *= 0.86;
                }

                if (uEnhanceEnabled == 1 && uSplitActive == 1 && abs(vTexSamplingCoord.x - uSplitPos) < 0.0028) {
                    gl_FragColor = vec4(uDividerColor, 1.0);
                } else {
                    gl_FragColor = vec4(rgb, baseColor.a);
                }
            }
        """
    }
}
