package com.serenegiant.xiaxl.gl_widget;


import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import com.serenegiant.xiaxl.gl_util.GLShaderUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;


public class GLTextureRect {

    private static final String TAG = GLTextureRect.class.getSimpleName();

    // 顶点着色器
    private static final String vertexSource
            = "uniform mat4 uMVPMatrix;\n"
            + "attribute highp vec3 aPosition;\n"
            + "attribute highp vec2 aTextureCoord;\n"
            + "varying highp vec2 vTextureCoord;\n"
            + "\n"
            + "void main() {\n"
            + "	gl_Position = uMVPMatrix * vec4(aPosition,1);\n"
            + "	vTextureCoord = aTextureCoord;\n"
            + "}\n";
    // 片元着色器
    private static final String fragmentSource
            = "#extension GL_OES_EGL_image_external : require\n"
            + "precision mediump float;\n"
            + "uniform samplerExternalOES sTexture;\n"
            + "varying highp vec2 vTextureCoord;\n"
            + "void main() {\n"
            + "  gl_FragColor = texture2D(sTexture, vTextureCoord);\n"
            + "}";

    // 顶点的数量
    private static final int VERTEX_NUM = 6;


    //顶点坐标数据缓冲
    private FloatBuffer mVertexBuffer;
    //顶点纹理坐标数据缓冲
    private FloatBuffer mTexCoorBuffer;

    //自定义渲染管线着色器程序id
    private int mProgram;

    //顶点位置属性引用
    int maPositionHandle;
    //顶点纹理坐标属性引用
    int maTexCoorHandle;
    //变换矩阵引用
    int muMVPMatrixHandle;


    /**
     * 构造方法
     */
    public GLTextureRect(float cameraPreviewWidth, float cameraPreviewHeight) {
        // 顶点坐标和纹理坐标
        initVertexData(cameraPreviewWidth, cameraPreviewHeight);
        // 初始化着色器
        initShader();
        //
    }


    /**
     * 初始化顶点坐标与着色数据的方法
     */
    public void initVertexData(float cameraPreviewWidth, float cameraPreviewHeight) {

        /**
         * 顶点坐标
         */
        float vertices[] =
                {
                        -2.0f, 2.0f, 0,
                        -2.0f, -2.0f, 0,
                        2.0f, 2.0f, 0,

                        -2.0f, -2.0f, 0,
                        2.0f, -2.0f, 0,
                        2.0f, 2.0f, 0
                };
        //创建顶点坐标数据缓冲
        ByteBuffer vbb = ByteBuffer.allocateDirect(vertices.length * 4);
        vbb.order(ByteOrder.nativeOrder());
        mVertexBuffer = vbb.asFloatBuffer();
        mVertexBuffer.put(vertices);
        mVertexBuffer.position(0);


        /**
         * 纹理坐标
         */

        // 1、纹理剪裁
        // 我们的需求是正方形图像，这里的纹理图像为1920*1080,所以，我们剪裁掉左右两个部分
        float tempX1 = 0;
        float tempX2 = 1;
        if (cameraPreviewWidth != 0 && cameraPreviewHeight != 0) {
            tempX1 = (cameraPreviewWidth - cameraPreviewHeight) / (cameraPreviewWidth * 2f);
            tempX2 = 1 - tempX1;
        }
        float texCoor[] = new float[]//纹理坐标
                {
                        tempX1, 0,
                        tempX1, 1,
                        tempX2, 0,
                        tempX1, 1,
                        tempX2, 1,
                        tempX2, 0
                };

        //创建顶点纹理坐标数据缓冲
        ByteBuffer cbb = ByteBuffer.allocateDirect(texCoor.length * 4);
        cbb.order(ByteOrder.nativeOrder());
        mTexCoorBuffer = cbb.asFloatBuffer();
        mTexCoorBuffer.put(texCoor);
        mTexCoorBuffer.position(0);


    }


    /**
     * 初始化着色器
     */
    private void initShader() {


        //基于顶点着色器与片元着色器创建程序
        mProgram = GLShaderUtil.createProgram(vertexSource, fragmentSource);
        //获取程序中顶点位置属性引用
        maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
        //获取程序中顶点纹理坐标属性引用
        maTexCoorHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");
        //获取程序中总变换矩阵id
        muMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");

    }


    /**
     * 绘制
     *
     * @param texId      纹理id
     * @param mMvpMatrix 最终变换矩阵
     */
    public void draw(final int texId, float[] mMvpMatrix) {

        // ---------注：这里直接用最终变换矩阵进行绘制，不要再进行其他的矩阵变换-----------

        //制定使用某套着色器程序
        GLES20.glUseProgram(mProgram);
        //矩阵传入着色器程序
        GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMvpMatrix, 0);

        // 将顶点坐标数据传入渲染管线
        GLES20.glVertexAttribPointer
                (
                        maPositionHandle,
                        3,
                        GLES20.GL_FLOAT,
                        false,
                        3 * 4,
                        mVertexBuffer
                );

        // 将顶点纹理坐标数据传入渲染管线
        GLES20.glVertexAttribPointer
                (
                        maTexCoorHandle,
                        2,
                        GLES20.GL_FLOAT,
                        false,
                        2 * 4,
                        mTexCoorBuffer
                );

        // 启用顶点位置数据
        GLES20.glEnableVertexAttribArray(maPositionHandle);
        GLES20.glEnableVertexAttribArray(maTexCoorHandle);

        //绑定纹理
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId);
        // 绘制三角形
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, VERTEX_NUM);
    }
}
