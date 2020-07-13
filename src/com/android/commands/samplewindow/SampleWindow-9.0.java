/*
**
** Copyright 2013, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/


package com.android.commands.samplewindow;

import android.util.Log;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Binder;
import android.view.Choreographer;
import android.view.Display;
import android.view.Gravity;
import android.view.IWindowSession;
import android.view.InputChannel;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.IBinder;
import android.graphics.Point;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.DragEvent;
import android.view.WindowManager;
import android.view.InputEventReceiver;
import android.view.IWindowManager;
import android.os.ServiceManager;
import android.view.WindowManagerGlobal;
import android.hardware.display.IDisplayManager;
import android.view.DisplayInfo;
import android.view.IWindow;
import android.view.WindowManager.LayoutParams;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import com.android.internal.os.IResultReceiver;
import android.view.DisplayCutout;
import android.util.MergedConfiguration;

import static android.view.Display.DEFAULT_DISPLAY;

public class SampleWindow {

    // IWindowSession 是客户端向WMS请求窗口操作的中间代理，并且是进程唯一的
    IWindowSession mWindowSession ;
    // InputChannel 是窗口接收用户输入事件的管道。在第5章中将对其进行详细探讨
    InputChannel mInputChannel = new InputChannel();
	
    // 下面的三个Rect保存了窗口的布局结果。其中mFrame表示了窗口在屏幕上的位置与尺寸
    // 在4.4节中将详细介绍它们的作用以及计算原理
    // final Rect mInsets = new Rect();
    // final Rect mFrame = new Rect();
    // Rect mVisibleInsets = new Rect();
    final Rect mVisibleInsets = new Rect();
    final Rect mWinFrame = new Rect();
    final Rect mOverscanInsets = new Rect();
    final Rect mContentInsets = new Rect();
    final Rect mStableInsets = new Rect();
    final Rect mOutsets = new Rect();
    final Rect mBackdropFrame = new Rect();
    final Rect mTmpRect = new Rect();
	
    MergedConfiguration mConfig = new MergedConfiguration();
    // 窗口的Surface，在此Surface上进行的绘制都将在此窗口上显示出来
    Surface mSurface = new Surface();
    // 用于在窗口上进行绘图的画刷
    Paint mPaint = new Paint();
    // 添加窗口所需的令牌，在4.2节将会对其进行介绍
    IBinder mToken = new Binder();
    // 一个窗口对象，本例演示了如何将此窗口添加到WMS中，并在其上进行绘制操作
    MyWindow mWindow = new MyWindow();
    WindowManager.LayoutParams mLayoutParams = new WindowManager.LayoutParams();
    Choreographer mChoreographer = null;
    // InputHandler 用于从InputChannel接收按键事件并做出响应
    InputHandler mInputHandler = null;
    boolean mContinueAnime = true;

    public static void main(String[] args) {
        try{
           SampleWindow sampleWindow = new SampleWindow();
           sampleWindow.run();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void run() throws Exception{
    	Log.i("dengsam","Run...111");
        Looper.prepareMainLooper();
		
        // 获取WMS服务。Google 的同志们他们利用IxxxxService.Stub.asInterface函数对这两种不同的情况进行了统一，也就是不管你是在同一进程还是不同进程，
        // 那么在拿到Binder引用后，调用IxxxxService.Stub.asInterface(IBinder obj) 即可得到一个IxxxxService 实例，然后你只管调用IxxxxService里的函数就成了。
        IWindowManager mWMS = IWindowManager.Stub.asInterface(ServiceManager.getService(Context.WINDOW_SERVICE));//不同于(WindowManager)getSystemService(Context.WINDOW_SERVICE)
        // 通过WindowManagerGlobal获取进程唯一的IWindowSession实例。它将用于向WMS
        // 发送请求。注意这个函数在较早的Android版本（如4.1）位于ViewRootImpl类中
        mWindowSession = WindowManagerGlobal.getWindowSession();
		
        // 获取屏幕分辨率
        IDisplayManager mDisplayManagerService = IDisplayManager.Stub.asInterface(ServiceManager.getService(Context.DISPLAY_SERVICE));
        DisplayInfo mDisplayInfo = mDisplayManagerService.getDisplayInfo(Display.DEFAULT_DISPLAY);
        Point mScreenSize = new Point(mDisplayInfo.appWidth, mDisplayInfo.appHeight);
		
        // 初始化WindowManager.LayoutParams
        initLayoutParams(mScreenSize);
        // 将新窗口添加到WMS
        installWindow(mWMS);
        // 初始化Choreographer的实例，此实例为线程唯一。这个类的用法与Handler
        // 类似，不过它总是在VSYC同步时回调，所以比Handler更适合做动画的循环器
        mChoreographer = Choreographer.getInstance();
        // 开始处理第一帧的动画,通过Looper.loop()循环绘制画矩形动画。
        scheduleNextFrameDrawRect();
        // 当前线程陷入消息循环，直到Looper.quit()
        Looper.loop();
        // 标记不要继续绘制动画帧
        mContinueAnime = false;
        // 卸载当前Window
        uninstallWindow(mWMS);
		Log.i("dengsam","Run...222");
    }

    public void initLayoutParams(Point screenSize) {
        // 标记即将安装的窗口类型为SYSTEM_ALERT
        mLayoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        mLayoutParams.setTitle("SampleWindow");
        // 设定窗口的左上角坐标以及高度和宽度
        mLayoutParams.gravity = Gravity.LEFT | Gravity.TOP;
        mLayoutParams.x = screenSize.x / 4;
        mLayoutParams.y = screenSize.y / 4;
        mLayoutParams.width = screenSize.x / 2;
        mLayoutParams.height = screenSize.y / 2;
        // 和输入事件相关的Flag，希望当输入事件发生在此窗口之外时，其他窗口也可以接收输入事件
        mLayoutParams.flags = mLayoutParams.flags | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
    }

    public void installWindow(IWindowManager mWMS) throws Exception {
        // 首先向WMS声明一个Token，任何一个Window都需要隶属于一个特定类型的Token
        mWMS.addWindowToken(mToken, WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,DEFAULT_DISPLAY);
        // 设置窗口所隶属的Token
        mLayoutParams.token = mToken;
        // 通过IWindowS
        // 目前仍然没有有效的Surface。不过，经过这个调用后，mInputChannel已经可以用来接收输入事件了
         mWindowSession.add(mWindow, 0, mLayoutParams, View.VISIBLE, mOverscanInsets, mStableInsets, mInputChannel);
        /*通过IWindowSession要求WMS对本窗口进行重新布局，经过这个操作后，WMS将会为窗口
          创建一块用于绘制的Surface并保存在参数mSurface中。同时，这个Surface被WMS放置在LayoutParams所指定的位置上 */
        mWindowSession.relayout(mWindow, 0, mLayoutParams, mLayoutParams.width, mLayoutParams.height, View.VISIBLE,0,-1,
        	                    mWinFrame, mOverscanInsets, mContentInsets,mVisibleInsets, mStableInsets, mOutsets, mBackdropFrame,
                                new DisplayCutout.ParcelableWrapper(), mConfig, mSurface);
        if (!mSurface.isValid()) {
            throw new RuntimeException("Failed creating Surface.");
        }
        // 基于WMS返回的InputChannel创建一个Handler，用于监听输入事件
        // mInputHandler一旦被创建，就已经在监听输入事件了
        mInputHandler = new InputHandler(mInputChannel, Looper.myLooper());
    }

    public void uninstallWindow(IWindowManager mWMS) throws Exception {
        // 从WMS处卸载窗口
        mWindowSession.remove(mWindow);
        // 从WMS处移除之前添加的Token
        mWMS.removeWindowToken(mToken,DEFAULT_DISPLAY);
    }

    public void scheduleNextFrameDrawRect() {
        // 要求在显示系统刷新下一帧时回调mFrameRender，注意，只回调一次
        mChoreographer.postCallback(Choreographer.CALLBACK_ANIMATION, mFrameRender, null);
    }

    // // 这个Runnable对象用于在窗口上描绘一帧
    public Runnable mFrameRender = new Runnable() {
        @Override
        public void run() {
            try {
                // 获取当期时间戳
                long time = mChoreographer.getFrameTime() % 1000;
                // 绘图
                if (mSurface.isValid()) {
                    Canvas canvas = mSurface.lockCanvas(null);
                    canvas.drawColor(Color.DKGRAY);
                    canvas.drawRect(2 * mLayoutParams.width * time / 1000
                            - mLayoutParams.width, 0, 2 * mLayoutParams.width * time
                            / 1000, mLayoutParams.height, mPaint);
                    mSurface.unlockCanvasAndPost(canvas);
					//WindowSession.finishDrawing会调用WMS.finishDrawingWindow()，会再调用WindowStateAnimator.finishDrawingLocked()更新窗口状态
					//mDrawState为COMMIT_DRAW_PENDING。同时还会调用requestTraversal()来触发一次界面刷新函数performSurfacePlacement()调用。
                    mWindowSession.finishDrawing(mWindow);//来通知WMS，该窗口已经绘制好了，调用该函数通知WMS开始显示该窗口。类似WindowSession.relayout调用relayoutWindow重新布局界面并刷新。
                }
                if (mContinueAnime)
                    scheduleNextFrameDrawRect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    // // 定义一个类继承InputEventReceiver，用于在其onInputEvent()函数中接收窗口的输入事件
    class InputHandler extends InputEventReceiver {
        Looper mLooper = null;
        public InputHandler(InputChannel inputChannel, Looper looper) {
            super(inputChannel, looper);
            mLooper = looper;
        }
        @Override
        public void onInputEvent(InputEvent event, int displayId) {
            if (event instanceof MotionEvent) {
                MotionEvent me = (MotionEvent)event;
                if (me.getAction() == MotionEvent.ACTION_UP) {
                    // 退出Looper.loop()循环，退出程序
                    mLooper.quit();
                }
            }
            super.onInputEvent(event,DEFAULT_DISPLAY);
        }
    }
    
    // // 实现一个继承自IWindow.Stub的类MyWindow
    static class MyWindow extends IWindow.Stub {
        // 保持默认的实现即可

        @Override
        public void resized(Rect frame, Rect overscanInsets, Rect contentInsets, Rect visibleInsets,
                            Rect stableInsets, Rect outsets, boolean reportDraw, MergedConfiguration newConfig,
                            Rect backDropFrame, boolean forceLayout, boolean alwaysConsumeNavBar,int displayId,
                            DisplayCutout.ParcelableWrapper displayCutout) {

        }
        @Override
        public void moved(int newX, int newY) {
        }

        @Override
        public void dispatchAppVisibility(boolean visible) {
        }

        @Override
        public void dispatchGetNewSurface() {
        }

        @Override
        public void windowFocusChanged(boolean hasFocus, boolean touchEnabled) {
        }
        
        @Override
        public void closeSystemDialogs(String reason) {
        }

        @Override
        public void dispatchWallpaperOffsets(float x, float y, float xStep, float yStep, boolean sync) {

        }

        @Override
        public void dispatchDragEvent(DragEvent event) {
        }

        @Override
        public void updatePointerIcon(float x, float y) {
        }

        @Override
        public void dispatchSystemUiVisibilityChanged(int seq, int globalUi,int localValue, int localChanges) {
        }

        @Override
        public void dispatchWallpaperCommand(String action, int x, int y, int z, Bundle extras, boolean sync) {

        }

        @Override
        public void dispatchWindowShown() {
        }

        @Override
        public void requestAppKeyboardShortcuts(IResultReceiver receiver, int deviceId) {

        }

        @Override
        public void executeCommand(String command, String parameters, ParcelFileDescriptor out) {

        }

        @Override
        public void dispatchPointerCaptureChanged(boolean hasCapture){
        }
    }
}
