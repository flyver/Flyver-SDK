package co.flyver.dataloggerlib;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;


public class LocalDataActivity extends Activity {

    TextView tvView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        StringBuilder sb = new StringBuilder();
        SimpleEvent se;

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_log_local_data);

        tvView = (TextView) findViewById(R.id.textView);

        try {
            LoggerService logger = new LoggerService(this.getApplicationContext());
            logger.LocalReadFromStart();

            for (int iCount = 0; iCount < 100; iCount++) {
                se = logger.LocalReadLogEntry();

                if (se == null) {
                    iCount = 100;
                    continue;
                }

                sb.append("Type: " + se.EventType + "\n" +
                        "Tags: " + se.EventTags + "\n" +
                        "Data: " + se.EventData + "\n" +
                        "Timestamp: " + se.EventTimeStamp + "\n");
            }

            tvView.setText(sb.toString());
            logger.LocalGoToEnd();
            logger.Dispose();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_logger_test, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
