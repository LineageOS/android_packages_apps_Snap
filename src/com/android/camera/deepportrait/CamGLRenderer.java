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
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Vector;
import java.lang.Thread;
import android.media.Image;
import android.media.Image.Plane;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.GLSurfaceView.Renderer;
import android.opengl.Matrix;
import android.util.Log;


public class CamGLRenderer implements Renderer
{
    // MVP
    private final float[] mtrxProjection        = new float[16];
    private final float[] mtrxView              = new float[16];
    private final float[] mtrxProjectionAndView = new float[16];
    // Vertex shader points

    public FloatBuffer mSquareVertices;
    public FloatBuffer[] mSquareTextureCoordinates = new FloatBuffer[4]; // 4 positions
    // synchronized vector
    private Vector<DPImage> mFrameQueue;

    private final boolean SHOW_LOGS = false;

    /** Program handles */
    private int mConvertProgramHandle;
    private int mBlurProgramHandle;
    private int mProgramHandle;
    private Boolean mActive = false;

    // Effects
    Boolean mBokehEffect = true;

    // Our screenresolution
    float mScreenWidth = 0;
    float mScreenHeight = 0;

    // Our screenresolution
    int mScreenROIX      = 0;
    int mScreenROIY      = 0;
    int mScreenROIWidth  = 0;
    int mScreenROIHeight = 0;

    //Display image resolution
    int mFrameWidth = 0;
    int mFrameHeight = 0;

    // Misc
    Context mContext;
    long mLastTime;
    int mProgram;
    private CamRenderTexture mCamTexture;

    private ByteBuffer scratchRGB;
    private CamGLRenderObserver mObserver;

    private final int NUM_PROGRAMS = 3;
    private int[] mVerticesHandle    = new int[NUM_PROGRAMS];
    private int[] mTexCoordLocHandle = new int[NUM_PROGRAMS];
    private int[] mMVPMtrxhandle     = new int[NUM_PROGRAMS];
    private int mRotMtrxhandle;
    private int mSurfaceRotMtrxhandle;
    private int mFlipMtrxhandle;
    private int mInYHandle;
    private int mInCHandle;
    private int mPositionConv;
    private int[] mInRGBHandle = new int[8];
    private int mForegroundRGBHandle;
    private int mBackGroundRGBHandle;
    private int mMaskHandle;
    private int mXPixelOffsetUniform;
    private int mYPixelOffsetUniform;
    private int mMipLevelUniform;
    private int mBlurLevel = 50;
    private int mRotationDegree = 90;
    private int mMaskWidth = 0;
    private int mMaskHeight = 0;
    private boolean mTexturesCreated = false;
    private static final String TAG = "<dp><app><CamGLRenderer>";
    private long prevTime, currentTime;
    private long minFrameDelta = 33;
    private int mFrameRotation = 0;

    private final CamRenderTexture.BlurType blurType = CamRenderTexture.BlurType.BlurTypeGaussianDilated;

    private final boolean ROTATE_MASK = false;

    private final float[] flipNone = new float[] { 1.0f, 0.0f, 1.0f, 0.0f }; // x, 1-x, y, 1-y
    private final float[] flipX    = new float[] { 0.0f, 1.0f, 1.0f, 0.0f }; // x, 1-x, y, 1-y
    private final float[] flipY    = new float[] { 1.0f, 0.0f, 0.0f, 1.0f }; // x, 1-x, y, 1-y
    private final float[] flipXY   = new float[] { 0.0f, 1.0f, 0.0f, 1.0f }; // x, 1-x, y, 1-y

    // clockwise rotations. All in column major
    private final float[] rotNone  = new float[] {  1.0f,  0.0f, 0.0f,  0.0f,  1.0f, 0.0f, 0.0f, 0.0f, 1.0f };
    // rotmatrix of 90 + move to 1st quadrant
    private final float[] rot90    = new float[] {  0.0f, -1.0f, 1.0f,  1.0f,  0.0f, 0.0f, 0.0f, 0.0f, 1.0f }; // 1-y, x
    // rotmatrix of 180 + move to 1st quadrant
    private final float[] rot180   = new float[] { -1.0f,  0.0f, 1.0f,  0.0f, -1.0f, 1.0f, 0.0f, 0.0f, 1.0f };
    // rotmatrix of 270 + move to 1st quadrant
    private final float[] rot270   = new float[] {  0.0f,  1.0f, 0.0f, -1.0f,  0.0f, 1.0f, 0.0f, 0.0f, 1.0f }; // y, 1-x

