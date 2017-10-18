package com.jsonse.video;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
    public void toRecordVideo(View view){
        startActivityForResult(new Intent(this,RecorderVideoActivity.class),100);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode==RESULT_OK){
            if (requestCode == 100) {
                Uri uri = data.getParcelableExtra("uri");
                String[] projects = new String[]{MediaStore.Video.Media.DATA,
                        MediaStore.Video.Media.DURATION};
                Cursor cursor = getContentResolver().query(
                        uri, projects, null,
                        null, null);
                int duration = 0;
                String videoPath = null;
                int id = 0;
                long size = 0;
                if (cursor.moveToFirst()) {
                    // path：MediaStore.Audio.Media.DATA
                    videoPath = cursor.getString(cursor
                            .getColumnIndexOrThrow(MediaStore.Video.Media.DATA));
                    // duration：MediaStore.Audio.Media.DURATION
                    duration = cursor
                            .getInt(cursor
                                    .getColumnIndexOrThrow(MediaStore.Video.Media.DURATION));
                }
                if (cursor != null) {
                    cursor.close();
                    cursor = null;
                }
                Intent intent=new Intent(this,EasePlayVideoActivity.class);
                intent.setDataAndType(Uri.fromFile(new File(videoPath)),
                        "video/mp4");
                startActivity(intent);
            }
        }
    }
}
