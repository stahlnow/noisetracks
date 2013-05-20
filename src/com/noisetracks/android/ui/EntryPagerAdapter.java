package com.noisetracks.android.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.view.ViewGroup;

import java.util.HashMap;
import java.util.Map;

public abstract class EntryPagerAdapter extends FragmentStatePagerAdapter {
	
	@SuppressWarnings("unused")
	private static final String TAG = "EntryPagerAdapter";

    protected boolean mDataValid;
    protected Cursor mCursor;
    protected Context mContext;
    private Map<Integer, Fragment> mPageReferenceMap;

    public EntryPagerAdapter(Context context, FragmentManager fm, Cursor cursor) {
        super(fm);
        init(context, cursor);
    }

    @SuppressLint("UseSparseArrays")
	void init(Context context, Cursor c) {
    	mPageReferenceMap = new HashMap<Integer, Fragment>();
        boolean cursorPresent = c != null;
        mCursor = c;
        mDataValid = cursorPresent;
        mContext = context;
    }

    public Cursor getCursor() {
        return mCursor;
    }

    @Override
    public Fragment getItem(int position) {
        if (mDataValid) {
            mCursor.moveToPosition(position);
            return getItem(mContext, mCursor);
        } else {
            return null;
        }
    }
    
    public abstract Fragment getItem(Context context, Cursor cursor);
    
    @Override
    public int getItemPosition(Object object) {
    	return POSITION_NONE;
    }
    
    /**
     * Get the fragment at the specified position from view pager.
     * @param position the page position in view pager.
     * @return the fragment or null, if the fragment was not found.
     */
    public Fragment getFragmentAtPosition(int position) {
		return mPageReferenceMap.get(position);
	}
    
    
    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        if (!mDataValid) {
            throw new IllegalStateException("this should only be called when the cursor is valid");
        }
        if (!mCursor.moveToPosition(position)) {
            throw new IllegalStateException("couldn't move cursor to position " + position);
        }

        Object obj = super.instantiateItem(container, position);
        mPageReferenceMap.put(position, (Fragment)obj); // put it in a map, so we have a reference!
        
        //Log.v(TAG, "instantiateItem " + position);
        
        return obj;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        super.destroyItem(container, position, object);
        
        if (position <= getCount()) {
            FragmentManager manager = ((Fragment) object).getFragmentManager();
            FragmentTransaction trans = manager.beginTransaction();
            trans.remove((Fragment) object);
            trans.commit();
        }
        
        mPageReferenceMap.remove(Integer.valueOf(position));
        
        //Log.v(TAG, "destroyItem " + position);
    }

    @Override
    public int getCount() {
        if (mDataValid) {
            return mCursor.getCount();
        } else {
            return 0;
        }
    }

    public void changeCursor(Cursor cursor) {
        Cursor old = swapCursor(cursor);
        if (old != null) {
            old.close();
        }
    }

    public Cursor swapCursor(Cursor newCursor) {
        if (newCursor == mCursor) {
            return null;
        }
        Cursor oldCursor = mCursor;
        mCursor = newCursor;
        if (newCursor != null) {
            mDataValid = true;
        } else {
            mDataValid = false;
        }
        
        notifyDataSetChanged();

        return oldCursor;
    }

}