    private float[] mRotationMatrix        = new float[9];
    private float[] mSurfaceRotationMatrix = new float[9];
    private GLSurfaceView mSurfaceView;

    public void sendFrame( DPImage dpimage )
    {
        if ( !mActive ) return;
        synchronized ( mFrameQueue ) {
            if ( mFrameQueue.size() > 3 ) {
                DPImage oldImage = mFrameQueue.get( 0 );
                mFrameQueue.removeElementAt( 0 );
                mObserver.onRenderComplete( oldImage, true );
            }
            mFrameQueue.add( dpimage );
        }
    }

    public void setBlurLevel( int level )
    {
        mBlurLevel = level;
        Log.e( TAG, "Blur Level " + mBlurLevel );
    }

    public void setRotationDegree( int camRotation, int frameRotation )
    {
        System.arraycopy( getRotationMatrix3x3( frameRotation ), 0, mSurfaceRotationMatrix, 0, 9 );

        mFrameRotation = frameRotation;
        mRotationDegree = ( camRotation + frameRotation ) % 360 ;
        System.arraycopy( getRotationMatrix3x3( mRotationDegree ), 0, mRotationMatrix, 0, 9 );
        switch ( camRotation ) {
        case  90:
            // transpose applied. apply H flip for 90 degree rotation - 1st column
            mRotationMatrix[0] *= -1;
            mRotationMatrix[1] *= -1;
            mRotationMatrix[2] = mRotationMatrix[2] > 0.0f ? 0.0f : 1.0f;
            break;
        case 180:
            // V flip applied. apply H flip for 180 degree rotation.
            mRotationMatrix[0] *= -1;
            mRotationMatrix[1] *= -1;
            mRotationMatrix[2] = mRotationMatrix[2] > 0.0f ? 0.0f : 1.0f;
            break;
        case 270:
            // transpose + H flip applied. correct rotation. No op
            break;
        }
        Log.e( TAG, "setRotationDegree cam " + camRotation + " adjusted " + mRotationDegree +
               " frame " + frameRotation );
    }

    public void prepareRotationMatrix( int camRotation )
    {
        mRotationDegree = mFrameRotation;
        System.arraycopy( getRotationMatrix3x3( mRotationDegree ), 0, mRotationMatrix, 0, 9 );
        if ( ROTATE_MASK ) {
            switch ( camRotation ) {
            case  90:
                // H flip applied. apply V flip for 90 degree rotation - 1st column
                mRotationMatrix[0] *= -1;
                mRotationMatrix[1] *= -1;
                mRotationMatrix[2] = mRotationMatrix[2] > 0.0f ? 0.0f : 1.0f;
                break;
            case 180:
                // V flip applied. apply V flip for 180 degree rotation.
                mRotationMatrix[3] *= -1;
                mRotationMatrix[4] *= -1;
                mRotationMatrix[5] = mRotationMatrix[5] > 0.0f ? 0.0f : 1.0f;
                break;
            }
        }
        Log.e( TAG, "setRotationDegree per frame single cam " + camRotation + " adjusted " + mRotationDegree +
               " frame " + mFrameRotation );
    }

    public void setMaskResolution( int width, int height )
    {
        mMaskWidth  = width;
        mMaskHeight = height;
        Log.e( TAG, "setMaskResolution width " + width + " height " + height );
    }

    public float[] getRotationMatrix( int rotationDegree )
    {
        float[] rotMat   = new float[4];
        float cosTheta = (float)Math.cos( Math.toRadians( rotationDegree ) );
        float sinTheta = (float)Math.sin( Math.toRadians( rotationDegree ) );
        rotMat[0] = cosTheta;
        rotMat[1] = -sinTheta;
        rotMat[2] = sinTheta;
        rotMat[3] = cosTheta;
        return rotMat;
    }

    public float[] getRotationMatrix3x3( int rotationDegree )
    {
        switch ( rotationDegree ) {
            case  90: return rot90;
            case 180: return rot180;
            case 270: return rot270;
        }
        return rotNone;
    }

