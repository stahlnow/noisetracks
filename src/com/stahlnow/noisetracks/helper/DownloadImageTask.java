package com.stahlnow.noisetracks.helper;

import java.io.InputStream;

import com.stahlnow.noisetracks.NoisetracksApplication;
import com.stahlnow.noisetracks.utility.AppLog;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ImageView;

public class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
	private ImageView mImage;
	private boolean mInvert;

	public DownloadImageTask(ImageView image, boolean invert) {
		this.mImage = image;
		this.mInvert = invert;
	}

	@Override
	protected void onPreExecute() {
		mImage.setTag(this);
	}

	protected Bitmap doInBackground(String... urls) {
		
		final String url = urls[0];
		Bitmap mIcon11 = null;
		try {
			InputStream in = new java.net.URL(url).openStream();
			mIcon11 = BitmapFactory.decodeStream(in);
		} catch (Exception e) {
			//AppLog.logString("Error: " + e.getMessage());
			e.printStackTrace();
		}

	
		mIcon11 = ImageHelper.transformBitmap(mIcon11, 5, mInvert);

		NoisetracksApplication.getBitmapCache().put(url, mIcon11); // put image in cache
		
		return mIcon11;
	}

	@Override
	protected void onPostExecute(Bitmap result) {
		if (mImage.getTag() == this) {
			mImage.setImageBitmap(result);
			mImage.setTag(null);
		}
	}

	private static class ImageHelper {
		/**
		 * Add rounded corners to bitmap and optionally invert colors.
		 * @param bitmap The Bitmap
		 * @param pixels The radius for corners
		 * @param invert Invert bitmap
		 * @return A rounded corner bitmap
		 */
		public static Bitmap transformBitmap(Bitmap bitmap, int pixels, boolean invert) {
			Bitmap output = Bitmap.createBitmap(bitmap.getWidth(),
					bitmap.getHeight(), Config.ARGB_8888);
			Canvas canvas = new Canvas(output);

			final int color = 0xffffffff;
			final Paint paint = new Paint();
			final Rect rect = new Rect(0, 0, bitmap.getWidth(),
					bitmap.getHeight());
			final RectF rectF = new RectF(rect);
			final float roundPx = pixels;

			paint.setAntiAlias(true);
			canvas.drawARGB(0, 0, 0, 0);
			paint.setColor(color);
			canvas.drawRoundRect(rectF, roundPx, roundPx, paint);

			paint.setXfermode(new PorterDuffXfermode(Mode.SRC_IN));
			
			if (invert) {
				float inv[] = { -1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, -1.0f, 0.0f,
						1.0f, 1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f,
						1.0f, 0.0f };
				ColorMatrix cm = new ColorMatrix(inv);
				paint.setColorFilter(new ColorMatrixColorFilter(cm));
			}
			canvas.drawBitmap(bitmap, rect, rect, paint);

			return output;
		}
	}

}
