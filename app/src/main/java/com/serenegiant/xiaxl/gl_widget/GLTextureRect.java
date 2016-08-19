package com.serenegiant.xiaxl.gl_widget;


import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import com.serenegiant.xiaxl.gl_util.GLShaderUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;


public class GLTextureRect {

    private static final String TAG = GLTextureRect.class.getSimpleName();

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


    //顶点坐标数据缓冲
    private final FloatBuffer mVertexBuffer;
    //顶点纹理坐标数据缓冲
    private final FloatBuffer mTexCoorBuffer;

    //自定义渲染管线着色器程序id
    private int mProgram;

    //顶点位置属性引用
    int maPositionHandle;
    //顶点纹理坐标属性引用
    int maTexCoorHandle;
    //变换矩阵引用
    int muMVPMatrixLoc;
    //变换矩阵引用
    int muTexMatrixLoc;

    //
    private final float[] mMvpMatrix = new float[16];

    private static final int FLOAT_SZ = Float.SIZE / 8;
    private static final int VERTEX_NUM = 4;
    private static final int VERTEX_SZ = VERTEX_NUM * 2;


    //按钮右下角的s、t值
    private float mCameraPreviewWidth;
    private float mCameraPreviewHeight;

    /**
     *
     */
    public GLTextureRect(float cameraPreviewWidth, float cameraPreviewHeight) {

        this.mCameraPreviewWidth = cameraPreviewWidth;
        this.mCameraPreviewHeight = cameraPreviewHeight;

        /**
         * 定点坐标
         */
        float[] VERTICES = {
                //
                1.0f, 1.0f,
                //
                -1.0f, 1.0f,
                //
                1.0f, -1.0f,
                //
                -1.0f, -1.0f
        };

        //创建顶点坐标数据缓冲
        ByteBuffer vbb = ByteBuffer.allocateDirect(VERTEX_SZ * FLOAT_SZ);
        vbb.order(ByteOrder.nativeOrder());
        mVertexBuffer = vbb.asFloatBuffer();
        mVertexBuffer.put(VERTICES);
        mVertexBuffer.position(0);


        /**
         * 纹理坐标
         */

        // 1、纹理剪裁
        // 我们的需求是正方形图像，这里的纹理图像为1920*1080,所以，我们剪裁掉左右两个部分
        float tempX1 = 0;
        float tempX2 = 1;
        if (mCameraPreviewWidth != 0 && mCameraPreviewHeight != 0) {
            tempX1 = (mCameraPreviewWidth - mCameraPreviewHeight) / (mCameraPreviewWidth * 2f);
            tempX2 = 1 - tempX1;
        }
        //
        final float[] TEXCOORD = {
                tempX1, 1.0f,     // 2 top left
                tempX1, 0.0f,     // 0 bottom left
                tempX2, 1.0f,     // 3 top right
                tempX2, 0.0f     // 1 bottom right
        };

        //创建顶点纹理坐标数据缓冲
        ByteBuffer cbb = ByteBuffer.allocateDirect(VERTEX_SZ * FLOAT_SZ);
        cbb.order(ByteOrder.nativeOrder());
        mTexCoorBuffer = cbb.asFloatBuffer();
        mTexCoorBuffer.put(TEXCOORD);
        mTexCoorBuffer.position(0);


        //基于顶点着色器与片元着色器创建程序
        mProgram = createProgram();
        //
        GLES20.glUseProgram(mProgram);
        //获取程序中顶点位置属性引用
        maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
        //获取程序中顶点纹理坐标属性引用
        maTexCoorHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");
        //获取程序中总变换矩阵id
        muMVPMatrixLoc = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
        //
        muTexMatrixLoc = GLES20.glGetUniformLocation(mProgram, "uTexMatrix");

        // 重置矩阵
        Matrix.setIdentityM(mMvpMatrix, 0);
        //将矩阵传入着色器程序
        GLES20.glUniformMatrix4fv(muMVPMatrixLoc, 1, false, mMvpMatrix, 0);
        //将矩阵传入着色器程序
        GLES20.glUniformMatrix4fv(muTexMatrixLoc, 1, false, mMvpMatrix, 0);
        // 将顶点纹理坐标数据传入渲染管线
        GLES20.glVertexAttribPointer(maPositionHandle, 2, GLES20.GL_FLOAT, false, VERTEX_SZ, mVertexBuffer);
        // 将顶点纹理坐标数据传入渲染管线
        GLES20.glVertexAttribPointer(maTexCoorHandle, 2, GLES20.GL_FLOAT, false, VERTEX_SZ, mTexCoorBuffer);
        // 启用顶点位置数据
        GLES20.glEnableVertexAttribArray(maPositionHandle);
        GLES20.glEnableVertexAttribArray(maTexCoorHandle);
    }


    /**
     * @param tex_id
     * @param tex_matrix
     */
    public void draw(final int tex_id, final float[] tex_matrix) {

        //制定使用某套着色器程序
        GLES20.glUseProgram(mProgram);
        //矩阵传入着色器程序
        if (tex_matrix != null) {
            GLES20.glUniformMatrix4fv(muTexMatrixLoc, 1, false, tex_matrix, 0);
        }
        //矩阵传入着色器程序
        GLES20.glUniformMatrix4fv(muMVPMatrixLoc, 1, false, mMvpMatrix, 0);

        //绑定纹理
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex_id);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_NUM);
        //
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


    //创建shader程序的方法
    public static int createProgram() {
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
            //存放链接成功program数量的数组
            int[] linkStatus = new int[1];
            //获取program的链接情况
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
            //若链接失败则报错并删除程序
            if (linkStatus[0] != GLES20.GL_TRUE) {
                Log.e("ES20_ERROR", "Could not link program: ");
                Log.e("ES20_ERROR", GLES20.glGetProgramInfoLog(program));
                GLES20.glDeleteProgram(program);
                program = 0;
            }
        }
        return program;
    }


}
