package com.example.bluetoothfound;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
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
 * @author gus
 * @version 1.0.0
 */
public class BluetoothFoundActivity extends Activity {
	
	//enable bluetooth request code
	private static final int REQUEST_ENABLE_BT = 2;
	
	private Button connectBtn;
	private Button alarmSettingBtn;
	private BluetoothAdapter mBluetoothAdapter;
	private TextView foundLogTextView;
	//ringtone player
	private MediaPlayer player;
	private SharedPreferences prefs;
	private Settings settings;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initSettings();
		setContentView(R.layout.activity_bluetooth_found);
		connectBtn = (Button)findViewById(R.id.connectBtn);
		alarmSettingBtn = (Button)findViewById(R.id.alarmSetting);
		
		foundLogTextView = (TextView)findViewById(R.id.foundLog);
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		
		// If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        player = new MediaPlayer();
        
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        BluetoothFoundActivity.this.registerReceiver(mReceiver, filter);
        
        /**
         * connect to device listener
         */
		connectBtn.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				String text = connectBtn.getText().toString();
				String deviceConnectText = getResources().getString(R.string.DeviceConnect);
				String deviceDisConnectText = getResources().getString(R.string.DeviceDisConnect);
				if (text.equalsIgnoreCase(deviceConnectText)) {
					if (mBluetoothAdapter.isDiscovering()) {
			            mBluetoothAdapter.cancelDiscovery();
			        }
			        // Request discover from BluetoothAdapter
			        mBluetoothAdapter.startDiscovery();
					connectBtn.setText(deviceDisConnectText);
					
				} else {
					connectBtn.setText(deviceConnectText);
				}
			}
		});
		
		/**
		 * alarm ringtone listener
		 */
		alarmSettingBtn.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(BluetoothFoundActivity.this, AlarmSettingActivity.class);
				startActivityForResult(intent, 3);
				//doPickRingtone();
			}
		});
		
		
		Button stopPlayBtn = (Button)findViewById(R.id.stopPlay);
		stopPlayBtn.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				stopPlayRingtone();
			}
		});
	}
	
	@Override
    public void onStart() {
        super.onStart();

        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        // Otherwise, setup the chat session
        }
    }
	
	@Override
    protected void onDestroy() {
        super.onDestroy();
        // Make sure we're not doing discovery anymore
        if (mBluetoothAdapter != null) {
        	mBluetoothAdapter.cancelDiscovery();
        }
        if (player != null && player.isPlaying()) {
        	player.stop();
        }
        // Unregister broadcast listeners
        unregisterReceiver(mReceiver);
    }
	
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.bluetooth_found, menu);
		return true;
	}
	
	
	
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
//		if (resultCode != Activity.RESULT_OK) {
//			return;
//		}

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

	
	
	/**
	 * play setting ringtone
	 */
	private void playRingtone() {
		try {
			SharedPreferences sharedPreferences = getSharedPreferences(AlarmSettingActivity.SETTING, Context.MODE_PRIVATE);
			String pickRingtoneUrl = sharedPreferences.getString("ringtoneUri", "");
			Uri pickUri = Uri.parse(pickRingtoneUrl);
			player.setDataSource(this, pickUri);
		} catch (Exception e) {
			e.printStackTrace();
		}
		final AudioManager audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		if (audioManager.getStreamVolume(AudioManager.STREAM_ALARM) != 0) {
			player.setAudioStreamType(AudioManager.STREAM_ALARM);
			
			//player.setLooping(true);
			try {
				player.prepare();
			} catch (Exception e) {
				e.printStackTrace();
			}
			player.start();
		}
	}
	/**
	 * stop play ringtone
	 */
	private void stopPlayRingtone() {
		if (player != null && player.isPlaying()) {
			player.stop();
		}
	}
	
	private void initSettings() {
		prefs = getSharedPreferences(AlarmSettingActivity.SETTING, Context.MODE_PRIVATE);
		settings = new Settings();
		settings.setDuration(prefs.getInt(AlarmSettingActivity.SETTING_DURATION, 0));
		String ringtoneUri = prefs.getString(AlarmSettingActivity.SETTING_RINGTONE_URI, "");
		if (ringtoneUri == "") {
			Toast.makeText(this, "请设置提醒铃声", Toast.LENGTH_LONG).show();
		}
		settings.setRingtone(ringtoneUri);
		
	}
	
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                //System.out.println("found devices:" + device.getName());
                short rssi = intent.getExtras().getShort( BluetoothDevice.EXTRA_RSSI);
                
                foundLogTextView.setText("found devices:" + device.getName() + ", rssi:" + rssi);
                //play ringtone
                playRingtone();
                
                
                // If it's already paired, skip it, because it's been listed already
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                  // System.out.println("found devices:" + device.getName());
                }
            // When discovery is finished, change the Activity title
            }
        }
    };
    
    
    
}
