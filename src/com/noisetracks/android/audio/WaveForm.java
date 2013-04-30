package com.noisetracks.android.audio;

import com.noisetracks.android.NoisetracksApplication;
import com.noisetracks.android.helper.Helper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class WaveForm extends SurfaceView implements SurfaceHolder.Callback {

	private static final String TAG = "WaveForm";
	private DrawThread mDrawThread;
	
	public WaveForm(Context context, AttributeSet attrs) {
		super(context, attrs);
		getHolder().addCallback(this);
		mDrawThread = new DrawThread(getHolder(), context);
		setFocusable(true);
	}

	public DrawThread getThread() {
		return mDrawThread;
	}

	/**
	 * Called when there's a change in the surface
	 */
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		mDrawThread.setSurfaceSize(width, height);
	}

	/**
	 * Creates the surface
	 */
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		Log.v(TAG, "Surface created");
		mDrawThread.setRunning(true);
		mDrawThread.start();
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		// we have to tell thread to shut down & wait for it to finish, or else
		// it might touch the Surface after we return and explode
		boolean retry = true;
		mDrawThread.setRunning(false);
		while (retry) {
			try {
				mDrawThread.join();
				retry = false;
			} catch (InterruptedException e) {
				// we will try it again and again...
			}
		}
	}

	public class DrawThread extends Thread {

		private String TAG = "WaveForm DrawThread";
		
		private SurfaceHolder mSurfaceHolder;
		private boolean mRun = false;
		
		private short[] mBuffer;
		
		private float mX;
		private Paint mLinePaint;
		private Path mPath;
		
		public DrawThread(SurfaceHolder holder, Context context) {
			mSurfaceHolder = holder;
			//mSurfaceHolder.setFormat(PixelFormat.TRANSPARENT);
			mLinePaint = new Paint();
			mLinePaint.setAntiAlias(true);
			mLinePaint.setDither(true);
			mLinePaint.setStyle(Paint.Style.STROKE);
			mLinePaint.setStrokeJoin(Paint.Join.ROUND);
			mLinePaint.setStrokeCap(Paint.Cap.ROUND);
			mLinePaint.setStrokeWidth(3.0f);
			mLinePaint.setARGB(255, 100, 100, 100);
			
			mBuffer = new short[2048];
			
			mPath = new Path();
			mX = 0.0f;
		}

		public void setRunning(boolean run) {
			mRun = run;
		}
		
		@Override
		public void run() {
			Canvas c;
	        while (mRun) {
	            c = null;
	            try {
	                c = mSurfaceHolder.lockCanvas(null);
	                synchronized (mSurfaceHolder) {
	                    drawBuffer(c);
	                }
	            } finally {
	                // do this in a finally so that if an exception is thrown
	                // during the above, we don't leave the Surface in an
	                // inconsistent state
	                if (c != null) {
	                    mSurfaceHolder.unlockCanvasAndPost(c);
	                }
	            }
	        }
		}
		
		protected void drawBuffer(Canvas canvas) {
		
			canvas.drawColor(0xFFEEEEEE);
			
			int min = 0;
			int max = 0;
			for (int i = 0; i < mBuffer.length; i += 2) { // only left channel
				min = Math.min(min, mBuffer[i]);
				max = Math.max(max, mBuffer[i]);
			}
			
			float y1 = Helper.map2range(min, -32768, 0, 0, getHeight()/2);
			float y2 = Helper.map2range(max, 0, 32768, getHeight()/2, getHeight());
			
			mPath.moveTo(mX, y1);
			mPath.lineTo(mX, y2);
			
			mX++;
			
			canvas.drawPath(mPath, mLinePaint);
			
		}
		
		public void setBuffer(short[] buffer) {
			synchronized (mBuffer) {
				mBuffer = buffer;
				return;
			}
		}

		public void setSurfaceSize(int width, int height) {
			synchronized (mSurfaceHolder) {
				return;
			}
		}
	}
}
