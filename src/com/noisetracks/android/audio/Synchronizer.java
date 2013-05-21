package com.noisetracks.android.audio;

import java.util.ArrayList;

public class Synchronizer {
	
	public Synchronizer() {
		m_handles=new ArrayList<Handle>();
		m_lock=new Object();
	}
	
	public Handle register() {
		Handle handle=new Handle(this);
		synchronized (m_lock) {
			m_handles.add(handle);
		}
		return handle;
	}
	
	/////////////////////////////////// Handle
	
	public static class Handle {
		public void unregister() {
			if (synchronizer!=null) {
				synchronizer.unregister(this);
				synchronizer=null;
			}
		}
		public boolean synchronize() {
			return (synchronizer!=null)?
				synchronizer.synchronize(this):
				true;
		}
		
		private Handle(Synchronizer synchronizer) {
			this.synchronizer=synchronizer;
		}
		private boolean synchronizing;
		private Synchronizer synchronizer;
	}
	
	///////////////////////////////////////////// implementation
	
	private void unregister(Handle handle) {
		synchronized (m_lock) {
			m_handles.remove(handle);
			if (isAllSynchronized()) {
				m_lock.notifyAll();
			}
		}
	}
	
	private boolean synchronize(Handle handle) {
		boolean ok=true;
		synchronized (m_lock) {
			handle.synchronizing=true;
			if (isAllSynchronized()) {
				m_lock.notifyAll();
			} else {
				ok=Simply.waitNoLock(m_lock);
			}
			handle.synchronizing=false;
		}
		return ok;
	}
	
	private boolean isAllSynchronized() {
		for (int i=0,e=m_handles.size();i!=e;++i) {
			if (!m_handles.get(i).synchronizing) {
				return false;
			}
		}
		return true;			
	}
	
	/////////////////////////////////// data
	
	private Object m_lock;
	private ArrayList<Handle> m_handles;
}
