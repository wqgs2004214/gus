package com.square;


import java.util.Observable;
import java.util.Observer;
import android.app.Activity;
import android.media.AudioRecord;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class SquareActivity extends Activity {
	private MagstripperModel model;
	private UIUpdateHandler uiUpdateHandler;
	private TextView decodedStringView;
	private TextView strippedBinaryView;
	private MicIn main;
	private Button startBtn;
	private Button stopBtn;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		startBtn = (Button)findViewById(R.id.startbtn);
		stopBtn = (Button)findViewById(R.id.stopbtn);
		stopBtn.setEnabled(false);
		decodedStringView = (TextView)findViewById(R.id.bytes);
		strippedBinaryView = (TextView)findViewById(R.id.bits);
		model = new MagstripperModel();
		MicListener ml = new MicListener();
		startBtn.setOnClickListener(ml);
		stopBtn.setOnClickListener(ml);
		uiUpdateHandler = new UIUpdateHandler();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (!main.isInterrupted()) {
			main.interrupt();
		}
		
		if (main.getMicIn().getState() == AudioRecord.STATE_INITIALIZED) {
			main.getMicIn().stop();
		}
		
	}



	/**
	 * Listener called with the mic status button is clicked, and when the zero level or noise thresholds are changed
	 */
	private class MicListener implements OnClickListener, Observer{
		
		MicListener(){
			main = new MicIn(model);
			main.setPriority(Thread.MAX_PRIORITY);
			main.start();
			model.addObserver(this);
			
		}
		
		/**
		 * Called when the mic button is clicked
		 * @param
		 */
		@Override
		public void onClick(View v) {
			if(main.getMicIn() == null){//mic could not be initialized
				
			}else if(v == stopBtn){//stop listening
				stopBtn.setEnabled(false);
				startBtn.setEnabled(true);
				stopListening();
			}else if(v == startBtn) {//start listening
				stopBtn.setEnabled(true);
				startBtn.setEnabled(false);
				startListening();
			}
		}

		
		public void startListening(){
			model.setListening(true);
			main.suspendListening(false);
		}
		
		public void stopListening(){
			model.setListening(false);
			main.suspendListening(true);
		}
		
		/**
		 * Listener called with the zero level or noise threshold is 
		 */
		public void update(Observable obs, Object obj){
			if(obj == "zerolevel"){
				if(!model.getZeroAutomatic()){ //zerolevel statically set
					main.setZeroLevel((short)model.getZeroLevel());
				}
			}else if(obj == "thresholds"){
				main.setThresholds(); //system log is updated in micin.java
			} else if(obj instanceof Swipe) {
				Message uiUpdateMsg = new Message();
				uiUpdateMsg.obj = obj;
				uiUpdateHandler.sendMessage(uiUpdateMsg);
			}
		}

		
	}
	
	
	private class UIUpdateHandler extends Handler {

		@Override
		public void handleMessage(Message msg) {
			Swipe swipe = (Swipe)msg.obj;
			decodedStringView.setText(swipe.getDecodedString());
			strippedBinaryView.setText(swipe.getStrippedBinary());
		}
		
	}
	
	

}