    public float[] getFlipMatrix( int rotationDegree )
    {
        switch ( rotationDegree ) {
        case  90: return flipX;
        case 180: return flipY;
        case 270: return flipXY;
        }
        return flipNone;
    }

    public CamGLRenderer( Context c, int textureWidth, int textureHeight,
                          CamGLRenderObserver observer, GLSurfaceView surfaceView )
    {
        mObserver = observer;
        mContext = c;
        mCamTexture = new CamRenderTexture();
        mFrameQueue = new Vector<DPImage>(5);
        mSurfaceView = surfaceView;

        // Create our UV coordinates.
        float[] squareTextureCoordinateData = new float[] {
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
        };
        float[] squareTextureCoordinateDataHFlip = new float[] {
            1.0f, 0.0f,
            0.0f, 0.0f,
            1.0f, 1.0f,
            0.0f, 1.0f
        };
        float[] squareTextureCoordinateDataVFlip = new float[] {
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f
        };
        float[] squareTextureCoordinateDataHVFlip = new float[] {
            1.0f, 1.0f,
            0.0f, 1.0f,
            1.0f, 0.0f,
            0.0f, 0.0f
        };
        // We have to create the vertices of our triangle.
        float[] squareVerticesData = new float[] {
            -1.0f, -1.0f,
             1.0f, -1.0f,
            -1.0f,  1.0f,
             1.0f,  1.0f,
        };

        // The texture buffer
        for ( int i = 0; i < 4; ++i ) {
            mSquareTextureCoordinates[i] = ByteBuffer.allocateDirect(
                                               squareTextureCoordinateData.length * 4 ).order(
                                               ByteOrder.nativeOrder() ).asFloatBuffer();
        }
        mSquareTextureCoordinates[0].put( squareTextureCoordinateData ).position( 0 );
        mSquareTextureCoordinates[1].put( squareTextureCoordinateDataHFlip ).position( 0 );
        mSquareTextureCoordinates[2].put( squareTextureCoordinateDataVFlip ).position( 0 );
        mSquareTextureCoordinates[3].put( squareTextureCoordinateDataHVFlip ).position( 0 );

        // The vertex buffer.
        mSquareVertices = ByteBuffer.allocateDirect( squareVerticesData.length * 4 ).order(
                          ByteOrder.nativeOrder() ).asFloatBuffer();
        mSquareVertices.put( squareVerticesData ).position(0);

        // initialize bytebuffer for the draw list
        // short[] drawIndicesData = new short[] {0, 1, 2, 0, 2, 3}; // The order of vertex rendering.
        // mSquareDrawIndices = ByteBuffer.allocateDirect( drawIndicesData.length * 2).order(
        //                                                 ByteOrder.nativeOrder() ).asShortBuffer();
        // mSquareDrawIndices.put( drawIndicesData ).position(0);

        mFrameHeight = textureHeight;
        mFrameWidth  = textureWidth;
        // mRotationMatrix = getRotationMatrix( 90 );
        mTexturesCreated = false;
        prevTime = System.currentTimeMillis();
    }

    public void onPause()
    {
        mActive = false;
    }

    public void onResume()
    {
        mActive = true;
    }

    public void open()
    {
    }

    public void close()
    {
        mFrameHeight   = 0;
        mFrameWidth    = 0;
        mCamTexture.deleteTextures();
        mCamTexture = null;
    }

