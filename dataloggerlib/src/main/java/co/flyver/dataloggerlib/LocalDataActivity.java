package co.flyver.dataloggerlib;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;


public class LocalDataActivity extends Activity {

    TextView tvView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_log_local_data);

        tvView = (TextView) findViewById(R.id.textView);
        tvView.setText("laskdfjasldkfjaslkdf\n" +
                       "laskdfjasldkfjaslkdf\n" +
                        "laskdfjasldkfjaslkdf\n" +
                        "laskdfjasldkfjaslkdf\n" +
                        "laskdfjasldkfjaslkdf\n" +
                        "laskdfjasldkfjaslkdf\n" +
                        "laskdfjasldkfjaslkdf\n" +
                        "laskdfjasldkfjaslkdf\n" +
                        "laskdfjasldkfjaslkdf\n" +
                        "laskdfjasldkfjaslkdf\n" +
                        "laskdfjasldkfjaslkdf\n" +
                        "laskdfjasldkfjaslkdf\n" +
                        "laskdfjasldkfjaslkdf\n" +
                        "laskdfjasldkfjaslkdf\n" +
                        "laskdfjasldkfjaslkdf\n" +
                        "laskdfjasldkfjaslkdf\n" +
                        "laskdfjasldkfjaslkdf\n" +
                        "laskdfjasldkfjaslkdf\n" +
                        "laskdfjasldkfjaslkdf\n" +
                        "laskdfjasldkfjaslkdf\n" +
                        "laskdfjasldkfjaslkdf\n" +
                        "laskdfjasldkfjaslkdf\n" +
                        "laskdfjasldkfjaslkdf\n" +
                        "laskdfjasldkfjaslkdf\n" +
                        "laskdfjasldkfjaslkdf\n" +
                        "laskdfjasldkfjaslkdf\n" +
                        "laskdfjasldkfjaslkdf\n" +
                        "laskdfjasldkfjaslkdf\n" +
                        "laskdfjasldkfjaslkdf\n" +
                        "laskdfjasldkfjaslkdf\n" +
                        "laskdfjasldkfjaslkdf\n" +
                        "laskdfjasldkfjaslkdf\n" +
                        "laskdfjasldkfjaslkdf\n" +
                        "laskdfjasldkfjaslkdf\n");
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
