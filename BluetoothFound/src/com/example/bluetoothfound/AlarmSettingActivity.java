/*
 * Copyright (C) 2012 Shenzhen SMT online Co., Ltd.
 * 
 * 项目名称:SMT移动信息化解决方案系统
 * 创建日期:2013年9月5日
 */
package com.example.bluetoothfound;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.Spinner;
import android.widget.Toast;

/**
 * alarm setting activity
 * 修改日期:2013年9月5日
 * @author gus
 * @version 1.0.0
 */
public class AlarmSettingActivity extends Activity {
	public static final int RINGTONE_ENABLE = 1;
	public static final int VIBRATE_ENABLE = 2;
	public static final int VIBRATE_RINGTONE_ENABLE = 3;
	//pick ringtone request code
	private static final int REQUEST_CODE_PICK_RINGTONE = 1;
	public static final String SETTING = "setting";
	public static final String SETTING_DURATION = "duration";
	public static final String SETTING_RINGTONE_URI = "ringtoneUri";
	private String mRingtoneUri = null;
	private SharedPreferences prefs;
	private Editor editor;
	private int[] duration = { 10, 20, 30, 60, 120, 300};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		prefs = getSharedPreferences(SETTING, Context.MODE_PRIVATE);
		editor = prefs.edit();
		setContentView(R.layout.alram_setting);
		
		Spinner sp = (Spinner)findViewById(R.id.duration);
		int durationValue = prefs.getInt(SETTING_DURATION, 0);
		//init cache duration value
		for (int index = 0; index < duration.length; index++) {
			if (durationValue == duration[index]) {
				sp.setSelection(index);
			}
		}
		sp.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> arg0, View arg1,
					int position, long arg3) {
					editor.putInt("duration", duration[position]);
					editor.commit();
			}
			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
			}
				
		});
		
		Button pickRingtoneBtn = (Button)findViewById(R.id.pickringtone);
		pickRingtoneBtn.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				doPickRingtone();
			}
		});
		
		//init cache ringer mode
		int ringerMode = prefs.getInt("ringerMode", RINGTONE_ENABLE);
		if (ringerMode == RINGTONE_ENABLE) {
			((RadioButton)findViewById(R.id.ringer_mode)).setChecked(true);
		} else if (ringerMode == VIBRATE_ENABLE) {
			((RadioButton)findViewById(R.id.vibrate_mode)).setChecked(true);
		} else if (ringerMode == VIBRATE_RINGTONE_ENABLE) {
			((RadioButton)findViewById(R.id.ringer_vibrate_mode)).setChecked(true);
		}
		RadioGroup rg = (RadioGroup)findViewById(R.id.alram_mode);
		rg.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				int ringerMode = AudioManager.RINGER_MODE_NORMAL;
				//set vibrate mode
				if(checkedId == R.id.vibrate_mode) {
					ringerMode = VIBRATE_ENABLE;
				} else if(checkedId == R.id.ringer_mode) {
					ringerMode = RINGTONE_ENABLE;
				} else if(checkedId == R.id.ringer_vibrate_mode) {
					ringerMode = VIBRATE_RINGTONE_ENABLE;
				}
				editor.putInt("ringerMode", ringerMode);
				editor.commit();
			}
		});
		
	}
	
	private void doPickRingtone() {
		Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
		// Allow user to pick 'Default'
		intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
		// Show only ringtones
		intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
				RingtoneManager.TYPE_RINGTONE);
		// Don't show 'Silent'
		intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);

		Uri ringtoneUri;
		if (mRingtoneUri != null) {
			ringtoneUri = Uri.parse(mRingtoneUri);
		} else {
			// Otherwise pick default ringtone Uri so that something is
			// selected.
			ringtoneUri = RingtoneManager
					.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
		}

		// Put checkmark next to the current ringtone for this contact
		intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
				ringtoneUri);

		// Launch!
		// startActivityForResult(intent, REQUEST_CODE_PICK_RINGTONE);
		startActivityForResult(intent, REQUEST_CODE_PICK_RINGTONE);
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {

		switch (requestCode) {
		case REQUEST_CODE_PICK_RINGTONE: {
			if (resultCode == Activity.RESULT_OK) {
				Uri pickedUri = data
						.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
				handleRingtonePicked(pickedUri);
			} else {
				//Toast.makeText(this, "铃声设置失败", Toast.LENGTH_LONG).show();
			}
			break;
		}
		}
	}
	
	private void handleRingtonePicked(Uri pickedUri) {
		if (pickedUri == null || RingtoneManager.isDefault(pickedUri)) {
			mRingtoneUri = null;
		} else {
			mRingtoneUri = pickedUri.toString();
		}
		// get ringtone name and you can save mRingtoneUri for database.
		if (mRingtoneUri != null) {
			SharedPreferences sharedPreferences = getSharedPreferences(SETTING, Context.MODE_PRIVATE);
			Editor editor = sharedPreferences.edit();
			editor.putString(SETTING_RINGTONE_URI, mRingtoneUri);
			editor.commit();
		} else {
			Toast.makeText(this, "铃声设置失败", Toast.LENGTH_LONG).show();
		}
	}
	
	
	
	
}
