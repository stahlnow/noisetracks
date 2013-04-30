package com.noisetracks.android.audio;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.EmbossMaskFilter;
import android.graphics.MaskFilter;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class Scope extends SurfaceView implements SurfaceHolder.Callback {

	private static final String TAG = "Scope";
	private DrawThread mDrawThread;
	
	public Scope(Context context, AttributeSet attrs) {
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

		private String TAG = "Scope DrawThread";
		
		private SurfaceHolder mSurfaceHolder;
		private boolean mRun = false;

		private Paint mBackPaint;
		private Bitmap mBackgroundImage;
		
		private short[] mBuffer;
		
		private int mCanvasWidth = 1;
		private int mCanvasHeight = 1;
		
		private Paint mLinePaint;
		private MaskFilter mEmboss;
		
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
			mBackPaint = new Paint();
			mBackPaint.setAntiAlias(true);
			mBackPaint.setARGB(255, 238, 238, 238);
			mBuffer = new short[2048];
			
			mEmboss = new EmbossMaskFilter(new float[] { 1, 1, 1 }, 0.4f, 6, 3.5f);
			//mLinePaint.setMaskFilter(mEmboss);
			
			mBackgroundImage = Bitmap.createBitmap(
					1,
					1,
					Bitmap.Config.ARGB_8888);

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
	                    drawScope(c);
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
		
		protected void drawScope(Canvas canvas) {
			
			canvas.drawPaint(mBackPaint);
			
			int height = canvas.getHeight();
			int width = canvas.getWidth();
			
			int BuffIndex = (mBuffer.length / 2 - canvas.getWidth()) / 2;
			
			int mBuffIndex = BuffIndex;
			
			int StartX = 0;
			if (StartX >= width) {
				canvas.save();
				return;
			}
			
			while (StartX < width - 1) {

				int StartBaseY = mBuffer[mBuffIndex - 1] / height;
				int StopBaseY  = mBuffer[mBuffIndex	   ] / height;
				
				if (StartBaseY > height / 2) {
					StartBaseY = 2 + height / 2;
					int checkSize = height / 2;
					if (StopBaseY <= checkSize)
						return;
					StopBaseY = 2 + height / 2;
				}

				int StartY = StartBaseY + height / 2;
				int StopY = StopBaseY + height / 2;
				canvas.drawLine(StartX, StartY, StartX + 1, StopY, mLinePaint);
				
				mBuffIndex++;
				StartX++;
				int checkSize_again = -1 * (height / 2);
				if (StopBaseY >= checkSize_again)
					continue;
				StopBaseY = -2 + -1 * (height / 2);
			}
		}

		public void setBuffer(short[] buffer) {
			synchronized (mBuffer) {
				mBuffer = buffer;
				return;
			}
		}

		public void setSurfaceSize(int width, int height) {
			synchronized (mSurfaceHolder) {
				mCanvasWidth = width;
				mCanvasHeight = height;
				mBackgroundImage = Bitmap.createScaledBitmap(mBackgroundImage, width, height, true);
				return;
			}
		}
	}
}
