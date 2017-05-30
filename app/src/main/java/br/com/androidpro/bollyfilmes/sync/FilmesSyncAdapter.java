package br.com.androidpro.bollyfilmes.sync;

//25/05/2017

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SyncRequest;
import android.content.SyncResult;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import br.com.androidpro.bollyfilmes.BuildConfig;
import br.com.androidpro.bollyfilmes.FilmeDetalheActivity;
import br.com.androidpro.bollyfilmes.ItemFilme;
import br.com.androidpro.bollyfilmes.JsonUtil;
import br.com.androidpro.bollyfilmes.R;
import br.com.androidpro.bollyfilmes.data.FilmesContract;

public class FilmesSyncAdapter extends AbstractThreadedSyncAdapter {

    public static final int SYNC_INTERVAL = 60 * 720;
    public static final int SYNC_FLEXTIME = SYNC_INTERVAL / 3;
    public static final int NOTIFICATION_FILME_ID = 1001;

    public FilmesSyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
        // https://api.themoviedb.org/3/movie/popular?api_key=qwer08776&language=pt-BR

        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        String ordem = preferences.getString(getContext().getString(R.string.prefs_ordem_key), "popular"); //<---Assim resolveu o problema
        String idioma = preferences.getString(getContext().getString(R.string.prefs_idioma_key), "pt-BR");

        try {
            String urlBase = "https://api.themoviedb.org/3/movie/" + ordem + "?";
            String apiKey = "api_key";
            String language = "language";

            Uri uriApi = Uri.parse(urlBase).buildUpon()
                    .appendQueryParameter(apiKey, BuildConfig.TMDB_API_KEY)
                    .appendQueryParameter(language, idioma)
                    .build();

            URL url = new URL(uriApi.toString());
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.connect();

            InputStream inputStream = urlConnection.getInputStream();
            reader = new BufferedReader(new InputStreamReader(inputStream));

            String linha;
            StringBuffer buffer = new StringBuffer();
            while ((linha = reader.readLine()) != null) {
                buffer.append(linha);
                buffer.append("\n");
            }

            List<ItemFilme> itemFilmes = JsonUtil.fromJsonToList(buffer.toString());
            if (itemFilmes == null) {
                return;
            }

            for (ItemFilme itemFilme : itemFilmes) {
                ContentValues values = new ContentValues();
                values.put(FilmesContract.FilmeEntry._ID, itemFilme.getId());
                values.put(FilmesContract.FilmeEntry.COLUMN_TITULO, itemFilme.getTitulo());
                values.put(FilmesContract.FilmeEntry.COLUMN_DESCRICAO, itemFilme.getDescricao());
                values.put(FilmesContract.FilmeEntry.COLUMN_POSTER_PATH, itemFilme.getPosterPath());
                values.put(FilmesContract.FilmeEntry.COLUMN_CAPA_PATH, itemFilme.getCapaPath());
                values.put(FilmesContract.FilmeEntry.COLUMN_AVALIACAO, itemFilme.getAvaliacao());
                values.put(FilmesContract.FilmeEntry.COLUMN_DATA_LANCAMENTO, itemFilme.getDataLancamento());
                values.put(FilmesContract.FilmeEntry.COLUMN_POPULARIDADE, itemFilme.getPopularidade());

                //Vai atualizar, e devolver o numero de linhas atualizadas como um inteiro.
                int update = getContext().getContentResolver().update(FilmesContract.FilmeEntry.buildUriForFilmes(itemFilme.getId()), values, null, null);
                //Caso o numero de linhas atualizadas seja 0, então é porque está vazio e precisa ser preenchido ou ja existe.
                if (update == 0) {
                    getContext().getContentResolver().insert(FilmesContract.FilmeEntry.CONTENT_URI, values);
                    notify(itemFilme);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    //30/05/17
    public void notify(ItemFilme itemFilme){
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext()).
                setSmallIcon(R.mipmap.ic_launcher).
                setContentTitle(itemFilme.getTitulo()).
                setContentText(itemFilme.getDescricao());

        Intent intent = new Intent(getContext(), FilmeDetalheActivity.class);
        Uri uri = FilmesContract.FilmeEntry.buildUriForFilmes(itemFilme.getId());
        intent.setData(uri);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(getContext());
        stackBuilder.addNextIntent(intent);
        PendingIntent pendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(pendingIntent);

        NotificationManager notificationManager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_FILME_ID, builder.build());
    }

    public static void configurePeriodicSync(Context context, int syncInterval, int syncFlextime) {
        Account account = getSyncAccount(context);
        String authority = context.getString(R.string.content_authority);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            //So está disponivel na versão KitKat em diate.
            SyncRequest syncRequest = new SyncRequest.Builder().
                    syncPeriodic(syncInterval, syncFlextime).
                    setSyncAdapter(account, authority).
                    setExtras(new Bundle()).build();
            ContentResolver.requestSync(syncRequest);
        } else {
            //Caso não seja KitKat, não poderemos oferecer um tempo mais flexivel.
            ContentResolver.addPeriodicSync(account, authority, new Bundle(), syncInterval);
        }
    }

    //Inicializará a sicronia de dados
    public static void syncImmediately(Context context) {
        Bundle bundle = new Bundle();
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);//<-- Significa sicronizar os dados rapidamente.
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);//<-- Ignora qualquer configuração de sicronia de dados, pois faremos manualmente.
        ContentResolver.requestSync(getSyncAccount(context), context.getString(R.string.content_authority), bundle);
    }

    //Criará a conta FAKE
    public static Account getSyncAccount(Context context) {
        AccountManager accountManager = (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);
        Account account = new Account(context.getString(R.string.app_name), context.getString(R.string.sync_account_type));

        if (accountManager.getPassword(account) == null) {
            if (!accountManager.addAccountExplicitly(account, "", null)) {
                return null;
            }

            onAccountCreated(account, context);

        }
        return account;
    }

    private static void onAccountCreated(Account account, Context context) {
        FilmesSyncAdapter.configurePeriodicSync(context, SYNC_INTERVAL, SYNC_FLEXTIME);
        ContentResolver.setSyncAutomatically(account, context.getString(R.string.content_authority), true);
        syncImmediately(context);
    }

    public static void initializeSyncAdapter(Context context) {
        getSyncAccount(context);
    }
}
