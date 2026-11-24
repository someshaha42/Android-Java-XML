package com.aistudio.framecut.encoder;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.view.Surface;
import com.aistudio.framecut.model.NormalizedRegion;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Pure Java OpenGL ES 2.0 + EGL14 GPU cropper that renders a normalized region from an input
 * SurfaceTexture (MediaProjection VirtualDisplay or Video Decoder) onto a MediaCodec input Surface.
 */
public final class GlRegionCropper implements SurfaceTexture.OnFrameAvailableListener {
    private static final String VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;\n" +
            "uniform mat4 uSTMatrix;\n" +
            "attribute vec4 aPosition;\n" +
            "attribute vec4 aTextureCoord;\n" +
            "varying vec2 vTextureCoord;\n" +
            "void main() {\n" +
            "  gl_Position = uMVPMatrix * aPosition;\n" +
            "  vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" +
            "}\n";

    private static final String FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "varying vec2 vTextureCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "void main() {\n" +
            "  gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
            "}\n";

    private final Object frameSync = new Object();
    private boolean frameAvailable = false;
    private volatile boolean released = false;

    private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;

    private int programId;
    private int textureId;
    private int aPositionHandle;
    private int aTexCoordHandle;
    private int uMVPMatrixHandle;
    private int uSTMatrixHandle;

    private final float[] mvpMatrix = new float[16];
    private final float[] stMatrix = new float[16];

    private FloatBuffer vertexBuffer;
    private FloatBuffer texCoordBuffer;

    private SurfaceTexture inputSurfaceTexture;
    private Surface inputSurface;

    private final int outputWidth;
    private final int outputHeight;

    public GlRegionCropper(
            Surface encoderInputSurface,
            int sourceWidth,
            int sourceHeight,
            int outputWidth,
            int outputHeight,
            NormalizedRegion region) {
        this.outputWidth = outputWidth;
        this.outputHeight = outputHeight;

        initEgl(encoderInputSurface);
        initGl(region);

        inputSurfaceTexture = new SurfaceTexture(textureId);
        inputSurfaceTexture.setDefaultBufferSize(Math.max(2, sourceWidth), Math.max(2, sourceHeight));
        inputSurfaceTexture.setOnFrameAvailableListener(this);
        inputSurface = new Surface(inputSurfaceTexture);
    }

    public Surface getInputSurface() {
        return inputSurface;
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        synchronized (frameSync) {
            frameAvailable = true;
            frameSync.notifyAll();
        }
    }

    public boolean awaitAndRenderNextFrame(long timeoutMs, long overrideTimestampNs) {
        synchronized (frameSync) {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (!frameAvailable && !released) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) return false;
                try {
                    frameSync.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            if (released) return false;
            frameAvailable = false;
        }

        inputSurfaceTexture.updateTexImage();
        inputSurfaceTexture.getTransformMatrix(stMatrix);

        GLES20.glViewport(0, 0, outputWidth, outputHeight);
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(programId);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);

        vertexBuffer.position(0);
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        GLES20.glEnableVertexAttribArray(aPositionHandle);

        texCoordBuffer.position(0);
        GLES20.glVertexAttribPointer(aTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer);
        GLES20.glEnableVertexAttribArray(aTexCoordHandle);

        Matrix.setIdentityM(mvpMatrix, 0);
        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0);
        GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, stMatrix, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        long ptsNs = overrideTimestampNs >= 0 ? overrideTimestampNs : inputSurfaceTexture.getTimestamp();
        if (ptsNs <= 0) ptsNs = System.nanoTime();
        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsNs);
        EGL14.eglSwapBuffers(eglDisplay, eglSurface);
        return true;
    }

    public void release() {
        released = true;
        synchronized (frameSync) {
            frameSync.notifyAll();
        }
        if (inputSurface != null) {
            inputSurface.release();
            inputSurface = null;
        }
        if (inputSurfaceTexture != null) {
            inputSurfaceTexture.release();
            inputSurfaceTexture = null;
        }
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface);
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext);
            EGL14.eglReleaseThread();
            EGL14.eglTerminate(eglDisplay);
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY;
        eglContext = EGL14.EGL_NO_CONTEXT;
        eglSurface = EGL14.EGL_NO_SURFACE;
    }

    private void initEgl(Surface surface) {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] version = new int[2];
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1);

        int[] attribList = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                0x3142, 1, // EGL_RECORDABLE_ANDROID
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0);

        int[] ctxAttribs = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0);
        int[] surfAttribs = {EGL14.EGL_NONE};
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], surface, surfAttribs, 0);
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);
    }

    private void initGl(NormalizedRegion region) {
        float[] quadVerts = {
                -1f, -1f, 0f,
                 1f, -1f, 0f,
                -1f,  1f, 0f,
                 1f,  1f, 0f
        };
        // SurfaceTexture coordinates have v=0 at bottom, v=1 at top before stMatrix flip.
        float u0 = region.left;
        float u1 = region.right;
        float vTop = 1f - region.top;
        float vBottom = 1f - region.bottom;
        float[] texCoords = {
                u0, vBottom,
                u1, vBottom,
                u0, vTop,
                u1, vTop
        };

        vertexBuffer = ByteBuffer.allocateDirect(quadVerts.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertexBuffer.put(quadVerts).position(0);

        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        texCoordBuffer.put(texCoords).position(0);

        int vs = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fs = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        programId = GLES20.glCreateProgram();
        GLES20.glAttachShader(programId, vs);
        GLES20.glAttachShader(programId, fs);
        GLES20.glLinkProgram(programId);

        aPositionHandle = GLES20.glGetAttribLocation(programId, "aPosition");
        aTexCoordHandle = GLES20.glGetAttribLocation(programId, "aTextureCoord");
        uMVPMatrixHandle = GLES20.glGetUniformLocation(programId, "uMVPMatrix");
        uSTMatrixHandle = GLES20.glGetUniformLocation(programId, "uSTMatrix");

        int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        textureId = tex[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    }

    private static int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        return shader;
    }
}
