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

import java.nio.ByteBuffer;
import android.opengl.GLES30;

public class CamRenderTexture
{
    int[] mTextureHandle;
    int[] mFBO;
    int[] mRBO;
    public enum BlurType
    {
        BlurTypeGaussianDilated,
        BlurTypeGaussianPyramid,
        BlurTypeGaussianKernelSize,
    }

    public int getInYTex()  { return mTextureHandle[0]; }
    public int getInCTex()  { return mTextureHandle[1]; }
    public int getMaskTex() { return mTextureHandle[2]; }
    public int getInRGBTex( int level ) { return mTextureHandle[3 + level]; }
    public int getInRGBFBO( int level ) { return mFBO[level]; }
    public int getInRGBRBO( int level ) { return mRBO[level]; }

    public void createTextures( int width, int height, int maskW, int maskH,
                                int levels, BlurType blurType )
    {
        mTextureHandle = new int[3 + levels];
        mFBO = new int[levels];
        mRBO = new int[levels];
        GLES30.glGenTextures( mTextureHandle.length, mTextureHandle, 0 );

        // Input Luma
        GLES30.glBindTexture( GLES30.GL_TEXTURE_2D, mTextureHandle[0] );
        GLES30.glTexParameteri( GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR );
        GLES30.glTexParameteri( GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR );
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexImage2D( GLES30.GL_TEXTURE_2D, 0, GLES30.GL_LUMINANCE, width, height, 0,
                             GLES30.GL_LUMINANCE, GLES30.GL_UNSIGNED_BYTE, null );

        // Input chroma
        GLES30.glBindTexture( GLES30.GL_TEXTURE_2D, mTextureHandle[1] );
        GLES30.glTexParameteri( GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR );
        GLES30.glTexParameteri( GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR );
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexImage2D( GLES30.GL_TEXTURE_2D, 0, GLES30.GL_LUMINANCE_ALPHA, width/2, height/2, 0,
                             GLES30.GL_LUMINANCE_ALPHA, GLES30.GL_UNSIGNED_BYTE, null );

        // mask
        GLES30.glBindTexture( GLES30.GL_TEXTURE_2D, mTextureHandle[2] );
        GLES30.glTexParameteri( GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST );
        GLES30.glTexParameteri( GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST );
        GLES30.glTexImage2D( GLES30.GL_TEXTURE_2D, 0, GLES30.GL_LUMINANCE, maskW, maskH, 0,
                             GLES30.GL_LUMINANCE , GLES30.GL_UNSIGNED_BYTE, null );

        // Input RGB
        GLES30.glGenFramebuffers( levels, mFBO, 0 );

        for ( int i = 0; i < levels; ++i )
        {
            int scaleFactor = ( blurType == BlurType.BlurTypeGaussianPyramid ) ? i + 1 : 1;
            GLES30.glBindTexture( GLES30.GL_TEXTURE_2D, mTextureHandle[3 + i] );
            GLES30.glTexParameteri( GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR );
            GLES30.glTexParameteri( GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR );
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glTexImage2D( GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGB,
                                 width/scaleFactor, height/scaleFactor, 0,
                                 GLES30.GL_RGB , GLES30.GL_UNSIGNED_BYTE, null );
        }
        //ToDo: move to render buffers
        //  GLES30.glGenRenderbuffers( 1, mRBO, 0 );
        //  GLES30.glBindRenderbuffer( GLES30.GL_RENDERBUFFER, mRBO[0]);
        //  GLES30.glRenderbufferStorage( GLES30.GL_RENDERBUFFER, GLES30.GL_RGB, width, height );
    }

    public void deleteTextures()
    {
        GLES30.glDeleteTextures( mTextureHandle.length, mTextureHandle, 0 );
        GLES30.glDeleteFramebuffers ( mFBO.length, mFBO, 0 );
     //   GLES30.glDeleteRenderbuffers( mRBO.length, mRBO, 0 );
    }
}
