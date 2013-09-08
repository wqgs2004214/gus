package com.example.bluetoothfound;

import android.os.Bundle;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * bluetooth found activity
 * 
 * 修改日期:2013年9月5日
 * 
 * @author gus
 * @version 1.0.0
 */
public class BluetoothFoundActivity extends Activity {
	public static final String ACTION_UI_UPDATE = "uiUpdate";
	//extras value
	public static final String UPDATE_STRING = "updateString";
	// enable bluetooth request code
	private static final int REQUEST_ENABLE_BT = 2;
	private Button connectBtn;
	private Button alarmSettingBtn;
	private TextView foundLogTextView;
	private SharedPreferences prefs;
	private Settings settings;
	private BluetoothAdapter mBluetoothAdapter;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initSettings();
		setContentView(R.layout.activity_bluetooth_found);
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		// If the adapter is null, then Bluetooth is not supported
		if (mBluetoothAdapter == null) {
			Toast.makeText(this, "Bluetooth is not available",
					Toast.LENGTH_LONG).show();
			finish();
			return;
		}
		
		connectBtn = (Button) findViewById(R.id.connectBtn);
		alarmSettingBtn = (Button) findViewById(R.id.alarmSetting);
		foundLogTextView = (TextView) findViewById(R.id.foundLog);

		/**
		 * connect to device listener
		 */
		connectBtn.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				String text = connectBtn.getText().toString();
				String deviceConnectText = getResources().getString(
						R.string.DeviceConnect);
				String deviceDisConnectText = getResources().getString(
						R.string.DeviceDisConnect);
				if (text.equalsIgnoreCase(deviceConnectText)) {
					connectBtn.setText(deviceDisConnectText);
					foundLogTextView.setText("设备搜索中...");
					startService();
				} else {
					connectBtn.setText(deviceConnectText);
					foundLogTextView.setText("搜索服务已停止..");
					Intent intent = new Intent();
					intent.setAction(BluetoothService.ACTION_STOP_PLAY_RINGTONE);
					sendBroadcast(intent);
				}
			}
		});

		/**
		 * alarm ringtone listener
		 */
		alarmSettingBtn.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent intent = new Intent(BluetoothFoundActivity.this,
						AlarmSettingActivity.class);
				startActivityForResult(intent, 3);
			}
		});

		//register ui update broadcast receiver
		IntentFilter filter = new IntentFilter();
		filter.addAction(ACTION_UI_UPDATE);
		registerReceiver(uiUpdateReceiver, filter);
	}

	@Override
	public void onStart() {
		super.onStart();
		// If BT is not on, request that it be enabled.
		// setupChat() will then be called during onActivityResult
		if (!mBluetoothAdapter.isEnabled()) {
			Intent enableIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
			// Otherwise, setup the chat session
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceiver(uiUpdateReceiver);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.bluetooth_found, menu);
		return true;
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case REQUEST_ENABLE_BT:
			if (resultCode == Activity.RESULT_OK) {
				// Bluetooth is now enabled, so set up a chat session
				Toast.makeText(this, "蓝牙启动成功", Toast.LENGTH_LONG).show();
			} else {
				Toast.makeText(this, "蓝牙无法启动", Toast.LENGTH_LONG).show();
				finish();
			}
			break;
		}
	}

	private void initSettings() {
		prefs = getSharedPreferences(AlarmSettingActivity.SETTING,
				Context.MODE_PRIVATE);
		settings = new Settings();
		// default ringtone duration time 20ms
		settings.setDuration(prefs.getInt(
				AlarmSettingActivity.SETTING_DURATION, 20 * 1000));
		String ringtoneUri = prefs.getString(
				AlarmSettingActivity.SETTING_RINGTONE_URI, "");
		if (ringtoneUri == "") {
			Toast.makeText(this, "请设置提醒铃声", Toast.LENGTH_LONG).show();
		}
		settings.setRingtone(ringtoneUri);

	}
	/**
	 * start discovery bluetooth device service.
	 */
	private void startService() {
		Intent service = new Intent("com.example.bluetoothfound.BluetoothService");
		startService(service);
	}
	/**
	 * stop discovery bluetooth device service.
	 */
	private void stopService() {
		Intent service = new Intent("com.example.bluetoothfound.BluetoothService");
		stopService(service);
	}
	
	/**
	 * update ui
	 */
	private final BroadcastReceiver uiUpdateReceiver = new BroadcastReceiver() {
		
		@Override
		public void onReceive(Context context, Intent intent) {
			Bundle b = intent.getExtras();
			String updateText = b.getString(UPDATE_STRING);
			foundLogTextView.setText(updateText);
		}
	};

}
