package org.willemsens.player.view.main;

import android.Manifest;
import android.app.ActivityManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;
import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import org.willemsens.player.R;
import org.willemsens.player.fetchers.AlbumInfoFetcherService;
import org.willemsens.player.fetchers.ArtistInfoFetcherService;
import org.willemsens.player.filescanning.FileScannerService;
import org.willemsens.player.filescanning.Mp3ScanningService;
import org.willemsens.player.musiclibrary.MusicLibraryBroadcastBuilder;
import org.willemsens.player.persistence.AppDatabase;
import org.willemsens.player.persistence.MusicDao;
import org.willemsens.player.playback.PlayBackIntentBuilder;
import org.willemsens.player.playback.PlayStatus;
import org.willemsens.player.view.main.album.AlbumFragment;
import org.willemsens.player.view.main.music.MusicFragment;
import org.willemsens.player.view.main.music.SubFragmentType;
import org.willemsens.player.view.main.music.albums.OnAlbumClickedListener;
import org.willemsens.player.view.main.music.artists.OnArtistClickedListener;
import org.willemsens.player.view.main.music.nowplaying.NowPlayingFragment;
import org.willemsens.player.view.main.music.songs.OnSongClickedListener;
import org.willemsens.player.view.main.settings.OnSettingsFragmentListener;
import org.willemsens.player.view.main.settings.SettingsFragment;

import static org.willemsens.player.musiclibrary.MusicLibraryBroadcastType.MLBT_ALBUMS_DELETED;
import static org.willemsens.player.musiclibrary.MusicLibraryBroadcastType.MLBT_ARTISTS_DELETED;
import static org.willemsens.player.musiclibrary.MusicLibraryBroadcastType.MLBT_SONGS_DELETED;
import static org.willemsens.player.playback.PlayBackBroadcastType.PBBT_PLAYER_STATUS_UPDATE;
import static org.willemsens.player.playback.PlayStatus.STOPPED;
import static org.willemsens.player.playback.PlayerCommand.PAUSE;
import static org.willemsens.player.playback.PlayerCommand.PLAY;

