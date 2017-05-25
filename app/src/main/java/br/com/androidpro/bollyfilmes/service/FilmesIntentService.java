package br.com.androidpro.bollyfilmes.service;

//22 de maio de 2017

import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.Nullable;

public class FilmesIntentService extends IntentService{

    public FilmesIntentService(){
        super("FilmesIntentService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {

    }

    public class FilmesReceiver extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {
            Intent intentService = new Intent(context, FilmesIntentService.class);
            context.startService(intentService);
        }
    }

}
