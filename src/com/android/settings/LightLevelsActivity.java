
package com.android.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.SystemSensorManager;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.IPowerManager;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import com.android.settings.R;

public class LightLevelsActivity extends Activity implements OnClickListener {

    // IDs used by dynamically created widgets
    // Levels textviews 1000-1999
    // Levels buttons 2000-2999
    // Lcd buttons 3000-3999
    // Buttons buttons 4000-4999

    private static final int UPDATE_RATE = 400;
    private static String TAG="LightLevelsActivity";

    private boolean mHasChanges;
    private Button mSave;
    private Button mDefaults;
    private Button mReload;
    private TextView mSensor;
    private TextView mScreen;
    private TextView mButtons;
    private int[] mLevels;
    private int[] mLcdValues;
    private int[] mBtnValues;
    private AlertDialog mDialog;
    private int mEditedId;
    private int mScreenBrightnessMin;
    private int mSensorValue;
    private EditText mEditor;
    private Handler mHandler;
    private int mSensorRange=5000;
    
    // Set to true if the light sensor is enabled.
    private boolean mLightSensorEnabled = true;

    // The sensor manager.
    private SensorManager mSensorManager;

    // The light sensor, or null if not available or needed.
    private Sensor mLightSensor;

    // Light sensor event rate in milliseconds.
    private static final int LIGHT_SENSOR_RATE_MILLIS = 1000;

    // A rate for generating synthetic light sensor events in the case where the light
    // sensor hasn't reported any new data in a while and we need it to update the
    // debounce filter.  We only synthesize light sensor measurements when needed.
    private static final int SYNTHETIC_LIGHT_SENSOR_RATE_MILLIS =
            LIGHT_SENSOR_RATE_MILLIS * 2;