    @Override
    public void onSurfaceCreated( GL10 gl, EGLConfig config )
    {
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        // Set the clear color to black
        GLES30.glClearColor( 0.0f, 0.0f, 0.0f, 1 );

        // Set the camera position (View matrix)
        Matrix.setLookAtM( mtrxView, 0, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1.0f, 0.0f );

        int convertVertexShaderHandle = CamRenderShader.compileShader(
            GLES30.GL_VERTEX_SHADER, getShaderByName("convVertexShaderSource"));
        int normalVertexShaderHandle = CamRenderShader.compileShader(
                GLES30.GL_VERTEX_SHADER, getShaderByName("norVertexShaderSource"));
        int vertexShaderHandle = CamRenderShader.compileShader(
            GLES30.GL_VERTEX_SHADER, getShaderByName("vertexShaderSource"));
        int convertShaderHandle = CamRenderShader.compileShader(
            GLES30.GL_FRAGMENT_SHADER, getShaderByName("convertShaderSource"));
        int blurShaderHandle = CamRenderShader.compileShader(
            GLES30.GL_FRAGMENT_SHADER, getShaderByName("blurShaderRGBSource"));
        int fragmentShaderHandle = CamRenderShader.compileShader(
            GLES30.GL_FRAGMENT_SHADER, getShaderByName("blendFragShaderRGBSource"));

        //----------------  Convert shader program -----------------------------------------------------
        mConvertProgramHandle = CamRenderShader.createAndLinkProgram( convertVertexShaderHandle, convertShaderHandle );
        mVerticesHandle[0]    = GLES30.glGetAttribLocation(  mConvertProgramHandle, "vPosition"   );
        mTexCoordLocHandle[0] = GLES30.glGetAttribLocation(  mConvertProgramHandle, "a_texCoord"  );
        mMVPMtrxhandle[0]     = GLES30.glGetUniformLocation( mConvertProgramHandle, "uMVPMatrix"  );
        mPositionConv         = GLES30.glGetUniformLocation( mConvertProgramHandle, "positionConv"  );
        mInYHandle            = GLES30.glGetUniformLocation( mConvertProgramHandle, "y_texture"   );
        mInCHandle            = GLES30.glGetUniformLocation( mConvertProgramHandle, "uv_texture"  );
        //----------------------------------------------------------------------------------------------

        //----------------  Blur + Blend shader program --------------------------------------------------------
        // mProgramHandle        = CamRenderShader.createAndLinkProgram( vertexShaderHandle, fragmentShaderHandle );
        // mVerticesHandle[1]    = GLES30.glGetAttribLocation(  mProgramHandle, "vPosition"        );
        // mTexCoordLocHandle[1] = GLES30.glGetAttribLocation(  mProgramHandle, "a_texCoord"       );
        // mMVPMtrxhandle[1]     = GLES30.glGetUniformLocation( mProgramHandle, "uMVPMatrix"       );
        // mInRGBHandle          = GLES30.glGetUniformLocation( mProgramHandle, "rgb_texture"      );
        // mBackGroundRGBHandle  = GLES30.glGetUniformLocation( mProgramHandle, "bg_rgb_texture"   );
        // mMaskHandle           = GLES30.glGetUniformLocation( mProgramHandle, "mask_texture"     );
        // mXPixelOffsetUniform  = GLES30.glGetUniformLocation( mProgramHandle, "xPixelBaseOffset" );
        // mYPixelOffsetUniform  = GLES30.glGetUniformLocation( mProgramHandle, "yPixelBaseOffset" );
        // mMipLevelUniform      = GLES30.glGetUniformLocation( mProgramHandle, "mipLevel"         );
        //----------------------------------------------------------------------------------------------

        //----------------  Blur shader program --------------------------------------------------------
        mBlurProgramHandle    = CamRenderShader.createAndLinkProgram( normalVertexShaderHandle, blurShaderHandle );
        mVerticesHandle[2]    = GLES30.glGetAttribLocation(  mBlurProgramHandle, "vPosition"        );
        mTexCoordLocHandle[2] = GLES30.glGetAttribLocation(  mBlurProgramHandle, "a_texCoord"       );
        mMVPMtrxhandle[2]     = GLES30.glGetUniformLocation( mBlurProgramHandle, "uMVPMatrix"       );
        for ( int i = 0; i < 8; ++i ) {
            mInRGBHandle[i] = GLES30.glGetUniformLocation( mBlurProgramHandle, "rgb_texture");
        }
        mXPixelOffsetUniform  = GLES30.glGetUniformLocation( mBlurProgramHandle, "xPixelBaseOffset" );
        mYPixelOffsetUniform  = GLES30.glGetUniformLocation( mBlurProgramHandle, "yPixelBaseOffset" );
        mMipLevelUniform      = GLES30.glGetUniformLocation( mBlurProgramHandle, "mipLevel"         );
        //----------------------------------------------------------------------------------------------

        //----------------  Blend shader program --------------------------------------------------------
        mProgramHandle        = CamRenderShader.createAndLinkProgram( vertexShaderHandle, fragmentShaderHandle );
        mVerticesHandle[1]    = GLES30.glGetAttribLocation(  mProgramHandle, "vPosition"      );
        mTexCoordLocHandle[1] = GLES30.glGetAttribLocation(  mProgramHandle, "a_texCoord"     );
        mMVPMtrxhandle[1]     = GLES30.glGetUniformLocation( mProgramHandle, "uMVPMatrix"     );
        mForegroundRGBHandle  = GLES30.glGetUniformLocation( mProgramHandle, "rgb_texture"    );
        mBackGroundRGBHandle  = GLES30.glGetUniformLocation( mProgramHandle, "bg_rgb_texture" );
        mMaskHandle           = GLES30.glGetUniformLocation( mProgramHandle, "mask_texture"   );
        mRotMtrxhandle        = GLES30.glGetUniformLocation( mProgramHandle, "rotMat"         );
        mSurfaceRotMtrxhandle = GLES30.glGetUniformLocation( mProgramHandle, "surfaceRotMat"  );
        mFlipMtrxhandle       = GLES30.glGetUniformLocation( mProgramHandle, "flipMat"        );
        //----------------------------------------------------------------------------------------------

        mActive = true;
    }

