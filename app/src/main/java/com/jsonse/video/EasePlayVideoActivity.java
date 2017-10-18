package com.jsonse.video;

import android.app.Activity;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.VideoView;

/**
 * Created by WangYingDi on 2016/8/31.
 */
public class EasePlayVideoActivity extends Activity {
    private View mExitText;

    @Override
    protected void onCreate(Bundle arg0) {
        super.onCreate(arg0);
        setContentView(R.layout.ease_activity_play_video);
        Uri data = getIntent().getData();
        final VideoView videoView = (VideoView) this.findViewById(R.id.video_view);
        mExitText = findViewById(R.id.exit_text);
//        videoView.setMediaController(new MediaController(this));
        videoView.setVideoURI(data);
        videoView.start();
        videoView.requestFocus();
        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                mExitText.setVisibility(View.VISIBLE);
                videoView.start();
            }
        });
        findViewById(R.id.container).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }
}