public class MainActivity extends AppCompatActivity
        implements OnSongClickedListener,
        OnArtistClickedListener,
        OnAlbumClickedListener,
        AlbumFragment.OnPlayAlbumListener,
        OnSettingsFragmentListener,
        NavigationView.OnNavigationItemSelectedListener {
    private static final int PERMISSION_REQUEST_CODE_READ_EXTERNAL_STORAGE = 1;
    private static final int PERMISSION_REQUEST_CODE_INTERNET = 2;

    @BindView(R.id.main_toolbar)
    Toolbar toolbar;

    @BindView(R.id.drawer_layout)
    DrawerLayout drawer;

    @BindView(R.id.nav_view)
    NavigationView navigationView;

    private Integer currentMenuItem;
    private MusicDao musicDao;
    private PlayBackStatusReceiver playBackStatusReceiver;
    private HeadsetReceiver headsetReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        this.musicDao = AppDatabase.getAppDatabase(this).musicDao();

        handlePermission(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                PERMISSION_REQUEST_CODE_READ_EXTERNAL_STORAGE,
                getString(R.string.read_external_storage_required),
                this::setupAfterPermissionReadExternalStorage);
        handlePermission(
                Manifest.permission.INTERNET,
                PERMISSION_REQUEST_CODE_INTERNET,
                getString(R.string.internet_required),
                this::setupAfterPermissionInternet);

        setupActionBarAndDrawer();
        if (savedInstanceState == null) {
            setMusicFragment(false);
        }

        new PlayBackIntentBuilder(this)
                .setup()
                .buildAndSubmit();
    }

    private void handlePermission(@NonNull String permission, int requestCode, String deniedUserMessage, Runnable runnable) {
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            runnable.run();
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                Toast.makeText(this, deniedUserMessage, Toast.LENGTH_LONG).show();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{permission}, requestCode);
            }
        }
    }

    private void setupAfterPermissionReadExternalStorage() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPreferences.getBoolean(getString(R.string.key_first_app_execution), true)) {
            this.musicDao.initDefaultMusicDirectory();

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(getString(R.string.key_first_app_execution), false);
            editor.apply();
        }

        startService(new Intent(this, FileScannerService.class));
        startService(new Intent(this, Mp3ScanningService.class));
    }

    private void setupAfterPermissionInternet() {
        Intent intent = new Intent(this, AlbumInfoFetcherService.class);
        startService(intent);

        intent = new Intent(this, ArtistInfoFetcherService.class);
        startService(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_CODE_READ_EXTERNAL_STORAGE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    setupAfterPermissionReadExternalStorage();
                } else {
                    Toast.makeText(this, getString(R.string.read_external_storage_required), Toast.LENGTH_LONG).show();
                }
                break;
            case PERMISSION_REQUEST_CODE_INTERNET:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    setupAfterPermissionInternet();
                } else {
                    Toast.makeText(this, getString(R.string.internet_required), Toast.LENGTH_LONG).show();
                }
        }
    }

    public void setupActionBarAndDrawer() {
        setSupportActionBar(toolbar);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        navigationView.setNavigationItemSelectedListener(this);
    }

    private void setMusicFragment(boolean replacePreviousFragment) {
        Fragment musicFragment = MusicFragment.newInstance();
        FragmentManager manager = getSupportFragmentManager();
        FragmentTransaction transaction = manager.beginTransaction();

        if (replacePreviousFragment) {
            transaction.replace(R.id.fragment_container, musicFragment);
            transaction.addToBackStack(null);
        } else {
            transaction.add(R.id.fragment_container, musicFragment);
        }

        transaction.commit();
    }

    private void setSettingsFragment() {
        Fragment settingsFragment = SettingsFragment.newInstance();
        FragmentManager manager = getSupportFragmentManager();
        FragmentTransaction transaction = manager.beginTransaction();
        transaction.replace(R.id.fragment_container, settingsFragment);
        transaction.addToBackStack(null);
        transaction.commit();
    }

    private void addNowPlayingFragment() {
        final FragmentManager manager = getSupportFragmentManager();
        NowPlayingFragment nowPlayingFragment = (NowPlayingFragment) manager.findFragmentById(R.id.now_playing_bar_container);
        if (nowPlayingFragment == null) {
            nowPlayingFragment = NowPlayingFragment.newInstance();

            final FragmentTransaction transaction = manager.beginTransaction();
            transaction.add(R.id.now_playing_bar_container, nowPlayingFragment);
            transaction.commit();

            // We have to execute the pending transactions since we're about to update the new fragment's view...
            manager.executePendingTransactions();
        }
    }

    private void removeNowPlayingFragment() {
        final FragmentManager manager = getSupportFragmentManager();
        NowPlayingFragment nowPlayingFragment = (NowPlayingFragment) manager.findFragmentById(R.id.now_playing_bar_container);
        if (nowPlayingFragment != null) {
            final FragmentTransaction transaction = manager.beginTransaction();
            transaction.remove(nowPlayingFragment);
            transaction.commit();
        }
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        if (currentMenuItem == null || currentMenuItem != item.getItemId()
                || item.getItemId() == R.id.nav_about || item.getItemId() == R.id.nav_contact) {
            switch (item.getItemId()) {
                case R.id.nav_music_library:
                    setMusicFragment(true);
                    currentMenuItem = item.getItemId();
                    break;
                case R.id.nav_settings:
                    setSettingsFragment();
                    currentMenuItem = item.getItemId();
                    break;
                case R.id.nav_about:
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setMessage(getString(R.string.app_name)
                            + " " + getString(R.string.version)
                            + " " + getString(R.string.app_version)
                            + "\n" + getString(R.string.about));
                    builder.setPositiveButton(android.R.string.ok, null).create().show();
                    break;
                case R.id.nav_contact:
                    AlertDialog.Builder builderContact = new AlertDialog.Builder(this);
                    builderContact.setMessage(getString(R.string.contact));
                    builderContact.setMessage(Html.fromHtml(
                            "<a href=\"" + getString(R.string.contact) + "\">" + getString(R.string.contact) + "</a>"));
                    AlertDialog dialog = builderContact.setPositiveButton(android.R.string.ok, null).create();
                    dialog.show();
                    ((TextView) dialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
                    break;
            }
        }

        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void songClicked(long songId) {
        new PlayBackIntentBuilder(this)
                .setSong(songId)
                .setPlayerCommand(PLAY)
                .buildAndSubmit();
    }

    @Override
    public void playAlbum(long albumId) {
        new PlayBackIntentBuilder(this)
                .setAlbum(albumId)
                .setPlayerCommand(PLAY)
                .buildAndSubmit();
    }

    @Override
    public void albumClicked(long albumId) {
        Fragment albumFragment = AlbumFragment.newInstance(albumId);
        FragmentManager manager = getSupportFragmentManager();
        FragmentTransaction transaction = manager.beginTransaction();
        transaction.replace(R.id.fragment_container, albumFragment);
        transaction.addToBackStack(null);
        transaction.commit();
    }

    @Override
    public void artistClicked(long artistId) {
        FragmentManager manager = getSupportFragmentManager();
        Fragment fragment = manager.findFragmentById(R.id.fragment_container);
        if (fragment instanceof MusicFragment) {
            MusicFragment musicFragment = (MusicFragment) fragment;
            musicFragment.setCurrentFragment(SubFragmentType.ALBUMS);
            musicFragment.filterAlbums(artistId);
        }
    }

    @Override
    public void onClearMusicCache() {
        final Intent mp3ScanningIntent = new Intent(this, Mp3ScanningService.class);
        stopService(mp3ScanningIntent);
        final Intent fileScannerIntent = new Intent(this, FileScannerService.class);
        stopService(fileScannerIntent);
        final Intent artistFetcherIntent = new Intent(this, ArtistInfoFetcherService.class);
        stopService(artistFetcherIntent);
        final Intent albumFetcherIntent = new Intent(this, AlbumInfoFetcherService.class);
        stopService(albumFetcherIntent);

        final Consumer<Void> servicesStoppedCallback = voidObject -> {
            this.musicDao.deleteAllMusic();

            MusicLibraryBroadcastBuilder builder = new MusicLibraryBroadcastBuilder(this);
            builder.setType(MLBT_ALBUMS_DELETED)
                    .buildAndSubmitBroadcast();
            builder.setType(MLBT_ARTISTS_DELETED)
                    .buildAndSubmitBroadcast();
            builder.setType(MLBT_SONGS_DELETED)
                    .buildAndSubmitBroadcast();

            startService(albumFetcherIntent);
            startService(artistFetcherIntent);
            startService(fileScannerIntent);
            startService(mp3ScanningIntent);
        };

        waitUntilAllServicesAreStopped(servicesStoppedCallback);
    }

    private void waitUntilAllServicesAreStopped(Consumer<Void> servicesStoppedCallback) {
        ProgressDialog dialog = ProgressDialog.show(this, "",
                getString(R.string.wait_while_clear), true);
        dialog.show();

        Observable<String> observable = Observable.create(subscriber -> {
            while (isServiceRunning(Mp3ScanningService.class)
                    || isServiceRunning(FileScannerService.class)
                    || isServiceRunning(ArtistInfoFetcherService.class)
                    || isServiceRunning(AlbumInfoFetcherService.class)) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    subscriber.onError(e);
                }
            }
            subscriber.onComplete();
        });

        observable
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(new Observer<String>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                    }

                    @Override
                    public void onNext(String s) {
                    }

                    @Override
                    public void onError(Throwable e) {
                    }

                    @Override
                    public void onComplete() {
                        dialog.dismiss();
                        try {
                            servicesStoppedCallback.accept(null);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();

        this.playBackStatusReceiver = new PlayBackStatusReceiver();
        IntentFilter filter = new IntentFilter(PBBT_PLAYER_STATUS_UPDATE.name());
        registerReceiver(this.playBackStatusReceiver, filter);

        this.headsetReceiver = new HeadsetReceiver();
        filter = new IntentFilter(AudioManager.ACTION_HEADSET_PLUG);
        registerReceiver(this.headsetReceiver, filter);
    }

    @Override
    protected void onPause() {
        unregisterReceiver(this.playBackStatusReceiver);
        unregisterReceiver(this.headsetReceiver);
        super.onPause();
    }

    private class PlayBackStatusReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO: runOnUiThread(new Runnable() {    ?
            final PlayStatus playStatus = musicDao.getCurrentPlayStatus_NON_Live();
            if (playStatus == STOPPED) {
                removeNowPlayingFragment();
            } else {
                addNowPlayingFragment();
            }
        }
    }

    private class HeadsetReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String intentAction = intent.getAction();
            if (intentAction.equals(AudioManager.ACTION_HEADSET_PLUG)) {
                boolean isPluggedIn = intent.getIntExtra("state", 0) == 1;
                if (!isPluggedIn) {
                    new PlayBackIntentBuilder(MainActivity.this)
                            .setPlayerCommand(PAUSE)
                            .buildAndSubmit();
                }
            }
        }
    }
}