    @Override
    public void onSurfaceChanged( GL10 gl, int width, int height )
    {

        // We need to know the current width and height.
        mScreenWidth = width;
        mScreenHeight = height;
        float aspectRatio = (float)mFrameWidth/mFrameHeight;
        float screenAspectRatio = (float)mScreenWidth/mScreenHeight;
        Log.d(TAG,"onSurfaceChanged aspectRatio="+aspectRatio+" screenAspectRatio="+screenAspectRatio+" w="+width+" h="+height);

        if ( screenAspectRatio > aspectRatio ) {
            mScreenROIWidth  = (int)Math.min( mScreenWidth,  mScreenWidth * aspectRatio / screenAspectRatio );
            mScreenROIHeight = (int)mScreenHeight;
        } else {
            mScreenROIWidth = (int) mScreenWidth;
            mScreenROIHeight = (int) Math.min( mScreenHeight,  mScreenWidth * aspectRatio);
        }
        mScreenROIX = (  (int)mScreenWidth -  mScreenROIWidth )/2;
        mScreenROIY = ( (int)mScreenHeight - mScreenROIHeight )/2;

        // Clear our matrices
        for ( int i = 0; i < 16; i++ ) {
            mtrxProjection[i]        = 0.0f;
            mtrxProjectionAndView[i] = 0.0f;
        }

        Log.e( TAG, "onSurfaceChanged Frame_dim " + mFrameWidth + " x " + mFrameHeight +
                    " ROI ( " + mScreenROIX + " " + mScreenROIY +
                    "  " + mScreenROIWidth + " " + mScreenROIHeight + ")" );
        // Setup our screen width and height for normal sprite translation.
        Matrix.orthoM( mtrxProjection, 0, -aspectRatio, aspectRatio, -1, 1, 0, 50 );

        // Calculate the projection and view transformation
        Matrix.multiplyMM( mtrxProjectionAndView, 0, mtrxProjection, 0, mtrxView, 0 );
    }

