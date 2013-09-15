package com.square;


import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class SquareActivity extends Activity {
	private UpdateBytesHandler updateBytesHandler;
	private UpdateBitsHandler updateBitsHandler;
	private TextView decodedStringView;
	private TextView strippedBinaryView;
	private Button startBtn;
	private Button stopBtn;
	private MagRead read;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		startBtn = (Button)findViewById(R.id.startbtn);
		stopBtn = (Button)findViewById(R.id.stopbtn);
		stopBtn.setEnabled(false);
		decodedStringView = (TextView)findViewById(R.id.bytes);
		strippedBinaryView = (TextView)findViewById(R.id.bits);
		
		read = new MagRead();
		read.addListener(new MagReadListener() {
			
			@Override
			public void updateBytes(String bytes) {
				Message msg = new Message();
				msg.obj = bytes;
				updateBytesHandler.sendMessage(msg);
			}
			
			@Override
			public void updateBits(String bits) {
				Message msg = new Message();
				msg.obj = bits;
				updateBitsHandler.sendMessage(msg);
				
			}
		});
		MicListener ml = new MicListener();
		startBtn.setOnClickListener(ml);
		stopBtn.setOnClickListener(ml);
		updateBytesHandler = new UpdateBytesHandler();
		updateBitsHandler = new UpdateBitsHandler();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		read.release();
	}



	/**
	 * Listener called with the mic status button is clicked, and when the zero level or noise thresholds are changed
	 */
	private class MicListener implements OnClickListener{
		
		/**
		 * Called when the mic button is clicked
		 * @param
		 */
		@Override
		public void onClick(View v) {
			if(v == stopBtn){//stop listening
				stopBtn.setEnabled(false);
				startBtn.setEnabled(true);
				read.stop();
			}else if(v == startBtn) {//start listening
				stopBtn.setEnabled(true);
				startBtn.setEnabled(false);
				read.start();
			}
		}
		
	}
	
	
	private class UpdateBytesHandler extends Handler {

		@Override
		public void handleMessage(Message msg) {
			String bytes = (String)msg.obj;
			decodedStringView.setText(bytes);
		}
		
	}
	
	private class UpdateBitsHandler extends Handler {

		@Override
		public void handleMessage(Message msg) {
			String bits = (String)msg.obj;
			strippedBinaryView.setText(bits);
		}
		
	}

	
	

}