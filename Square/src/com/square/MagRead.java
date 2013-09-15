package com.square;

import java.util.Observable;
import java.util.Observer;

import android.media.AudioRecord;

/**
 * magstripper reader
 * @author gus
 *
 */
public class MagRead implements Observer{
	private MagReadListener mListener;
	private MicIn main;
	private MagstripperModel model;
	public MagRead() {
		model = new MagstripperModel();
		main = new MicIn(model);
		main.setPriority(Thread.MAX_PRIORITY);
		main.start();
		model.addObserver(this);
	}
	
	/**
	 * stop magstripper service.
	 */
	public void start() {
		model.setListening(true);
		main.suspendListening(false);
	}
	/**
	 * start magstripper read service.
	 */
	public void stop() {
		model.setListening(false);
		main.suspendListening(true);
	}
	/**
	 * add magstripper read listener.
	 * @param listener 
	 */
	public void addListener(MagReadListener listener) {
		mListener = listener;
	}
	/**
	 * if exit service you must call release method.
	 * 
	 */
	public void release() {
		if (!main.isInterrupted()) {
			main.interrupt();
		}
		
		if (main.getMicIn().getState() == AudioRecord.STATE_INITIALIZED) {
			main.getMicIn().release();
		}
	}

	@Override
	public void update(Observable observable, Object obj) {
		if(obj == "zerolevel"){
			if(!model.getZeroAutomatic()){ //zerolevel statically set
				main.setZeroLevel((short)model.getZeroLevel());
			}
		}else if(obj == "thresholds"){
			main.setThresholds(); //system log is updated in micin.java
		} else if(obj instanceof Swipe) {
			Swipe swipe = (Swipe)obj;
			mListener.updateBytes(swipe.getDecodedString());
			mListener.updateBits(swipe.getStrippedBinary());
		}
	}
	
	
	
}