    public void executeConverter( ByteBuffer bufferY, ByteBuffer bufferC, boolean offline )
    {
        // clear Screen and Depth Buffer, we have set the clear color as black.
        GLES30.glClear( GLES30.GL_COLOR_BUFFER_BIT );

        if ( offline ) {
            GLES30.glViewport( 0, 0, ( int )mFrameWidth, ( int )mFrameHeight );
            GLES30.glBindFramebuffer( GLES30.GL_FRAMEBUFFER, mCamTexture.getInRGBFBO( 0 ) );
            GLES30.glFramebufferTexture2D( GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                                           GLES30.GL_TEXTURE_2D, mCamTexture.getInRGBTex( 0 ), 0 );
        } else {
            GLES30.glViewport( 0, 0, ( int )mScreenWidth, ( int )mScreenHeight );
        }

        GLES30.glActiveTexture( GLES30.GL_TEXTURE0 );
        GLES30.glBindTexture( GLES30.GL_TEXTURE_2D, mCamTexture.getInYTex() );
        GLES30.glTexSubImage2D( GLES30.GL_TEXTURE_2D, 0, 0, 0, mFrameWidth, mFrameHeight,
                                GLES30.GL_LUMINANCE, GLES30.GL_UNSIGNED_BYTE, bufferY );

        GLES30.glActiveTexture( GLES30.GL_TEXTURE1 );
        GLES30.glBindTexture( GLES30.GL_TEXTURE_2D, mCamTexture.getInCTex() );
        GLES30.glTexSubImage2D( GLES30.GL_TEXTURE_2D, 0, 0, 0, mFrameWidth/2, mFrameHeight/2,
                                GLES30.GL_LUMINANCE_ALPHA, GLES30.GL_UNSIGNED_BYTE, bufferC );

        GLES30.glUseProgram( mConvertProgramHandle );
        if (offline) {
            GLES30.glUniform1i(mPositionConv,0);
        } else {
            GLES30.glUniform1i(mPositionConv,1);
        }
        GLES30.glUniform1i ( mInYHandle, 0 );
        GLES30.glUniform1i ( mInCHandle, 1 );
        GLES30.glVertexAttribPointer( mVerticesHandle[0], 2, GLES30.GL_FLOAT, false, 0, mSquareVertices );
        GLES30.glVertexAttribPointer ( mTexCoordLocHandle[0], 2, GLES30.GL_FLOAT, false, 0, mSquareTextureCoordinates[0] );
        GLES30.glUniformMatrix4fv( mMVPMtrxhandle[0], 1, false, mtrxProjectionAndView, 0);
        GLES30.glEnableVertexAttribArray( mVerticesHandle[0] );
        GLES30.glEnableVertexAttribArray ( mTexCoordLocHandle[0] );

        //GLES30.glDrawElements( GLES30.GL_TRIANGLES, 6, GLES30.GL_UNSIGNED_SHORT, mSquareDrawIndices );
        GLES30.glDrawArrays( GLES30.GL_TRIANGLE_STRIP, 0, 4 );

        if ( offline ) {
            int status = GLES30.glCheckFramebufferStatus( GLES30.GL_FRAMEBUFFER );
            if ( status == GLES30.GL_FRAMEBUFFER_COMPLETE ) {
            /// Debug
            ///    GLES30.glReadPixels( 0, 0, mFrameWidth, mFrameHeight, GLES30.GL_RGB, GLES30.GL_UNSIGNED_BYTE, scratchRGB );
            ///    Log.e( TAG, "RGB Buffer " + scratchRGB.get(1000) + " " + scratchRGB.get(1001) +
            ///           "handles "  + mCamTexture.getInRGBFBO() + " " + mCamTexture.getInRGBTex() );
            } else {
                Log.e( TAG, "FBO status " + status + "error " + GLES30.glGetError() );
            }
        }

        // Disable vertex array
        GLES30.glDisableVertexAttribArray( mVerticesHandle[0] );
        GLES30.glDisableVertexAttribArray( mTexCoordLocHandle[0] );
        // Reset FBO
        GLES30.glBindFramebuffer( GLES30.GL_FRAMEBUFFER, 0 );
        GLES30.glBindTexture( GLES30.GL_TEXTURE_2D, 0 );
    }

