package com.serenegiant.xiaxl.gl_util;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;

/**
 * 纹理工具类
 *
 * @author xiaxl1
 */
public class GLTextureUtil {

    private static final String TAG = GLTextureUtil.class.getSimpleName();


    /**
     * 创建OES纹理id
     *
     * @return
     */
    public static int createOESTextureID() {
        final int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex[0]);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);

        return tex[0];
    }


    /**
     * delete specific texture
     */
    public static void deleteTex(final int tex) {
        final int[] texArray = new int[]{tex};
        GLES20.glDeleteTextures(1, texArray, 0);
    }

}
