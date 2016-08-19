package com.serenegiant.xiaxl.gl_widget;

import android.opengl.GLES20;
import android.util.Log;

//加载顶点Shader与片元Shader的工具类
public class GLShaderUtil
{
   //加载制定shader的方法
   public static int loadShader
   (
           int shaderType, //shader的类型  GLES20.GL_VERTEX_SHADER   GLES20.GL_FRAGMENT_SHADER
           String source   //shader的脚本字符串
   ) 
   {
	    //创建一个新shader
        int shader = GLES20.glCreateShader(shaderType);
        //若创建成功则加载shader
        if (shader != 0) 
        {
        	//加载shader的源代码
            GLES20.glShaderSource(shader, source);
            //编译shader
            GLES20.glCompileShader(shader);
            //存放编译成功shader数量的数组
            int[] compiled = new int[1];
            //获取Shader的编译情况
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) 
            {//若编译失败则显示错误日志并删除此shader
                Log.e("ES20_ERROR", "Could not compile shader " + shaderType + ":");
                Log.e("ES20_ERROR", GLES20.glGetShaderInfoLog(shader));
                GLES20.glDeleteShader(shader);
                shader = 0;      
            }  
        }
        return shader;
    }


}