    public void executeBlur( int level )
    {
        int viewPortScaleFactor = 1;
        int texScaleFactor = 1;
        float blurScaleFactor = 1.0f; // 2x->.5

        switch ( blurType )
        {
        case BlurTypeGaussianPyramid:
            viewPortScaleFactor = level + 1;
            texScaleFactor = level;
            break;
        case BlurTypeGaussianDilated:
            blurScaleFactor = 4.0f;
            break;
        case BlurTypeGaussianKernelSize:
            break;
        }

        GLES30.glClear( GLES30.GL_COLOR_BUFFER_BIT );
        //GLES30.glViewport( 0, 0, ( int )mFrameWidth/(level+1), ( int )mFrameHeight/(level+1) );
        GLES30.glViewport( 0, 0, ( int )mFrameWidth/viewPortScaleFactor,
                           ( int )mFrameHeight/viewPortScaleFactor );

        // Bind Mask texture to texturename
        GLES30.glActiveTexture( GLES30.GL_TEXTURE0 );
        // GLES30.glBindTexture( GLES30.GL_TEXTURE_2D, mCamTexture.getInRGBTex( 0 ) );
        GLES30.glBindTexture( GLES30.GL_TEXTURE_2D, mCamTexture.getInRGBTex( level - 1 ) );
        GLES30.glTexSubImage2D( GLES30.GL_TEXTURE_2D, 0, 0, 0,
                                mFrameWidth/texScaleFactor,
                                mFrameHeight/texScaleFactor,
                                GLES30.GL_RGB, GLES30.GL_UNSIGNED_BYTE, null );
        GLES30.glUniform1i ( mInRGBHandle[level-1], 0 );

        GLES30.glBindFramebuffer( GLES30.GL_FRAMEBUFFER, mCamTexture.getInRGBFBO( level ) );
        GLES30.glFramebufferTexture2D( GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                                       GLES30.GL_TEXTURE_2D, mCamTexture.getInRGBTex( level ), 0 );

        float xPixelOffset = blurScaleFactor/(float)mFrameWidth;
        float yPixelOffset = blurScaleFactor/(float)mFrameHeight;
        float mipLevel = (float)level;
        GLES30.glUniform1f( mMipLevelUniform, mipLevel );
        GLES30.glUniform1f( mXPixelOffsetUniform, xPixelOffset );
        GLES30.glUniform1f( mYPixelOffsetUniform, yPixelOffset );

        GLES30.glUseProgram( mBlurProgramHandle );

        GLES30.glVertexAttribPointer( mVerticesHandle[2], 2, GLES30.GL_FLOAT, false, 0, mSquareVertices );
        GLES30.glEnableVertexAttribArray( mVerticesHandle[2] );
        GLES30.glVertexAttribPointer ( mTexCoordLocHandle[2], 2, GLES30.GL_FLOAT, false, 0, mSquareTextureCoordinates[0] );
        GLES30.glEnableVertexAttribArray ( mTexCoordLocHandle[2] );

        // GLES30.glDrawElements( GLES30.GL_TRIANGLES, 6, GLES30.GL_UNSIGNED_SHORT, mSquareDrawIndices );
        GLES30.glDrawArrays( GLES30.GL_TRIANGLE_STRIP, 0, 4 );

        // Disable vertex array
        GLES30.glDisableVertexAttribArray( mVerticesHandle[2] );
        GLES30.glDisableVertexAttribArray( mTexCoordLocHandle[2] );

        // Reset FBO
        GLES30.glBindFramebuffer( GLES30.GL_FRAMEBUFFER, 0 );
        GLES30.glBindTexture( GLES30.GL_TEXTURE_2D, 0 );
    }

