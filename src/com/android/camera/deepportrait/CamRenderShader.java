/*
 * Copyright (c) 2017, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above
 *    copyright notice, this list of conditions and the following
 *    disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *  * Neither the name of The Linux Foundation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.camera.deepportrait;
import android.opengl.GLES30;
import android.util.Log;

public class CamRenderShader
{
    public static final String TAG = "<dp><app><CamRenderShader>";

    public static int compileShader( final int shaderType, final String shaderSource )
    {
        int shaderHandle = GLES30.glCreateShader( shaderType );

        if ( shaderHandle != 0 )
        {
            // Pass in the shader source.
            GLES30.glShaderSource( shaderHandle, shaderSource );

            // Compile the shader.
            GLES30.glCompileShader( shaderHandle );

            // Get the compilation status.
            final int[] compileStatus = new int[1];
            GLES30.glGetShaderiv( shaderHandle, GLES30.GL_COMPILE_STATUS, compileStatus, 0 );

            // If the compilation failed, delete the shader.
            if ( compileStatus[0] == 0 ) 
            {
                Log.e( TAG, "Error compiling shader: " + GLES30.glGetShaderInfoLog( shaderHandle ) );
                GLES30.glDeleteShader( shaderHandle );
                shaderHandle = 0;
            }
        }

        if ( shaderHandle == 0 )
        {
            throw new RuntimeException( "Error creating shader." );
        }

        return shaderHandle;
    }

    public static int createAndLinkProgram( final int vertexShaderHandle,
                                            final int fragmentShaderHandle )
    {
        int programHandle = GLES30.glCreateProgram();

        if ( programHandle != 0 ) {
            // Bind the vertex shader to the program.
            GLES30.glAttachShader( programHandle, vertexShaderHandle );
            
            // Bind the fragment shaders to the program
            GLES30.glAttachShader( programHandle, fragmentShaderHandle );

            // Link the two shaders together into a program.
            GLES30.glLinkProgram( programHandle );

            // Get the link status.
            final int[] linkStatus = new int[1];
            GLES30.glGetProgramiv( programHandle, GLES30.GL_LINK_STATUS, linkStatus, 0 );

            // If the link failed, delete the program.
            if ( linkStatus[0] == 0 ) 
            {
                Log.e(TAG, "Error compiling program: " + GLES30.glGetProgramInfoLog(programHandle));
                GLES30.glDeleteProgram(programHandle);
                programHandle = 0;
            }
        }

        if ( programHandle == 0 ) {
            throw new RuntimeException("Error creating program.");
        }
        
        return programHandle;
    }
}