    private final SensorEventListener mLightSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (mLightSensorEnabled) {
                final long time = SystemClock.uptimeMillis();
                final float lux = event.values[0];
                handleLightSensorEvent(time, lux);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Not used.
        }
    };
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.ll_title);
        setContentView(R.layout.lightlevels);
        mHandler = new Handler();
        
        mSensorManager = new SystemSensorManager(mHandler.getLooper());
        mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
                        
        mScreenBrightnessMin=getResources().getInteger(
                com.android.internal.R.integer.config_screenBrightnessSettingMinimum);
                
        mSave = (Button) findViewById(R.id.btn_save);
        mSave.setOnClickListener(this);
        mDefaults = (Button) findViewById(R.id.btn_default);
        mDefaults.setOnClickListener(this);
        mReload = (Button) findViewById(R.id.btn_reload);
        mReload.setOnClickListener(this);
        mSensor = (TextView) findViewById(R.id.ll_tw_lux_value);
        mScreen = (TextView) findViewById(R.id.ll_tw_lcd_value);
        mButtons = (TextView) findViewById(R.id.ll_tw_btn_value);


        mEditor = new EditText(this);
        mEditor.setInputType(InputType.TYPE_CLASS_NUMBER);
        mEditor.setImeOptions(EditorInfo.IME_ACTION_NONE);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setPositiveButton(getString(android.R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialogOk();
                    }
                }).setNegativeButton(android.R.string.cancel,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                }).setView(mEditor);
        mDialog = builder.create();
        mDialog.setOwnerActivity(this);
        mDialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                        | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        loadData(false);
        mHasChanges = false;
        updateButtons();
    }

    @Override
    public void onResume() {
        super.onResume();
        mSensorManager.registerListener(mLightSensorListener, mLightSensor,
                        LIGHT_SENSOR_RATE_MILLIS * 1000, mHandler);    
        mUpdateTask.run();
    }

    @Override
    public void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mUpdateTask);
        mSensorManager.unregisterListener(mLightSensorListener);
    }

    private void handleLightSensorEvent(long time, float lux) {
        mSensorValue=(int)lux;
        Log.d(TAG, "handleLightSensorEvent "+mSensorValue);
    }
    
    @Override
    public void onClick(View v) {
        if (v == mSave) {
            save();
        } else if (v == mDefaults) {
            loadData(true);
            mHasChanges = true;
            updateButtons();
        } else if (v == mReload) {
            loadData(false);
            mHasChanges = false;
            updateButtons();
        } else {
            int id = v.getId();
            mEditedId = id;
            int value = -1;
            int[] range = getValidValueRange();
            if(range == null){
            	return;
            }
            
            int min = range[0];
            int max = range[1];
            if (id >= 2000 && id < 3000) {
                value = mLevels[id - 2000];
            } else if (id >= 3000 && id < 4000) {
                value = mLcdValues[id - 3000];
            } else if (id >= 4000 && id < 5000) {
                value = mBtnValues[id - 4000];
            } else {
                value = -1;
            }

            if (value >= 0) {
                mDialog.setMessage(min + " - " + max);
                mEditor.setText(String.valueOf(value));
                mEditor.selectAll();
                mDialog.show();
            }
        }
    }

    private void updateButtons() {
        mSave.setEnabled(mHasChanges);
    }

	private int[] getValidValueRange() {
        int valLimitHi = android.os.PowerManager.BRIGHTNESS_ON;
   		try {
			if (mEditedId >= 2000 && mEditedId < 3000) {
                int minValue = 0;
                int maxValue = mSensorRange;
                Button minValueView = ((Button) findViewById(mEditedId - 1));
                Button maxValueView = ((Button) findViewById(mEditedId + 1));                
                if(minValueView!=null){
                	minValue = Integer.valueOf(minValueView.getText().toString());
                }
                if(maxValueView!=null){
                	maxValue = Integer.valueOf(maxValueView.getText().toString());
                }
                return new int[] {minValue, maxValue};
            } else if (mEditedId >= 3000 && mEditedId < 4000) {
                int minValue = mScreenBrightnessMin;
                int maxValue = valLimitHi;
                TextView minValueView = ((TextView) findViewById(mEditedId - 1));
                TextView maxValueView = ((TextView) findViewById(mEditedId + 1));
                if(minValueView!=null){
                	minValue = Integer.valueOf(minValueView.getText().toString());
                }
                if(maxValueView!=null){
                	maxValue = Integer.valueOf(maxValueView.getText().toString());
                }
                return new int[] {minValue, maxValue};
            } else if (mEditedId >= 4000 && mEditedId < 5000) {
                int minValue = 0;
                int maxValue = 1;
                return new int[] {minValue, maxValue};
            }
        } catch (NumberFormatException e) {
            // Ignore
        }
        return null;
	}
	
    private void dialogOk() {
        boolean changed = false;
         
        int[] range = getValidValueRange();
        if(range == null){
        	return;
        }

        int min = range[0];
        int max = range[1];

        try {
            int value = Integer.valueOf(mEditor.getText().toString());
            int valLimitHi = android.os.PowerManager.BRIGHTNESS_ON;
			if (mEditedId >= 2000 && mEditedId < 3000) {
                if (value >= min && value <= max) {
                    mLevels[mEditedId - 2000] = value;
                    ((Button) findViewById(mEditedId)).setText(String.valueOf(value));
                    ((TextView) findViewById(mEditedId - 1000)).setText(String.valueOf(value - 1));
                    changed = true;
                }
            } else if (mEditedId >= 3000 && mEditedId < 4000) {
                if (value >= min && value <= max) {
                    mLcdValues[mEditedId - 3000] = value;
                    ((Button) findViewById(mEditedId)).setText(String.valueOf(value));
                    changed = true;
                }
            } else if (mEditedId >= 4000 && mEditedId < 5000) {
                if (value >= min && value <= max) {
                    mBtnValues[mEditedId - 4000] = value;
                    ((Button) findViewById(mEditedId)).setText(String.valueOf(value));
                    changed = true;
                }
            }
        } catch (NumberFormatException e) {
            // Ignore
        }
        if (changed) {
            mHasChanges = true;
            mSave.setEnabled(mHasChanges);
        }
    }

    private void loadData(boolean defaults) {
        if (!defaults) {
            try {
                ContentResolver cr = getContentResolver();
                mLevels = parseIntArray(Settings.System.getString(cr,
                        Settings.System.LIGHT_SENSOR_LEVELS));

                mLcdValues = parseIntArray(Settings.System.getString(cr,
                        Settings.System.LIGHT_SENSOR_LCD_VALUES));

                mBtnValues = parseIntArray(Settings.System.getString(cr,
                        Settings.System.LIGHT_SENSOR_BUTTON_VALUES));

                // Sanity check
                int N = mLevels.length;
                if (N < 1 || mLcdValues.length != (N + 1) || mBtnValues.length != (N + 1)) {
                    throw new Exception("sanity check failed");
                }
            } catch (Exception e) {
                // Use defaults since we can't trust custom values
                defaults = true;
            }
        }

        if (defaults) {
            mLevels = getResources().getIntArray(
                    com.android.internal.R.array.config_autoBrightnessLevels);
            mLcdValues = getResources().getIntArray(
                    com.android.internal.R.array.config_autoBrightnessLcdBacklightValues);
            mBtnValues = getResources().getIntArray(
                    com.android.internal.R.array.config_autoBrightnessButtonBacklightValues);
        }
        createEditor();
    }

    private int[] parseIntArray(String intArray) {
        int[] result;
        if (intArray == null || intArray.length() == 0) {
            result = new int[0];
        } else {
            String[] split = intArray.split(",");
            result = new int[split.length];
            for (int i = 0; i < split.length; i++) {
                result[i] = Integer.parseInt(split[i]);
            }
        }
        return result;
    }

    private void save() {
        // Sanity check
        boolean doSave = true;
        for (int i = 1; i < mLevels.length; i++) {
            if (mLevels[i] <= mLevels[i - 1]) {
                Toast.makeText(this, getString(R.string.ll_bad_levels), Toast.LENGTH_SHORT).show();
                doSave = false;
                break;
            }
        }
        if (doSave) {
            Settings.System.putString(getContentResolver(), Settings.System.LIGHT_SENSOR_LEVELS,
                    intArrayToString(mLevels));
            Settings.System.putString(getContentResolver(),
                    Settings.System.LIGHT_SENSOR_LCD_VALUES, intArrayToString(mLcdValues));
            Settings.System.putString(getContentResolver(),
                    Settings.System.LIGHT_SENSOR_BUTTON_VALUES, intArrayToString(mBtnValues));
            //Settings.System.putInt(getContentResolver(),
            //        Settings.System.LIGHT_SENSOR_CUSTOM, 1);

            long tag = Settings.System.getLong(getContentResolver(),
                    Settings.System.LIGHTS_CHANGED, 0) + 1;
            Settings.System.putLong(getContentResolver(), Settings.System.LIGHTS_CHANGED, tag);

            mHasChanges = false;
            mSave.setEnabled(mHasChanges);
        }
    }

    private String intArrayToString(int[] array) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < array.length - 1; i++) {
            sb.append(array[i]);
            sb.append(",");
        }
        sb.append(array[array.length - 1]);
        return sb.toString();
    }

    private Runnable mUpdateTask = new Runnable() {
        public void run() {

            mScreen.setText("-");
            mSensor.setText("-");
            mButtons.setText("-");

            try {
                IPowerManager power = IPowerManager.Stub.asInterface(ServiceManager.getService("power"));
                if(power.isUsingAutoBrightness()){
                    mSensor.setText(String.valueOf(mSensorValue));
                } else {
                    mSensor.setText("disabled");
                }
                mScreen.setText(String.valueOf(power.getCurrentScreenBrightnessValue()));
                mButtons.setText(String.valueOf(power.getCurrentButtonBrightnessValue()));
            } catch (Exception e) {
                // Display "-" on any error
                mScreen.setText("-");
                mSensor.setText("-");
                mButtons.setText("-");
            }

            mHandler.postDelayed(mUpdateTask, UPDATE_RATE);
        }
    };

    private void createEditor() {
        // Assume at least one defined level (two values)
        TableLayout table = (TableLayout) findViewById(R.id.ll_table_config);

        // Clear all
        while (table.getChildCount() > 1) {
            table.removeViewAt(table.getChildCount() - 1);
        }

        TableRow row;

        // First row
        row = createRow();

        // Lower
        row.addView(createTextView(0, "0"));

        // Upper
        row.addView(createTextView(1000, String.valueOf(mLevels[0] - 1)));

        // Screen
        row.addView(createButton(3000, String.valueOf(mLcdValues[0])));

        // Buttons
        row.addView(createButton(4000, String.valueOf(mBtnValues[0])));

        table.addView(row, table.getChildCount());

        for (int i = 0; i < mLevels.length - 1; i++) {
            row = createRow();

            // Lower
            row.addView(createButton(2000 + i, String.valueOf(mLevels[i])));

            // Upper
            row.addView(createTextView(1000 + i + 1,
                    String.valueOf(Math.max(0, mLevels[i + 1] - 1))));

            // Screen
            row.addView(createButton(3000 + i + 1, String.valueOf(mLcdValues[i + 1])));

            // Buttons
            row.addView(createButton(4000 + i + 1, String.valueOf(mBtnValues[i + 1])));

            table.addView(row, table.getChildCount());
        }

        row = createRow();

        // Lower
        row.addView(createButton(2000 + mLevels.length - 1,
                String.valueOf(mLevels[mLevels.length - 1])));

        // Upper
        row.addView(createTextView((int) 1e10, String.valueOf((char) '\u221e')));

        // Screen
        row.addView(createButton(3000 + mLevels.length, String.valueOf(mLcdValues[mLevels.length])));

        // Buttons
        row.addView(createButton(4000 + mLevels.length, String.valueOf(mBtnValues[mLevels.length])));


        table.addView(row, table.getChildCount());

        table.setColumnStretchable(0, true);
        table.setColumnStretchable(2, true);
        table.setColumnStretchable(3, true);
    }

    private int dp2px(int dp) {
        return (int) getResources().getDisplayMetrics().density * dp;
    }

    private Button createButton(int id, String text) {
        Button btn = new Button(this);
        btn.setId(id);
        btn.setText(text);
        btn.setMinWidth(dp2px(50));
        btn.setMaxWidth(dp2px(120));
        btn.setOnClickListener(this);
        return btn;
    }

    private TextView createTextView(int id, String text) {
        TextView tv = new TextView(this);
        tv.setGravity(Gravity.CENTER);
        tv.setText(text);
        tv.setWidth(dp2px(60));
        tv.setId(id);
        return tv;
    }

    private TableRow createRow() {
        TableRow row = new TableRow(this);
        row.setGravity(Gravity.CENTER);
        return row;
    }
}