    public void executeBlend( ByteBuffer bufferMask, int level )
    {
        GLES30.glClear( GLES30.GL_COLOR_BUFFER_BIT /*| GLES30.GL_DEPTH_BUFFER_BIT*/ );
        GLES30.glViewport( mScreenROIX, mScreenROIY, ( int )mScreenROIWidth, ( int )mScreenROIHeight );
        //GLES30.glEnable( GLES30.GL_DEPTH_TEST );
        GLES30.glUseProgram( mProgramHandle );

        // Bind Mask texture to texturename
        GLES30.glActiveTexture( GLES30.GL_TEXTURE0 );
        GLES30.glBindTexture( GLES30.GL_TEXTURE_2D, mCamTexture.getInRGBTex( 0 ) );
        GLES30.glTexSubImage2D( GLES30.GL_TEXTURE_2D, 0, 0, 0, mFrameWidth, mFrameHeight,
                                GLES30.GL_RGB, GLES30.GL_UNSIGNED_BYTE, null );
        GLES30.glUniform1i ( mForegroundRGBHandle, 0 );

        GLES30.glActiveTexture( GLES30.GL_TEXTURE1 );
        GLES30.glBindTexture( GLES30.GL_TEXTURE_2D, mCamTexture.getInRGBTex(level));
        //GLES30.glTexSubImage2D( GLES30.GL_TEXTURE_2D, 0, 0, 0, mFrameWidth/(level+1), mFrameHeight/(level+1),
        GLES30.glTexSubImage2D( GLES30.GL_TEXTURE_2D, 0, 0, 0, mFrameWidth, mFrameHeight,
                                GLES30.GL_RGB, GLES30.GL_UNSIGNED_BYTE, null );
        GLES30.glUniform1i ( mBackGroundRGBHandle, 1 );

        GLES30.glActiveTexture( GLES30.GL_TEXTURE2 );
        GLES30.glBindTexture( GLES30.GL_TEXTURE_2D, mCamTexture.getMaskTex() );
        GLES30.glTexSubImage2D( GLES30.GL_TEXTURE_2D, 0, 0, 0, mMaskWidth, mMaskHeight,
                                GLES30.GL_LUMINANCE, GLES30.GL_UNSIGNED_BYTE, bufferMask );
        GLES30.glUniform1i ( mMaskHandle, 2 );

        GLES30.glVertexAttribPointer( mVerticesHandle[1], 2, GLES30.GL_FLOAT, false, 0, mSquareVertices );
        GLES30.glEnableVertexAttribArray( mVerticesHandle[1] );
        GLES30.glVertexAttribPointer ( mTexCoordLocHandle[1], 2, GLES30.GL_FLOAT, false, 0, mSquareTextureCoordinates[0] );
        GLES30.glEnableVertexAttribArray ( mTexCoordLocHandle[1] );
        GLES30.glUniformMatrix4fv( mMVPMtrxhandle[1], 1, false, mtrxProjectionAndView, 0 );
        GLES30.glUniformMatrix3fv( mRotMtrxhandle, 1, false, mRotationMatrix, 0 );
        GLES30.glUniformMatrix3fv( mSurfaceRotMtrxhandle, 1, false, mSurfaceRotationMatrix, 0 );
        GLES30.glUniformMatrix2fv( mFlipMtrxhandle, 1, false, flipNone, 0 );

        // GLES30.glDrawElements( GLES30.GL_TRIANGLES, 6, GLES30.GL_UNSIGNED_SHORT, mSquareDrawIndices );
        GLES30.glDrawArrays( GLES30.GL_TRIANGLE_STRIP, 0, 4 );

        // Disable vertex array
        GLES30.glDisableVertexAttribArray( mVerticesHandle[1] );
        GLES30.glDisableVertexAttribArray( mTexCoordLocHandle[1] );
    }

    @Override
    public void onDrawFrame( GL10 unused )
    {
        if ( !mActive || mFrameQueue.size() == 0 ) {
            return;
        }

        currentTime = System.currentTimeMillis();
        long delta = currentTime - prevTime;
        Log.d(TAG,"frame delta time = "+delta);
        try {
            if ( minFrameDelta > delta )
                Thread.sleep( minFrameDelta - delta );
        } catch ( InterruptedException e ) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        prevTime = System.currentTimeMillis();

        if ( !mTexturesCreated && mMaskWidth > 0 && mMaskHeight  > 0 ) {
            Log.d( TAG, "onDrawFrame createTextures " + blurType );
            mCamTexture.createTextures( mFrameWidth, mFrameHeight,
                                        mMaskWidth, mMaskHeight, 8,
                                        blurType );
            mTexturesCreated = true;
        } else if ( !mTexturesCreated ) {
            // No op
            return;
        }

        DPImage dpimage = mFrameQueue.get( 0 );
        mFrameQueue.removeElementAt( 0 );
        Plane[] planes = dpimage.mImage.getPlanes();
        ByteBuffer bufferY  = planes[0].getBuffer();
        ByteBuffer bufferC = planes[2].getBuffer();

        if ( dpimage.mMask == null) {
            executeConverter( bufferY, bufferC, false );
            Log.d( TAG, "onDrawFrame no processing" );
        } else {
            int mipLevel = (int)(( mBlurLevel * 8.0f )/100.0f);
            if ( mipLevel >= 7 )
                mipLevel = 7;// clamp
            Log.d( TAG, "[DP_BUF_DBG] onDrawFrame frame " + dpimage.mSeqNumber + " mipLevel "
                    + mipLevel );
            executeConverter( bufferY, bufferC, true );

            for ( int lvl = 1; lvl <= mipLevel; ++lvl ) {
               executeBlur( lvl );
            }

            // Set rotation
            if ( dpimage.mOrientation >= 0 ) {
                prepareRotationMatrix( dpimage.mOrientation );
            }
            executeBlend( dpimage.mMask, mipLevel );
        }
        if ( mActive ) {
            mObserver.onRenderComplete( dpimage, false );
        }
    }

    private native String getShaderByName(String type);
}
