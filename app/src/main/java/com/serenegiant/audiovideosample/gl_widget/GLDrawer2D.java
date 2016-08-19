package com.serenegiant.audiovideosample.gl_widget;


import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;


/**
 *
 */
public class GLDrawer2D {

    private static final String TAG = GLDrawer2D.class.getSimpleName();

    // 定点着色器
    private static final String vertexTex
            = "uniform mat4 uMVPMatrix;\n"
            + "uniform mat4 uTexMatrix;\n"
            + "attribute highp vec4 aPosition;\n"
            + "attribute highp vec4 aTextureCoord;\n"
            + "varying highp vec2 vTextureCoord;\n"
            + "\n"
            + "void main() {\n"
            + "	gl_Position = uMVPMatrix * aPosition;\n"
            + "	vTextureCoord = (uTexMatrix * aTextureCoord).xy;\n"
            + "}\n";
    // 片元着色器
    private static final String fragTex
            = "#extension GL_OES_EGL_image_external : require\n"
            + "precision mediump float;\n"
            + "uniform samplerExternalOES sTexture;\n"
            + "varying highp vec2 vTextureCoord;\n"
            + "void main() {\n"
            + "  gl_FragColor = texture2D(sTexture, vTextureCoord);\n"
            + "}";
    /**
     * 定点坐标
     */
    private static final float[] VERTICES = {
            //
            1.0f, 1.0f,
            //
            -1.0f, 1.0f,
            //
            1.0f, -1.0f,
            //
            -1.0f, -1.0f
    };

    /**
     * 纹理坐标
     */
    private static final float tempX1 = (1280 - 720) / (1280 * 2f);
    private static final float tempX2 = 1 - tempX1;
    private static final float[] TEXCOORD = {
            tempX1, 1.0f,     // 2 top left
            tempX1, 0.0f,     // 0 bottom left
            tempX2, 1.0f,     // 3 top right
            tempX2, 0.0f     // 1 bottom right
    };


    private final FloatBuffer pVertex;
    private final FloatBuffer pTexCoord;
    private int hProgram;
    int maPositionLoc;
    int maTextureCoordLoc;
    int muMVPMatrixLoc;
    int muTexMatrixLoc;
    private final float[] mMvpMatrix = new float[16];

    private static final int FLOAT_SZ = Float.SIZE / 8;
    private static final int VERTEX_NUM = 4;
    private static final int VERTEX_SZ = VERTEX_NUM * 2;

    /**
     * Constructor
     * this should be called in GL context
     */
    public GLDrawer2D() {
        pVertex = ByteBuffer.allocateDirect(VERTEX_SZ * FLOAT_SZ)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        pVertex.put(VERTICES);
        pVertex.flip();
        pTexCoord = ByteBuffer.allocateDirect(VERTEX_SZ * FLOAT_SZ)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        pTexCoord.put(TEXCOORD);
        pTexCoord.flip();

        hProgram = loadShader();
        GLES20.glUseProgram(hProgram);
        maPositionLoc = GLES20.glGetAttribLocation(hProgram, "aPosition");
        maTextureCoordLoc = GLES20.glGetAttribLocation(hProgram, "aTextureCoord");
        muMVPMatrixLoc = GLES20.glGetUniformLocation(hProgram, "uMVPMatrix");
        muTexMatrixLoc = GLES20.glGetUniformLocation(hProgram, "uTexMatrix");

        Matrix.setIdentityM(mMvpMatrix, 0);
        GLES20.glUniformMatrix4fv(muMVPMatrixLoc, 1, false, mMvpMatrix, 0);
        GLES20.glUniformMatrix4fv(muTexMatrixLoc, 1, false, mMvpMatrix, 0);
        GLES20.glVertexAttribPointer(maPositionLoc, 2, GLES20.GL_FLOAT, false, VERTEX_SZ, pVertex);
        GLES20.glVertexAttribPointer(maTextureCoordLoc, 2, GLES20.GL_FLOAT, false, VERTEX_SZ, pTexCoord);
        GLES20.glEnableVertexAttribArray(maPositionLoc);
        GLES20.glEnableVertexAttribArray(maTextureCoordLoc);
    }

    /**
     * draw specific texture with specific texture matrix
     *
     * @param tex_id     texture ID
     * @param tex_matrix texture matrix、if this is null, the last one use(we don't check size of this array and needs at least 16 of float)
     */
    public void draw(final int tex_id, final float[] tex_matrix) {
        GLES20.glUseProgram(hProgram);
        if (tex_matrix != null)
            GLES20.glUniformMatrix4fv(muTexMatrixLoc, 1, false, tex_matrix, 0);
        GLES20.glUniformMatrix4fv(muMVPMatrixLoc, 1, false, mMvpMatrix, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex_id);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_NUM);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        GLES20.glUseProgram(0);
    }

    /**
     * Set model/view/projection transform matrix
     *
     * @param matrix
     * @param offset
     */
    public void setMatrix(final float[] matrix, final int offset) {
        if ((matrix != null) && (matrix.length >= offset + 16)) {
            System.arraycopy(matrix, offset, mMvpMatrix, 0, 16);
        } else {
            Matrix.setIdentityM(mMvpMatrix, 0);
        }
    }


    /**
     * @return
     */
    public static int loadShader() {


        //加载顶点着色器
        int vertexShader = GLShaderUtil.loadShader(GLES20.GL_VERTEX_SHADER, vertexTex);
        if (vertexShader == 0) {
            return 0;
        }

        //加载片元着色器
        int pixelShader = GLShaderUtil.loadShader(GLES20.GL_FRAGMENT_SHADER, fragTex);
        if (pixelShader == 0) {
            return 0;
        }

        //创建程序
        int program = GLES20.glCreateProgram();
        //若程序创建成功则向程序中加入顶点着色器与片元着色器
        if (program != 0) {
            //向程序中加入顶点着色器
            GLES20.glAttachShader(program, vertexShader);
            //向程序中加入片元着色器
            GLES20.glAttachShader(program, pixelShader);
            //链接程序
            GLES20.glLinkProgram(program);
        }

        return program;
    }

}
