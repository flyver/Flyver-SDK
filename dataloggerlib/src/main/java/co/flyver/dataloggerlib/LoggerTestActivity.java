package co.flyver.dataloggerlib;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;


public class LoggerTestActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logger_test);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_logger_test, menu);
        return super.onCreateOptionsMenu(menu);
        //return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        Intent intent;

        int id = item.getItemId();

        if (id == R.id.action_logger) {
            intent = new Intent(this, LoggerTestActivity.class);
            this.startActivity(intent);
        } else if (id == R.id.action_loggerlocaldata) {
            intent = new Intent(this, LocalDataActivity.class);
            this.startActivity(intent);
        } else if (id == R.id.action_settings) {
            intent = new Intent(this, SettingsActivity.class);
            this.startActivity(intent);
        } else {
            return super.onOptionsItemSelected(item);
        }

        return true;
    }

    public void button_ClickMe_onClick(View v)
    {
        LoggerService logger = new LoggerService(this.getApplicationContext());
        logger.Start();

        String eType = "TestType", eTags = "TestTags", eData = "TestData";
        String tmp = "";

        EditText et_Type = (EditText) findViewById(R.id.editText_EventType);
        EditText et_Tags = (EditText) findViewById(R.id.editText_EventTags);
        EditText et_Data = (EditText) findViewById(R.id.editText_EventData);

        tmp = et_Type.getText().toString();
        if (tmp != null && tmp != "")
            eType = tmp;

        tmp = et_Tags.getText().toString();
        if (tmp != null && tmp != "")
            eTags = tmp;

        tmp = et_Data.getText().toString();
        if (tmp != null && tmp != "")
            eData = tmp;

        logger.LogData(eType, eTags, eData);
        logger.Stop();
    }
}
